package com.sk.vcs.network

import android.os.SystemClock
import com.sk.vcs.ErrorCode
import com.sk.vcs.data.MediaBuffer
import com.sk.vcs.network.base.TcpClient
import com.sk.vcs.utils.ByteUtils.byteToInt
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info
import com.sk.vcs.utils.LogUtils.verbose
import java.io.ByteArrayOutputStream

class StreamingClient(
    deviceId: String?,
    private val mMediaBufferList: MediaBuffer,
    private val mAlphaBufferList: MediaBuffer?,
    private val mStreamingListener: OnStreamingListener?,
    private val mIsVideo: Boolean
) :
    TcpClient(deviceId!!) {
    private var mCheckFirstVideoFrame = true
    private var mLogTag: String? = null
    private var mMaxBufferSize = 0

    interface OnStreamingListener {
        fun onError(error: Int, message: String?)
    }

    override fun startReaderThread() {
        mIsRun = true
        mCheckFirstVideoFrame = true

        val thread = Thread(object : Runnable {
            private var mIsStream = false

            override fun run() {
                while (mIsRun) {
                    if (mIsStream) {
                        readStream()
                    } else {
                        mIsStream = readCommand()
                    }
                }
            }
        })

        thread.start()
    }

    private fun readCommand(): Boolean {
        val command = readInt()

        // Socket Read 오류
        if (command < 0) {
            if (mIsRun) {
                error(mLogTag!!, "ReadCommand::Socket Read Error!!!")
                notifyError(ErrorCode.SOCKET_READ_ERROR, "")
            }
            return false
        }

        if (command == COMMAND_2021) {
            val length = readInt()
            if (length > 0) {
                val buffer = ByteArray(length)
                read(buffer, length)

                info(
                    mLogTag!!,
                    "<<<<< Receive COMMAND_2021:: " + (if (mIsVideo) "Video" else "Audio") + ", Data = " + String(
                        buffer
                    )
                )
            }
            return true
        } else {
            info(
                TAG,
                "<<<<< Receive Undefined COMMAND = $command"
            )
            notifyError(command, "")
        }

        return false
    }

    private fun readStream() {
        var readSize: Int
        var sop: Byte

        var fpsStartTime = 0L
        var frameCnt = 0
        var timeElapsed = 0.0

        info(mLogTag!!, (if (mIsVideo) "VideoStream Start!!" else "AudioStream Start!!"))

        while (mIsRun) {
            // 데이터 Full일 경우 Delay 처리
            if (mMediaBufferList.isFull) {
                error(
                    mLogTag!!,
                    (if (mIsVideo) "VideoStream" else "AudioStream") + " :: MediaBuffer Full!!!"
                )
                notifyError(ErrorCode.MEDIA_BUFFER_FULL, "")
                SystemClock.sleep(10)
                continue
            }

            // SoP 수신(0xF2 or 0xF3)
            sop = readByte()
            if (sop.toInt().toByte() == (-4).toByte()) {
                if (mIsRun) {
                    error(mLogTag!!, "ReadStream::Socket Read Error!!!")
                    notifyError(ErrorCode.SOCKET_READ_ERROR, "")
                }
                continue
            }

            // Packet Sequence 수신
            val packetSeq = readShortR()

            // Contents Length 수신 (헤더 크기 7 제외 - Sop, Seq, Length)
            var contentsLength = readIntR()
            contentsLength -= 7

            // Buffer 크키보다 작은지 확인
            if (contentsLength > mMaxBufferSize) {
                error(mLogTag!!, "VCS Stream Contents Length Error!!!")
                error(
                    mLogTag!!,
                    "MAX_BUFFER_SIZE = $mMaxBufferSize, contentsLength = $contentsLength"
                )
                notifyError(ErrorCode.SOCKET_READ_ERROR, "")
                continue
            }

            // Frame 데이터 수신
            val buffer = mMediaBufferList.nextBuffer
            readSize = read(buffer, 0, contentsLength)
            if (readSize <= 0) {
                error(mLogTag!!, "VCS Stream Read Size = $readSize")
                continue
            }

            // 디코더 활성화 여부에 따라 Queue에 적용
            if (mIsVideo) {
                mMediaBufferList.enQueue(buffer)

                // Alpha Frame 버퍼에 데이터 복사
                if (mAlphaBufferList != null) {
                    if (mAlphaBufferList.isFull) {
                        error(mLogTag!!, "VideoStream :: AlphaBuffer Full!!!")
                    }
                    val alphaFrameSize = getAlphaFrameSize(buffer)
                    if (alphaFrameSize > 0) {
                        val alphaBuffer = mAlphaBufferList.nextBuffer
                        System.arraycopy(buffer, 0, alphaBuffer, 0, alphaFrameSize + 30)
                        mAlphaBufferList.enQueue(alphaBuffer)
                        //LogUtils.error(mLogTag, "alphaFrameSize = " + alphaFrameSize);
                    }
                    //LogUtils.error(mLogTag, "mAlphaBuffer Size = " + mAlphaBufferList.size());
                }

                // 첫번째 Frame 수신 이벤트 생성
                if (mCheckFirstVideoFrame) {
                    mCheckFirstVideoFrame = false
                    //notifyError(ErrorCode.FIRST_VIDEO_FRAME_RECEIVED, "");
                }
            } else {
                // aac에서 4118 바이트의 무음 프레임이 오는 경우는 제외함
                if (contentsLength != 4118) {
                    mMediaBufferList.enQueue(buffer)
                }
            }

            // 수신 데이터 로그
            //LogUtils.debug(mLogTag, "Packet-Sequence : " + packetSeq + ", Packet-Length : " + contentsLength);

            // Video일 경우 FPS 로그 처리
            if (mIsVideo && LOG_FPS_ENABLED) {
                val fpsEndTime = System.currentTimeMillis()
                val timeDelta = (fpsEndTime - fpsStartTime) * 0.001f
                frameCnt++
                timeElapsed += timeDelta.toDouble()

                // FPS를 구해서 로그로 표시
                if (timeElapsed >= 1.0f) {
                    val fps = (frameCnt / timeElapsed).toFloat()
                    verbose(mLogTag!!, "VcsVideo-FPS : " + Math.round(fps))
                    frameCnt = 0
                    timeElapsed = 0.0
                }
                // Frame 시작 시간 다시 셋팅
                fpsStartTime = System.currentTimeMillis()
            }

            SystemClock.sleep(5)
        }

        verbose(mLogTag!!, (if (mIsVideo) "VideoStream Finish!!" else "AudioStream Finish!!"))
    }

    /** Request function  */
    fun requestStreamInfo() {
        val baos = ByteArrayOutputStream()
        appendStreamInt(baos, COMMAND_2020)
        appendStreamInt(baos, if (mIsVideo) 0 else 1)
        appendStreamString(baos, "{$mDeviceId}")
        sendRequest(baos, mErrorCallback)
    }

    private val mErrorCallback: ErrorNotifyCallback = object : ErrorNotifyCallback {
        override fun NotifyError() {
            // 에러 알림
            notifyError(ErrorCode.SOCKET_SEND_ERROR, "")
        }
    }

    init {
        if (mIsVideo) {
            mLogTag = TAG + "_Video"
            mMaxBufferSize = VIDEO_BUFFER_SIZE
        } else {
            mLogTag = TAG + "_Audio"
            mMaxBufferSize = AUDIO_BUFFER_SIZE
        }
    }

    private fun notifyError(error: Int, message: String) {
        if (error != ErrorCode.FIRST_VIDEO_FRAME_RECEIVED) {
            info(mLogTag!!, "#### notifyError : error = $error, message = $message")
        }

        if (mStreamingListener != null) {
            var errorCode = error
            if (error < ErrorCode.VCS_ERROR_START) {
                errorCode = ErrorCode.VCS_ERROR_START + error
            }

            mStreamingListener.onError(errorCode, message)
        }
    }

    private fun getAlphaFrameSize(buff: ByteArray): Int {
        val contentsType = buff[0].toInt() and 0xFF
        if (contentsType == 82) {
            return byteToInt(buff, 26)
        }
        return 0
    }

    companion object {
        private const val TAG = "StreamingClient"

        const val AUDIO_BUFFER_SIZE: Int = 8 * 1024
        const val VIDEO_BUFFER_SIZE: Int = 700 * 1024

        private const val COMMAND_2020 = 2020 // Stream Info Send Command
        private const val COMMAND_2021 = 2021 // Stream Info Receive Command

        private const val LOG_FPS_ENABLED = true
    }
}