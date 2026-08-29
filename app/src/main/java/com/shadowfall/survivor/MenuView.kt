package com.shadowfall.survivor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class MenuView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val save = context.getSharedPreferences("shadowfall", Context.MODE_PRIVATE)
    private var screen = Screen.MENU
    private enum class Screen { MENU, BEST, HELP, FORGE }

    init { MetaProgression.init(context) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawColor(Color.rgb(8, 7, 11))
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(22, 19, 29); canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = Color.rgb(36, 30, 43)
        for (x in -width..width step 90) canvas.drawRect(w / 2f + x, 0f, w / 2f + x + 2, h, paint)
        for (y in -height..height step 90) canvas.drawRect(0f, h / 2f + y, w, h / 2f + y + 2, paint)
        when (screen) {
            Screen.MENU -> drawMenu(canvas)
            Screen.BEST -> drawBestRun(canvas)
            Screen.HELP -> drawHelp(canvas)
            Screen.FORGE -> drawForge(canvas)
        }
        postInvalidateOnAnimation()
    }

    private fun drawMenu(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.textAlign = Paint.Align.CENTER; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.rgb(225, 190, 105); paint.textSize = 44f; canvas.drawText("SHADOWFALL", w / 2f, 115f, paint)
        paint.color = Color.LTGRAY; paint.textSize = 18f; canvas.drawText("SURVIVOR", w / 2f, 145f, paint)
        paint.color = Color.rgb(120, 110, 125); paint.textSize = 13f; canvas.drawText("DESCEND • SURVIVE • DEFEAT THE WARDEN", w / 2f, 173f, paint)
        button(canvas, 90f, 220f, w - 90f, 290f, "PLAY")
        button(canvas, 90f, 305f, w - 90f, 375f, "SOUL FORGE")
        button(canvas, 90f, 390f, w - 90f, 460f, "BEST RUN")
        button(canvas, 90f, 475f, w - 90f, 545f, "HOW TO PLAY")
        paint.color = Color.rgb(225, 190, 105); paint.textSize = 15f; canvas.drawText("SOULS  ${MetaProgression.souls()}", w / 2f, h - 54f, paint)
        paint.color = Color.rgb(165, 155, 175); paint.textSize = 12f
        canvas.drawText("BEST • ROOMS ${save.getInt("roomsCleared", 0)}   KILLS ${save.getInt("bestKills", 0)}   LV ${save.getInt("bestLevel", 1)}", w / 2f, h - 32f, paint)
    }

    private fun drawBestRun(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.textAlign = Paint.Align.CENTER; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE; paint.textSize = 30f; canvas.drawText("BEST RUN", w / 2f, 110f, paint)
        paint.color = Color.rgb(150, 135, 165); paint.textSize = 14f; canvas.drawText("Your strongest recorded run", w / 2f, 138f, paint)
        statCard(canvas, 55f, 190f, w - 55f, 275f, "ROOMS CLEARED", save.getInt("roomsCleared", 0).toString())
        statCard(canvas, 55f, 295f, w - 55f, 380f, "TOTAL KILLS", save.getInt("bestKills", 0).toString())
        statCard(canvas, 55f, 400f, w - 55f, 485f, "HIGHEST LEVEL", save.getInt("bestLevel", 1).toString())
        button(canvas, 90f, minOf(h - 105f, 535f), w - 90f, minOf(h - 35f, 605f), "BACK")
    }

    private fun statCard(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, label: String, value: String) {
        paint.color = Color.rgb(39, 33, 48); canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        paint.color = Color.rgb(120, 105, 135); paint.textAlign = Paint.Align.LEFT; paint.textSize = 13f; canvas.drawText(label, left + 20f, top + 25f, paint)
        paint.color = Color.rgb(225, 190, 105); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; paint.textSize = 27f; canvas.drawText(value, left + 20f, top + 58f, paint)
        paint.textAlign = Paint.Align.CENTER
    }

    private fun drawForge(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.textAlign = Paint.Align.CENTER; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.rgb(225, 190, 105); paint.textSize = 32f; canvas.drawText("SOUL FORGE", w / 2f, 95f, paint)
        paint.color = Color.LTGRAY; paint.textSize = 16f; canvas.drawText("Permanent upgrades carry into every run", w / 2f, 125f, paint)
        paint.color = Color.WHITE; paint.textSize = 22f; canvas.drawText("SOULS  ${MetaProgression.souls()}", w / 2f, 170f, paint)
        forgeCard(canvas, 45f, 210f, "VITALITY", "${MetaProgression.level(MetaProgression.Upgrade.VITALITY)} / 20", "+10 MAX HP", MetaProgression.cost(MetaProgression.Upgrade.VITALITY))
        forgeCard(canvas, 45f, 330f, "MIGHT", "${MetaProgression.level(MetaProgression.Upgrade.MIGHT)} / 20", "+2 ATTACK", MetaProgression.cost(MetaProgression.Upgrade.MIGHT))
        forgeCard(canvas, 45f, 450f, "SWIFTNESS", "${MetaProgression.level(MetaProgression.Upgrade.SWIFTNESS)} / 20", "+8 MOVE SPEED", MetaProgression.cost(MetaProgression.Upgrade.SWIFTNESS))
        button(canvas, 90f, minOf(h - 95f, 585f), w - 90f, minOf(h - 25f, 655f), "BACK")
    }

    private fun forgeCard(canvas: Canvas, x: Float, y: Float, title: String, levelText: String, effect: String, cost: Int) {
        val right = width - 45f
        paint.color = Color.rgb(39, 33, 48); canvas.drawRoundRect(RectF(x, y, right, y + 92f), 16f, 16f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(225, 190, 105); paint.textSize = 18f; canvas.drawText(title, x + 18f, y + 27f, paint)
        paint.color = Color.LTGRAY; paint.textSize = 13f; canvas.drawText(levelText, x + 18f, y + 50f, paint); canvas.drawText(effect, x + 18f, y + 70f, paint)
        paint.textAlign = Paint.Align.RIGHT; paint.color = Color.WHITE; paint.textSize = 14f; canvas.drawText(if (cost > 0) "BUY • $cost" else "MAX", right - 18f, y + 48f, paint); paint.textAlign = Paint.Align.CENTER
    }

    private fun drawHelp(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = Color.argb(245, 7, 6, 10); canvas.drawRect(24f, 60f, w - 24f, h - 60f, paint)
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD; paint.textSize = 28f; canvas.drawText("HOW TO PLAY", w / 2f, 115f, paint)
        paint.textAlign = Paint.Align.LEFT; paint.typeface = android.graphics.Typeface.DEFAULT; paint.textSize = 16f; paint.color = Color.LTGRAY
        listOf("Move with the left joystick.", "Hold ATTACK to strike the nearest enemy.", "Use DASH to escape attacks.", "Clear rooms and open treasure chests.", "Choose upgrades when you level up.", "Defeat the Warden to win the run.", "Survive runs to earn Souls.", "Spend Souls in the Soul Forge.", "Tap anywhere to return.").forEachIndexed { i, line -> canvas.drawText(line, 48f, 165f + i * 38f, paint) }
    }

    private fun button(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, label: String) {
        paint.color = Color.rgb(42, 36, 51); canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = Color.rgb(92, 78, 105); canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textSize = 20f; paint.textAlign = Paint.Align.CENTER; canvas.drawText(label, (left + right) / 2f, top + 44f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        when (screen) {
            Screen.MENU -> when {
                y in 205f..300f -> context.startActivity(android.content.Intent(context, GameActivity::class.java))
                y in 300f..385f -> screen = Screen.FORGE
                y in 385f..470f -> screen = Screen.BEST
                y in 470f..555f -> screen = Screen.HELP
            }
            Screen.BEST, Screen.HELP -> screen = Screen.MENU
            Screen.FORGE -> {
                when {
                    y in 200f..315f -> MetaProgression.purchase(MetaProgression.Upgrade.VITALITY)
                    y in 315f..435f -> MetaProgression.purchase(MetaProgression.Upgrade.MIGHT)
                    y in 435f..555f -> MetaProgression.purchase(MetaProgression.Upgrade.SWIFTNESS)
                    y >= 555f -> screen = Screen.MENU
                }
            }
        }
        invalidate(); return true
    }
}
