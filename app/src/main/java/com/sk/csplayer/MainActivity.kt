package com.sk.csplayer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.GridView
import android.widget.Toast
import com.rayneo.arsdk.android.demo.R
import com.sk.csplayer.ui.ContentsListAdapter
import com.sk.csplayer.ui.ContentsListAdapter.ContentItem
import com.sk.csplayer.util.Preferences
import com.sk.csplayer.util.Preferences.getResolution
import com.sk.csplayer.util.Preferences.getServerAddress
import com.sk.csplayer.util.Preferences.getServerPort
import com.sk.vcs.VcsDefine
import com.sk.vcs.utils.LogUtils

class MainActivity : Activity() {
    // Main UI Component
    private var mContentsListView: GridView? = null
    private var mContentsListAdapter: ContentsListAdapter? = null

    private var mReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // StrictMode 비활성 처리
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().permitNetwork().build()
        )
        System.setProperty("tcp_low_latency", "1")
        volumeControlStream = AudioManager.STREAM_MUSIC

        // 메뉴 리스트
        mContentsListAdapter = ContentsListAdapter(this)
        mContentsListAdapter!!.setSelectEffect(true)

        // 메뉴 리스트 뷰 생성
        mContentsListView = findViewById<View>(R.id.contents_list) as GridView
        mContentsListView!!.adapter = mContentsListAdapter
        mContentsListView!!.onItemClickListener = mItemClickListener

        registerReceiver()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        LogUtils.error(TAG, "onKeyDown keyCode : $keyCode")

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        LogUtils.error(TAG, "onKeyUp keyCode : $keyCode")

        // 옵션키로 서버 설정 메뉴 실행
        if (keyCode == KeyEvent.KEYCODE_F11 || keyCode == 293 || keyCode == 393 || keyCode == 90) {
            startSettingMenu()
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver()
    }

    // 컨텐츠 그리드 뷰 클릭 리스너
    private val mItemClickListener =
        OnItemClickListener { parent, view, position, id ->
            if (position != AdapterView.INVALID_POSITION) {
                val item = mContentsListAdapter!!.getItem(position) as ContentItem
                startVcsPlayer(item.appId)
            }
        }

    private fun startSettingMenu() {
        val position = mContentsListView!!.selectedItemPosition
        val item = mContentsListAdapter!!.getItem(position) as ContentItem

        val intent = Intent(this@MainActivity, SettingActivity::class.java)
        intent.putExtra("appId", item.appId)
        intent.putExtra("appTitle", item.title)
        startActivity(intent)
    }

    private fun startVcsPlayer(appId: Int) {
        // 자체 영상인지 체크
        if (appId == 600 || appId == 601) {
            val playerIntent = Intent(this@MainActivity, VideoPlayerActivity::class.java)
            playerIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            if (appId == 600) {
                playerIntent.putExtra("filename", "cs_video.mp4")
            } else {
                playerIntent.putExtra("filename", "cs_video_eng.mp4")
            }
            startActivity(playerIntent)
            return
        }

        val preferenceName = Preferences.PREF_NAME + appId

        // 설정한 서버 주소로 접속
        val address = getServerAddress(preferenceName)
        if (TextUtils.isEmpty(address)) {
            Toast.makeText(this@MainActivity, "서버 설정 후 실행해주세요.\n(옵션키 입력)", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val port = getServerPort(preferenceName)
        if (port <= 0) {
            Toast.makeText(this@MainActivity, "서버 설정 후 실행해주세요.\n(옵션키 입력)", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // 해상도 설정 (Default 720)
        val resolution = getResolution(preferenceName)
        if (resolution == 1080) {
            VcsDefine.SCREEN_WIDTH = 1920
            VcsDefine.SCREEN_HEIGHT = 1080
        } else {
            VcsDefine.SCREEN_WIDTH = 1280
            VcsDefine.SCREEN_HEIGHT = 720
        }

        val intent: Intent
        // VCS Player 실행
        if (appId == 200) {
            intent = Intent(this@MainActivity, AlphaVcsPlayerActivity::class.java)
            intent.putExtra("serverIp", address)
            intent.putExtra("serverPort", port)
        } else {
            intent = Intent(this@MainActivity, VcsPlayerActivity::class.java)
            intent.putExtra("serverIp", address)
            intent.putExtra("serverPort", port)
        }

        startActivity(intent)
    }

    private fun registerReceiver() {
        if (mReceiver != null) return

        val theFilter = IntentFilter()
        theFilter.addAction(BROADCAST_MESSAGE)

        this.mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val errorCode = intent.getIntExtra("error_code", 0)
                if (intent.action == BROADCAST_MESSAGE) {
                    val errorMsg = String.format("서비스가 원활하지 않습니다. (%s)", errorCode)
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        this.registerReceiver(this.mReceiver, theFilter)
    }

    private fun unregisterReceiver() {
        if (mReceiver != null) {
            this.unregisterReceiver(mReceiver)
            mReceiver = null
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        const val BROADCAST_MESSAGE: String = "VCS_ERROR_MESSAGE"
    }
}