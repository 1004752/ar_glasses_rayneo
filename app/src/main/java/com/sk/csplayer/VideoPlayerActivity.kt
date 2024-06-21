package com.sk.csplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.Window
import android.widget.Toast
import android.widget.VideoView
import com.rayneo.arsdk.android.demo.R
import com.sk.vcs.utils.LogUtils

class VideoPlayerActivity : Activity(), OnCompletionListener, MediaPlayer.OnErrorListener {
    private var mVideoView: VideoView? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.activity_video_player)
        mVideoView = findViewById<View>(R.id.video_view) as VideoView
        mVideoView!!.setOnCompletionListener(this)
        mVideoView!!.setOnErrorListener(this)

        startVideoPlay(intent.getStringExtra("filename"))
    }

    private fun startVideoPlay(fileName: String?) {
        try {
            val filePath =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName
            mVideoView!!.setVideoPath(filePath)

            Thread.sleep(500)

            mVideoView!!.start()
        } catch (ex: Exception) {
            LogUtils.debug(TAG, "Video failed: '$ex'")
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        mVideoView!!.stopPlayback()
    }

    override fun onCompletion(player: MediaPlayer) {
        LogUtils.debug(TAG, "onCompletion()")

        finish()
    }

    @SuppressLint("DefaultLocale")
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        val errorMsg = String.format("영상 재생 오류 (%d-%d)", what, extra)
        Toast.makeText(this@VideoPlayerActivity, errorMsg, Toast.LENGTH_SHORT).show()
        finish()
        return true
    }

    companion object {
        private const val TAG = "VideoPlayerActivity"
    }
}