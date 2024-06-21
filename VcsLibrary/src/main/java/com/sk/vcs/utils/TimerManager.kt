package com.sk.vcs.utils

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 각 클래스마다 timer를 구현하면 코드가 복잡해지고,
 * 설정한 클래스 이외에 다른 클래스에서도 참조해야하는 경우가 있으므로 global timer를 만들어 사용함.
 * TIMEOUT 이벤트를 여러곳에서 받고 싶으면 수정해서 사용하면 됨.
 *
 * 예시)
 * 1. timer정의와 start, stop
 * public static final String CONTROL_KEEP_ALIVE = "CONTROL_KEEP_ALIVE";
 * // Keep Alive
 * public static void startKeepAliveTimer(OnTimeoutListener listener) {
 * startTimer(CONTROL_KEEP_ALIVE, 4000(timeout), listener);
 * }
 *
 * public static void stopKeepAliveTimer() {
 * stopTimer(CONTROL_KEEP_ALIVE);
 * }
 *
 * 2. timer가 돌고 있는지 조회
 * hasTimer(CONTROL_KEEP_ALIVE);
 *
 * @author ejlee
 */
open class TimerManager {
    interface OnTimeoutListener {
        fun onTimeout(type: String?)
    }

    private val mTimerMap = ConcurrentHashMap<String, OnTimeoutListener>()

    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val type = msg.obj as String
            Log.d(TAG, "onTimeout $type")

            val listener = mTimerMap[type]
            if (listener != null) {
                listener.onTimeout(type)
                mTimerMap.remove(type)
            }

            super.handleMessage(msg)
        }
    }

    companion object {
        private const val TAG = "TimerManager"

        private var mManager: TimerManager? = null

        @Synchronized
        fun getInstance(): TimerManager? {
            if (mManager == null) {
                mManager = TimerManager()
            }

            return mManager
        }

        fun getHandler(): Handler {
            return getInstance()!!.mHandler
        }

        fun getTimerMap(): ConcurrentHashMap<String, OnTimeoutListener> {
            return getInstance()!!.mTimerMap
        }

        fun hasTimer(type: String?): Boolean {
            return getTimerMap()[type] != null
        }

        fun startTimer(type: String, delayTime: Long, listener: OnTimeoutListener?) {
            Log.d(TAG, "startTimer $type($delayTime)")

            stopTimer(type)

            getHandler().sendMessageDelayed(
                getHandler().obtainMessage(type.hashCode(), type),
                delayTime
            )

            getTimerMap()[type] = listener!!
        }

        fun stopTimer(type: String) {
            getHandler().removeMessages(type.hashCode())

            if (getTimerMap().remove(type) == null) {
                Log.d(TAG, "timer is not found !!!")
            } else {
                Log.d(TAG, "stopTimer $type")
            }
        }
    }
}