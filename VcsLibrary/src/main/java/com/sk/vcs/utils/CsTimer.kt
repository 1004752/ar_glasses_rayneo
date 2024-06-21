package com.sk.vcs.utils

import com.sk.vcs.VcsDefine

object CsTimer : TimerManager() {
    const val CONTROL_KEEP_ALIVE_TIMEOUT: String =
        "CONTROL_KEEP_ALIVE_TIMEOUT" // keep alive response timer

    fun startKeepAliveTimer(listener: OnTimeoutListener?) {
        startTimer(CONTROL_KEEP_ALIVE_TIMEOUT, VcsDefine.CONTROL_KEEP_ALIVE_TIMEOUT.toLong(), listener)
    }

    fun stopKeepAliveTimer() {
        stopTimer(CONTROL_KEEP_ALIVE_TIMEOUT)
    }
}