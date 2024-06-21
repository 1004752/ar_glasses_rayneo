package com.sk.vcs.qsm

import android.annotation.SuppressLint
import android.text.TextUtils
import com.sk.vcs.VcsDefine
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

/**
 * SKB QSM 로그 전송을 위한 클래스
 *
 */
class SkbQsmLogManager {
    private var mVcsStartTime: Long = 0
    private var mVcsServerIp: String? = null
    private var mVcsAppId: String? = null

    fun intVcsInfo() {
        mVcsStartTime = 0
        mVcsServerIp = ""
        mVcsAppId = ""
    }

    fun intVcsStartTime() {
        mVcsStartTime = System.currentTimeMillis()
    }

    fun setVcsAppId(appId: String?) {
        mVcsAppId = appId
    }

    fun setVcsServerIp(serverIp: String?) {
        mVcsServerIp = serverIp
    }

    fun getInitiateTimeLog(initiateType: String?): String? {
        try {
            val jsonMain = JSONObject()
            jsonMain.put("transType", "VCS_QUALITY")

            val jsonLog = JSONObject()
            jsonLog.put("log_info_type", "Q2")
            jsonLog.put("log_time", getLogTime())

            val jsonQuality = JSONObject()
            jsonQuality.put("vcs_version", VcsDefine.VCS_VERSION)
            jsonQuality.put("vcs_ip", mVcsServerIp)
            jsonQuality.put("vcs_app_id", mVcsAppId)

            val initiateTime = (System.currentTimeMillis() - mVcsStartTime) / 1000.0
            jsonQuality.put("initiate_time", initiateTime)
            jsonQuality.put("initiate_type", initiateType)

            jsonLog.put("vcs_quality", jsonQuality)
            jsonMain.put("log", jsonLog)

            return jsonMain.toString()
        } catch (ignore: java.lang.Exception) {
        }
        return null
    }

    fun getQualityLog(videoErrorCount: Int, audioErrorCount: Int): String? {
        // 에러가 있을 경우에만 전송
        if (videoErrorCount > 0 || audioErrorCount > 0) {
            try {
                val jsonMain = JSONObject()
                jsonMain.put("transType", "VCS_QUALITY")

                val jsonLog = JSONObject()
                jsonLog.put("log_info_type", "Q2")
                jsonLog.put("log_time", getLogTime())

                val jsonQuality = JSONObject()
                jsonQuality.put("vcs_version", VcsDefine.VCS_VERSION)
                jsonQuality.put("vcs_ip", mVcsServerIp)
                jsonQuality.put("vcs_app_id", mVcsAppId)

                if (videoErrorCount > 0) {
                    jsonQuality.put("video_jitter", videoErrorCount)
                }
                if (audioErrorCount > 0) {
                    jsonQuality.put("audio_jitter", audioErrorCount)
                }

                jsonLog.put("vcs_quality", jsonQuality)
                jsonMain.put("log", jsonLog)

                return jsonMain.toString()
            } catch (ignore: java.lang.Exception) {
            }
        }
        return null
    }

    fun getErrorLog(errorCode: String?, errorDesc: String?): String? {
        try {
            val jsonMain = JSONObject()
            jsonMain.put("transType", "VCS_ERROR")

            val jsonLog = JSONObject()
            jsonLog.put("log_info_type", "Q2")
            jsonLog.put("log_time", getLogTime())

            val jsonQuality = JSONObject()
            jsonQuality.put("vcs_version", VcsDefine.VCS_VERSION)
            jsonQuality.put("vcs_ip", if (TextUtils.isEmpty(mVcsServerIp)) "" else mVcsServerIp)
            jsonQuality.put("vcs_app_id", mVcsAppId)

            val vcsUseTime = (System.currentTimeMillis() - mVcsStartTime) / 1000.0
            jsonQuality.put("vcs_use_time", vcsUseTime)
            jsonQuality.put("error_code", errorCode)
            jsonQuality.put("error_description", errorDesc)

            jsonLog.put("vcs_error", jsonQuality)
            jsonMain.put("log", jsonLog)

            return jsonMain.toString()
        } catch (ignore: java.lang.Exception) {
        }
        return null
    }

    fun getSessionTimeoutLog(): String? {
        try {
            val jsonMain = JSONObject()
            jsonMain.put("transType", "VCS_ERROR")

            val jsonLog = JSONObject()
            jsonLog.put("log_info_type", "Q2")
            jsonLog.put("log_time", getLogTime())

            val jsonQuality = JSONObject()
            jsonQuality.put("vcs_version", VcsDefine.VCS_VERSION)
            jsonQuality.put("vcs_ip", mVcsServerIp)
            jsonQuality.put("vcs_app_id", mVcsAppId)

            val vcsUseTime = (System.currentTimeMillis() - mVcsStartTime) / 1000.0
            jsonQuality.put("vcs_use_time", vcsUseTime)
            jsonQuality.put("session_timeout", true)

            jsonLog.put("vcs_error", jsonQuality)
            jsonMain.put("log", jsonLog)

            return jsonMain.toString()
        } catch (ignore: Exception) {
        }
        return null
    }

    private fun getLogTime(): String {
        @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyyMMddHHmmss.SSS")
        return sdf.format(Date())
    }

    companion object {
        private var mSkbQsmManager: SkbQsmLogManager? = null

        @Synchronized
        fun getInstance(): SkbQsmLogManager? {
            if (mSkbQsmManager == null) {
                mSkbQsmManager = SkbQsmLogManager()
            }

            return mSkbQsmManager
        }
    }
}