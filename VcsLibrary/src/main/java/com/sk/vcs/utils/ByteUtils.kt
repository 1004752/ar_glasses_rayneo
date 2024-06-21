package com.sk.vcs.utils

import java.nio.ByteBuffer

object ByteUtils {
    fun byteToShort(src: ByteArray, offset: Int): Short {
        val s1 = src[offset].toInt() and 0xFF
        val s2 = src[offset + 1].toInt() and 0xFF

        return ((s1 shl 8) + s2).toShort()
    }

    fun byteToShortR(src: ByteArray, offset: Int): Short {
        val s1 = src[offset + 1].toInt() and 0xFF
        val s2 = src[offset].toInt() and 0xFF

        return ((s1 shl 8) + s2).toShort()
    }

    fun byteToInt(src: ByteArray, offset: Int): Int {
        val s1 = src[offset].toInt() and 0xFF
        val s2 = src[offset + 1].toInt() and 0xFF
        val s3 = src[offset + 2].toInt() and 0xFF
        val s4 = src[offset + 3].toInt() and 0xFF

        return ((s1 shl 24) + (s2 shl 16) + (s3 shl 8) + (s4))
    }

    fun byteToIntR(buffer: ByteArray, offset: Int): Int {
        val s1 = buffer[offset + 3].toInt() and 0xFF
        val s2 = buffer[offset + 2].toInt() and 0xFF
        val s3 = buffer[offset + 1].toInt() and 0xFF
        val s4 = buffer[offset].toInt() and 0xFF

        return ((s1 shl 24) + (s2 shl 16) + (s3 shl 8) + (s4))
    }

    fun byteToLong(src: ByteArray, offset: Int): Long {
        val s1 = src[offset].toLong() and 0xFFL
        val s2 = src[offset + 1].toLong() and 0xFFL
        val s3 = src[offset + 2].toLong() and 0xFFL
        val s4 = src[offset + 3].toLong() and 0xFFL
        val s5 = src[offset + 4].toLong() and 0xFFL
        val s6 = src[offset + 5].toLong() and 0xFFL
        val s7 = src[offset + 6].toLong() and 0xFFL
        val s8 = src[offset + 7].toLong() and 0xFFL

        return ((s1 shl 56) + (s2 shl 48) + (s3 shl 40) + (s4 shl 32) + (s5 shl 24) + (s6 shl 16) + (s7 shl 8) + (s8))
    }

    fun byteToLongR(src: ByteArray, offset: Int): Long {
        val s1 = src[offset + 7].toLong() and 0xFFL
        val s2 = src[offset + 6].toLong() and 0xFFL
        val s3 = src[offset + 5].toLong() and 0xFFL
        val s4 = src[offset + 4].toLong() and 0xFFL
        val s5 = src[offset + 3].toLong() and 0xFFL
        val s6 = src[offset + 2].toLong() and 0xFFL
        val s7 = src[offset + 1].toLong() and 0xFFL
        val s8 = src[offset].toLong() and 0xFFL

        return ((s1 shl 56) + (s2 shl 48) + (s3 shl 40) + (s4 shl 32) + (s5 shl 24) + (s6 shl 16) + (s7 shl 8) + (s8))
    }

    fun intToByte(value: Int, dest: ByteArray, offset: Int) {
        dest[offset] = (value shr 24 and 0xFF).toByte()
        dest[offset + 1] = (value shr 16 and 0xFF).toByte()
        dest[offset + 2] = (value shr 8 and 0xFF).toByte()
        dest[offset + 3] = (value and 0xFF).toByte()
    }

    fun intToByteR(value: Int, dest: ByteArray, offset: Int) {
        dest[offset + 3] = (value shr 24 and 0xFF).toByte()
        dest[offset + 2] = (value shr 16 and 0xFF).toByte()
        dest[offset + 1] = (value shr 8 and 0xFF).toByte()
        dest[offset] = (value and 0xFF).toByte()
    }

    fun floatToByte(value: Float, dest: ByteArray, offset: Int) {
        val intValue = java.lang.Float.floatToIntBits(value)

        dest[offset] = (intValue shr 24 and 0xFF).toByte()
        dest[offset + 1] = (intValue shr 16 and 0xFF).toByte()
        dest[offset + 2] = (intValue shr 8 and 0xFF).toByte()
        dest[offset + 3] = (intValue and 0xFF).toByte()
    }

    fun getByteArrayFromByteBuffer(byteBuffer: ByteBuffer): ByteArray {
        val bytesArray = ByteArray(byteBuffer.remaining())
        byteBuffer[bytesArray, 0, bytesArray.size]

        return bytesArray
    }
}