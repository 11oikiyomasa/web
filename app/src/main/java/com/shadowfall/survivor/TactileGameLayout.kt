package com.shadowfall.survivor

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout

/** Adds lightweight tactile confirmation to the game's touch surface. */
class TactileGameLayout(context: Context) : FrameLayout(context) {
    private var lastFeedbackAt = 0L

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastFeedbackAt >= 70L) {
                performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.CONFIRM
                    else
                        HapticFeedbackConstants.VIRTUAL_KEY
                )
                lastFeedbackAt = now
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
