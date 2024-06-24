package com.sk.csplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.StrictMode
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import com.rayneo.arsdk.android.demo.R
import com.sk.csplayer.util.VcsConnectionManager
import com.sk.vcs.ErrorCode
import com.sk.vcs.VcsDefine
import com.sk.vcs.VcsPlayer
import com.sk.vcs.data.VcsSessionInfo
import com.sk.vcs.view.VcsGlTextureView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AlphaVcsPlayerActivity : Activity() {
    private var mVcsPlayer: VcsPlayer? = null
    private var mVcsSurface: Surface? = null
    private var mVcsGlTextureView: VcsGlTextureView? = null
    private var mVcsRendering = true

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alpha_vcs_player)

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().permitNetwork().build()
        )

        mVcsGlTextureView = findViewById(R.id.vcs_gl_texture_view)
        mVcsGlTextureView?.apply {
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
            setOpaque(false)
            setSurfaceCallback(mOnSurfaceCallback)
        }

        mVcsPlayer = VcsPlayer().apply {
            setOnVcsEventListener(mOnVcsEventListener)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (sendVcsKeyEvent(keyCode, event) == true) true else super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (sendVcsKeyEvent(keyCode, event) == true) true else super.onKeyUp(keyCode, event)
    }

    private fun sendVcsKeyEvent(keyCode: Int, event: KeyEvent): Boolean? {
        val eventAction = event.action

        return when {
            eventAction == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_F12 || keyCode == 285 || keyCode == 385) -> {
                stopVcsPlayer()
                true
            }
            keyCode == KeyEvent.KEYCODE_F11 || keyCode == 293 || keyCode == 393 || keyCode == 90 -> {
                if (eventAction == KeyEvent.ACTION_UP) {
                    toggleVcsRender()
                }
                true
            }
            eventAction == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (mVcsPlayer?.getState() == VcsPlayer.STATE_STOP) {
                    startVcsPlayer()
                    true
                } else {
                    false
                }
            }
            eventAction == KeyEvent.ACTION_UP -> mVcsPlayer?.sendKeyUpEvent(keyCode)
            else -> mVcsPlayer?.sendKeyDownEvent(keyCode)
        }
    }

    private val mOnSurfaceCallback = VcsGlTextureView.SurfaceCallback { surface ->
        Log.e(TAG, "mOnSurfaceCallback IN!!!")
        mVcsSurface = surface
        mVcsEventHandler.sendEmptyMessage(0)
    }

    private fun startVcsPlayer() {
        Log.e(TAG, "Start VCS Player IN!!!")

        Thread {
            val vcsApp = VcsConnectionManager.getInstance()?.getVcsApp()
            val fileName = if (vcsApp?.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_JSON) "StartApp.json" else "StartApp.xml"
            val startAppCommand = VcsConnectionManager.getInstance()?.getStartCommand(this@AlphaVcsPlayerActivity, fileName)

            mVcsPlayer?.apply {
                setSurface(mVcsSurface!!)
                setAlphaView(mVcsGlTextureView!!)
                if (vcsApp != null) {
                    if (startAppCommand != null) {
                        start(vcsApp, startAppCommand)
                    }
                }
            }
            mVcsGlTextureView?.requestRender()
        }.start()
    }

    private fun stopVcsPlayer() {
        Thread { mVcsPlayer?.stop() }.start()
        finish()
    }

    private val mOnVcsEventListener = object : VcsPlayer.OnVcsEventListener {
        override fun onStatusChanged(status: Int) {
            if (status == VcsPlayer.STATE_TIMEOUT) stopVcsPlayer()
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
            when {
                command!!.contains("\"COMMAND\":\"GetProperty\"") && command.contains("\"PROFILE_ID\"") -> {
                    try {
                        val jsonObject = JSONObject(command)
                        val dataObject = jsonObject.getJSONObject("DATA")
                        val hash = dataObject.getString("hash")
                        val res = """
                            {"TYPE":"response","COMMAND":"GetProperty","CONTENTS":"","DATA":{"propertyList":[{"propertyInfo":{"key":"PROFILE_ID","value":"9c58dcc5-58fb-4554-acc7-d52ee874f67c"}},{"propertyInfo":{"key":"START_PROFILE_ID","value":"9c58dcc5-58fb-4554-acc7-d52ee874f67c"}},{"propertyInfo":{"key":"INSPECT_YN","value":""}},{"propertyInfo":{"key":"PROPERTY_MENU_SOUND_EFFECT","value":"1"}}],"hash":"$hash"}}
                        """.trimIndent()
                        mVcsPlayer!!.sendCommand(res)
                    } catch (e: Exception) {
                        Log.e(TAG, "onReceiveCommand :: Exception", e)
                    }
                }
                command.contains("\"COMMAND\":\"controlPlayer\"") -> mVcsCommandHandler.sendEmptyMessageDelayed(1, 100)
                command.contains("\"COMMAND\":\"GetPlayInfo\"") -> mVcsCommandHandler.sendEmptyMessageDelayed(2, 10)
                command.contains("\"COMMAND\":\"Play\"") -> mVcsCommandHandler.sendEmptyMessageDelayed(0, 100)
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

    private val mVcsCommandHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val command = """
                {"TYPE":"response","COMMAND":"controlPlayer","CONTENTS":"","DATA":{"CONTROL_TYPE":"SEND_TO_CLIENT","IS_SEND_TO_CLIENT":"false"}}
            """.trimIndent()
            mVcsPlayer?.sendCommand(command)
        }
    }

    private val mVcsEventHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            startVcsPlayer()
        }
    }

    private fun toggleVcsRender() {
        mVcsRendering = !mVcsRendering
        mVcsGlTextureView?.setRenderMode(
            if (mVcsRendering) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
        )
    }

    @Throws(IOException::class)
    private fun saveScreenBitmap(filePath: String): String? {
        val bitmap = mVcsGlTextureView?.getBitmap() ?: return null
        val file = File(filePath)
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        return file.absolutePath
    }

    companion object {
        private const val TAG = "AlphaVcsPlayerActivity"
    }
}
