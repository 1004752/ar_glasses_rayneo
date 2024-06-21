package com.sk.vcs

import android.graphics.Bitmap
import android.text.TextUtils
import android.view.Surface
import com.sk.vcs.ErrorCode.UI_EMERGENCY_MODE
import com.sk.vcs.ErrorCode.getErrorCodeString
import com.sk.vcs.data.MediaBuffer
import com.sk.vcs.data.MediaNativeBuffer
import com.sk.vcs.data.VcsApp
import com.sk.vcs.data.VcsSessionInfo
import com.sk.vcs.network.ControlClient
import com.sk.vcs.network.ControlClient.OnControlListener
import com.sk.vcs.network.StreamingClient
import com.sk.vcs.network.StreamingClient.OnStreamingListener
import com.sk.vcs.network.VcsRouter
import com.sk.vcs.player.AudioDecoder
import com.sk.vcs.player.VideoDecoder
import com.sk.vcs.player.base.Decoder.DecoderEventListener
import com.sk.vcs.qsm.SkbQsmLogManager
import com.sk.vcs.utils.ByteUtils.byteToLongR
import com.sk.vcs.utils.KeyConverter
import com.sk.vcs.utils.LogUtils
import com.sk.vcs.utils.LogUtils.debug
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info
import com.sk.vcs.utils.LogUtils.verbose
import com.sk.vcs.view.VcsGlTextureView
import org.json.JSONObject

class VcsPlayer {
    private var mControlClient: ControlClient? = null
    private var mAudioClient: StreamingClient? = null
    private var mVideoClient: StreamingClient? = null

    private val mAudioBuffer = MediaBuffer(AUDIO_BUFFER_CAPACITY, AUDIO_BUFFER_SIZE)
    private val mVideoBuffer = MediaBuffer(VIDEO_BUFFER_CAPACITY, VIDEO_BUFFER_SIZE)
    private val mAlphaBuffer = MediaBuffer(ALPHA_BUFFER_CAPACITY, ALPHA_BUFFER_SIZE)
    private val mAlphaByteBuffer = MediaNativeBuffer(
        ALPHA_BYTE_BUFFER_CAPACITY,
        VcsDefine.SCREEN_WIDTH * VcsDefine.SCREEN_HEIGHT
    )

    private var mSurface: Surface? = null
    private var mGlTextureView: VcsGlTextureView? = null
    private var mAudioDecoder: AudioDecoder? = null
    private var mVideoDecoder: VideoDecoder? = null

    private var mPlayerStatus = STATE_STOP
    private var mOnVcsEventListener: OnVcsEventListener? = null

    private var mVcsAppInfo: VcsApp? = null
    private var mServerIp: String? = null
    private var mServerPort = 0

    private var mSessionId = ""
    private var mStartAppCommand: String? = null

    interface OnVcsEventListener {
        fun onStatusChanged(status: Int)

        fun onError(error: Int, message: String?)

        fun onReceiveCommand(command: String?)

        fun onSessionInfo(info: VcsSessionInfo?)

        fun onLogging(type: Int, data: String?)
    }

    fun setOnVcsEventListener(listener: OnVcsEventListener) {
        mOnVcsEventListener = listener
    }

    fun setSurface(surface: Surface) {
        mSurface = surface
    }

    fun setAlphaView(view: VcsGlTextureView) {
        mGlTextureView = view
        mGlTextureView!!.setAlphaNativeBuffer(mAlphaByteBuffer)
    }

    fun setFrameRendering(render: Boolean) {
        if (mVideoDecoder != null) {
            mVideoDecoder!!.setFrameRendering(render)
        }
    }

    fun getBitmap(): Bitmap? {
        if (mGlTextureView == null) {
            error(TAG, "getBitmap Error :: mGlTextureView is NULL!!!")
            return null
        }

        if (!mGlTextureView!!.isAvailable) {
            error(TAG, "getBitmap Error :: mGlTextureView not Available!!!")
            return null
        }

        return mGlTextureView!!.bitmap
    }

