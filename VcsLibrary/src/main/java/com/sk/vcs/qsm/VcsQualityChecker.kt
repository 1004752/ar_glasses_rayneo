package com.sk.vcs.qsm

import com.sk.vcs.utils.LogUtils.debug
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs

class VcsQualityChecker {
    private val renderTimeQueue: Queue<Long> = LinkedList()

    private var totalSum = 0L

    fun getRenderingErrorCount(): Int {
        return renderingErrorCount
    }

    fun getRenderingDropCount(): Int {
        val diff = abs((decoderInCount - rendererOutCount).toDouble())
            .toInt()
        if (diff >= DROP_FRAME_THRESHOLD) {
            renderingDropCount = diff
        }
        return renderingDropCount
    }

    fun resetRenderingErrorCount() {
        renderingErrorCount = 0
        decoderInCount = 0
        rendererOutCount = 0
        renderingDropCount = 0
    }

    fun addDecoderInput() {
        decoderInCount++
    }

    fun checkValidRenderTime(pts: Long, renderTime: Long) {
        val renderingTimeDifference = abs((renderTime - pts).toDouble()).toLong()

        rendererOutCount++

        if (pts == DUMMY_FRAME_PTS.toLong()) {
            checkAndReset()
        } else {
            addRenderTime(renderingTimeDifference)
        }
        //LogUtils.debug(TAG, "pts: " + pts +  ", current : " + renderTime + ", renderingTimeDifference : " + renderingTimeDifference);
    }

    fun checkAndReset() {
        if (renderTimeQueue.size > 0) {
            val average = totalSum / renderTimeQueue.size
            checkLargeDifference(average)
        }

        reset()
    }

    fun reset() {
        renderTimeQueue.clear()
        totalSum = 0
    }

    private fun addRenderTime(time: Long) {
        renderTimeQueue.offer(time)
        totalSum += time

        if (renderTimeQueue.size > MAX_QUEUE_SIZE) {
            val renderTime = renderTimeQueue.poll()
            if (renderTime != null) {
                totalSum -= renderTime
            }
        }
    }

    private fun checkLargeDifference(average: Long) {
        var average = average
        if (renderTimeQueue.size > 0) {
            val timestampList = StringBuilder()

            val iterator = renderTimeQueue.iterator()
            while (iterator.hasNext()) {
                val value = iterator.next()
                if (abs((value - average).toDouble()) >= RENDER_TIME_THRESHOLD) {
                    iterator.remove()
                    renderingErrorCount++
                    totalSum -= value

                    timestampList.append(value).append(" ")

                    average = totalSum / renderTimeQueue.size
                }
            }

            if (timestampList.length > 0) {
                debug(TAG, "checkLargeDifference: $timestampList")
            }
        }
    }

    companion object {
        private const val TAG = "VcsQualityChecker"
        private const val MAX_QUEUE_SIZE = 100

        private const val RENDER_TIME_THRESHOLD = 300

        private const val DROP_FRAME_THRESHOLD = 5

        private const val DUMMY_FRAME_PTS = 1

        private var renderingErrorCount = 0

        private var renderingDropCount = 0

        private var decoderInCount = 0

        private var rendererOutCount = 0
    }
}