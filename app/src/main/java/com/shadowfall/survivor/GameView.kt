package com.shadowfall.survivor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private data class Enemy(var x: Float, var y: Float, var hp: Float, var maxHp: Float, var speed: Float, var kind: Int, var elite: Boolean = false, var cooldown: Float = 0f)
    private data class Orb(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enemies = mutableListOf<Enemy>()
    private val orbs = mutableListOf<Orb>()
    private val rng = Random.Default
    private var playerX = 0f
    private var playerY = 0f
    private var hp = 100f
    private var maxHp = 100f
    private var xp = 0f
    private var xpNeed = 30f
    private var level = 1
    private var attack = 18f
    private var moveSpeed = 260f
    private var attackCooldown = 0f
    private var dashCooldown = 0f
    private var spawnTimer = 0f
    private var elapsed = 0f
    private var room = 1
    private var kills = 0
    private var paused = false
    private var gameOver = false
    private var victory = false
    private var choosingUpgrade = false
    private var lastTime = System.nanoTime()
    private var joystickId = -1
    private var joystickX = 0f
    private var joystickY = 0f
    private var attackDown = false
    private var dashDown = false

    private val save = context.getSharedPreferences("shadowfall", Context.MODE_PRIVATE)

    init {
        isFocusable = true
        resetRun()
    }

    private fun resetRun() {
        playerX = 0f; playerY = 0f; hp = maxHp; xp = 0f; level = 1; attack = 18f
        xpNeed = 30f; room = 1; kills = 0; elapsed = 0f; gameOver = false; victory = false
        choosingUpgrade = false; paused = false; enemies.clear(); orbs.clear()
        spawnTimer = 0.3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dt = min(0.033f, (System.nanoTime() - lastTime) / 1_000_000_000f)
        lastTime = System.nanoTime()
        if (!paused && !gameOver && !victory && !choosingUpgrade) update(dt)
        drawGame(canvas)
        postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        elapsed += dt
        attackCooldown = max(0f, attackCooldown - dt)
        dashCooldown = max(0f, dashCooldown - dt)
        spawnTimer -= dt
        if (spawnTimer <= 0f && enemies.size < 18) {
            spawnEnemy()
            spawnTimer = max(0.35f, 1.25f - room * 0.05f)
        }

        var dx = joystickX
        var dy = joystickY
        val mag = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (mag > 0.01f) {
            dx /= max(1f, mag); dy /= max(1f, mag)
            playerX += dx * moveSpeed * dt
            playerY += dy * moveSpeed * dt
        }
        if (dashDown && dashCooldown <= 0f && mag > 0.1f) {
            playerX += dx * 150f; playerY += dy * 150f; dashCooldown = 1.8f
        }
        playerX = playerX.coerceIn(-width * 0.42f, width * 0.42f)
        playerY = playerY.coerceIn(-height * 0.32f, height * 0.35f)

        if (attackDown && attackCooldown <= 0f) performAttack()

        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.cooldown = max(0f, e.cooldown - dt)
            val ex = e.x - playerX; val ey = e.y - playerY
            val dist = hypot(ex.toDouble(), ey.toDouble()).toFloat().coerceAtLeast(1f)
            if (e.kind == 1) {
                if (dist < 420f && e.cooldown <= 0f) {
                    val nx = ex / dist; val ny = ey / dist
                    orbs.add(Orb(e.x, e.y, nx * 180f, ny * 180f, 2.2f)); e.cooldown = 2.2f
                }
            } else {
                val nx = ex / dist; val ny = ey / dist
                val stop = if (e.kind == 2) 125f else 42f
                if (dist > stop) { e.x += nx * e.speed * dt; e.y += ny * e.speed * dt }
                if (dist <= stop + 15f && e.cooldown <= 0f) {
                    hp -= if (e.elite) 12f else 6f
                    e.cooldown = if (e.kind == 2) 1.1f else 0.8f
                    if (hp <= 0f) { hp = 0f; gameOver = true; saveRun() }
                }
            }
        }

        val oi = orbs.iterator()
        while (oi.hasNext()) {
            val o = oi.next(); o.x += o.vx * dt; o.y += o.vy * dt; o.life -= dt
            if (hypot((o.x - playerX).toDouble(), (o.y - playerY).toDouble()) < 28 || o.life <= 0f) {
                if (o.life > 0f) hp -= 10f
                oi.remove()
                if (hp <= 0f) { hp = 0f; gameOver = true; saveRun() }
            }
        }

        if (elapsed > room * 22f && enemies.none { it.elite }) {
            room++
            if (room >= 4) spawnBoss()
        }
        if (room >= 4 && enemies.none { it.elite } && kills >= 28) victory = true
    }

    private fun performAttack() {
        attackCooldown = 0.38f
        var best: Enemy? = null; var bestDist = 220f
        for (e in enemies) {
            val d = hypot((e.x - playerX).toDouble(), (e.y - playerY).toDouble()).toFloat()
            if (d < bestDist) { best = e; bestDist = d }
        }
        best?.let { target ->
            target.hp -= attack
            if (target.hp <= 0f) {
                enemies.remove(target); kills++; xp += if (target.elite) 25f else 8f
                if (target.elite) room++
                if (xp >= xpNeed) {
                    xp -= xpNeed; level++; xpNeed *= 1.35f; choosingUpgrade = true
                }
            }
        }
    }

    private fun spawnEnemy() {
        val angle = rng.nextDouble(0.0, Math.PI * 2).toFloat()
        val radius = rng.nextFloat() * 220f + 300f
        val kind = rng.nextInt(0, 3)
        val elite = room >= 2 && rng.nextFloat() < 0.10f
        val base = if (elite) 80f + room * 12f else 32f + room * 8f
        enemies.add(Enemy(cos(angle) * radius, sin(angle) * radius * 0.65f, base, base, if (kind == 2) 90f else 65f, kind, elite))
    }

    private fun spawnBoss() {
        enemies.add(Enemy(0f, -250f, 650f, 650f, 45f, 2, true))
    }

    private fun drawGame(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        canvas.drawColor(android.graphics.Color.rgb(9, 8, 13))
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(23, 20, 30)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.rgb(31, 28, 38)
        for (x in -width..width step 80) canvas.drawRect(cx + x, 0f, cx + x + 2, height.toFloat(), paint)
        for (y in -height..height step 80) canvas.drawRect(0f, cy + y, width.toFloat(), cy + y + 2, paint)

        canvas.save(); canvas.translate(cx, cy)
        for (e in enemies) drawEnemy(canvas, e)
        for (o in orbs) { paint.color = android.graphics.Color.rgb(190, 90, 255); canvas.drawCircle(o.x, o.y, 8f, paint) }
        paint.color = android.graphics.Color.rgb(210, 180, 120); canvas.drawCircle(playerX, playerY, 24f, paint)
        paint.color = android.graphics.Color.rgb(70, 45, 35); canvas.drawCircle(playerX, playerY - 7, 18f, paint)
        canvas.restore()

        drawHud(canvas)
        if (choosingUpgrade) drawUpgrade(canvas)
        if (paused) drawOverlay(canvas, "PAUSED", "Tap PAUSE to resume")
        if (gameOver) drawOverlay(canvas, "YOU DIED", "Tap RESTART")
        if (victory) drawOverlay(canvas, "DUNGEON CLEARED", "Tap RESTART")
    }

    private fun drawEnemy(canvas: Canvas, e: Enemy) {
        paint.color = when { e.elite -> android.graphics.Color.rgb(190, 70, 80); e.kind == 1 -> android.graphics.Color.rgb(100, 110, 190); e.kind == 2 -> android.graphics.Color.rgb(125, 75, 55); else -> android.graphics.Color.rgb(80, 145, 90) }
        canvas.drawCircle(e.x, e.y, if (e.elite) 30f else 20f, paint)
        paint.color = android.graphics.Color.rgb(20, 15, 20)
        canvas.drawRect(e.x - 22, e.y - 34, e.x + 22, e.y - 29, paint)
        paint.color = android.graphics.Color.rgb(220, 65, 65)
        canvas.drawRect(e.x - 22, e.y - 34, e.x - 22 + 44 * (e.hp / e.maxHp).coerceIn(0f,1f), e.y - 29, paint)
    }

    private fun drawHud(canvas: Canvas) {
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 22f; paint.color = android.graphics.Color.WHITE
        canvas.drawText("SHADOWFALL  •  ROOM $room", 28f, 38f, paint)
        canvas.drawText("LV $level   KILLS $kills", 28f, 66f, paint)
        paint.color = android.graphics.Color.rgb(70, 20, 25); canvas.drawRect(28f, 82f, 248f, 100f, paint)
        paint.color = android.graphics.Color.rgb(205, 55, 65); canvas.drawRect(28f, 82f, 28f + 220f * hp / maxHp, 100f, paint)
        paint.color = android.graphics.Color.rgb(45, 35, 70); canvas.drawRect(28f, 106f, 248f, 120f, paint)
        paint.color = android.graphics.Color.rgb(130, 95, 220); canvas.drawRect(28f, 106f, 28f + 220f * xp / xpNeed, 120f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 14f; canvas.drawText("HP ${hp.toInt()}/${maxHp.toInt()}", 34f, 96f, paint)

        paint.color = android.graphics.Color.rgb(42, 48, 58); canvas.drawCircle(100f, height - 115f, 72f, paint)
        paint.color = android.graphics.Color.rgb(90, 95, 110); canvas.drawCircle(100f + joystickX * 45f, height - 115f + joystickY * 45f, 30f, paint)
        paint.color = android.graphics.Color.rgb(120, 45, 55); canvas.drawCircle(width - 105f, height - 125f, 64f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 18f; canvas.drawText("ATTACK", width - 138f, height - 119f, paint)
        paint.color = android.graphics.Color.rgb(60, 65, 75); canvas.drawCircle(width - 220f, height - 55f, 36f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 13f; canvas.drawText("DASH", width - 244f, height - 50f, paint)
        paint.color = android.graphics.Color.rgb(45, 40, 50); canvas.drawRoundRect(RectF(width - 125f, 18f, width - 28f, 58f), 12f, 12f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 15f; canvas.drawText(if (paused) "RESUME" else "PAUSE", width - 110f, 44f, paint)
    }

    private fun drawUpgrade(canvas: Canvas) {
        paint.color = android.graphics.Color.argb(235, 5, 4, 8); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 30f; canvas.drawText("LEVEL UP", width / 2f - 80f, 150f, paint)
        paint.textSize = 16f; canvas.drawText("Choose one upgrade", width / 2f - 78f, 180f, paint)
        drawCard(canvas, 70f, 230f, "IRON HEART", "+25 maximum health")
        drawCard(canvas, 70f, 330f, "RAVENOUS EDGE", "+8 attack damage")
        drawCard(canvas, 70f, 430f, "WIND STEP", "+35 movement speed")
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, title: String, desc: String) {
        paint.color = android.graphics.Color.rgb(37, 32, 45); canvas.drawRoundRect(RectF(x, y, width - 70f, y + 78f), 14f, 14f, paint)
        paint.color = android.graphics.Color.rgb(215, 170, 80); paint.textSize = 18f; canvas.drawText(title, x + 18f, y + 30f, paint)
        paint.color = android.graphics.Color.LTGRAY; paint.textSize = 14f; canvas.drawText(desc, x + 18f, y + 55f, paint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, hint: String) {
        paint.color = android.graphics.Color.argb(220, 5, 4, 8); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 32f; canvas.drawText(title, width / 2f - paint.measureText(title) / 2, height / 2f - 20, paint)
        paint.textSize = 16f; canvas.drawText(hint, width / 2f - paint.measureText(hint) / 2, height / 2f + 20, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> handleDown(event.getActionIndex(), x, y)
            MotionEvent.ACTION_MOVE -> {
                if (joystickId >= 0) {
                    val i = findPointer(event, joystickId)
                    if (i >= 0) updateJoystick(event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> handleUp(event.getActionIndex(), event.getPointerId(event.getActionIndex()), x, y)
        }
        return true
    }

    private fun handleDown(index: Int, x: Float, y: Float) {
        if (choosingUpgrade) {
            if (y in 220f..535f) {
                when {
                    y < 320f -> maxHp += 25f
                    y < 420f -> attack += 8f
                    else -> moveSpeed += 35f
                }
                hp = maxHp.coerceAtMost(hp + 25f); choosingUpgrade = false
            }
            return
        }
        if (gameOver || victory) { resetRun(); return }
        if (x > width - 145 && y > height - 195) { attackDown = true; return }
        if (x > width - 260 && y > height - 100) { dashDown = true; return }
        if (x > width - 145 && y < 80) { paused = !paused; return }
        if (x < 210 && y > height - 210) { joystickId = index; updateJoystick(x, y); return }
    }

    private fun handleUp(index: Int, id: Int, x: Float, y: Float) {
        if (id == joystickId) { joystickId = -1; joystickX = 0f; joystickY = 0f }
        if (x > width - 145 && y > height - 195) attackDown = false
        if (x > width - 260 && y > height - 100) dashDown = false
    }

    private fun updateJoystick(x: Float, y: Float) {
        val ox = 100f; val oy = height - 115f
        joystickX = ((x - ox) / 72f).coerceIn(-1f, 1f)
        joystickY = ((y - oy) / 72f).coerceIn(-1f, 1f)
    }

    private fun findPointer(event: MotionEvent, id: Int): Int {
        for (i in 0 until event.pointerCount) if (event.getPointerId(i) == id) return i
        return -1
    }

    fun pauseGame() { paused = true; saveRun() }
    fun resumeGame() { if (!gameOver && !victory) paused = false }

    private fun saveRun() {
        save.edit().putInt("level", level).putInt("kills", kills).putFloat("maxHp", maxHp).apply()
    }
}
