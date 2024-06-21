package com.sk.csplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import com.rayneo.arsdk.android.demo.R
import com.sk.csplayer.util.VcsConnectionManager
import com.sk.vcs.ErrorCode
import com.sk.vcs.VcsDefine
import com.sk.vcs.VcsPlayer
import com.sk.vcs.VcsPlayer.OnVcsEventListener
import com.sk.vcs.data.VcsApp
import com.sk.vcs.data.VcsSessionInfo
import com.sk.vcs.view.VcsGlTextureView
import com.sk.vcs.view.VcsGlTextureView.SurfaceCallback
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class AlphaVcsPlayerActivity : Activity() {
    // VCS Component
    private var mVcsPlayer: VcsPlayer? = null

    private var mVcsSurface: Surface? = null
    private var mVcsGlTextureView: VcsGlTextureView? = null

    private var mVcsRendering = true

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_alpha_vcs_player)

        // StrictMode 비활성 처리
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().permitNetwork().build()
        )

        mVcsGlTextureView = findViewById<View>(R.id.vcs_gl_texture_view) as VcsGlTextureView
        mVcsGlTextureView!!.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
        mVcsGlTextureView!!.setOpaque(false)
        mVcsGlTextureView!!.setSurfaceCallback(mOnSurfaceCallback)

        mVcsPlayer = VcsPlayer()
        if (mOnVcsEventListener != null) {
            mVcsPlayer!!.setOnVcsEventListener(mOnVcsEventListener)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        //Log.e(TAG, "onKeyDown keyCode : " + keyCode);
        if (sendVcsKeyEvent(keyCode, event) == true) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        //Log.e(TAG, "onKeyUp keyCode : " + keyCode);

        if (sendVcsKeyEvent(keyCode, event) == true) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    /**
     * KeyEvent 처리 함수
     */
    private fun sendVcsKeyEvent(keyCode: Int, event: KeyEvent): Boolean? {
        val eventAction = event.action

        // 나가기 버튼
        if (eventAction == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_F12 || keyCode == 285 || keyCode == 385)) {
            stopVcsPlayer()
            return true
        }

        // 테스트 옵션 키
        if (keyCode == KeyEvent.KEYCODE_F11 || keyCode == 293 || keyCode == 393 || keyCode == 90) {
            if (eventAction == KeyEvent.ACTION_UP) {
                toggleVcsRender()
            }
            return true
        }

        if (eventAction == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (mVcsPlayer!!.getState() == VcsPlayer.STATE_STOP) {
                startVcsPlayer()
                return true
            }
        }

        //Log.i(TAG, "[CSFragment] sendVcsKeyEvent() EventAction:" + eventAction + "  KeyCode:" + keyCode);
        return if (eventAction == KeyEvent.ACTION_UP) {
            mVcsPlayer?.sendKeyUpEvent(keyCode)
        } else {
            mVcsPlayer?.sendKeyDownEvent(keyCode)
        }
    }

    private val mOnSurfaceCallback =
        SurfaceCallback { surface ->
            Log.e(TAG, "mOnSurfaceCallback IN!!!")
            // Surface 저장
            mVcsSurface = surface

            // VCS 실행 요청
            mVcsEventHandler.sendEmptyMessage(0)
        }

    private fun startVcsPlayer() {
        Log.e(TAG, "Start VCS Player IN!!!")

        val thread = Thread {
            val vcsApp: VcsApp = VcsConnectionManager.getInstance()!!.getVcsApp()
            val startAppCommand: String
            val fileName = if (vcsApp.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_JSON) {
                "StartApp.json"
            } else {
                "StartApp.xml"
            }
            startAppCommand = VcsConnectionManager.getInstance()!!
                .getStartCommand(this@AlphaVcsPlayerActivity, fileName)

            mVcsPlayer!!.setSurface(mVcsSurface!!)
            mVcsPlayer!!.setAlphaView(mVcsGlTextureView!!)
            mVcsPlayer!!.start(vcsApp, startAppCommand)
            mVcsGlTextureView!!.requestRender()
        }
        thread.start()
    }

    private fun stopVcsPlayer() {
        val thread = Thread { mVcsPlayer?.stop() }
        thread.start()

        finish()
    }

    /**
     * VCS Player Event Listener
     */
    private val mOnVcsEventListener: OnVcsEventListener? = object : OnVcsEventListener {
        override fun onStatusChanged(status: Int) {
            if (status == VcsPlayer.STATE_TIMEOUT) {
                stopVcsPlayer()
            }
        }

        override fun onError(error: Int, message: String?) {
            if (error != ErrorCode.FIRST_VIDEO_FRAME_RECEIVED && error != ErrorCode.MEDIA_BUFFER_FULL) {
                val intent = Intent(MainActivity.BROADCAST_MESSAGE)
                intent.putExtra("error_code", error)
                sendBroadcast(intent)

                stopVcsPlayer()
            }
        }

        override fun onReceiveCommand(command: String?) {
            Log.e(TAG, "onReceiveCommand(ORG) = $command")
            //{"TYPE":"request","COMMAND":"GetProperty","CONTENTS":"","DATA":{"propertyList":[{"propertyInfo":{"key":"PROFILE_ID"}},{"propertyInfo":{"key":"START_PROFILE_ID"}},{"propertyInfo":{"key":"INSPECT_YN"}}],"hash":"153356059"}}
            if (command!!.contains("\"COMMAND\":\"GetProperty\"") && command.contains("\"PROFILE_ID\"")) {
                try {
                    val jsonObject = JSONObject(command)
                    val dataObject = jsonObject.getJSONObject("DATA")
                    val hash = dataObject.getString("hash")
                    val res =
                        "{\"TYPE\":\"response\",\"COMMAND\":\"GetProperty\",\"CONTENTS\":\"\",\"DATA\":{\"propertyList\":[{\"propertyInfo\":{\"key\":\"PROFILE_ID\",\"value\":\"9c58dcc5-58fb-4554-acc7-d52ee874f67c\"}},{\"propertyInfo\":{\"key\":\"START_PROFILE_ID\",\"value\":\"9c58dcc5-58fb-4554-acc7-d52ee874f67c\"}},{\"propertyInfo\":{\"key\":\"INSPECT_YN\",\"value\":\"\"}},{\"propertyInfo\":{\"key\":\"PROPERTY_MENU_SOUND_EFFECT\",\"value\":\"1\"}}],\"hash\":\"$hash\"}}"
                    mVcsPlayer!!.sendCommand(res)
                } catch (e: Exception) {
                    Log.e(TAG, "onReceiveCommand :: Exception", e)
                }
            } else if (command.contains("\"COMMAND\":\"controlPlayer\"")) {
                mVcsCommandHandler.sendEmptyMessageDelayed(1, 100)
            } else if (command.contains("\"COMMAND\":\"GetPlayInfo\"")) {
                mVcsCommandHandler.sendEmptyMessageDelayed(2, 10)
            } else if (command.contains("\"COMMAND\":\"Play\"")) {
                mVcsCommandHandler.sendEmptyMessageDelayed(0, 100)
            }
        }

        override fun onSessionInfo(info: VcsSessionInfo?) {
            Log.e(TAG, "onSessionInfo Receive!!!")
        }

        override fun onLogging(type: Int, data: String?) {
            if (type == VcsDefine.LOG_QSM) {
                Log.e(TAG, "onLogging QSM = $data")
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private val mVcsCommandHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == 0) {
                var json =
                    "{\"TYPE\":\"response\",\"COMMAND\":\"GetPlayInfo\",\"CONTENTS\":\"\",\"DATA\":{\"playType\":\"VOD\",\"contentId\":\"\",\"vodPlayType\":\"default\",\"isKidsContents\":\"N\",\"currentPlayTime\":0,\"totalPlayTime\":0}}"
                mVcsPlayer!!.sendCommand(json)

                json =
                    "{\"TYPE\":\"response\",\"COMMAND\":\"Play\",\"CONTENTS\":\"\",\"DATA\":{\"state\":\"start\",\"playType\":\"promotion\"}}"
                mVcsPlayer!!.sendCommand(json)
            } else if (msg.what == 1) {
                val json =
                    "{\"TYPE\":\"response\",\"COMMAND\":\"Play\",\"CONTENTS\":\"\",\"DATA\":{\"state\":\"stop\",\"playType\":\"promotion\"}}"
                mVcsPlayer!!.sendCommand(json)
            } else if (msg.what == 2) {
                val json =
                    "{\"TYPE\":\"response\",\"COMMAND\":\"GetPlayInfo\",\"CONTENTS\":\"\",\"DATA\":{\"playType\":\"VOD\",\"contentId\":\"\",\"vodPlayType\":\"promotion\",\"isKidsContents\":\"N\",\"currentPlayTime\":7,\"totalPlayTime\":123}}"
                mVcsPlayer!!.sendCommand(json)
            }

            super.handleMessage(msg)
        }
    }

    @SuppressLint("HandlerLeak")
    private val mVcsEventHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == 0) {
                startVcsPlayer()
            }

            super.handleMessage(msg)
        }
    }

    private fun toggleVcsRender() {
        mVcsRendering = !mVcsRendering

        mVcsPlayer?.setFrameRendering(mVcsRendering)
    }

    private fun saveScreenBitmap() {
        Log.e(TAG, "saveScreenBitmap Start!!!")
        if (mVcsPlayer != null) {
            val image = mVcsPlayer!!.getBitmap()
            if (image != null) {
                //내부저장소 캐시 경로를 받아옵니다.
                val storage = cacheDir

                //저장할 파일 이름
                val fileName = "vcs_home_cache.jpg"

                //storage 에 파일 인스턴스를 생성합니다.
                val tempFile = File(storage, fileName)

                try {
                    // 파일 생성
                    tempFile.createNewFile()

                    Log.e(TAG, "Bitmap File Path = " + tempFile.absolutePath)

                    // 파일을 쓸 수 있는 스트림을 준비합니다.
                    val out = FileOutputStream(tempFile)

                    // compress 함수를 사용해 스트림에 비트맵을 저장합니다.
                    image.compress(Bitmap.CompressFormat.JPEG, 100, out)

                    // 스트림 사용후 닫아줍니다.
                    out.close()
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "FileNotFoundException : " + e.message)
                } catch (e: IOException) {
                    Log.e(TAG, "IOException : " + e.message)
                }
            } else {
                Log.e(TAG, "Bitmap is NULL!!!")
            }
        }
        Log.e(TAG, "saveScreenBitmap Finish!!!")
    }

    companion object {
        private const val TAG = "AlphaVcsPlayerActivity"
    }
}