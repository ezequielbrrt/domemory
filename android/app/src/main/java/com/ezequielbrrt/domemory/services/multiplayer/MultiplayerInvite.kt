package com.ezequielbrrt.domemory.services.multiplayer

/** Stable invite URLs shared with iOS. The Play URL also gives recipients without the app a path to install. */
object MultiplayerInvite {
    fun schemeUrl(code: String) = "domemory://join/$code"
    fun playStoreUrl() = "https://play.google.com/store/apps/details?id=com.ezequielbrrt.domemory"
    fun shareText(caption: String, code: String) = "$caption\n${schemeUrl(code)}\n${playStoreUrl()}"
}
