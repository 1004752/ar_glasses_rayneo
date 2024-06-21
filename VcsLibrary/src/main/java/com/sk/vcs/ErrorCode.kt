package com.sk.vcs

object ErrorCode {
    /** GW error  */
    const val UI_EMERGENCY_MODE: Int = 8301

    /** csr error 20000 ~ 29999  */
    const val CSR_ERROR_START: Int = 20000

    const val ICS_ERROR_START: Int = 30000

    /** vcs error 40000 ~ 49999  */
    const val VCS_ERROR_START: Int = 40000
    const val VCS_CONNECTION_ERROR: Int = 40003
    const val VCS_FRAME_RECEIVE_ERROR: Int = 40004
    const val VCS_KEY_RESPONSE_TIMEOUT: Int = 40005
    const val DECODER_CREATE_ERROR: Int = 40007
    const val DECODER_MEDIACODEC_ERROR: Int = 40008

    /** other error 5xxxx  */
    const val SOCKET_READ_ERROR: Int = 50001
    const val SOCKET_SEND_ERROR: Int = 50002
    const val FIRST_VIDEO_FRAME_RECEIVED: Int = 90000
    const val MEDIA_BUFFER_FULL: Int = 90001

    fun getErrorCodeString(errorCode: Int): String {
        if (errorCode >= CSR_ERROR_START && errorCode < ICS_ERROR_START) {
            return "csr_server_error"
        } else if (errorCode >= VCS_ERROR_START) {
            return when (errorCode) {
                VCS_CONNECTION_ERROR -> "connection_error"
                VCS_FRAME_RECEIVE_ERROR -> "stream_error"
                VCS_KEY_RESPONSE_TIMEOUT -> "alive_error"
                DECODER_CREATE_ERROR -> "decoder_error"
                DECODER_MEDIACODEC_ERROR -> "mediacodec_error"
                SOCKET_READ_ERROR -> "socket_read_error"
                SOCKET_SEND_ERROR -> "socket_send_error"
                else -> "vcs_server_error"
            }
        }

        return "unknown_error"
    }
}