    fun getState(): Int {
        return mPlayerStatus
    }

    @Synchronized
    fun start(vcsAppInfo: VcsApp, startCommand: String) {
        verbose(TAG, "start called :: tid=" + Thread.currentThread().id)

        if (mPlayerStatus != STATE_STOP) {
            info(TAG, "Client already started")
            return
        }
        notifyStatus(STATE_LOADING)

        SkbQsmLogManager.getInstance()!!.intVcsInfo()
        SkbQsmLogManager.getInstance()!!.intVcsStartTime()

        // App 정보 저장
        mStartAppCommand = startCommand
        mVcsAppInfo = vcsAppInfo
        mVcsAppInfo!!.printVcsAppInfo()
        LogUtils.LOG_INFO_ENABLED = mVcsAppInfo!!.mEnableInfoLog
        SkbQsmLogManager.getInstance()!!.setVcsAppId(mVcsAppInfo!!.mAppId)

        // VCS 서버 정보 설정(CSR 연동)
        if (getVcsServerInfo()) {
            verbose(
                TAG,
                "VCS-INFO = " + mServerIp + ":" + mServerPort + "  (mSoCode = " + mVcsAppInfo!!.mSoCode + ", mPublicIp = " + mVcsAppInfo!!.mPublicIp + ", mSessionId = " + mSessionId + ")"
            )
            SkbQsmLogManager.getInstance()!!.setVcsServerIp(mServerIp)

            // Init Player
            if (initPlayer()) {
                // 세션 연결 요청
                connectCommandClient(getVcsStartAppCommand())
            } else {
                notifyError(ErrorCode.DECODER_CREATE_ERROR, "")
            }
        }
    }

    @Synchronized
    fun pause() {
        verbose(TAG, "pause called :: tid=" + Thread.currentThread().id)

        if (mPlayerStatus == STATE_STOP) {
            info(TAG, "Client already stopped")
            return
        }
        notifyStatus(STATE_PAUSE)

        stopClient()

        pausePlayer()
    }

    @Synchronized
    fun resume(vcsAppInfo: VcsApp, startCommand: String) {
        verbose(TAG, "resume called :: tid=" + Thread.currentThread().id)

        if (mPlayerStatus != STATE_PAUSE) {
            info(TAG, "VCS Player Not Pause!!!")
            return
        }
        notifyStatus(STATE_LOADING)

        SkbQsmLogManager.getInstance()!!.intVcsInfo()
        SkbQsmLogManager.getInstance()!!.intVcsStartTime()

        // App 정보 저장
        mStartAppCommand = startCommand
        mVcsAppInfo = vcsAppInfo
        mVcsAppInfo!!.printVcsAppInfo()
        LogUtils.LOG_INFO_ENABLED = mVcsAppInfo!!.mEnableInfoLog
        SkbQsmLogManager.getInstance()!!.setVcsAppId(mVcsAppInfo!!.mAppId)

        // VCS 서버 설정
        if (getVcsServerInfo()) {
            verbose(
                TAG,
                "VCS-INFO = " + mServerIp + ":" + mServerPort + "  (mSoCode = " + mVcsAppInfo!!.mSoCode + ", mPublicIp = " + mVcsAppInfo!!.mPublicIp + ", mSessionId = " + mSessionId + ")"
            )
            SkbQsmLogManager.getInstance()!!.setVcsServerIp(mServerIp)

            // 세션 연결 요청
            connectCommandClient(getVcsStartAppCommand())
        }
    }

    @Synchronized
    fun stop() {
        verbose(TAG, "stop called :: tid=" + Thread.currentThread().id)

        if (mPlayerStatus == STATE_STOP) {
            info(TAG, "Player Already Stopped!!!")
            return
        }
        notifyStatus(STATE_STOP)

        stopClient()
        stopPlayer()

        verbose(TAG, "stopped!!!")
    }

