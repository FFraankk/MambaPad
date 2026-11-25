package com.github.ifeel.keysjoy.input

/** Existing HID keyboard usages and modifier masks; no new USB protocol messages. */
enum class TouchpadShortcut(val usage: Int, val modifiers: Int) {
    SHOW_DESKTOP(7, 8),
    TASK_VIEW(43, 8),
    NEXT_APP(43, 4),
    PREVIOUS_APP(43, 6),
    NEXT_DESKTOP(79, 9),
    PREVIOUS_DESKTOP(80, 9)
}
