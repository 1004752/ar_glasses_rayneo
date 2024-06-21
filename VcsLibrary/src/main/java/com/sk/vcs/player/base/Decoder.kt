package com.sk.vcs.player.base

import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.sk.vcs.ErrorCode
import com.sk.vcs.data.MediaBuffer
import com.sk.vcs.qsm.VcsQualityChecker
import com.sk.vcs.utils.LogUtils.debug
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.verbose
import java.nio.ByteBuffer

abstract class Decoder protected constructor(
    protected val mMediaBuffer: MediaBuffer?,
    private val TAG: String
) {
    protected var mIsRun: Boolean = false
    protected var mIsRenderRun: Boolean = false
    protected var mIsMediaCodecRun: Boolean = false

    protected var mDecoder: MediaCodec? = null
    protected lateinit var mInputBuffer: Array<ByteBuffer>
    protected lateinit var mOutputBuffer: Array<ByteBuffer>
    protected val mReleaseLock: Any = Any()

    protected val mQualityChecker: VcsQualityChecker = VcsQualityChecker()

    protected var mEventListener: DecoderEventListener? = null

    interface DecoderEventListener {
        fun onFirstFrameRendered()
        fun onMediaQualityErrorAlert(count: Int)
        fun onError(error: Int, message: String?)
    }

    abstract fun init(): Boolean
    protected abstract fun startDecodeThread()

    fun start() {
        mIsRun = true
        mIsRenderRun = true

        startDecodeThread()
    }

    open fun stop() {
        mIsRun = false

        if (mMediaBuffer != null) {
            synchronized(mMediaBuffer) {
                (mMediaBuffer as Object).notifyAll()
            }
        }

        if (mIsRenderRun) {
            debug(TAG, "VcsDecoder-stop Lock-IN!!!")
            synchronized(mReleaseLock) {
                try {
                    (mReleaseLock as Object).wait(2000)
                } catch (ignored: InterruptedException) {
                }
            }
            debug(TAG, "VcsDecoder-stop Lock-OUT!!!")
        }
        debug(TAG, "VcsDecoder stop!!!")
    }

    protected open fun release() {
        if (mDecoder != null) {
            try {
                if (mIsMediaCodecRun) {
                    mDecoder!!.stop()
                }
            } catch (e: IllegalStateException) {
                error(TAG, "Decoder Release Exception", e)
            } finally {
                mDecoder!!.release()
                mDecoder = null
                mIsMediaCodecRun = false
            }
        }
    }

    fun setDecoderEventListener(listener: DecoderEventListener?) {
        mEventListener = listener
    }

    fun pause() {
        mQualityChecker.reset()
    }


    protected val mQsmThread: Thread = Thread {
        while (mIsRenderRun) {
            // 에러 개수가 있을 경우 알림 전송
            val dropCnt: Int = mQualityChecker.getRenderingDropCount()
            val delayCnt: Int = mQualityChecker.getRenderingErrorCount()
            val errorCnt = dropCnt + delayCnt

            if (errorCnt > 0) {
                verbose(
                    "QsmThread",
                    "FrameDelayedCount:$delayCnt, FrameDroppedCount:$dropCnt"
                )
                if (mEventListener != null) {
                    mEventListener!!.onMediaQualityErrorAlert(errorCnt)
                }
            }
            mQualityChecker.resetRenderingErrorCount()

            // 10초에 1번 전송
            try {
                Thread.sleep(10000) // 10초
            } catch (ignored: InterruptedException) {
            }
        }
    }

    protected fun notifyMediaCodecError(errorCode: Int) {
        if (mIsMediaCodecRun) {
            mIsMediaCodecRun = false
            mErrorHandler.sendEmptyMessage(errorCode)
        }
    }

    private val mErrorHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (mEventListener != null) {
                val errorCode = msg.what
                if (errorCode == ErrorCode.DECODER_MEDIACODEC_ERROR) {
                    mEventListener!!.onError(
                        ErrorCode.DECODER_MEDIACODEC_ERROR,
                        "IllegalStateException"
                    )
                } else {
                    mEventListener!!.onError(ErrorCode.DECODER_CREATE_ERROR, "")
                }
            }

            super.handleMessage(msg)
        }
    }
}