    fun sendKeyDownEvent(keyCode: Int): Boolean {
        if (mPlayerStatus != STATE_PLAY) {
            error(TAG, "sendKeyDownEvent Error : Check VCS Status!!!")
            return false
        }

        return mControlClient!!.requestKeyDown(keyCode)
    }

    fun sendKeyUpEvent(keyCode: Int): Boolean {
        if (mPlayerStatus != STATE_PLAY) {
            error(TAG, "sendKeyUpEvent Error : Check VCS Status!!!")
            return false
        }

        return mControlClient!!.requestKeyUp(keyCode)
    }

    fun sendKeepAliveEvent() {
        if (mPlayerStatus != STATE_PLAY) {
            error(TAG, "sendKeepAliveEvent Error : Check VCS Status!!!")
            return
        }

        mControlClient!!.requestKeepAliveEvent(KeyConverter.KEEP_ALIVE_KEY_CODE)
    }

    fun sendCommand(command: String?) {
        if (mPlayerStatus != STATE_PLAY) {
            error(TAG, "sendCommand Error : Check VCS Status!!!")
            return
        }

        if (mControlClient != null) {
            if (mVcsAppInfo!!.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_JSON) {
                mControlClient!!.sendJSONCommand(command!!)
            } else {
                mControlClient!!.sendXMLCommand(command!!)
            }
        }
    }

    @Synchronized
    private fun connectCommandClient(startCommand: String?) {
        verbose(TAG, "connectCommandClient!!!")

        // XML 연동 포맷일 경우 SessionID 추가
        val connectXml = if (mVcsAppInfo!!.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_XML) {
            startCommand!!.substring(
                0,
                startCommand.indexOf("</INTERFACE>")
            ) + "<sessionId>" + mSessionId + "</sessionId></INTERFACE>"
        } else {
            startCommand
        }

        // Control 소켓 연결
        if (mControlClient == null) {
            mControlClient = ControlClient(mVcsAppInfo!!.mMacAddress)
            mControlClient!!.setOnControlListener(mCommandListener)
        }

        if (mControlClient!!.startClient(mServerIp, mServerPort, VcsDefine.CONNECT_TIMEOUT)) {
            mControlClient!!.sendStartApp(connectXml!!)
        } else {
            error(TAG, "CommandClient Connect Error!!!")
            notifyError(ErrorCode.VCS_CONNECTION_ERROR, "")
        }
    }

    private val mCommandListener: OnControlListener = object : OnControlListener {
        override fun onSessionConnected() {
            verbose(
                TAG,
                "ControlClient onSessionConnected :: Current-Status = " + stateToString(
                    mPlayerStatus
                )
            )

            // 현재 로딩중 일 경우에만 처리
            if (mPlayerStatus == STATE_LOADING) {
                // Streaming 소켓 연결
                connectStreamingClient()

                // JSON 연동 포맷일 경우 StartApp 데이터 전송
                if (mVcsAppInfo!!.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_JSON) {
                    // 세션 ID 추가
                    addCsrSessionId()

                    verbose(TAG, "StartAppCommand : $mStartAppCommand")

                    if (mPlayerStatus != STATE_LOADING) {
                        verbose(TAG, "ControlClient onSessionConnected : STATE Not LOADING!!!")
                        return
                    }

                    // StartApp Json 데이터 전송
                    mControlClient!!.sendJSONCommand(mStartAppCommand!!)
                }

                // 서버 연결 정보 전송
                sendSessionInfo()

                // 상태 변경
                notifyStatus(STATE_PLAY)
            }
        }

        override fun onError(error: Int, message: String?) {
            error(TAG, "ControlClient onError : error = $error, message = $message")
            if (message != null) {
                notifyError(error, message)
            }
        }

        override fun onReceiveXml(command: String?) {
            // 타임아웃 체크
            if (checkTimeoutCommand(command)) {
                // 타임아웃 QSM 로그 전송
                sendQsmLog(SkbQsmLogManager.getInstance()!!.getSessionTimeoutLog()!!)

                // 타임아웃 상태 변경
                notifyStatus(STATE_TIMEOUT)
                return
            }

            if (mOnVcsEventListener != null) {
                mOnVcsEventListener!!.onReceiveCommand(command)
            }
        }

        override fun onReceiveJson(command: String?) {
            if (mOnVcsEventListener != null) {
                mOnVcsEventListener!!.onReceiveCommand(command)
            }
        }
    }

