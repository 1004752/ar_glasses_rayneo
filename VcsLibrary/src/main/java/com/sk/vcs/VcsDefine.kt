package com.sk.vcs

/**
 * 어플리케이션 define class
 *
 */
object VcsDefine {
    const val VCS_VERSION: String = BuildConfig.VCS_VERSION

    /** time, timeout 관련 세팅  */
    const val CONNECT_TIMEOUT: Int = 2000
    const val CONTROL_KEEP_ALIVE_TIMEOUT: Int = 2000

    /** WebApp 연동 데이터 포맷  */
    const val INTERFACE_TYPE_XML: Int = 0
    const val INTERFACE_TYPE_JSON: Int = 1

    /** 로깅 유형  */
    const val LOG_QSM: Int = 0

    /** Video Streaming Option  */
    var SCREEN_WIDTH: Int = 1920
    var SCREEN_HEIGHT: Int = 1080
}