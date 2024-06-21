package com.sk.vcs.data

import android.annotation.SuppressLint
import android.text.TextUtils
import com.sk.vcs.VcsDefine.INTERFACE_TYPE_JSON
import com.sk.vcs.utils.LogUtils.verbose

class VcsApp {
    var mStbModel: String? = null
    var mMacAddress: String? = null
    var mSvcId: String? = null
    var mWebAppVersion: String? = null
    var mWebAppUrl: String? = null
    var mPublicIp: String? = null
    var mInitiateType: String? = null
    var mAuthVal: String? = null
    var mApiKey: String? = null

    var mAppId: String? = null
    var mSoCode: String? = null

    var mCsrUrl: String? = null

    var mVcsIp: String? = null
    var mVcsPort: Int = 0

    var mRecentVcsIp: String? = null
    var mRecentVcsPort: Int = 0

    var mUseReferrer: Boolean = false
    var mReferrerIp: String? = null
    var mReferrerPort: Int = 0

    var mUseCsr: Boolean = false
    var mEnableInfoLog: Boolean = false

    var mClearCsrCache: Boolean = false
    var mInterfaceFormat: Int = 0

    var mDebugSessionIndex: Int = -1 // WebApp Debug용 Session Index (0~8까지만 지원, 사용하지 않을 경우 -1로 설정)

    var mVideoCodec: String = "H264" // H264, H265
    var mVideoFps: Int = 30 // 30, 60
    var mVideoBitrate: Int = 5000
    var mCompressionType: String = "lz4hc" // lz4, lz4hc, zip

    @SuppressLint("DefaultLocale")
    fun printVcsAppInfo() {
        verbose(
            TAG,
            "=================================================================================================================="
        )
        verbose(
            TAG,
            String.format(
                "StbModel = %s, MacAddress = %s, SvcId(STB_ID) = %s",
                mStbModel,
                mMacAddress,
                mSvcId
            )
        )
        verbose(
            TAG,
            String.format("AppId = %s, PublicIp = %s, SoCode = %s", mAppId, mPublicIp, mSoCode)
        )
        verbose(
            TAG,
            String.format("WebAppVersion = %s, WebAppUrl = %s", mWebAppVersion, mWebAppUrl)
        )
        verbose(
            TAG,
            String.format(
                "InitiateType = %s, InterfaceFormat = %s",
                mInitiateType,
                if ((mInterfaceFormat == INTERFACE_TYPE_JSON)) "JSON" else "XML"
            )
        )

        if (!TextUtils.isEmpty(mVcsIp)) {
            verbose(TAG, String.format("User Setting VCS Server = %s:%d", mVcsIp, mVcsPort))
        } else {
            verbose(TAG, "User Setting VCS Server = NONE")
        }

        if (mUseCsr) {
            verbose(TAG, String.format("CSR Server = %s", mCsrUrl))
            verbose(
                TAG,
                String.format("CSR Server Cache = %s", if (mClearCsrCache) "false" else "true")
            )
        } else {
            verbose(TAG, "CSR Server Disabled!!!")
        }

        if (!TextUtils.isEmpty(mRecentVcsIp)) {
            verbose(TAG, String.format("Recent VCS Server = %s:%d", mRecentVcsIp, mRecentVcsPort))
        } else {
            verbose(TAG, "Recent VCS Server = NONE")
        }

        if (mUseReferrer) {
            verbose(TAG, String.format("Referrer Server = %s:%d", mReferrerIp, mReferrerPort))
        } else {
            verbose(TAG, "Referrer Server Disabled!!!")
        }
        verbose(
            TAG,
            String.format("%s", if (mEnableInfoLog) "VCS Log Enabled!!!" else "VCS Log Disabled!!!")
        )
        verbose(
            TAG,
            "=================================================================================================================="
        )
    }

    companion object {
        private const val TAG = "VcsApp"
    }
}