    private fun connectStreamingClient() {
        var connect = false

        if (connectVideoClient()) {
            connect = connectAudioClient()
        }

        if (!connect) {
            error(TAG, "StreamingClient Connect Error!!!")
            notifyError(ErrorCode.VCS_CONNECTION_ERROR, "")
        }
    }

    @Synchronized
    private fun connectVideoClient(): Boolean {
        info(TAG, "connectVideoClient!!!")

        mVideoClient = StreamingClient(
            mVcsAppInfo!!.mMacAddress,
            mVideoBuffer,
            mAlphaBuffer,
            mVideoStreamingListener,
            true
        )
        val isConnect =
            mVideoClient!!.startClient(mServerIp, mServerPort, VcsDefine.CONNECT_TIMEOUT)
        if (isConnect) {
            mVideoClient!!.requestStreamInfo()
        } else {
            error(TAG, "Video Streaming Client Connect Error")
        }

        return isConnect
    }

    @Synchronized
    private fun connectAudioClient(): Boolean {
        info(TAG, "connectAudioClient!!!")

        mAudioClient = StreamingClient(
            mVcsAppInfo!!.mMacAddress,
            mAudioBuffer,
            null,
            mAudioStreamingListener,
            false
        )
        val isConnect =
            mAudioClient!!.startClient(mServerIp, mServerPort, VcsDefine.CONNECT_TIMEOUT)
        if (isConnect) {
            mAudioClient!!.requestStreamInfo()
        } else {
            error(TAG, "Audio Streaming Client Connect Error")
        }

        return isConnect
    }

    private val mAudioStreamingListener: OnStreamingListener = object : OnStreamingListener {
        override fun onError(error: Int, message: String?) {
            if (error == ErrorCode.MEDIA_BUFFER_FULL) {
                mAudioBuffer.reset()
                mVideoBuffer.reset()
                return
            }

            error(
                TAG,
                "mAudioStreamingListener onError : error = $error, message = $message"
            )
            if (message != null) {
                notifyError(error, message)
            }
        }
    }

    private val mVideoStreamingListener: OnStreamingListener = object : OnStreamingListener {
        override fun onError(error: Int, message: String?) {
            if (error == ErrorCode.MEDIA_BUFFER_FULL) {
                if (mVideoBuffer.isFull && mGlTextureView != null) {
                    val buffer = mVideoBuffer.lastBuffer
                    if (buffer != null) {
                        val timestamp = byteToLongR(buffer, 10)
                        mGlTextureView!!.setAlphaFrameTimestamp(timestamp)
                    }
                }

                mAudioBuffer.reset()
                mVideoBuffer.reset()
                return
            }

            debug(
                TAG,
                "mVideoStreamingListener onError : error = $error, message = $message"
            )

            if (message != null) {
                notifyError(error, message)
            }
        }
    }

    /**
     * 서버 연결 종료
     */
    @Synchronized
    private fun stopClient() {
        verbose(TAG, "stopClient")

        if (mControlClient != null) {
            mControlClient!!.stopClient()
            mControlClient = null
        }

        if (mAudioClient != null) {
            mAudioClient!!.stopClient()
            mAudioClient = null
        }

        if (mVideoClient != null) {
            mVideoClient!!.stopClient()
            mVideoClient = null
        }
    }

