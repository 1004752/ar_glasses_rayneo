package com.sk.vcs.network

import android.annotation.SuppressLint
import android.text.TextUtils
import com.sk.vcs.ErrorCode
import com.sk.vcs.data.VcsApp
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * CSR 연동 클래스
 *
 */
class VcsRouter {
    private var mTrustAllHostsSSLContext: SSLContext? = null

    private var mCsrResponse: String? = null
    private val mLock = Any()

    class CsrResult {
        var errorCode: Int = 0
        var errorText: String? = null
        var cssIp: String? = null
        var cssPort: Int = 0
        var cssSessionId: String? = null
    }

    init {
        mTrustAllHostsSSLContext = getSSLContextThatTrustAllHosts()
    }

    /**
     * GW를 통하여 VCS 서버 정보 요청
     *
     * @param vcsApp vcs app info
     *
     * @return Routing Result Object
     */
    fun requestRoute(vcsApp: VcsApp): CsrResult? {
        mCsrResponse = null

        var csrStatus = "INIT"
        if (vcsApp.mClearCsrCache) {
            vcsApp.mClearCsrCache = false
            csrStatus = "NO-CACHE"
        }

        val requestUrl = vcsApp.mCsrUrl + "/CSRS/IFCSR_ROUTE_INFO.action"
        val entity = ("<?xml version='1.0' encoding='utf-8' ?>"
                + "<CSROUTE>"
                + "<STB ID ='{" + vcsApp.mMacAddress + "}' "
                + "STATUS='" + csrStatus + "' "
                + "APPID='" + vcsApp.mAppId + "' "
                + "SVCID='" + vcsApp.mSvcId + "' "
                + "SOCODE='' />"
                + "</CSROUTE>")

        info(TAG, "CSR Request URL : $requestUrl")
        info(TAG, "CSR Request Body : $entity")

        val thread = Thread({
            var connection: HttpsURLConnection? = null
            try {
                connection = getHttpsUrlConnection(requestUrl)
                connection.setRequestProperty("Content-Type", "text/xml")
                connection.setRequestProperty("Client_ID", vcsApp.mSvcId)
                connection.setRequestProperty("CLIENT_IP", vcsApp.mPublicIp)
                connection.setRequestProperty("TimeStamp", getLogTime())
                connection.setRequestProperty("Auth_Val", vcsApp.mAuthVal)
                connection.setRequestProperty("Api_Key", vcsApp.mApiKey)

                // Body 데이터 전송
                val os = connection.outputStream
                os.write(entity.toByteArray(StandardCharsets.UTF_8))
                os.flush()
                os.close()

                val resCode = connection.responseCode
                if (resCode == HttpsURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val buffer = StringBuilder()
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        buffer.append(line)
                    }
                    reader.close()

                    mCsrResponse = buffer.toString().trim { it <= ' ' }
                } else {
                    info(TAG, "Http Response Code : $resCode")
                    if (resCode == 590) {
                        val result = connection.getHeaderField("result")
                        val reason = connection.getHeaderField("reason")

                        mCsrResponse = makeCsrErrorMessage(result, reason)
                    }
                }
            } catch (e: Exception) {
                error(TAG, "requestRoute Exception = ", e)
            } finally {
                synchronized(mLock) {
                    (mLock as Object).notify()
                }

                connection?.disconnect()
            }
        }, "requestThread")
        thread.start()

