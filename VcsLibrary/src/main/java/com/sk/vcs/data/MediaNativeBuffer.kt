package com.sk.vcs.data

import com.sk.vcs.utils.LogUtils.error

/**
 * 재할당을 하지 않고 가져다 쓸수 있는 NativeBuffer
 */
class MediaNativeBuffer(private val mCapacity: Int, private val mBufferSize: Int) {
    private val mBuffer = arrayOfNulls<ByteArray>(mCapacity)
    private val mUsed = BooleanArray(mCapacity)

    init {
        for (i in 0 until mCapacity) {
            mUsed[i] = false

            // 5개의 버퍼는 미리 할당
            if (i < 5) {
                mBuffer[i] = ByteArray(mBufferSize)
            }
        }
    }

    @get:Synchronized
    val buffer: ByteArray?
        get() {
            for (i in 0 until mCapacity) {
                if (!mUsed[i]) {
                    mUsed[i] = true

                    // 메모리 할당 체크
                    if (mBuffer[i] == null) {
                        mBuffer[i] = ByteArray(mBufferSize)
                        if (DEBUG) error(
                            TAG,
                            "getBuffer new alloc : $i"
                        )
                    } else {
                        if (DEBUG) error(
                            TAG,
                            "getBuffer : $i"
                        )
                    }
                    return mBuffer[i]
                }
            }

            if (DEBUG) error(TAG, "getBuffer : -1")

            return null
        }

    @Synchronized
    fun releaseBuffer(buffer: ByteArray) {
        for (i in 0 until mCapacity) {
            if (mBuffer[i] == buffer) {
                mUsed[i] = false

                if (DEBUG) error(
                    TAG,
                    "releaseBuffer : $i"
                )

                return
            }
        }

        if (DEBUG) error(TAG, "releaseBuffer : -1")
    }

    companion object {
        private const val TAG = "MediaNativeBuffer"
        private const val DEBUG = false
    }
}