    private val mVideoDecoderListener: DecoderEventListener = object : DecoderEventListener {
        override fun onFirstFrameRendered() {
            notifyError(ErrorCode.FIRST_VIDEO_FRAME_RECEIVED, "")

            // 첫 프레임 렌더링 시 연동 유형이 있을 경우 접속 시간 전송
            if (!TextUtils.isEmpty(mVcsAppInfo!!.mInitiateType)) {
                sendQsmLog(
                    SkbQsmLogManager.getInstance()!!
                        .getInitiateTimeLog(mVcsAppInfo!!.mInitiateType)!!
                )
                mVcsAppInfo!!.mInitiateType = null
            }
        }

        override fun onMediaQualityErrorAlert(count: Int) {
            // 비디오 에러 카운트 로그 전송
            sendQsmLog(SkbQsmLogManager.getInstance()!!.getQualityLog(count, 0)!!)
        }

        override fun onError(error: Int, message: String?) {
            notifyError(error, "")
        }
    }

    private val mAudioDecoderListener: DecoderEventListener = object : DecoderEventListener {
        override fun onFirstFrameRendered() {
        }

        override fun onMediaQualityErrorAlert(count: Int) {
            // 오디오 에러 카운트 로그 전송
            sendQsmLog(SkbQsmLogManager.getInstance()!!.getQualityLog(0, count)!!)
        }

        override fun onError(error: Int, message: String?) {
            notifyError(error, "")
        }
    }

    /**
     * Player 초기화
     */
    private fun initPlayer(): Boolean {
        verbose(TAG, "initPlayer")

        // Audio, Video Decoder가 null일 경우에만 생성
        if (mVideoDecoder == null && mAudioDecoder == null) {
            mVideoBuffer.reset()
            mAudioBuffer.reset()

            // Video Decoder 생성
            mVideoDecoder = VideoDecoder(
                mSurface!!,
                mVideoBuffer,
                mGlTextureView,
                mAlphaBuffer,
                mAlphaByteBuffer
            )
            mVideoDecoder!!.setVideoInfo(mVcsAppInfo!!.mVideoCodec, mVcsAppInfo!!.mVideoFps)
            mVideoDecoder!!.setDecoderEventListener(mVideoDecoderListener)
            mVideoDecoder!!.start()

            // Audio Decoder 생성
            mAudioDecoder = AudioDecoder(mAudioBuffer)
            mAudioDecoder!!.setDecoderEventListener(mAudioDecoderListener)
            mAudioDecoder!!.start()
        }

        return true
    }

    /**
     * Player 일시 정지
     */
    private fun pausePlayer() {
        verbose(TAG, "pausePlayer")
        if (mAudioDecoder != null) {
            mAudioDecoder!!.pause()
        }

        if (mVideoDecoder != null) {
            mVideoDecoder!!.pause()
        }
    }

    /**
     * Player 종료
     */
    private fun stopPlayer() {
        verbose(TAG, "stopPlayer")
        if (mAudioDecoder != null) {
            mAudioDecoder!!.stop()
            mAudioDecoder = null
        }

        if (mVideoDecoder != null) {
            mVideoDecoder!!.stop()
            mVideoDecoder = null
        }
    }

    private fun stateToString(state: Int): String {
        when (state) {
            STATE_STOP -> return "STATE_STOP"
            STATE_LOADING -> return "STATE_LOADING"
            STATE_PLAY -> return "STATE_PLAY"
            STATE_PAUSE -> return "STATE_PAUSE"
            STATE_TIMEOUT -> return "STATE_TIMEOUT"
        }
        return "STATE_NONE"
    }

    private fun getVcsServerInfo(): Boolean {
        if (mVcsAppInfo!!.mUseCsr) {
            return requestVcsRoute()
        } else {
            mServerIp = mVcsAppInfo!!.mVcsIp
            mServerPort = mVcsAppInfo!!.mVcsPort
        }
        return true
    }

