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
    private enum class Phase { COMBAT, CHEST, BOSS, VICTORY }
    private data class Enemy(
        var x: Float, var y: Float, var hp: Float, var maxHp: Float,
        var speed: Float, var kind: Int, var elite: Boolean = false,
        var cooldown: Float = 0f, var telegraph: Float = 0f
    )
    private data class Orb(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var heavy: Boolean = false)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enemies = mutableListOf<Enemy>()
    private val orbs = mutableListOf<Orb>()
    private val rng = Random.Default
    private val save = context.getSharedPreferences("shadowfall", Context.MODE_PRIVATE)

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
    private var roomKills = 0
    private var totalKills = 0
    private var roomsCleared = 0
    private var chestOpen = false
    private var chestRoll = 0
    private var bossPhase = 1
    private var bossEnraged = false
    private var paused = false
    private var gameOver = false
    private var victory = false
    private var choosingUpgrade = false
    private var phase = Phase.COMBAT
    private var lastTime = System.nanoTime()
    private var joystickId = -1
    private var joystickX = 0f
    private var joystickY = 0f
    private var attackDown = false
    private var dashDown = false

    init {
        isFocusable = true
        resetRun()
    }

    private fun resetRun() {
        playerX = 0f; playerY = 0f
        hp = maxHp.coerceAtLeast(100f)
        xp = 0f; xpNeed = 30f; level = 1; attack = 18f; moveSpeed = 260f
        room = 1; roomKills = 0; totalKills = 0; roomsCleared = 0
        chestOpen = false; chestRoll = 0; bossPhase = 1; bossEnraged = false
        paused = false; gameOver = false; victory = false; choosingUpgrade = false
        phase = Phase.COMBAT; enemies.clear(); orbs.clear(); spawnTimer = 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = min(0.033f, (now - lastTime) / 1_000_000_000f)
        lastTime = now
        if (!paused && !gameOver && !victory && !choosingUpgrade) update(dt)
        drawGame(canvas)
        postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        elapsed += dt
        attackCooldown = max(0f, attackCooldown - dt)
        dashCooldown = max(0f, dashCooldown - dt)

        if (phase == Phase.COMBAT) {
            updatePlayer(dt)
            updateEnemies(dt)
            updateProjectiles(dt)
            if (enemies.isEmpty() && roomKills >= roomTarget()) {
                phase = Phase.CHEST
                chestOpen = false
                chestRoll = rng.nextInt(0, 3)
            }
        } else if (phase == Phase.BOSS) {
            updatePlayer(dt)
            updateEnemies(dt)
            updateProjectiles(dt)
            if (enemies.isEmpty()) {
                victory = true
                phase = Phase.VICTORY
                saveRun(true)
            }
        }
    }

    private fun roomTarget(): Int = 5 + room * 3

    private fun updatePlayer(dt: Float) {
        var dx = joystickX
        var dy = joystickY
        val mag = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (mag > 0.01f) {
            dx /= max(1f, mag); dy /= max(1f, mag)
            playerX += dx * moveSpeed * dt
            playerY += dy * moveSpeed * dt
        }
        if (dashDown && dashCooldown <= 0f && mag > 0.1f) {
            playerX += dx * 155f; playerY += dy * 155f; dashCooldown = 1.8f
        }
        playerX = playerX.coerceIn(-width * 0.42f, width * 0.42f)
        playerY = playerY.coerceIn(-height * 0.30f, height * 0.34f)
        if (attackDown && attackCooldown <= 0f) performAttack()
    }

    private fun updateEnemies(dt: Float) {
        if (phase == Phase.COMBAT && roomKills < roomTarget()) {
            spawnTimer -= dt
            val cap = 5 + min(10, room * 2)
            if (spawnTimer <= 0f && enemies.size < cap) {
                spawnEnemy()
                spawnTimer = max(0.45f, 1.15f - room * 0.07f)
            }
        }

        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.cooldown = max(0f, e.cooldown - dt)
            if (e.telegraph > 0f) e.telegraph -= dt
            val ex = e.x - playerX
            val ey = e.y - playerY
            val dist = hypot(ex.toDouble(), ey.toDouble()).toFloat().coerceAtLeast(1f)
            val nx = ex / dist
            val ny = ey / dist

            if (phase == Phase.BOSS && e.elite) {
                updateBoss(e, dt, dist, nx, ny)
            } else if (e.kind == 1) {
                if (dist < 460f && e.cooldown <= 0f) {
                    orbs.add(Orb(e.x, e.y, nx * 190f, ny * 190f, 2.4f))
                    e.cooldown = 2.0f
                }
                if (dist < 150f) { e.x -= nx * e.speed * dt; e.y -= ny * e.speed * dt }
            } else {
                val stop = if (e.kind == 2) 125f else 44f
                if (dist > stop) { e.x += nx * e.speed * dt; e.y += ny * e.speed * dt }
                if (dist <= stop + 12f && e.cooldown <= 0f) {
                    damagePlayer(if (e.elite) 14f else if (e.kind == 2) 8f else 6f)
                    e.cooldown = if (e.kind == 2) 1.1f else 0.8f
                }
            }
        }
    }

    private fun updateBoss(e: Enemy, dt: Float, dist: Float, nx: Float, ny: Float) {
        val hpRatio = e.hp / e.maxHp
        bossPhase = when {
            hpRatio <= 0.33f -> 3
            hpRatio <= 0.66f -> 2
            else -> 1
        }
        bossEnraged = bossPhase == 3
        val targetDistance = if (bossPhase == 1) 150f else 190f
        if (dist > targetDistance) {
            e.x += nx * e.speed * dt * (if (bossPhase == 3) 1.45f else 1f)
            e.y += ny * e.speed * dt * (if (bossPhase == 3) 1.45f else 1f)
        }
        if (e.telegraph <= 0f && e.cooldown <= 0f) {
            val count = if (bossPhase == 3) 8 else if (bossPhase == 2) 6 else 4
            val speed = if (bossPhase == 3) 250f else 210f
            repeat(count) { i ->
                val a = (Math.PI * 2.0 * i / count).toFloat() + elapsed * 0.25f
                orbs.add(Orb(e.x, e.y, cos(a) * speed, sin(a) * speed, 2.5f, true))
            }
            e.cooldown = if (bossPhase == 3) 1.35f else 1.8f
            e.telegraph = if (bossPhase == 3) 0.45f else 0.65f
        }
        if (dist < 125f && e.cooldown <= 0.2f) damagePlayer(if (bossPhase == 3) 15f else 10f)
    }

    private fun updateProjectiles(dt: Float) {
        val iterator = orbs.iterator()
        while (iterator.hasNext()) {
            val o = iterator.next()
            o.x += o.vx * dt; o.y += o.vy * dt; o.life -= dt
            val hit = hypot((o.x - playerX).toDouble(), (o.y - playerY).toDouble()) < 30f
            if (hit) {
                damagePlayer(if (o.heavy) 12f else 10f)
                iterator.remove()
            } else if (o.life <= 0f) iterator.remove()
        }
    }

    private fun damagePlayer(amount: Float) {
        hp -= amount
        if (hp <= 0f) {
            hp = 0f; gameOver = true; saveRun(false)
        }
    }

    private fun performAttack() {
        attackCooldown = 0.38f
        var best: Enemy? = null
        var bestDist = 225f
        for (e in enemies) {
            val d = hypot((e.x - playerX).toDouble(), (e.y - playerY).toDouble()).toFloat()
            if (d < bestDist) { best = e; bestDist = d }
        }
        best?.let { target ->
            target.hp -= attack
            if (target.hp <= 0f) {
                val wasBoss = phase == Phase.BOSS && target.elite
                enemies.remove(target)
                totalKills++
                roomKills++
                xp += if (wasBoss) 100f else if (target.elite) 25f else 8f
                checkLevelUp()
                if (!wasBoss && roomKills >= roomTarget() && enemies.isEmpty()) {
                    phase = Phase.CHEST
                    chestOpen = false
                    chestRoll = rng.nextInt(0, 3)
                }
            }
        }
    }

    private fun checkLevelUp() {
        while (xp >= xpNeed) {
            xp -= xpNeed
            level++
            xpNeed *= 1.35f
            choosingUpgrade = true
            break
        }
    }

    private fun spawnEnemy() {
        val angle = rng.nextDouble(0.0, Math.PI * 2).toFloat()
        val radius = rng.nextFloat() * 180f + 330f
        val kind = rng.nextInt(0, 3)
        val elite = room >= 2 && rng.nextFloat() < min(0.18f, 0.07f + room * 0.025f)
        val base = if (elite) 85f + room * 14f else 30f + room * 9f
        enemies.add(Enemy(
            cos(angle) * radius,
            sin(angle) * radius * 0.65f,
            base, base,
            if (kind == 2) 88f else 65f,
            kind, elite
        ))
    }

    private fun startBoss() {
        phase = Phase.BOSS
        room = 4
        roomKills = 0
        bossPhase = 1
        bossEnraged = false
        enemies.clear()
        orbs.clear()
        enemies.add(Enemy(0f, -240f, 900f, 900f, 48f, 2, true, 0f, 1.0f))
    }

    private fun openChest() {
        if (chestOpen) return
        chestOpen = true
        when (chestRoll) {
            0 -> { maxHp += 20f; hp = min(maxHp, hp + 35f) }
            1 -> attack += 7f
            else -> moveSpeed += 30f
        }
    }

    private fun continueFromChest() {
        if (!chestOpen) { openChest(); return }
        roomsCleared++
        if (roomsCleared >= 3) {
            startBoss()
            return
        }
        room++
        roomKills = 0
        phase = Phase.COMBAT
        chestOpen = false
        spawnTimer = 0.35f
    }

    private fun drawGame(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        canvas.drawColor(android.graphics.Color.rgb(9, 8, 13))
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(22, 19, 29)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.rgb(35, 31, 42)
        for (x in -width..width step 80) canvas.drawRect(cx + x, 0f, cx + x + 2, height.toFloat(), paint)
        for (y in -height..height step 80) canvas.drawRect(0f, cy + y, width.toFloat(), cy + y + 2, paint)

        canvas.save(); canvas.translate(cx, cy)
        for (o in orbs) drawOrb(canvas, o)
        for (e in enemies) drawEnemy(canvas, e)
        drawPlayer(canvas)
        canvas.restore()

        drawHud(canvas)
        if (phase == Phase.CHEST) drawChest(canvas)
        if (choosingUpgrade) drawUpgrade(canvas)
        if (paused) drawOverlay(canvas, "PAUSED", "Tap PAUSE to resume")
        if (gameOver) drawOverlay(canvas, "YOU DIED", "Tap anywhere to restart")
        if (victory) drawOverlay(canvas, "DUNGEON CLEARED", "Tap anywhere to play again")
    }

    private fun drawPlayer(canvas: Canvas) {
        paint.color = android.graphics.Color.rgb(210, 180, 120)
        canvas.drawCircle(playerX, playerY, 24f, paint)
        paint.color = android.graphics.Color.rgb(68, 43, 35)
        canvas.drawCircle(playerX, playerY - 8f, 18f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(playerX, playerY, 27f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawOrb(canvas: Canvas, o: Orb) {
        paint.color = if (o.heavy) android.graphics.Color.rgb(255, 105, 75) else android.graphics.Color.rgb(190, 90, 255)
        canvas.drawCircle(o.x, o.y, if (o.heavy) 10f else 8f, paint)
    }

    private fun drawEnemy(canvas: Canvas, e: Enemy) {
        val boss = phase == Phase.BOSS && e.elite
        paint.color = when {
            boss && bossEnraged -> android.graphics.Color.rgb(235, 55, 70)
            boss -> android.graphics.Color.rgb(175, 55, 70)
            e.elite -> android.graphics.Color.rgb(190, 70, 80)
            e.kind == 1 -> android.graphics.Color.rgb(100, 110, 190)
            e.kind == 2 -> android.graphics.Color.rgb(125, 75, 55)
            else -> android.graphics.Color.rgb(80, 145, 90)
        }
        canvas.drawCircle(e.x, e.y, if (boss) 48f else if (e.elite) 30f else 20f, paint)
        if (boss && e.telegraph > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = android.graphics.Color.rgb(245, 120, 80)
            canvas.drawCircle(e.x, e.y, 72f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = android.graphics.Color.rgb(20, 15, 20)
        val barW = if (boss) 90f else 44f
        val left = e.x - barW / 2f
        canvas.drawRect(left, e.y - if (boss) 62f else 34f, left + barW, e.y - if (boss) 56f else 29f, paint)
        paint.color = android.graphics.Color.rgb(220, 65, 65)
        canvas.drawRect(left, e.y - if (boss) 62f else 34f, left + barW * (e.hp / e.maxHp).coerceIn(0f, 1f), e.y - if (boss) 56f else 29f, paint)
    }

    private fun drawHud(canvas: Canvas) {
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 21f; paint.color = android.graphics.Color.WHITE
        val title = if (phase == Phase.BOSS) "SHADOWFALL  •  THE WARDEN" else "SHADOWFALL  •  ROOM $room"
        canvas.drawText(title, 24f, 36f, paint)
        paint.textSize = 15f
        canvas.drawText(if (phase == Phase.BOSS) "PHASE $bossPhase" else "CLEARS $roomsCleared/3   KILLS $roomKills/${roomTarget()}", 24f, 60f, paint)

        paint.color = android.graphics.Color.rgb(70, 20, 25); canvas.drawRect(24f, 75f, 244f, 94f, paint)
        paint.color = android.graphics.Color.rgb(205, 55, 65); canvas.drawRect(24f, 75f, 24f + 220f * hp / maxHp, 94f, paint)
        paint.color = android.graphics.Color.rgb(45, 35, 70); canvas.drawRect(24f, 100f, 244f, 114f, paint)
        paint.color = android.graphics.Color.rgb(130, 95, 220); canvas.drawRect(24f, 100f, 24f + 220f * xp / xpNeed, 114f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 13f
        canvas.drawText("HP ${hp.toInt()}/${maxHp.toInt()}   LV $level   DMG ${attack.toInt()}", 30f, 90f, paint)

        paint.color = android.graphics.Color.rgb(42, 48, 58); canvas.drawCircle(100f, height - 115f, 72f, paint)
        paint.color = android.graphics.Color.rgb(90, 95, 110); canvas.drawCircle(100f + joystickX * 45f, height - 115f + joystickY * 45f, 30f, paint)
        paint.color = android.graphics.Color.rgb(120, 45, 55); canvas.drawCircle(width - 105f, height - 125f, 64f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 17f; canvas.drawText("ATTACK", width - 138f, height - 119f, paint)
        paint.color = android.graphics.Color.rgb(60, 65, 75); canvas.drawCircle(width - 220f, height - 55f, 36f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 13f; canvas.drawText("DASH", width - 244f, height - 50f, paint)
        paint.color = android.graphics.Color.rgb(45, 40, 50); canvas.drawRoundRect(RectF(width - 125f, 18f, width - 28f, 58f), 12f, 12f, paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 15f; canvas.drawText(if (paused) "RESUME" else "PAUSE", width - 110f, 44f, paint)
    }

    private fun drawChest(canvas: Canvas) {
        paint.color = android.graphics.Color.argb(238, 5, 4, 8)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.rgb(215, 170, 80)
        canvas.drawRoundRect(RectF(width / 2f - 90f, height / 2f - 65f, width / 2f + 90f, height / 2f + 35f), 18f, 18f, paint)
        paint.color = android.graphics.Color.rgb(80, 45, 25)
        canvas.drawRect(width / 2f - 90f, height / 2f - 15f, width / 2f + 90f, height / 2f + 35f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 27f
        val title = if (!chestOpen) "TREASURE" else "REWARD"
        canvas.drawText(title, width / 2f - paint.measureText(title) / 2f, height / 2f - 95f, paint)
        paint.textSize = 17f
        val reward = when (chestRoll) { 0 -> "IRON HEART  •  +20 MAX HP"; 1 -> "RAVENOUS EDGE  •  +7 DAMAGE"; else -> "WIND STEP  •  +30 SPEED" }
        if (chestOpen) canvas.drawText(reward, width / 2f - paint.measureText(reward) / 2f, height / 2f + 80f, paint)
        val hint = if (!chestOpen) "TAP CHEST TO OPEN" else if (roomsCleared >= 2) "TAP TO FACE THE WARDEN" else "TAP TO ENTER THE NEXT ROOM"
        paint.textSize = 15f
        canvas.drawText(hint, width / 2f - paint.measureText(hint) / 2f, height / 2f + 120f, paint)
    }

    private fun drawUpgrade(canvas: Canvas) {
        paint.color = android.graphics.Color.argb(240, 5, 4, 8); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 30f
        canvas.drawText("LEVEL UP", width / 2f - 80f, 145f, paint)
        paint.textSize = 16f; canvas.drawText("Choose one upgrade", width / 2f - 78f, 175f, paint)
        drawCard(canvas, 60f, 215f, "IRON HEART", "+25 maximum health")
        drawCard(canvas, 60f, 315f, "RAVENOUS EDGE", "+8 attack damage")
        drawCard(canvas, 60f, 415f, "WIND STEP", "+35 movement speed")
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, title: String, desc: String) {
        paint.color = android.graphics.Color.rgb(37, 32, 45); canvas.drawRoundRect(RectF(x, y, width - 60f, y + 78f), 14f, 14f, paint)
        paint.color = android.graphics.Color.rgb(215, 170, 80); paint.textSize = 18f; canvas.drawText(title, x + 18f, y + 30f, paint)
        paint.color = android.graphics.Color.LTGRAY; paint.textSize = 14f; canvas.drawText(desc, x + 18f, y + 55f, paint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, hint: String) {
        paint.color = android.graphics.Color.argb(225, 5, 4, 8); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.WHITE; paint.textSize = 32f
        canvas.drawText(title, width / 2f - paint.measureText(title) / 2f, height / 2f - 20f, paint)
        paint.textSize = 16f
        canvas.drawText(hint, width / 2f - paint.measureText(hint) / 2f, height / 2f + 20f, paint)
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
            if (y in 200f..510f) {
                when { y < 300f -> maxHp += 25f; y < 400f -> attack += 8f; else -> moveSpeed += 35f }
                hp = min(maxHp, hp + 25f)
                choosingUpgrade = false
            }
            return
        }
        if (phase == Phase.CHEST) {
            val cx = width / 2f; val cy = height / 2f
            if (hypot((x - cx).toDouble(), (y - cy).toDouble()) < 180.0) {
                if (!chestOpen) openChest() else continueFromChest()
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

    fun pauseGame() { paused = true; saveRun(false) }
    fun resumeGame() { if (!gameOver && !victory) paused = false }

    private fun saveRun(won: Boolean) {
        save.edit()
            .putInt("bestLevel", max(save.getInt("bestLevel", 1), level))
            .putInt("bestKills", max(save.getInt("bestKills", 0), totalKills))
            .putInt("roomsCleared", max(save.getInt("roomsCleared", 0), roomsCleared + if (won) 3 else 0))
            .apply()
    }
}
