package com.sk.vcs.player

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import com.sk.vcs.ErrorCode
import com.sk.vcs.VcsDefine
import com.sk.vcs.data.MediaBuffer
import com.sk.vcs.data.MediaDataManager
import com.sk.vcs.data.MediaNativeBuffer
import com.sk.vcs.player.base.Decoder
import com.sk.vcs.utils.ByteUtils.byteToInt
import com.sk.vcs.utils.ByteUtils.byteToIntR
import com.sk.vcs.utils.ByteUtils.byteToLongR
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info
import com.sk.vcs.utils.LogUtils.verbose
import com.sk.vcs.utils.Lz4Utils.decompress
import com.sk.vcs.utils.RleUtil.decode
import com.sk.vcs.view.VcsGlTextureView
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class VideoDecoder(
    private val mSurface: Surface,
    mediaBuffer: MediaBuffer?,
    private val mGlTextureView: VcsGlTextureView?,
    private val mAlphaBuffer: MediaBuffer,
    private val mAlphaByteBuffer: MediaNativeBuffer
) :
    Decoder(mediaBuffer, TAG) {
    private var mCheckFirstVideoFrame = true
    private var mFrameRendering = true

    private var mFrameSleepTime = 30
    private var mVideoCodec = "H264"
    private var mVideoFps = 30

    fun setVideoInfo(codec: String?, fps: Int) {
        mVideoCodec = if ("H265".equals(codec, ignoreCase = true)) {
            "video/hevc"
        } else {
            "video/avc"
        }
        mVideoFps = fps
        mFrameSleepTime = (1000 / mVideoFps) - 3
    }

    override fun init(): Boolean {
        verbose(TAG, "VideoDecoder-initDecoder START!!!")
        try {
            // Decoder 생성
            mDecoder = MediaCodec.createDecoderByType(mVideoCodec)
            val format = MediaFormat.createVideoFormat(
                "video/avc",
                VcsDefine.SCREEN_WIDTH,
                VcsDefine.SCREEN_HEIGHT
            )
            format.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoFps)
            format.setInteger("vendor.error_frame_policy.enable", 1)
            mDecoder!!.configure(format, mSurface, null, 0)
            mDecoder!!.start()
            mIsMediaCodecRun = true

            mInputBuffer = mDecoder!!.inputBuffers
            verbose(TAG, "VideoDecoder-initDecoder SUCCESS!!!")

            return true
        } catch (e: java.lang.Exception) {
            error(TAG, "VideoDecoder-initDecoder FAIL!!!", e)
        }

        return false
    }

    fun setFrameRendering(rendering: Boolean) {
        mFrameRendering = rendering
    }

    override fun startDecodeThread() {
        mVideoDecoderThread.start()
        mVideoDecoderThread.name = "VideoDecoder-Thread"
        mVideoRenderThread.start()
        mVideoRenderThread.name = "VideoRender-Thread"
        mQsmThread.start()
        mQsmThread.name = "VideoQSM-Thread"

        if (mGlTextureView != null) {
            mAlphaRenderThread.start()
            mAlphaRenderThread.name = "AlphaRender-Thread"
        }
    }

    override fun stop() {
        verbose(TAG, "VideoDecoder-Stop Called!!!")
        super.stop()

        synchronized(mAlphaBuffer) {
            (mAlphaBuffer as Object).notifyAll()
        }
    }

    override fun release() {
        super.release()

        verbose(TAG, "VideoDecoder-Release!!!")
    }

    private val mVideoDecoderThread = Thread(Runnable {
        if (!init()) {
            notifyMediaCodecError(ErrorCode.DECODER_CREATE_ERROR)
            return@Runnable
        }
        val dataManager = MediaDataManager()

        while (mIsRun) {
            if (!mIsMediaCodecRun) {
                SystemClock.sleep(30)
                continue
            }

            synchronized(mMediaBuffer!!) {
                if (mMediaBuffer.isEmpty) {
                    try {
                        (mMediaBuffer as Object).wait()
                    } catch (ignored: InterruptedException) {
                    }
                }
            }

            if (!mMediaBuffer.isEmpty) {
                try {
                    val inputBufferId = mDecoder!!.dequeueInputBuffer(BUFFER_TIMEOUT.toLong())
                    if (inputBufferId != -1) {
                        val esData = mMediaBuffer.deQueue()
                        if (esData != null) {
                            dataManager.buffer = esData

                            val dataSize =
                                dataManager.getMediaData(mInputBuffer[inputBufferId])
                            if (dataSize > 0) {
                                // Video Frame 디코딩 요청
                                val ts = dataManager.timeStamp
                                mDecoder!!.queueInputBuffer(inputBufferId, 0, dataSize, ts, 0)
                                mQualityChecker.addDecoderInput()

                                // 추가 프레임일 경우 알파프레임 잔상 해결 예외 처리
                                if (mGlTextureView != null && ts == 1L) {
                                    mGlTextureView.setAlphaFrameTimestamp(Long.MAX_VALUE)
                                }
                            }
                        }
                    }
                } catch (e: java.lang.Exception) {
                    error(TAG, "mVideoDecoderThread Exception::", e)

                    // MediaCodec Exception 발생시 에러 처리
                    if (e is java.lang.IllegalStateException) {
                        notifyMediaCodecError(ErrorCode.DECODER_MEDIACODEC_ERROR)
                    }
                }
            }

            SystemClock.sleep(5)
        }

        info(TAG, "mVideoDecoderThread Exit!!!")
        mIsRenderRun = false
    })

    private val mVideoRenderThread = Thread {
        val info = MediaCodec.BufferInfo()
        mGlTextureView?.reset()

        var releaseTime = 0L

        while (mIsRenderRun) {
            if (!mIsMediaCodecRun) {
                SystemClock.sleep(30)
                continue
            }

            try {
                val outputBufferId =
                    mDecoder!!.dequeueOutputBuffer(info, OUTPUT_BUFFER_TIMEOUT.toLong())
                if (outputBufferId >= 0) {
                    // 첫번째 Frame 수신 이벤트 생성
                    if (mCheckFirstVideoFrame) {
                        mCheckFirstVideoFrame = false

                        if (mEventListener != null) {
                            mEventListener!!.onFirstFrameRendered()
                        }
                        info(
                            TAG,
                            "mVideoRenderThread : First Video Frame Render!!!"
                        )

                        mGlTextureView?.updateSurface()
                    }

                    // SleepTime 설정
                    if (releaseTime > 0) {
                        val workingTime =
                            System.currentTimeMillis() - releaseTime
                        if (workingTime < mFrameSleepTime) {
                            //LogUtils.debug(TAG, "VideoRenderSleepTime = " + (mFrameSleepTime - workingTime) + ", WorkingTime = " + workingTime);
                            SystemClock.sleep(mFrameSleepTime - workingTime)
                        } else if (workingTime < (mFrameSleepTime * 2L)) {
                            //LogUtils.debug(TAG, "VideoRenderSleepTime = 0, WorkingTime = " + workingTime);
                            SystemClock.sleep(0)
                        }
                    }

                    mDecoder!!.releaseOutputBuffer(outputBufferId, mFrameRendering)
                    releaseTime = System.currentTimeMillis()

                    mQualityChecker.checkValidRenderTime(
                        info.presentationTimeUs,
                        System.currentTimeMillis()
                    )

                    mGlTextureView?.setAlphaFrameTimestamp(info.presentationTimeUs)
                } else {
                    SystemClock.sleep(1)
                }
            } catch (e: java.lang.Exception) {
                error(TAG, "mVideoRenderThread Exception::", e)

                // MediaCodec Exception 발생시 에러 처리
                if (e is IllegalStateException) {
                    notifyMediaCodecError(ErrorCode.DECODER_MEDIACODEC_ERROR)
                }
            }
        }
        info(TAG, "mVideoRenderThread Exit!!!")

        release()

        mGlTextureView?.reset()

        verbose(TAG, "VideoDecoder-ThreadExit!!!")
        synchronized(mReleaseLock) {
            (mReleaseLock as Object).notifyAll()
        }
    }

    // Alpha 처리 관련 로직
    private var mDecompressor: Inflater? = null

    private fun getAlphaBuffer(size: Int): ByteArray {
        var buffer = mAlphaByteBuffer.buffer
        if (buffer == null) {
            verbose(TAG, "getAlphaBuffer new alloc = $size")
            buffer = ByteArray(size)
        }

        return buffer
    }

    private val mAlphaRenderThread = Thread {
        Thread.currentThread().priority = Thread.MAX_PRIORITY
        mDecompressor = Inflater()

        while (mIsRenderRun) {
            synchronized(mAlphaBuffer) {
                if (mAlphaBuffer.isEmpty) {
                    try {
                        (mAlphaBuffer as Object).wait()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }

            if (!mAlphaBuffer.isEmpty) {
                try {
                    mAlphaBuffer.deQueue()?.let { parseAlphaFrame(it) }
                } catch (e: Exception) {
                    error(TAG, "parseAlphaFrame Exception!!", e)
                }

                SystemClock.sleep(1)
            } else {
                SystemClock.sleep(5)
            }
        }
        verbose(TAG, "mAlphaRenderThread Exit!!!")
    }

    var mTotalWorkingTime: Long = 0L
    var mTotalCount: Int = 0

    private fun parseAlphaFrame(alphaDataBuffer: ByteArray) {
        val contentsType = alphaDataBuffer[0].toInt() and 0xFF
        val contentsCount = alphaDataBuffer[1].toInt() and 0xFF
        val timestamp = byteToLongR(alphaDataBuffer, 10)
        val frameStart = 10

        var alphaDataSize = 0

        val startTime: Long
        val endTime: Long
        var workingTime = 0L

        // Alpha ES일 경우 Alpha 데이터 처리
        if (contentsType == 82) {
            val alphaDataLength = byteToIntR(alphaDataBuffer, frameStart + 8)
            val alphaType = byteToInt(
                alphaDataBuffer,
                frameStart + 12
            ) // 0x00:없음, 0x01:ZIP, 0x02:RLE, 0x03:ZIP+RLE
            val alphaBodyLength = byteToInt(alphaDataBuffer, frameStart + 16)

            val startAlphaOffset = frameStart + 20
            if (alphaBodyLength > 0) {
                val x = byteToInt(alphaDataBuffer, startAlphaOffset)
                val y = byteToInt(alphaDataBuffer, startAlphaOffset + 4)
                val w = byteToInt(alphaDataBuffer, startAlphaOffset + 8)
                val h = byteToInt(alphaDataBuffer, startAlphaOffset + 12)
                val lengthAlpha = byteToInt(alphaDataBuffer, startAlphaOffset + 16)

                if (alphaType == 1) {
                    // ZIP 압축일 경우
                    mDecompressor!!.setInput(alphaDataBuffer, startAlphaOffset + 20, lengthAlpha)
                    val alphaBuf = getAlphaBuffer(w * h)
                    try {
                        startTime = SystemClock.uptimeMillis()
                        alphaDataSize = mDecompressor!!.inflate(alphaBuf, 0, (w * h))
                        endTime = SystemClock.uptimeMillis()
                        workingTime = endTime - startTime
                    } catch (e: DataFormatException) {
                        error(TAG, "Zip Decompress Error : ", e)
                    } finally {
                        mDecompressor!!.reset()
                    }

                    if (mGlTextureView != null && alphaDataSize > 0) {
                        mGlTextureView.updateAlphaChannel(x, y, w, h, alphaBuf, w * h, timestamp)
                    }
                } else if (alphaType == 2) {
                    // RLE 압축일 경우
                    val alphaBuf = getAlphaBuffer(w * h)

                    startTime = SystemClock.uptimeMillis()
                    alphaDataSize =
                        decode(alphaBuf, 0, alphaDataBuffer, startAlphaOffset + 20, lengthAlpha)
                    endTime = SystemClock.uptimeMillis()
                    workingTime = endTime - startTime

                    if (mGlTextureView != null && alphaDataSize > 0) {
                        mGlTextureView.updateAlphaChannel(x, y, w, h, alphaBuf, w * h, timestamp)
                    }
                } else if (alphaType == 3) {
                    // ZIP+RLE 압축일 경우
                    mDecompressor!!.setInput(alphaDataBuffer, startAlphaOffset + 20, lengthAlpha)
                    val zipBuf = ByteArray(w * h)
                    try {
                        startTime = SystemClock.uptimeMillis()
                        val zipDataSize = mDecompressor!!.inflate(zipBuf)
                        mDecompressor!!.reset()

                        // RLE 압축 해제
                        val alphaBuf = getAlphaBuffer(w * h)
                        alphaDataSize = decode(alphaBuf, 0, zipBuf, 0, zipDataSize)
                        endTime = SystemClock.uptimeMillis()
                        workingTime = endTime - startTime

                        if (mGlTextureView != null && alphaDataSize > 0) {
                            mGlTextureView.updateAlphaChannel(
                                x,
                                y,
                                w,
                                h,
                                alphaBuf,
                                w * h,
                                timestamp
                            )
                        }
                    } catch (e: DataFormatException) {
                        e.printStackTrace()
                    }
                } else if (alphaType == 4) {
                    // LZ4 압축일 경우
                    val alphaBuf = getAlphaBuffer(w * h)

                    startTime = SystemClock.uptimeMillis()
                    alphaDataSize =
                        decompress(alphaDataBuffer, startAlphaOffset + 20, lengthAlpha, alphaBuf, 0)
                    endTime = SystemClock.uptimeMillis()
                    workingTime = endTime - startTime

                    if (mGlTextureView != null && alphaDataSize > 0) {
                        mGlTextureView.updateAlphaChannel(x, y, w, h, alphaBuf, w * h, timestamp)
                    }
                }
                info(
                    TAG,
                    "VCS-Performance::Uncompress-Time = $workingTime  ZipType:$alphaType  ($w - $h) : $lengthAlpha"
                )

//				mTotalWorkingTime += workingTime;
//				mTotalCount++;
//				LogUtils.info(TAG, "VCS-Performance::Uncompress-AverageTime = " + mTotalWorkingTime/mTotalCount);
            }
        }
    }

    companion object {
        private const val TAG = "VcsVideoDecoder"
        private const val VIDEO_FRAME_RATE = 30
        private const val BUFFER_TIMEOUT: Int = 10000
        private const val OUTPUT_BUFFER_TIMEOUT: Int = 100 * 1000 // 100ms
    }
}