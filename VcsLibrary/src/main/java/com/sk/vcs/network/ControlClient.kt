package com.sk.vcs.network

import com.sk.vcs.ErrorCode
import com.sk.vcs.network.base.TcpClient
import com.sk.vcs.utils.CsTimer
import com.sk.vcs.utils.CsTimer.startKeepAliveTimer
import com.sk.vcs.utils.CsTimer.stopKeepAliveTimer
import com.sk.vcs.utils.KeyConverter.convertServerKeyCode
import com.sk.vcs.utils.LogUtils.debug
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info
import com.sk.vcs.utils.LogUtils.verbose
import com.sk.vcs.utils.TimerManager.OnTimeoutListener
import java.io.ByteArrayOutputStream

class ControlClient(deviceId: String?) : TcpClient(deviceId!!) {
    private var mControlListener: OnControlListener? = null
    private val mLock = Any()

    interface OnControlListener {
        fun onSessionConnected()
        fun onError(error: Int, message: String?)
        fun onReceiveXml(command: String?)
        fun onReceiveJson(command: String?)
    }

    fun setOnControlListener(listener: OnControlListener) {
        mControlListener = listener
    }

    override fun stopClient(): Int {
        stopKeepAliveTimer()

        // 연결 종료 알림
        if (isRun()) {
            sendDisconnectCommand()

            synchronized(mLock) {
                try {
                    (mLock as Object).wait(500)
                } catch (ignored: InterruptedException) {
                }
            }
        }
        info(TAG, "stopClient!!!")

        return super.stopClient()
    }

    override fun startReaderThread() {
        mIsRun = true

        val thread = Thread {
            while (mIsRun) {
                val command = readInt()
                var bodySize: Int
                var resCode: Int

                // Socket Read 오류
                if (command < 0) {
                    if (mIsRun) {
                        error(TAG, "Socket Read Error!!!")
                        notifyError(ErrorCode.SOCKET_READ_ERROR, "")
                    }
                    continue
                }

                when (command) {
                    COMMAND_2001 -> {
                        resCode = readInt()
                        val maxPacketCount = readByte()
                        info(
                            TAG,
                            "<<<<< Receive COMMAND_2001:: Result Code = $resCode maxPacketCount = " + String.format(
                                "%02x ",
                                maxPacketCount
                            )
                        )

                        if (resCode < 0) {
                            notifyError(ErrorCode.SOCKET_READ_ERROR, "")
                        } else {
                            // 세션 연결 알림
                            if (mControlListener != null) {
                                mControlListener!!.onSessionConnected()
                            }
                        }
                    }

                    COMMAND_2002 -> {
                        resCode = readInt()
                        val maxPacketCount = readByte()
                        info(
                            TAG,
                            "<<<<< Receive COMMAND_2002:: Result Code = $resCode maxPacketCount = " + String.format(
                                "%02x ",
                                maxPacketCount
                            )
                        )

                        if (resCode < 0) {
                            notifyError(ErrorCode.SOCKET_READ_ERROR, "")
                        } else {
                            notifyError(COMMAND_2002, resCode.toString() + "")
                        }
                    }

                    COMMAND_2300 -> {
                        bodySize = readInt()
                        if (bodySize > 0) {
                            val receiveXml = readString(bodySize)
                        }
                        val checkSum = readInt()
                        info(
                            TAG,
                            "<<<<< Receive COMMAND_2300:: bodySize = $bodySize, checkSum = $checkSum"
                        )
                    }

                    COMMAND_3000 -> {
                        bodySize = readInt()
                        if (bodySize > 0) {
                            val receiveXml = readString(bodySize)
                            info(
                                TAG,
                                "<<<<< Receive COMMAND_3000:: XmlSize = $bodySize, XmlData = $receiveXml"
                            )
                            mControlListener!!.onReceiveXml(receiveXml)
                        } else {
                            info(
                                TAG,
                                "<<<<< Receive COMMAND_3000:: XmlSize = $bodySize"
                            )
                        }
                    }

                    COMMAND_12010 -> {
                        bodySize = readInt()
                        if (bodySize > 0) {
                            val receiveJson = readString(bodySize)
                            info(
                                TAG,
                                "<<<<< Receive COMMAND_12010:: JsonSize = $bodySize, JsonData = $receiveJson"
                            )
                            mControlListener!!.onReceiveJson(receiveJson)
                        } else {
                            info(
                                TAG,
                                "<<<<< Receive COMMAND_12010:: JsonSize = $bodySize"
                            )
                        }
                    }

                    COMMAND_1011 -> {
                        info(TAG, "<<<<< Receive COMMAND_1011:: Alive-Server!!")
                        // 타이머 해제
                        stopKeepAliveTimer()
                    }

                    COMMAND_7001 -> {
                        bodySize = readInt()
                        resCode = readInt()
                        val reserved = readInt()
                        info(
                            TAG,
                            "<<<<< Receive COMMAND_7001:: bodySize = $bodySize, resCode = $resCode, reserved = $reserved"
                        )

                        notifyError(COMMAND_7001, resCode.toString() + "")
                    }

                    else -> info(
                        TAG,
                        "<<<<< Receive COMMAND_$command   Undefine!!"
                    )
                }
            }
        }

        thread.start()
    }

