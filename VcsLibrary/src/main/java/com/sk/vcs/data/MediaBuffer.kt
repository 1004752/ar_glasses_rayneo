package com.sk.vcs.data

import com.sk.vcs.utils.LogUtils

/**
 * 메모리를 재할당을 하지 않고 사용하도록 만든 Queue 클래스
 *
 */
class MediaBuffer(private val mCapacity: Int, size: Int) {
    private val mBuffer = Array(mCapacity) { ByteArray(size) }
    private var mHead: Int
    private var mTail: Int
    private val mSyncObject = Any()

    init {
        mHead = -1
        mTail = -1
    }

    fun reset() {
        synchronized(mSyncObject) {
            mHead = -1
            mTail = -1
        }
    }

    val isEmpty: Boolean
        get() = mHead == mTail

    val isFull: Boolean
        get() = (mTail == mHead - 1 || mTail == (mHead + mCapacity - 1))

    val nextBuffer: ByteArray
        get() = mBuffer[(mTail + 1) % mCapacity]

    fun enQueue(buff: ByteArray) {
        synchronized(mSyncObject) {
            mTail = (mTail + 1) % mCapacity
            mBuffer[mTail] = buff

            if (DEBUG) LogUtils.info(
                TAG,
                "enQueue head : $mHead tail : $mTail"
            )
            synchronized(this) {
                if (size() == 1) {
                    (this as Object).notifyAll()
                }
            }
        }
    }

    val lastBuffer: ByteArray?
        get() {
            if (mTail != -1) {
                return mBuffer[mTail]
            }

            return null
        }

    fun deQueue(): ByteArray? {
        synchronized(mSyncObject) {
            if (isEmpty) {
                LogUtils.info(
                    TAG,
                    "deQueue head : $mHead tail : $mTail"
                )
                return null
            }
            mHead = (mHead + 1) % mCapacity

            if (DEBUG) LogUtils.info(
                TAG,
                "deQueue head : $mHead tail : $mTail"
            )
            return mBuffer[mHead]
        }
    }

    fun size(): Int {
        synchronized(mSyncObject) {
            if (isEmpty) {
                return 0
            }
            return if (mHead < mTail) {
                mTail - mHead
            } else {
                mTail + mCapacity - mHead
            }
        }
    }

    companion object {
        private const val TAG = "MediaBuffer"
        private const val DEBUG = false
    }
}