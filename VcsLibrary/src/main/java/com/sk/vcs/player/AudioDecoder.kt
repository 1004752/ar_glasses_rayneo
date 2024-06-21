package com.sk.vcs.player

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import com.sk.vcs.ErrorCode
import com.sk.vcs.data.MediaBuffer
import com.sk.vcs.data.MediaDataManager
import com.sk.vcs.player.base.Decoder
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info
import com.sk.vcs.utils.LogUtils.verbose
import java.nio.ByteBuffer

class AudioDecoder(mediaBuffer: MediaBuffer?) : Decoder(mediaBuffer, TAG) {
    private var mAudioTrack: AudioTrack? = null

    override fun init(): Boolean {
        verbose(TAG, "AudioDecoder-initDecoder START!!!")
        try {
            val format = makeAACCodecSpecificData(
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                SAMPLE_RATE,
                CHANNEL_COUNT
            )
            mDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm")
            mDecoder!!.configure(format, null, null, 0)
            mDecoder!!.start()
            mIsMediaCodecRun = true

            mInputBuffer = mDecoder!!.inputBuffers
            mOutputBuffer = mDecoder!!.outputBuffers

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            mAudioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )
            mAudioTrack!!.setStereoVolume(1.0f, 1.0f)
            mAudioTrack!!.play()

            verbose(TAG, "AudioDecoder-initDecoder SUCCESS!!!")

            return true
        } catch (e: java.lang.Exception) {
            error(TAG, "AudioDecoder-initDecoder FAIL!!!", e)
        }

        return false
    }

    override fun startDecodeThread() {
        mAudioDecoderThread.start()
        mAudioDecoderThread.name = "AudioDecoder-Thread"
        mAudioRenderThread.start()
        mAudioRenderThread.name = "AudioRender-Thread"
        mQsmThread.start()
        mQsmThread.name = "AudioQSM-Thread"
    }

    override fun stop() {
        verbose(TAG, "AudioDecoder-Stop Called!!!")
        super.stop()
    }

    override fun release() {
        super.release()

        // AudioTrack 릴리즈
        if (mAudioTrack != null) {
            try {
                mAudioTrack!!.stop()
                mAudioTrack!!.release()
                mAudioTrack = null
            } catch (e: java.lang.Exception) {
                error(TAG, "mAudioRenderThread Exception", e)
            }
        }
        verbose(TAG, "AudioDecoder-Release!!!")
    }

    private val mAudioDecoderThread = Thread(Runnable {
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
                    val inputBufferId =
                        mDecoder!!.dequeueInputBuffer(BUFFER_TIMEOUT.toLong())
                    if (inputBufferId != -1) {
                        val esData = mMediaBuffer.deQueue()
                        if (esData != null) {
                            dataManager.buffer = esData
                            val dataSize =
                                dataManager.getMediaData(mInputBuffer[inputBufferId])
                            if (dataSize > 0) {
                                val ts = dataManager.timeStamp
                                mDecoder!!.queueInputBuffer(inputBufferId, 0, dataSize, ts, 0)
                                mQualityChecker.addDecoderInput()
                            }
                        }
                    }
                } catch (e: java.lang.Exception) {
                    error(TAG, "mAudioDecoderThread Exception::", e)

                    // MediaCodec Exception 발생시 에러 처리
                    if (e is java.lang.IllegalStateException) {
                        notifyMediaCodecError(ErrorCode.DECODER_MEDIACODEC_ERROR)
                    }
                }
            }
        }
        SystemClock.sleep(5)

        info(TAG, "mAudioDecoderThread Exit!!!")
        mIsRenderRun = false
    })

    private val mAudioRenderThread = Thread {
        val info = MediaCodec.BufferInfo()
        val pcm = ByteArray(1024 * 100)

        while (mIsRenderRun) {
            if (!mIsMediaCodecRun) {
                SystemClock.sleep(30)
                continue
            }

            try {
                val outputBufferId = mDecoder!!.dequeueOutputBuffer(
                    info,
                    OUTPUT_BUFFER_TIMEOUT.toLong()
                )
                if (outputBufferId >= 0) {
                    val outBuffer = mOutputBuffer[outputBufferId]
                    if (outBuffer != null) {
                        outBuffer[pcm, 0, info.size]
                        outBuffer.clear()

                        if (info.size > 0) {
                            mAudioTrack!!.write(pcm, info.offset, info.size)
                        }
                    }
                    mDecoder!!.releaseOutputBuffer(outputBufferId, false)

                    mQualityChecker.checkValidRenderTime(
                        info.presentationTimeUs,
                        System.currentTimeMillis()
                    )

                    SystemClock.sleep(1)
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    info(
                        TAG,
                        "mAudioRenderThread INFO_OUTPUT_BUFFERS_CHANGED"
                    )
                    mOutputBuffer = mDecoder!!.outputBuffers
                } else {
                    SystemClock.sleep(30)
                }
            } catch (e: Exception) {
                error(TAG, "mAudioRenderThread Exception::", e)

                // MediaCodec Exception 발생시 에러 처리
                if (e is IllegalStateException) {
                    notifyMediaCodecError(ErrorCode.DECODER_MEDIACODEC_ERROR)
                }
            }
        }
        info(TAG, "mAudioRenderThread Exit!!!")

        release()

        verbose(TAG, "AudioDecoder-ThreadExit!!!")
        synchronized(mReleaseLock) {
            (mReleaseLock as Object).notifyAll()
        }
    }

    private fun makeAACCodecSpecificData(
        audioProfile: Int,
        sampleRate: Int,
        channelConfig: Int
    ): MediaFormat {
        val format = MediaFormat()
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm")
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig)

        val samplingFreq = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
            16000, 12000, 11025, 8000
        )

        // Search the Sampling Frequencies
        var sampleIndex = -1
        for (i in samplingFreq.indices) {
            if (samplingFreq[i] == sampleRate) {
                sampleIndex = i
            }
        }

        val csd = ByteBuffer.allocate(2)
        csd.put(((audioProfile shl 3) or (sampleIndex shr 1)).toByte())
        csd.position(1)
        csd.put((((sampleIndex shl 7) and 0x80).toByte().toInt() or (channelConfig shl 3)).toByte())
        csd.flip()
        format.setByteBuffer("csd-0", csd)
        format.setInteger(MediaFormat.KEY_IS_ADTS, 1)

        return format
    }

    companion object {
        private const val TAG = "VcsAudioDecoder"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_COUNT = 2
        private const val BUFFER_TIMEOUT: Int = 10000
        private const val OUTPUT_BUFFER_TIMEOUT: Int = 100 * 1000 // 100ms
    }
}