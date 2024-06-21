package com.sk.vcs.utils

import com.sk.vcs.utils.LogUtils.error
import net.jpountz.lz4.LZ4Factory

object Lz4Utils {
    private const val TAG = "Lz4Utils"

    fun getMaxCompressedLength(length: Int): Int {
        return LZ4Factory.nativeInstance().highCompressor().maxCompressedLength(length)
    }

    fun compress(src: ByteArray?, srcOff: Int, srcLen: Int, dest: ByteArray?, destOff: Int): Int {
        try {
            // Create an LZ4 compressor
            val compressor = LZ4Factory.nativeInstance().highCompressor()
            return compressor.compress(src, srcOff, srcLen, dest, destOff)
        } catch (e: Exception) {
            error(TAG, "compress Exception", e)
        }

        return 0
    }

    fun decompress(src: ByteArray?, srcOff: Int, srcLen: Int, dest: ByteArray?, destOff: Int): Int {
        try {
            // Create an LZ4 decompressor
            val decompressor = LZ4Factory.nativeInstance().safeDecompressor()

            // Decompress the data
            return decompressor.decompress(src, srcOff, srcLen, dest, destOff)
        } catch (e: Exception) {
            error(TAG, "decompress Exception", e)
            return 0
        }
    }
}