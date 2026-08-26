package com.shadowfall.survivor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class MenuView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val save = context.getSharedPreferences("shadowfall", Context.MODE_PRIVATE)
    private var showHelp = false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(android.graphics.Color.rgb(8, 7, 11))

        paint.color = android.graphics.Color.rgb(22, 19, 29)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = android.graphics.Color.rgb(36, 30, 43)
        for (x in -width..width step 90) canvas.drawRect(w / 2f + x, 0f, w / 2f + x + 2, h, paint)
        for (y in -height..height step 90) canvas.drawRect(0f, h / 2f + y, w, h / 2f + y + 2, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = android.graphics.Color.rgb(225, 190, 105)
        paint.textSize = 44f
        canvas.drawText("SHADOWFALL", w / 2f, 130f, paint)
        paint.color = android.graphics.Color.LTGRAY
        paint.textSize = 18f
        canvas.drawText("SURVIVOR", w / 2f, 160f, paint)
        paint.color = android.graphics.Color.rgb(120, 110, 125)
        paint.textSize = 13f
        canvas.drawText("DESCEND • SURVIVE • DEFEAT THE WARDEN", w / 2f, 190f, paint)

        button(canvas, 90f, 260f, w - 90f, 330f, "PLAY")
        button(canvas, 90f, 350f, w - 90f, 420f, "BEST RUN")
        button(canvas, 90f, 440f, w - 90f, 510f, "HOW TO PLAY")

        val bestKills = save.getInt("bestKills", save.getInt("kills", 0))
        val bestRooms = save.getInt("bestRooms", save.getInt("rooms", 0))
        val bestLevel = save.getInt("bestLevel", save.getInt("level", 1))
        paint.color = android.graphics.Color.rgb(165, 155, 175)
        paint.textSize = 14f
        canvas.drawText("BEST  •  ROOM $bestRooms   KILLS $bestKills   LV $bestLevel", w / 2f, h - 54f, paint)

        if (showHelp) drawHelp(canvas)
        paint.textAlign = Paint.Align.LEFT
        postInvalidateOnAnimation()
    }

    private fun button(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, label: String) {
        paint.color = android.graphics.Color.rgb(42, 36, 51)
        canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.rgb(92, 78, 105)
        canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, (left + right) / 2f, top + 44f, paint)
    }

    private fun drawHelp(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = android.graphics.Color.argb(245, 7, 6, 10)
        canvas.drawRect(24f, 72f, w - 24f, h - 72f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 28f
        canvas.drawText("HOW TO PLAY", w / 2f, 125f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 16f
        paint.color = android.graphics.Color.LTGRAY
        val lines = listOf(
            "Move with the left joystick.",
            "Hold ATTACK to strike the nearest enemy.",
            "Use DASH to escape projectiles and melee attacks.",
            "Clear each room, then open the treasure chest.",
            "After three rooms, face the Warden boss.",
            "Level up and choose upgrades between fights.",
            "Tap anywhere here to close."
        )
        lines.forEachIndexed { i, line -> canvas.drawText(line, 48f, 180f + i * 42f, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x
        val y = event.y
        if (showHelp) {
            showHelp = false
            invalidate()
            return true
        }
        when {
            y in 250f..340f -> context.startActivity(android.content.Intent(context, GameActivity::class.java))
            y in 340f..430f -> showHelp = true
            y in 430f..520f -> showHelp = true
        }
        invalidate()
        return true
    }
}
