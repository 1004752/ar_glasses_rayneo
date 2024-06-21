package com.sk.vcs.data

import com.sk.vcs.utils.ByteUtils
import com.sk.vcs.utils.LogUtils.info
import java.nio.ByteBuffer

/**
 * VCS 서버에서 수신한 ES 데이터 처리 클래스 (Alpha 데이터 포함 규격)
 *
 */
class MediaDataManager {
    lateinit var buffer: ByteArray

    val timeStamp: Long
        get() {
            var timestamp: Long = 1

            // Add Frame이고 Alpha Frame이 없을 경우 0으로 설정
            if (buffer[4].toInt() == 0x01 && !existAlphaFrame()) {
                LogInfo(TAG, "Add-Frame Timestamp!!!")
            } else {
                timestamp = ByteUtils.byteToLongR(buffer, 10)
            }

            return timestamp
        }

    val contentsLength: Int
        get() = ByteUtils.byteToIntR(buffer, 18)

    fun existAlphaFrame(): Boolean {
        val contentsType = buffer[0].toInt() and 0xFF
        if (contentsType == 82) {
            return ByteUtils.byteToInt(buffer, 26) > 0
        }
        return false
    }

    val sampleRate: Int
        /**
         * audio packet 에서만 서용. adts header 의 sample frequency index 참조
         */
        get() {
            val index = buffer[24].toInt() shr 2 and 0x0f

            when (index) {
                0 -> return 96000
                1 -> return 88200
                2 -> return 64000
                3 -> return 48000
                4 -> return 44100
                5 -> return 32000
                6 -> return 24000
                7 -> return 22050
                8 -> return 16000
                9 -> return 12000
                10 -> return 11025
                11 -> return 8000
                12 -> return 7350
            }
            return 44100
        }

    /*
    * ES Alpha 데이터 수신 처리
    */
    fun getMediaData(buffer: ByteBuffer): Int {
        LogInfo(TAG, "getMediaData Start!!!")
        val contentsType = buffer[0].toInt() and 0xFF // Video:0x12, Audio:0x22, Alpha:0x52
        val contentsCount = buffer[1].toInt() and 0xFF
        val videoType = buffer[2].toInt() and 0xFF
        val audioType = buffer[3].toInt() and 0xFF
        val optionalInfo = buffer[4].toInt() and 0xFF

        var alphaDataLength = 0
        var alphaType = 0
        var alphaBodyLength = 0

        var frameStart = 10
        buffer.rewind()

        // Alpha ES일 경우 Alpha 데이터 처리
        if (contentsType == 82) {
            alphaDataLength = ByteUtils.byteToIntR(this.buffer, frameStart + 8)
            alphaType = ByteUtils.byteToInt(
                this.buffer,
                frameStart + 12
            ) // 0x00:없음, 0x01:ZIP, 0x02:RLE, 0x03:ZIP+RLE
            alphaBodyLength = ByteUtils.byteToInt(this.buffer, frameStart + 16)
            frameStart = 10 + 28 + alphaBodyLength
        } else {
            frameStart = 18
        }

        // ES 데이터 수신 처리
        val esDataLength: Int = ByteUtils.byteToIntR(this.buffer, frameStart)
        buffer.put(this.buffer, frameStart + 4, esDataLength)

        // 로그
        if (LOG_ES_PARSE) {
            if (contentsType == 82) {
                LogInfo(
                    TAG,
                    "ES-DATA-INFO :: contentsType=ALPHA+VIDEO, contentsCount=" + contentsCount + ", videoType=" + videoType + ", audioType=" + audioType + ", optionalInfo=" + optionalInfo +
                            ", alphaDataLength=" + alphaDataLength + ", alphaType=" + alphaType + ", alphaBodyLength=" + alphaBodyLength + ", FrameType=" + getFrameType(
                        this.buffer, frameStart + 4, esDataLength
                    )
                )
            } else if (contentsType == 18) {
                LogInfo(
                    TAG,
                    "ES-DATA-INFO :: contentsType=VIDEO, contentsCount=" + contentsCount + ", videoType=" + videoType + ", audioType=" + audioType + ", optionalInfo=" + optionalInfo +
                            ", FrameType=" + getFrameType(this.buffer, frameStart + 4, esDataLength)
                )
            } else {
                LogInfo(
                    TAG,
                    "ES-DATA-INFO :: contentsType=AUDIO, contentsCount=$contentsCount, videoType=$videoType, audioType=$audioType, optionalInfo=$optionalInfo"
                )
            }
        }

        return esDataLength
    }

    private fun LogInfo(tag: String, msg: String) {
        if (LOG_ES_PARSE) {
            info(tag, msg)
        }
    }

    private fun getFrameType(data: ByteArray, offset: Int, length: Int): String {
        for (i in offset until length - 3) {
            if (data[i].toInt() == 0x00 && data[i + 1].toInt() == 0x00 && data[i + 2].toInt() == 0x01) {
                val nalUnitType = data[i + 3].toInt() and 0x1F
                var frameType = ""

                frameType = when (nalUnitType) {
                    NALU_TYPE_SLICE -> "P"
                    NALU_TYPE_IDR -> "I"
                    else -> continue
                }
                return frameType
            }
        }
        return ""
    }

    companion object {
        private const val TAG = "MediaDataManager"
        private const val LOG_ES_PARSE = false

        // Define NALUTypeSlice and NALUTypeIDR as constants
        private const val NALU_TYPE_SLICE = 1 // You may need to adjust these values
        private const val NALU_TYPE_IDR = 5 // based on your specific use case
    }
}