package com.sk.vcs.network.base

import com.sk.vcs.utils.ByteUtils.byteToInt
import com.sk.vcs.utils.ByteUtils.byteToIntR
import com.sk.vcs.utils.ByteUtils.byteToShort
import com.sk.vcs.utils.ByteUtils.byteToShortR
import com.sk.vcs.utils.LogUtils.error
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

abstract class TcpClient(protected var mDeviceId: String) {
    private var mSocket: Socket? = null
    private var mBufferedInputStream: BufferedInputStream? = null
    private var mBufferedOutputStream: BufferedOutputStream? = null
    private var mInputStream: DataInputStream? = null
    private var mOutputStream: DataOutputStream? = null
    protected var mIsRun: Boolean = false

    interface ErrorNotifyCallback {
        fun NotifyError()
    }

    abstract fun startReaderThread()

    fun startClient(ip: String?, port: Int, timeout: Int): Boolean {
        try {
            mSocket = Socket()
            val address = InetSocketAddress(ip, port)

            mSocket!!.soTimeout = 2000
            mSocket!!.setSoLinger(true, 0)
            mSocket!!.tcpNoDelay = true

            mSocket!!.connect(address, timeout)

            mBufferedInputStream = BufferedInputStream(mSocket!!.getInputStream())
            mInputStream = DataInputStream(mBufferedInputStream)

            mBufferedOutputStream = BufferedOutputStream(mSocket!!.getOutputStream())
            mOutputStream = DataOutputStream(mBufferedOutputStream)
        } catch (e: java.lang.Exception) {
            error(TAG, "startClient Exception!!!", e)

            stopClient()
            return false
        }

        startReaderThread()

        return true
    }

    fun isRun(): Boolean {
        return mIsRun
    }

    fun read(buffer: ByteArray?, offset: Int, readSize: Int): Int {
        var size = readSize
        var len = 0

        while (size > 0) {
            try {
                mInputStream!!.readFully(buffer, offset, size)
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: java.lang.Exception) {
                return -2
            }
            len = size
            size = 0
        }

        return len
    }

    fun read(buffer: ByteArray?, size: Int): Int {
        return read(buffer, 0, size)
    }

    protected fun readByte(): Byte {
        val buffer = ByteArray(1)
        val len = read(buffer, 1)

        if (len == 1) {
            return buffer[0]
        }

        return (-4.toByte()).toByte()
    }

    fun readShort(): Short {
        val buffer = ByteArray(2)
        val len = read(buffer, 2)
        // LogUtils.logBytes("readShort", buffer, 2);
        if (len < 0) {
            return len.toShort()
        }

        if (len == 2) {
            return byteToShort(buffer, 0)
        }

        return -4
    }

    protected fun readShortR(): Short {
        val buffer = ByteArray(2)
        val len = read(buffer, 2)
        // LogUtils.logBytes("readShortR", buffer, 2);
        if (len < 0) {
            return len.toShort()
        }

        if (len == 2) {
            return byteToShortR(buffer, 0)
        }

        return -4
    }

    // 뒤집혀져 오는 short 값을 unsigned int로 바꾼다.
    fun readShortRToInt(): Int {
        val buffer = ByteArray(2)
        val len = read(buffer, 2)
        if (len < 0) {
            return len.toShort().toInt()
        }

        if (len == 2) {
            // LogUtils.logBytes("readShortRToInt", buffer, 2);
            return ((buffer[1].toInt() and 0xFF) shl 8) + (buffer[0].toInt() and 0xFF)
        }

        return -4
    }

    fun readInt(): Int {
        val buffer = ByteArray(4)
        val len = read(buffer, 4)

        if (len < 0) {
            return len
        }

        if (len == 4) {
            return byteToInt(buffer, 0)
        }

        return -4
    }

    fun readIntR(): Int {
        val buffer = ByteArray(4)
        val len = read(buffer, 4)

        if (len < 0) {
            return len
        }

        if (len == 4) {
            return byteToIntR(buffer, 0)
        }

        return -4
    }

    fun readString(size: Int): String? {
        val buffer = ByteArray(size)
        val len = read(buffer, size)

        if (len < 0 || len != size) {
            return null
        }

        return String(buffer)
    }

    protected fun appendStreamString(baos: ByteArrayOutputStream, text: String?): Int {
        if (text == null || text.isEmpty()) {
            return appendStreamInt(baos, 0)
        }

        var length = -1
        try {
            val byBuffer = text.toByteArray()
            length = byBuffer.size
            appendStreamInt(baos, length)

            baos.write(byBuffer)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return length
    }

    protected fun appendStreamBytes(baos: ByteArrayOutputStream, buffer: ByteArray?): Int {
        if (buffer == null || buffer.isEmpty()) {
            return appendStreamInt(baos, 0)
        }

        try {
            baos.write(buffer)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
        return buffer.size + 4
    }

    protected fun appendStreamInt(baos: ByteArrayOutputStream?, value: Int): Int {
        if (baos == null) {
            return 0
        }

        baos.write(((value shr 24) and 0xFF).toByte().toInt())
        baos.write(((value shr 16) and 0xFF).toByte().toInt())
        baos.write(((value shr 8) and 0xFF).toByte().toInt())
        baos.write((value and 0xFF).toByte().toInt())

        return 4
    }

    protected fun sendRequest(baos: ByteArrayOutputStream, callback: ErrorNotifyCallback?) {
        try {
            sendBytes(baos.toByteArray(), callback)
        } catch (e: java.lang.Exception) {
            error(TAG, "sendByteArray Exception = ", e)

            callback?.NotifyError()
        }
    }

    protected fun sendRequest(buffer: ByteArray) {
        try {
            if (mOutputStream != null) {
                mOutputStream!!.write(buffer, 0, buffer.size)
                mOutputStream!!.flush()
            }
        } catch (e: java.lang.Exception) {
            error(TAG, "sendRequest Exception = ", e)
        }
    }

    private fun sendBytes(buffer: ByteArray, callback: ErrorNotifyCallback?) {
        val thread = Thread {
            try {
                if (mOutputStream != null) {
                    mOutputStream!!.write(buffer, 0, buffer.size)
                    mOutputStream!!.flush()
                }
            } catch (e: java.lang.Exception) {
                error(TAG, "sendBytes Exception = ", e)

                callback?.NotifyError()
            }
        }

        thread.start()
    }

    open fun stopClient(): Int {
        mIsRun = false

        try {
            if (mSocket != null) {
                mSocket!!.close()
                mSocket = null
            }

            if (mInputStream != null) {
                mInputStream!!.close()
                mInputStream = null
            }

            if (mBufferedInputStream != null) {
                mBufferedInputStream!!.close()
                mBufferedInputStream = null
            }

            if (mOutputStream != null) {
                mOutputStream!!.close()
                mOutputStream = null
            }

            if (mBufferedOutputStream != null) {
                mBufferedOutputStream!!.close()
                mBufferedOutputStream = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return 0
    }

    companion object {
        private const val TAG = "TcpClient"
    }
}