package com.sk.vcs.utils

import android.text.TextUtils
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class SoCodeManager(private val mCsrUrl: String, private val mReferrerUrl: String) {
    private var mResponseData: String? = null
    private val mLock = Any()

    class ResultData {
        var publicIp: String? = null
        var soCode: String? = null
    }

    /**
     * SKB 셋탑 SoCode를 획득하는 함수
     *
     * @param appId      app id
     * @param macAddress mac adress
     *
     * @return Public IP, SoCode 정보
     */
    fun getSoCode(appId: String, macAddress: String): ResultData {
        val result = ResultData()

        result.publicIp = getPublicIp(appId, macAddress)
        if (!TextUtils.isEmpty(result.publicIp)) {
            result.soCode = getCommunityId(result.publicIp)
        }

        return result
    }

    /**
     * CSR을 통하여 셋탑의 Public Ip를 획득하는 함수
     *
     * @param appId      app id
     * @param macAddress mac adress
     * @return Public IP 정보
     */
    private fun getPublicIp(appId: String, macAddress: String): String? {
        var publicIp: String? = null
        mResponseData = null

        val requestUrl = "$mCsrUrl/CSRS/IFCSR_REMOTE_IP.action"
        val requestBody = ("<?xml version='1.0' encoding='utf-8' ?>"
                + "<CSROUTE>"
                + "<STB ID ='{" + macAddress + "}' "
                + "STATUS='INIT' "
                + "APPID='" + appId + "' "
                + "SOCODE='' />"
                + "</CSROUTE>")

        Log.i(TAG, "CSR Request URL : $requestUrl")
        Log.i(TAG, "CSR Request Body : $requestBody")

        val thread = Thread({
            var connection: HttpURLConnection? = null
            try {
                connection =
                    URL(requestUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection!!.doOutput = true
                connection.doInput = true
                connection.useCaches = false
                connection.defaultUseCaches = false
                connection.connectTimeout = 2000
                connection.setRequestProperty("Content-Type", "text/xml")
                val os = connection.outputStream
                os.write(requestBody.toByteArray(StandardCharsets.UTF_8))

                os.flush()
                os.close()

                val reader = BufferedReader(
                    InputStreamReader(
                        connection.inputStream
                    )
                )
                val buffer = StringBuilder()
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    buffer.append(line)
                }
                reader.close()

                mResponseData = buffer.toString().trim { it <= ' ' }
            } catch (e: Exception) {
                Log.e(TAG, "getPublicIp Exception = ", e)
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
                (mLock as Object).wait(3000)
            } catch (ignored: InterruptedException) {
            }
        }

        // 수신 데이터 유효성 체크
        if (!TextUtils.isEmpty(mResponseData)) {
            Log.i(TAG, "getPublicIp Response = $mResponseData")

            val pos = IntArray(2)
            try {
                publicIp = getStringValue(mResponseData, "STB IP=", pos)
            } catch (ignored: Exception) {
            }
        }

        return publicIp
    }

    /**
     * SKB Referrer 서버로부터 SoCode를 획득하는 함수
     *
     * @param remoteIp 셋탑 Public IP
     * @return SKB CommunityId 정보
     */
    private fun getCommunityId(remoteIp: String?): String? {
        var communityId: String? = null
        mResponseData = null

        val referrerUrl =
            "$mReferrerUrl/referrer/v1.0/community?remoteIp=$remoteIp&groupId=17"
        Log.i(TAG, "requestCommunityId URL : $referrerUrl")

        val thread = Thread({
            var reader: BufferedReader? = null
            var connection: HttpURLConnection? = null
            try {
                connection =
                    URL(referrerUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection!!.connectTimeout = 2000
                connection.setRequestProperty("Content-Type", "application/json")

                val resCode = connection.responseCode
                if (resCode == HttpURLConnection.HTTP_OK) {
                    reader =
                        BufferedReader(InputStreamReader(connection.inputStream))

                    val buffer = StringBuilder()
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        buffer.append(line)
                    }
                    mResponseData = buffer.toString().trim { it <= ' ' }
                } else {
                    Log.e(
                        TAG,
                        "Error Http Response Code : $resCode"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "getCommunityId Exception = ", e)
            } finally {
                synchronized(mLock) {
                    (mLock as Object).notify()
                }

                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        Log.e(
                            TAG,
                            "getCommunityId Exception = ",
                            e
                        )
                    }
                }
                connection?.disconnect()
            }
        }, "requestThread")
        thread.start()

        synchronized(mLock) {
            try {
                (mLock as Object).wait(3000)
            } catch (ignored: InterruptedException) {
            }
        }

        // 결과 Parsing
        if (!TextUtils.isEmpty(mResponseData)) {
            Log.i(
                TAG,
                "getCommunityId Response = $mResponseData"
            )

            try {
                val jsonObject = JSONObject(mResponseData)
                communityId = jsonObject.getString("communityId")
            } catch (e: JSONException) {
                Log.e(TAG, "getCommunityId JsonParse Exception = ", e)
            }
        }
        return communityId
    }

    /**
     * xml 문자열에서 token에 해당하는 value 값을 반환
     */
    private fun getStringValue(text: String?, token: String, pos: IntArray): String {
        pos[0] = text!!.indexOf(token, pos[1]) + token.length
        pos[0] = text.indexOf("\"", pos[0]) + 1
        pos[1] = text.indexOf("\"", pos[0])

        return text.substring(pos[0], pos[1])
    }

    companion object {
        private const val TAG = "SoCodeManager"
    }
}