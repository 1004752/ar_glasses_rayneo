package com.sk.vcs.data

import android.annotation.SuppressLint
import com.sk.vcs.utils.LogUtils.verbose

class VcsSessionInfo {
    var mServerIp: String? = null
    var mServerPort: Int = 0
    var mSoCode: String? = null
    var mPublicIp: String? = null
    var mVcsSessionId: String? = null

    @SuppressLint("DefaultLocale")
    fun printVcsSessionInfo() {
        verbose(TAG, "VCSSessionInfo ===================================================")
        verbose(TAG, String.format("VCS Connection Server = %s:%d", mServerIp, mServerPort))
        verbose(
            TAG,
            String.format(
                "mSoCode = %s, mPublicIp = %s, mVcsSessionId = %s",
                mSoCode,
                mPublicIp,
                mVcsSessionId
            )
        )
        verbose(TAG, "VCSSessionInfo ===================================================")
    }

    companion object {
        private const val TAG = "VcsSessionInfo"
    }
}