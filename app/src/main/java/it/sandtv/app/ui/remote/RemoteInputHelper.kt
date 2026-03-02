package it.sandtv.app.ui.remote

import android.view.KeyEvent

/**
 * Helper for handling TV remote control input
 * Provides consistent mapping across different remotes
 */
object RemoteInputHelper {
    
    /**
     * Standard navigation keys
     */
    fun isNavigationKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER
        )
    }
    
    /**
     * Selection/Enter keys
     */
    fun isSelectKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER
        )
    }
    
    /**
     * Back keys
     */
    fun isBackKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE
        )
    }
    
    /**
     * Media playback keys
     */
    fun isPlayKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        )
    }
    
    fun isPauseKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        )
    }
    
    fun isStopKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_STOP
    }
    
    fun isFastForwardKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT // When in player context
        )
    }
    
    fun isRewindKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT // When in player context
        )
    }
    
    fun isNextKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_CHANNEL_UP
        )
    }
    
    fun isPreviousKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_CHANNEL_DOWN
        )
    }
    
    /**
     * Color button keys (found on many TV remotes)
     */
    fun isRedKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_PROG_RED
    fun isGreenKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_PROG_GREEN
    fun isYellowKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_PROG_YELLOW
    fun isBlueKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_PROG_BLUE
    
    /**
     * Quick access keys
     */
    fun isMenuKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS
        )
    }
    
    fun isInfoKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_GUIDE
        )
    }
    
    fun isSearchKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_SEARCH
    }
    
    /**
     * Number keys for direct channel input
     */
    fun isNumberKey(keyCode: Int): Boolean {
        return keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
               keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9
    }
    
    fun getNumber(keyCode: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
            else -> null
        }
    }
    
    /**
     * Volume keys (usually handled by system, but available for custom handling)
     */
    fun isVolumeKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE
        )
    }
}
