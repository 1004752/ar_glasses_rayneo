package com.sk.csplayer.util

import android.content.Context
import com.sk.vcs.VcsDefine
import com.sk.vcs.data.VcsApp
import java.net.NetworkInterface
import java.util.Collections

class VcsConnectionManager {
    // WebApp 연동 규격에 맞게 VCS 시작 커맨드(2000)를 정의하여 사용
    fun getStartCommand(context: Context, fileName: String?): String {
        var startApp = ""

        try {
            val `is` = context.assets.open(fileName!!)
            val size = `is`.available()
            val buf = ByteArray(size)

            if (`is`.read(buf) > 0) {
                startApp = String(buf)
            }

            `is`.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return startApp
    }

    fun getVcsApp(): VcsApp {
        val vcsApp = VcsApp()

        vcsApp.mStbModel = "BFX-AT100"
        vcsApp.mMacAddress = getMACAddress()
        vcsApp.mSvcId = "{3E176719-BAE9-11EC-8695-9146D6A4D74A}"
        vcsApp.mPublicIp = ""

        vcsApp.mAppId = "300"
        vcsApp.mSoCode = ""

        vcsApp.mWebAppVersion = "541"
        //vcsApp.mWebAppUrl = ""; //XML 연동일 때
        vcsApp.mWebAppUrl = "http://agw-stg.sk-iptv.com:8080/ui5vcs/v542/" // Json 연동 가능 웹앱
        //        vcsApp.mWebAppUrl = "http://1.255.102.2:8095/alpha-ccu"; // 내부 CCU 측정용 웹앱
        vcsApp.mInitiateType = "홈"
        //vcsApp.mApiKey = "l7xx159a8ca72966400b886a93895ec9e2e3"; // STG
        vcsApp.mApiKey = "l7xx851d12cc66dc4d2e86a461fb5a530f4a" // PRD
        vcsApp.mAuthVal = "EVFr8Zq+USKkdUMtmtY9gEBUdb/T+6Pn4jwPH2X0j8g="

        vcsApp.mUseCsr = true
        vcsApp.mCsrUrl = "https://agw.sk-iptv.com:8443"

        vcsApp.mVcsIp = "1.224.3.158" // 1.224.3.158
        vcsApp.mVcsPort = 3390

        vcsApp.mRecentVcsIp = ""
        vcsApp.mRecentVcsPort = 0

        vcsApp.mUseReferrer = false
        vcsApp.mReferrerIp = ""
        vcsApp.mReferrerPort = 0

        vcsApp.mEnableInfoLog = true
        vcsApp.mClearCsrCache = true
        vcsApp.mInterfaceFormat = VcsDefine.INTERFACE_TYPE_JSON

        return vcsApp
    }

    private fun getMACAddress(): String {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.name.equals("eth0", ignoreCase = true)) continue

                val mac = intf.hardwareAddress ?: return ""
                val buf = StringBuilder()
                for (aMac in mac) buf.append(String.format("%02X:", aMac))
                if (buf.length > 0) buf.deleteCharAt(buf.length - 1)
                return buf.toString()
            }
        } catch (ignored: java.lang.Exception) {
        } // for now eat exceptions


        return "12:34:56:78:90:AB"
    }

    companion object {
        private var mVcsConnectionManager: VcsConnectionManager? = null

        @Synchronized
        fun getInstance(): VcsConnectionManager? {
            if (mVcsConnectionManager == null) {
                mVcsConnectionManager = VcsConnectionManager()
            }

            return mVcsConnectionManager
        }
    }
}