    private fun notifyError(error: Int, message: String) {
        if (mPlayerStatus == STATE_STOP) {
            info(TAG, "notifyError : Current Status STOP!!!")
            return
        }

        // 에러 로그 출력(첫 프레임 수신은 제외)
        if (error == ErrorCode.FIRST_VIDEO_FRAME_RECEIVED) {
            verbose(TAG, "First VideoFrame is Rendered!!!")
        } else {
            error(TAG, "notifyError : error = $error, message = $message")
        }

        // QSM 로그 전송
        if (error != ErrorCode.FIRST_VIDEO_FRAME_RECEIVED && error != ErrorCode.UI_EMERGENCY_MODE) {
            // 에러 코드 생성
            val errorCode = if (TextUtils.isEmpty(message)) {
                error.toString() + ""
            } else {
                "$error:$message"
            }

            // QSM 로그 생성 후 전송
            sendQsmLog(
                SkbQsmLogManager.getInstance()!!.getErrorLog(errorCode, getErrorCodeString(error))!!
            )
        }

        if (mOnVcsEventListener != null) {
            mOnVcsEventListener!!.onError(error, message)
        }

        // Video First Frame 수신 이벤트가 아니면 종료 처리
        if (error != ErrorCode.FIRST_VIDEO_FRAME_RECEIVED) {
            stop()
        }
    }

    private fun notifyStatus(status: Int) {
        // 타임아웃 요청은 상태를 저장하지 않음
        if (status != STATE_TIMEOUT) {
            mPlayerStatus = status
        }

        verbose(TAG, "notifyStatus : " + stateToString(status))

        if (mOnVcsEventListener != null) {
            mOnVcsEventListener!!.onStatusChanged(status)
        }
    }

    // VCS 라우팅 요청
    private fun requestVcsRoute(): Boolean {
        val router = VcsRouter()

        // Public IP 획득
        if (TextUtils.isEmpty(mVcsAppInfo!!.mPublicIp)) {
            mVcsAppInfo!!.mPublicIp = router.requestPublicIp(mVcsAppInfo!!)
        }

        // Referrer 서버 연동하여 SoCode 획득이 필요할 경우
        if (mVcsAppInfo!!.mUseReferrer && TextUtils.isEmpty(mVcsAppInfo!!.mSoCode)) {
            val referrerUrl = mVcsAppInfo!!.mReferrerIp + ":" + mVcsAppInfo!!.mReferrerPort
            mVcsAppInfo!!.mSoCode = router.requestCommunityId(
                referrerUrl,
                mVcsAppInfo!!.mPublicIp!!, "17"
            )
        } else {
            mVcsAppInfo!!.mSoCode = ""
        }

        // 라우팅 요청 (실패시 1회 더)
        var result = router.requestRoute(mVcsAppInfo!!)
        if (result!!.errorCode == ErrorCode.VCS_CONNECTION_ERROR) {
            result = router.requestRoute(mVcsAppInfo!!)
        }

        // 결과 처리
        if (result!!.errorCode == 0) {
            mServerIp = result!!.cssIp
            mServerPort = result!!.cssPort
            mSessionId = result!!.cssSessionId!!
            return true
        }

        // 에러 코드 생성
        var errorCode = result!!.errorCode
        if (errorCode != UI_EMERGENCY_MODE) {
            if (errorCode < ErrorCode.CSR_ERROR_START) {
                errorCode = ErrorCode.CSR_ERROR_START + errorCode
            }
        }

        // CSR 관련 에러 처리
        notifyError(errorCode, "")

        return false
    }

    // VCS 연결 서버 정보 전송
    private fun sendSessionInfo() {
        val info = VcsSessionInfo()
        info.mServerIp = mServerIp
        info.mServerPort = mServerPort
        info.mSoCode = mVcsAppInfo!!.mSoCode
        info.mPublicIp = mVcsAppInfo!!.mPublicIp
        info.mVcsSessionId = mSessionId

        if (mOnVcsEventListener != null) {
            mOnVcsEventListener!!.onSessionInfo(info)
        }
    }

    // QSM 로그 전송 요청
    private fun sendQsmLog(log: String) {
        if (mOnVcsEventListener != null) {
            verbose(TAG, "sendQsmLog : QSM-LOG = $log")
            mOnVcsEventListener!!.onLogging(VcsDefine.LOG_QSM, log)
        }
    }

