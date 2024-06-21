package com.sk.vcs.utils

import android.view.KeyEvent

/**
 * Android KeyCode 변환 클래스(웹앱과의 코드를 맞춰서 정의)
 *
 */
object KeyConverter {
    const val KEEP_ALIVE_KEY_CODE: Int = 1000 // Keep Alive 처리를 위한 Dummy KeyCode

    // VCS KeyCode Table (STB_KEY_CODE, WEB_KEY_CODE)
    private val KEY_TABLE = arrayOf(
        intArrayOf(KeyEvent.KEYCODE_1, 2),
        intArrayOf(KeyEvent.KEYCODE_2, 3),
        intArrayOf(KeyEvent.KEYCODE_3, 4),
        intArrayOf(KeyEvent.KEYCODE_4, 5),
        intArrayOf(KeyEvent.KEYCODE_5, 6),
        intArrayOf(KeyEvent.KEYCODE_6, 7),
        intArrayOf(KeyEvent.KEYCODE_7, 8),
        intArrayOf(KeyEvent.KEYCODE_8, 9),
        intArrayOf(KeyEvent.KEYCODE_9, 10),
        intArrayOf(KeyEvent.KEYCODE_0, 11),
        intArrayOf(KeyEvent.KEYCODE_POUND, 13),
        intArrayOf(KeyEvent.KEYCODE_BACK, 14),
        intArrayOf(KeyEvent.KEYCODE_DPAD_CENTER, 28),
        intArrayOf(KeyEvent.KEYCODE_MEDIA_NEXT, 51),
        intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, 52),
        intArrayOf(KeyEvent.KEYCODE_SEARCH, 63),
        intArrayOf(KeyEvent.KEYCODE_GUIDE, 64),
        intArrayOf(KeyEvent.KEYCODE_MENU, 65),
        intArrayOf(KeyEvent.KEYCODE_BOOKMARK, 66),
        intArrayOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 67),
        intArrayOf(KeyEvent.KEYCODE_PROG_YELLOW, 67),
        intArrayOf(KeyEvent.KEYCODE_MEDIA_STOP, 68),
        intArrayOf(KeyEvent.KEYCODE_PROG_GREEN, 68),
        intArrayOf(KeyEvent.KEYCODE_HOME, 71),
        intArrayOf(KeyEvent.KEYCODE_DPAD_UP, 72),
        intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, 75),
        intArrayOf(KeyEvent.KEYCODE_DPAD_RIGHT, 77),
        intArrayOf(KeyEvent.KEYCODE_DPAD_DOWN, 80),
        intArrayOf(KeyEvent.KEYCODE_DEL, 83),
        intArrayOf(KeyEvent.KEYCODE_MEDIA_REWIND, 87),
        intArrayOf(KeyEvent.KEYCODE_PROG_RED, 87),
        intArrayOf(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 88),
        intArrayOf(KeyEvent.KEYCODE_PROG_BLUE, 88),
        intArrayOf(KeyEvent.KEYCODE_F6, 90),
        intArrayOf(KeyEvent.KEYCODE_F9, 91),
        intArrayOf(KeyEvent.KEYCODE_F7, 97),
        intArrayOf(KeyEvent.KEYCODE_F11, 98),
        intArrayOf(KeyEvent.KEYCODE_F12, 99)
    )

    fun convertServerKeyCode(keycode: Int): Int {
        var code = -1

        for (ints in KEY_TABLE) {
            if (ints[0] == keycode) {
                code = ints[1]
                break
            }
        }

        return code
    }
}