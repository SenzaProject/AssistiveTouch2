package com.senza.assistivetouch

object Actions {
    const val NONE = "NONE"
    const val MENU = "MENU"
    const val HOME = "HOME"
    const val BACK = "BACK"
    const val RECENTS = "RECENTS"
    const val NOTIF = "NOTIF"
    const val QS = "QS"
    const val LOCK = "LOCK"
    const val SHOT = "SHOT"
    const val POWER = "POWER"
    const val ASSIST = "ASSIST"

    val codes = listOf(NONE, MENU, HOME, BACK, RECENTS, NOTIF, QS, LOCK, SHOT, POWER, ASSIST)
    val labels = listOf(
        "Tidak ada", "Buka menu", "Home", "Kembali", "Aplikasi terbaru",
        "Notifikasi", "Pusat Kontrol", "Kunci layar", "Screenshot", "Menu daya", "Asisten suara"
    )

    const val PREFS = "senza_prefs"
    const val K_SIZE = "size"
    const val K_OPACITY = "opacity"
    const val K_GLASS = "glass"
    const val K_BLUR = "blur"
    const val K_TAP = "tap"
    const val K_DOUBLE = "double"
    const val K_LONG = "long"
    const val K_X = "pos_x"
    const val K_Y = "pos_y"
}
