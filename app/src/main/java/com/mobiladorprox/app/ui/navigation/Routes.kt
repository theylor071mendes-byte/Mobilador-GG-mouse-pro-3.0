package com.mobiladorprox.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val GAME_PICKER = "game_picker"
    const val MENU = "menu"
    const val KEYMAPS = "keymaps/{profileId}"
    const val OPTIMIZATION = "optimization"
    const val PERFORMANCE = "performance"
    const val MOUSE_SETTINGS = "mouse_settings"

    fun keymaps(profileId: Long) = "keymaps/$profileId"
}