    private fun getVcsStartAppCommand(): String {
        var startAppCommand = mStartAppCommand!!

        // Json 연동 유형일 경우 별도의 StartApp을 생성하여 연결
        if (mVcsAppInfo!!.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_JSON) {
            startAppCommand = """<?xml version="1.0" encoding="utf-8"?>
<INTERFACE version="3">
    <TYPE>request</TYPE>
    <COMMAND>StartApp</COMMAND>
    <CONTENTS></CONTENTS>
    <DATA>
        <cloudVersion>${VcsDefine.VCS_VERSION}</cloudVersion>
        <stbId>${mVcsAppInfo!!.mSvcId}</stbId>
        <stbModel>${mVcsAppInfo!!.mStbModel}</stbModel>
        <mac>${mVcsAppInfo!!.mMacAddress}</mac>
        <webAppVersion>${mVcsAppInfo!!.mWebAppVersion}</webAppVersion>
        <starturl>
            <![CDATA[${mVcsAppInfo!!.mWebAppUrl}]]>
        </starturl>
        <sessionId>$mSessionId</sessionId>
        <videoInfo>
            <codec>${mVcsAppInfo!!.mVideoCodec}</codec>
            <fps>${mVcsAppInfo!!.mVideoFps}</fps>
            <bitrateKb>${mVcsAppInfo!!.mVideoBitrate}</bitrateKb>
            <resolution>${VcsDefine.SCREEN_WIDTH}x${VcsDefine.SCREEN_HEIGHT}</resolution>
        </videoInfo>
        <alphaInfo>
            <compressionType>${mVcsAppInfo!!.mCompressionType}</compressionType>
            <compressionLevel>1</compressionLevel>
        </alphaInfo>
"""

            if (mVcsAppInfo!!.mDebugSessionIndex != -1) {
                startAppCommand += """        <connectInfo>
            <sessionDbgIdx>${mVcsAppInfo!!.mDebugSessionIndex}</sessionDbgIdx>
        </connectInfo>
"""
            }
            startAppCommand +=
                """    </DATA>
</INTERFACE>"""
        }

        return startAppCommand
    }

    private fun checkTimeoutCommand(command: String?): Boolean {
        return command!!.contains("<COMMAND>ConnectionInfo</COMMAND>") && command.contains("<returnApp>-3</returnApp>")
    }

    private fun addCsrSessionId() {
        try {
            val startAppJson = JSONObject(mStartAppCommand)
            val dataJson = startAppJson.getJSONObject("DATA")
            dataJson.put("sessionId", mSessionId)

            mStartAppCommand = startAppJson.toString()
        } catch (ignored: Exception) {
        }
    }

    companion object {
        private const val TAG = "VcsPlayer"

        /**
         * Player 중지 상태
         */
        const val STATE_STOP: Int = 0

        /**
         * Player 실행 중 상태
         */
        const val STATE_LOADING: Int = 1

        /**
         * Player 실행 상태
         */
        const val STATE_PLAY: Int = 2

        /**
         * Player 일시 정지 상태
         */
        const val STATE_PAUSE: Int = 3

        /**
         * Player 서버 타임아웃 요청 상태
         */
        const val STATE_TIMEOUT: Int = 4

        /**
         * Media Buffer
         */
        const val AUDIO_BUFFER_CAPACITY: Int = 10
        const val VIDEO_BUFFER_CAPACITY: Int = 10
        const val ALPHA_BUFFER_CAPACITY: Int = 10
        const val AUDIO_BUFFER_SIZE: Int = 8 * 1024
        const val VIDEO_BUFFER_SIZE: Int = 700 * 1024
        const val ALPHA_BUFFER_SIZE: Int = 200 * 1024
        const val ALPHA_BYTE_BUFFER_CAPACITY: Int = 15

        private var mPlayerStatus = STATE_STOP
    }
}