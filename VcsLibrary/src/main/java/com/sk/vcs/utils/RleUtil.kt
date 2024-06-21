package com.sk.vcs.utils

import com.sk.vcs.utils.LogUtils.info
import java.util.Arrays
import java.util.concurrent.TimeUnit

object RleUtil {
    private const val TAG = "RleUtil"

    fun encode(input: String): String {
        val nano1 = System.nanoTime()

        val result = StringBuilder()
        val lengthOfInput = input.length
        var lastCharacter = input[0]
        var lastCharacterCount = 1

        for (index in 1..lengthOfInput) {
            if (index == lengthOfInput) {
                result.append(lastCharacter).append(lastCharacterCount)
                break
            }

            val currentCharacter = input[index]
            if (lastCharacter == currentCharacter) {
                lastCharacterCount++
            } else {
                result.append(lastCharacter).append(lastCharacterCount)
                lastCharacter = currentCharacter
                lastCharacterCount = 1
            }
        }

        val nano2 = System.nanoTime()
        val result1 = TimeUnit.NANOSECONDS.toMicros(nano2 - nano1)
        info(TAG, "encoding total time taken : nano seconds -> $result1")

        return result.toString()
    }

    fun decode(encoded: String): String {
        val nano1 = System.nanoTime()

        info(TAG, "decode data = $encoded")

        val result = StringBuilder()
        val lengthOfEncodedString = encoded.length

        var timesToRepeatLastCharacter = StringBuilder()
        var lastCharacter = encoded[0]

        for (index in 1..lengthOfEncodedString) {
            if (index == lengthOfEncodedString) {
                for (i in 0 until timesToRepeatLastCharacter.toString().toInt()) {
                    result.append(lastCharacter)
                }
                break
            }

            val currentCharacter = encoded[index]
            if (Character.isDigit(currentCharacter)) {
                timesToRepeatLastCharacter.append(currentCharacter)
            } else {
                for (i in 0 until timesToRepeatLastCharacter.toString().toInt()) {
                    result.append(lastCharacter)
                }

                lastCharacter = currentCharacter
                timesToRepeatLastCharacter = StringBuilder()
            }
        }

        val nano2 = System.nanoTime()
        val result1 = TimeUnit.NANOSECONDS.toMicros(nano2 - nano1)
        info(TAG, "decoding total time taken : nano seconds -> $result1")

        return result.toString()
    }

    fun decode(dst: ByteArray?, dstOffset: Int, src: ByteArray, srcOffset: Int, srcSize: Int): Int {
        var srcSize = srcSize
        var dstSize = 0
        var srcCnt: Int

        srcSize--

        var i = srcOffset
        while (i < srcOffset + srcSize) {
            srcCnt = src[i + 1].toInt() and 0xFF
            Arrays.fill(dst, dstOffset + dstSize, dstOffset + dstSize + srcCnt, src[i])
            dstSize += srcCnt
            i += 2
        }

        return dstSize
    }
}