package com.sk.csplayer

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.rayneo.arsdk.android.demo.R
import com.sk.csplayer.util.VcsConnectionManager
import com.sk.vcs.ErrorCode
import com.sk.vcs.VcsDefine
import com.sk.vcs.VcsPlayer
import com.sk.vcs.VcsPlayer.OnVcsEventListener
import com.sk.vcs.data.VcsSessionInfo
import com.sk.vcs.utils.LogUtils.error
import com.sk.vcs.utils.LogUtils.info

class VcsPlayerActivity : Activity() {
    // VCS Component
    private var mVcsSurface: Surface? = null
    private var mVcsPlayer: VcsPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_vcs_player)

        // StrictMode 비활성 처리
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().permitNetwork().build()
        )
        System.setProperty("tcp_low_latency", "1")
        volumeControlStream = AudioManager.STREAM_MUSIC

        val vcsSurfaceView = findViewById<View>(R.id.vcs_surfaceview) as SurfaceView
        vcsSurfaceView.holder.addCallback(mSurfaceCallback)

        mVcsPlayer = VcsPlayer()
        if (mOnVcsEventListener != null) {
            mVcsPlayer!!.setOnVcsEventListener(mOnVcsEventListener)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        error(TAG, "onKeyDown keyCode : $keyCode")

        if (sendVcsKeyEvent(keyCode, event)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        error(TAG, "onKeyUp keyCode : $keyCode")

        if (sendVcsKeyEvent(keyCode, event)) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    // VCS Surface Callback
    private val mSurfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            error(TAG, "surfaceCreated")

            mVcsSurface = holder.surface
            mVcsPlayer!!.setSurface(mVcsSurface!!)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            error(TAG, "surfaceChanged")

            mVcsSurface = holder.surface

            startVcsPlayer()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            error(TAG, "surfaceDestroyed")
            mVcsSurface = null
        }
    }

    /**
     * KeyEvent 처리 함수
     */
    private fun sendVcsKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        val eventAction = event.action
        info(
            TAG,
            "[CSFragment] sendVcsKeyEvent() EventAction:$eventAction  KeyCode:$keyCode"
        )

        // 나가기 버튼
        if (eventAction == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_F12 || keyCode == 285 || keyCode == 385)) {
            stopVcsPlayer()
            return true
        }

        return if (eventAction == KeyEvent.ACTION_UP) {
            mVcsPlayer!!.sendKeyUpEvent(keyCode)
        } else {
            mVcsPlayer!!.sendKeyDownEvent(keyCode)
        }
    }

    /**
     * VCS Player Event Listener
     */
    private val mOnVcsEventListener: OnVcsEventListener? = object : OnVcsEventListener {
        override fun onStatusChanged(status: Int) {
            if (status == VcsPlayer.STATE_STOP) {
                //finish();
            }
        }

        override fun onError(error: Int, message: String?) {
            if (error != ErrorCode.FIRST_VIDEO_FRAME_RECEIVED && error != ErrorCode.MEDIA_BUFFER_FULL) {
                val intent = Intent(MainActivity.BROADCAST_MESSAGE)
                intent.putExtra("error_code", error)
                sendBroadcast(intent)

                finish()
            }
        }

        override fun onReceiveCommand(command: String?) {
        }

        override fun onSessionInfo(info: VcsSessionInfo?) {
        }

        override fun onLogging(type: Int, data: String?) {
        }
    }

    private fun startVcsPlayer() {
        error(TAG, "Start VCS Player IN!!!")

        val vcsApp = VcsConnectionManager.getInstance()!!.getVcsApp()

        val startAppCommand: String
        val fileName = if (vcsApp.mInterfaceFormat == VcsDefine.INTERFACE_TYPE_JSON) {
            "StartApp.json"
        } else {
            "StartApp.xml"
        }
        startAppCommand = VcsConnectionManager.getInstance()!!
            .getStartCommand(this, fileName)

        mVcsPlayer!!.setSurface(mVcsSurface!!)
        mVcsPlayer!!.start(vcsApp, startAppCommand)
    }

    private fun stopVcsPlayer() {
        mVcsPlayer?.stop()

        finish()
    }

    companion object {
        private const val TAG = "VcsPlayerActivity"
    }
}