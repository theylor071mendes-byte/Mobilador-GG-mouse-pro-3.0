package com.mobiladorprox.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val PROFILES = "profiles"
    const val OPTIMIZATION = "optimization"
    const val PERMISSIONS = "permissions"
    const val PROFILE_EDITOR = "profile_editor/{profileId}"

    fun profileEditor(profileId: Long) = "profile_editor/$profileId"
}
