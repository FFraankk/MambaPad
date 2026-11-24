package com.github.ifeel.keysjoy.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View

/** Local touch acknowledgement; never waits for or changes remote input timing. */
object TouchFeedback {
    private const val KEY = "haptic_feedback"
    fun enabled(context: Context) = context.getSharedPreferences("control_ui", Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("control_ui", Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
    fun perform(view: View, keyboard: Boolean = false) {
        if (enabled(view.context)) view.performHapticFeedback(
            if (keyboard) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
}