        synchronized(mLock) {
            try {
                (mLock as Object).wait(4000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        return getRouteResult(mCsrResponse!!)
    }

    private fun getRouteResult(response: String): CsrResult {
        val csrResult = CsrResult()

        info(TAG, "getRouteResult : $response")

        // 수신 데이터 유효성 체크
        if (!TextUtils.isEmpty(response)) {
            val pos = IntArray(2)

            try {
                csrResult.errorCode = getIntValue(response, "Result Code=", pos)
                csrResult.errorText = getStringValue(response, "Message=", pos)

                if (csrResult.errorCode == 0) {
                    csrResult.cssIp = getStringValue(response, "IP=", pos)
                    csrResult.cssPort = getIntValue(response, "Port=", pos)
                    csrResult.cssSessionId = getStringValue(response, "SessionID=", pos)
                }
            } catch (ignored: java.lang.Exception) {
            }
        } else {
            csrResult.errorCode = ErrorCode.VCS_CONNECTION_ERROR
        }

        return csrResult
    }

    /**
     * GW를 통하여 셋탑의 Public Ip를 획득하는 함수
     *
     * @param vcsApp vcs app 정보
     *
     * @return Public IP 정보
     */
    fun requestPublicIp(vcsApp: VcsApp): String? {
        mCsrResponse = null
        val requestUrl = vcsApp.mCsrUrl + "/CSRS/IFCSR_REMOTE_IP.action"
        val entity = ("<?xml version='1.0' encoding='utf-8' ?>"
                + "<CSROUTE>"
                + "<STB ID ='{" + vcsApp.mMacAddress + "}' "
                + "STATUS='INIT' "
                + "APPID='" + vcsApp.mAppId + "' "
                + "SOCODE='" + (if (TextUtils.isEmpty(vcsApp.mSoCode)) "" else vcsApp.mSoCode) + "' />"
                + "</CSROUTE>")

        info(TAG, "CSR Request URL : $requestUrl")
        info(TAG, "CSR Request Body : $entity")

        val thread = Thread({
            var connection: HttpsURLConnection? = null
            try {
                connection = getHttpsUrlConnection(requestUrl)
                connection.setRequestProperty("Content-Type", "text/xml")
                connection.setRequestProperty("Client_ID", vcsApp.mSvcId)
                connection.setRequestProperty("CLIENT_IP", vcsApp.mPublicIp)
                connection.setRequestProperty("TimeStamp", getLogTime())
                connection.setRequestProperty("Auth_Val", vcsApp.mAuthVal)
                connection.setRequestProperty("Api_Key", vcsApp.mApiKey)

                // Body 데이터 전송
                val os = connection.outputStream
                os.write(entity.toByteArray(StandardCharsets.UTF_8))
                os.flush()
                os.close()

                // 데이터 수신 처리
                val resCode = connection.responseCode
                if (resCode == HttpsURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val buffer = java.lang.StringBuilder()
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        buffer.append(line)
                    }
                    reader.close()

                    mCsrResponse = buffer.toString().trim { it <= ' ' }
                } else {
                    info(TAG, "Http Response Code : $resCode")
                    if (resCode == 590) {
                        val result = connection.getHeaderField("result")
                        val reason = connection.getHeaderField("reason")

                        mCsrResponse = makeCsrErrorMessage(result, reason)
                    }
                }
            } catch (e: java.lang.Exception) {
                error(TAG, "requestPublicIp Exception = ", e)
            } finally {
                synchronized(mLock) {
                    (mLock as Object).notify()
                }

                connection?.disconnect()
            }
        }, "requestThread")
        thread.start()

        synchronized(mLock) {
            try {
                (mLock as Object).wait(4000)
            } catch (ignored: InterruptedException) {
            }
        }

        return getPublicIp(mCsrResponse!!)
    }

    private fun getPublicIp(response: String): String? {
        var publicIp: String? = null

        info(TAG, "getPublicIp : $response")

        // 수신 데이터 유효성 체크
        if (!TextUtils.isEmpty(response)) {
            val pos = IntArray(2)

            try {
                publicIp = getStringValue(response, "STB IP=", pos)

                if (publicIp!!.contains(",")) {
                    val ipList = publicIp!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    publicIp = ipList[0]
                }
            } catch (ignored: java.lang.Exception) {
            }
        }

        return publicIp
    }

    fun requestCommunityId(referrerUrl: String, remoteIp: String, groupId: String): String? {
        info(
            TAG,
            "request url : $referrerUrl, remoteIp : $remoteIp, groupId : $groupId"
        )

        mCsrResponse = null

        val csrUrl =
            "http://$referrerUrl/referrer/v1.0/community?remoteIp=$remoteIp&groupId=$groupId"
        info(TAG, "requestCommunityId URL : $csrUrl")

        val thread = Thread({
            var reader: BufferedReader? = null
            var connection: HttpURLConnection? = null
            try {
                connection = URL(csrUrl).openConnection() as HttpURLConnection
                connection!!.requestMethod = "GET"
                connection!!.connectTimeout = 2000
                connection!!.setRequestProperty("Content-Type", "application/json")

                val resCode = connection!!.responseCode
                info(TAG, "Response Code : $resCode")

                if (resCode == HttpURLConnection.HTTP_OK) {
                    reader = BufferedReader(InputStreamReader(connection!!.inputStream))

                    val buffer = java.lang.StringBuilder()
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        buffer.append(line)
                    }
                    mCsrResponse = buffer.toString().trim { it <= ' ' }
                }
            } catch (e: java.lang.Exception) {
                error(TAG, "requestCommunityId Exception=", e)
            } finally {
                synchronized(mLock) {
                    (mLock as Object).notify()
                }

                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        error(TAG, "requestCommunityId Exception=", e)
                    }
                }
                connection?.disconnect()
            }
        }, "requestThread")
        thread.start()

        synchronized(mLock) {
            try {
                (mLock as Object).wait(4000)
            } catch (ignored: InterruptedException) {
            }
        }

        return getCommunityId(mCsrResponse!!)
    }

    private fun getCommunityId(response: String): String? {
        var communityId: String? = null

        info(TAG, "getCommunityId : $response")

        // 결과 Parsing
        if (!TextUtils.isEmpty(response)) {
            try {
                val jsonObject = JSONObject(response)
                communityId = jsonObject.getString("communityId")
            } catch (e: JSONException) {
            }
        }
        return communityId
    }

    /**
     * xml 문자열에서 token에 해당하는 value 값을 반환
     *
     */
    private fun getStringValue(text: String, token: String, pos: IntArray): String? {
        pos[0] = text.indexOf(token, pos[1]) + token.length
        pos[0] = text.indexOf("\"", pos[0]) + 1
        pos[1] = text.indexOf("\"", pos[0])

        return text.substring(pos[0], pos[1])
    }

    /**
     * xml 문자열에서 token에 해당하는 value 값을 반환
     *
     */
    private fun getIntValue(text: String, token: String, pos: IntArray): Int {
        pos[0] = text.indexOf(token, pos[1]) + token.length
        pos[0] = text.indexOf("\"", pos[0]) + 1
        pos[1] = text.indexOf("\"", pos[0])

        val value = text.substring(pos[0], pos[1])

        return value.toInt()
    }

    private fun getLogTime(): String {
        @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyyMMddHHmmss.SSS")
        return sdf.format(Date())
    }

    private fun makeCsrErrorMessage(errorCode: String, errorMsg: String): String {
        val errorFmt =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><CSROUTE><Result Code=\"%s\" Message=\"%s\"/></CSROUTE>"
        return String.format(errorFmt, errorCode, errorMsg)
    }

    @Throws(IOException::class)
    private fun getHttpsUrlConnection(httpsUrl: String): HttpsURLConnection {
        val connection = URL(httpsUrl).openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.doInput = true
        connection.useCaches = false
        connection.defaultUseCaches = false
        connection.connectTimeout = 2000
        connection.hostnameVerifier =
            HostnameVerifier { hostname: String?, session: SSLSession? -> true }
        connection.sslSocketFactory = mTrustAllHostsSSLContext!!.socketFactory

        return connection
    }

    private fun getSSLContextThatTrustAllHosts(): SSLContext? {
        var sSLContext: SSLContext? = null

        val trustManagers = (arrayOf<TrustManager>(
            (object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return (arrayOf())
                }

                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                }
            }),
        ))

        try {
            sSLContext = SSLContext.getInstance("TLS")
            sSLContext.init(null, trustManagers, (SecureRandom()))
        } catch (e: NoSuchAlgorithmException) {
            error(TAG, "getSSLContextThatTrustAllHosts", e)
        } catch (e: KeyManagementException) {
            error(TAG, "getSSLContextThatTrustAllHosts", e)
        }

        return sSLContext
    }

    companion object {
        private const val TAG = "VcsRouter"
    }
}