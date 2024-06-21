package com.sk.vcs.utils

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID

object LogUtils {
    private const val LOG_TAG = "VcsLibrary"
    private const val MAX_LOG_BUFFER = 3800
    var LOG_INFO_ENABLED: Boolean = false

    private var LOG_UUID: String? = null

    fun logByteStream(tag: String, baos: ByteArrayOutputStream) {
        if (!LOG_INFO_ENABLED) {
            return
        }

        val buff = baos.toByteArray()
        val buffer = StringBuilder()

        for (b in buff) {
            buffer.append(String.format("%02x ", b))
        }

        Log.i(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + "bytes : " + buffer)
    }

    fun logBytes(tag: String, buff: ByteArray, offset: Int, length: Int) {
        if (!LOG_INFO_ENABLED) {
            return
        }

        if (buff.size < (offset + length)) {
            return
        }

        val buffer = StringBuilder()

        for (i in offset until offset + length) {
            buffer.append(String.format("%02x ", buff[i]))
        }

        Log.i(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + "bytes : " + buffer)
    }

    fun debug(tag: String, log: String) {
        if (!LOG_INFO_ENABLED) {
            return
        }

        if (log.length > MAX_LOG_BUFFER) {
            val chunkCount = log.length / MAX_LOG_BUFFER
            for (i in 0..chunkCount) {
                val max = MAX_LOG_BUFFER * (i + 1)
                if (max >= log.length) {
                    Log.d(
                        LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log.substring(
                            MAX_LOG_BUFFER * i
                        )
                    )
                } else {
                    Log.d(
                        LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log.substring(
                            MAX_LOG_BUFFER * i, max
                        )
                    )
                }
            }
        } else {
            Log.d(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log)
        }
    }

    fun info(tag: String, log: String) {
        if (!LOG_INFO_ENABLED) {
            return
        }

        if (log.length > MAX_LOG_BUFFER) {
            val chunkCount = log.length / MAX_LOG_BUFFER
            for (i in 0..chunkCount) {
                val max = MAX_LOG_BUFFER * (i + 1)
                if (max >= log.length) {
                    Log.i(
                        LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log.substring(
                            MAX_LOG_BUFFER * i
                        )
                    )
                } else {
                    Log.i(
                        LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log.substring(
                            MAX_LOG_BUFFER * i, max
                        )
                    )
                }
            }
        } else {
            Log.i(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log)
        }
    }

    // 에러 로그는 무조건 출력
    fun error(tag: String, log: String) {
        if (log.length > MAX_LOG_BUFFER) {
            val chunkCount = log.length / MAX_LOG_BUFFER
            for (i in 0..chunkCount) {
                val max = MAX_LOG_BUFFER * (i + 1)
                if (max >= log.length) {
                    Log.e(
                        LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log.substring(
                            MAX_LOG_BUFFER * i
                        )
                    )
                } else {
                    Log.e(
                        LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log.substring(
                            MAX_LOG_BUFFER * i, max
                        )
                    )
                }
            }
        } else {
            Log.e(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log)
        }
    }

    fun error(tag: String, log: String, throwable: Throwable?) {
        Log.e(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log, throwable)
    }

    // 필터 없이 항상 출력
    fun verbose(tag: String, log: String) {
        Log.v(LOG_TAG, "[" + LOG_UUID + "] " + "[" + tag + "] " + log)
    }

    fun initLogUniqueId() {
        val timeBasedId = UUID.randomUUID()

        val currentTimeMillis = System.currentTimeMillis()
        val timeBasedUniqueId = UUID(currentTimeMillis, timeBasedId.leastSignificantBits)

        val splitParts =
            timeBasedUniqueId.toString().split("-".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

        // 마지막 부분 선택
        LOG_UUID = splitParts[splitParts.size - 1].uppercase(Locale.getDefault())
    }
}