    private val mTimeoutListener: OnTimeoutListener = object : OnTimeoutListener {
        override fun onTimeout(type: String?) {
            if (type == CsTimer.CONTROL_KEEP_ALIVE_TIMEOUT) {
                notifyError(ErrorCode.VCS_KEY_RESPONSE_TIMEOUT, "")
            }
        }
    }

    fun requestKeyUp(keyCode: Int): Boolean {
        var keyCode = keyCode
        keyCode = convertServerKeyCode(keyCode)
        if (keyCode == -1) {
            return false
        }

        // 타이머 설정
        startKeepAliveTimer(mTimeoutListener)

        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_1010)
        appendStreamInt(baos, keyCode)
        appendStreamInt(baos, 0)
        sendRequest(baos, mErrorCallback)

        debug(TAG, ">>>>> Send COMMAND_900:: KeyUpEvent KeyCode = $keyCode")

        return true
    }

    fun requestKeyDown(keyCode: Int): Boolean {
        var keyCode = keyCode
        keyCode = convertServerKeyCode(keyCode)
        if (keyCode == -1) {
            return false
        }

        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_900)
        appendStreamInt(baos, keyCode)
        sendRequest(baos, mErrorCallback)

        debug(TAG, ">>>>> Send COMMAND_900:: KeyDownEvent KeyCode = $keyCode")

        return true
    }

    fun requestKeepAliveEvent(keyCode: Int) {
        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_900)
        appendStreamInt(baos, keyCode)
        sendRequest(baos, mErrorCallback)

        debug(TAG, ">>>>> Send COMMAND_900:: KeyDownEvent KeyCode = $keyCode")
    }

    /**
     * 2000 Command 전송
     */
    fun sendStartApp(statusInfo: String) {
        info(TAG, "ControlClient::sendStartApp")
        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_2000)
        appendStreamInt(baos, 0) // UDP Video Port
        appendStreamInt(baos, 0) // UDP Audio Port
        appendStreamInt(baos, 4416) // A/V Packet Contents length
        appendStreamString(baos, "{$mDeviceId}") // STB-ID
        appendStreamString(baos, statusInfo) // StatusInfo XML

        sendRequest(baos, mErrorCallback)

        verbose(TAG, ">>>>> Send COMMAND_2000:: SendData = $statusInfo")
    }

    /**
     * 3010 Command 전송
     */
    fun sendXMLCommand(xmlCommand: String) {
        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_3010)
        appendStreamString(baos, xmlCommand)

        sendRequest(baos, mErrorCallback)

        debug(TAG, ">>>>> Send COMMAND_3010:: SendXmlData = $xmlCommand")
    }

    /**
     * 12000 Command 전송
     */
    fun sendJSONCommand(jsonCommand: String) {
        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_12000)
        appendStreamString(baos, jsonCommand)

        sendRequest(baos, mErrorCallback)

        debug(TAG, ">>>>> Send COMMAND_12000:: SendJsonData = $jsonCommand")
    }

    /**
     * 종료 요청에 대한 커맨드 전송은 자체 스레드로 처리
     */
    fun sendDisconnectCommand() {
        val thread = Thread {
            try {
                val baos = ByteArrayOutputStream()
                appendStreamInt(baos, COMMAND_2010)
                appendStreamString(baos, "{$mDeviceId}")

                sendRequest(baos.toByteArray())

                debug(TAG, ">>>>> Send COMMAND_2010:: Disconnect!!")
            } catch (e: Exception) {
                error(TAG, "sendDisconnectCommand Exception = ", e)
            } finally {
                synchronized(mLock) {
                    (mLock as Object).notify()
                }
            }
        }
        thread.start()
    }

    var mErrorCallback: ErrorNotifyCallback = object : ErrorNotifyCallback {
        override fun NotifyError() {
            // 에러 알림
            notifyError(ErrorCode.SOCKET_SEND_ERROR, "")
        }
    }

    private fun notifyError(error: Int, message: String) {
        info(TAG, "#### notifyError : error = $error, message = $message")

        if (mControlListener != null) {
            var errorCode = error
            if (error < ErrorCode.VCS_ERROR_START) {
                errorCode = ErrorCode.VCS_ERROR_START + error
            }

            mControlListener!!.onError(errorCode, message)
        }
    }

    companion object {
        private const val TAG = "ControlClient"

        /** VCS Server Command Code  */
        private const val COMMAND_900 = 900 // Key Down Event Send Command
        private const val COMMAND_1010 = 1010 // Key Up Event Send Command
        private const val COMMAND_1011 = 1011 // Key Up Event Receive Command
        private const val COMMAND_2000 = 2000 // Connect Command
        private const val COMMAND_2001 = 2001 // Connect Success Command
        private const val COMMAND_2002 = 2002 // Connect Fail Command
        private const val COMMAND_2010 = 2010 // Disconnect Command
        private const val COMMAND_2300 = 2300 // AppReadyToComm
        private const val COMMAND_3000 = 3000 // XML Receive Command
        private const val COMMAND_3010 = 3010 // XML Send Command
        private const val COMMAND_7001 = 7001 // Error Report Command
        private const val COMMAND_12000 = 12000 // JSON Send Command
        private const val COMMAND_12010 = 12010 // JSON Receive Command
    }
}