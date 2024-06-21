package com.sk.csplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.rayneo.arsdk.android.demo.R
import com.sk.csplayer.util.Preferences
import com.sk.csplayer.util.Preferences.getResolution
import com.sk.csplayer.util.Preferences.getServerAddress
import com.sk.csplayer.util.Preferences.getServerPort
import com.sk.csplayer.util.Preferences.setResolution
import com.sk.csplayer.util.Preferences.setServerAddress
import com.sk.csplayer.util.Preferences.setServerPort

class SettingActivity : Activity() {
    private val mAddress = arrayOfNulls<EditText>(4)
    private var mPort: EditText? = null

    private var mRes720: RadioButton? = null
    private var mRes1080: RadioButton? = null
    private var mRadioGroup: RadioGroup? = null

    private var mPreferenceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        val appId = intent.getIntExtra("appId", 0)
        mPreferenceName = Preferences.PREF_NAME + appId

        val appTitle = intent.getStringExtra("appTitle")
        (findViewById<View>(R.id.setting_title) as TextView).text = "VCS 서버 설정 ($appTitle)"

        mAddress[0] = findViewById<View>(R.id.ip1) as EditText
        mAddress[1] = findViewById<View>(R.id.ip2) as EditText
        mAddress[2] = findViewById<View>(R.id.ip3) as EditText
        mAddress[3] = findViewById<View>(R.id.ip4) as EditText

        mPort = findViewById<View>(R.id.port) as EditText

        mRes720 = findViewById<View>(R.id.resolution_720) as RadioButton
        mRes1080 = findViewById<View>(R.id.resolution_1080) as RadioButton
        mRadioGroup = findViewById<View>(R.id.radGroup) as RadioGroup

        findViewById<View>(R.id.remove).setOnClickListener(mOnClickListener)
        findViewById<View>(R.id.confirm).setOnClickListener(mOnClickListener)
        findViewById<View>(R.id.cancel).setOnClickListener(mOnClickListener)

        initConfigInfo()
    }

    @SuppressLint("SetTextI18n")
    private fun initConfigInfo() {
        val address = getServerAddress(mPreferenceName!!)
        if (!TextUtils.isEmpty(address)) {
            val split = address!!.split("[.]".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

            if (split.size == 4) {
                for (i in 0..3) {
                    mAddress[i]!!.setText(split[i])
                }
            }
        }

        val port = getServerPort(mPreferenceName!!)
        if (port > 0) {
            mPort!!.setText(port.toString() + "")
        }

        var resolution = getResolution(mPreferenceName!!)
        if (resolution == 0) {
            resolution = 720
        }

        if (resolution == 1080) {
            mRes1080!!.isChecked = true
        } else {
            mRes720!!.isChecked = true
        }
    }

    private val mOnClickListener =
        View.OnClickListener { v ->
            val id = v.id
            if (id == R.id.remove) {
                for (i in 0..3) {
                    mAddress[i]!!.setText("")
                }
                mPort!!.setText("")
            } else if (id == R.id.confirm) {
                val address = StringBuilder()
                for (i in 0..3) {
                    if (mAddress[i]!!.text.length == 0) {
                        Toast.makeText(
                            this@SettingActivity,
                            "IP가 유효하지 않습니다. 입력을 확인하세요",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@OnClickListener
                    }

                    address.append(mAddress[i]!!.text)

                    if (i != 3) address.append(".")
                }

                if (mPort!!.text.length == 0) {
                    Toast.makeText(
                        this@SettingActivity,
                        "PORT가 유효하지 않습니다. 입력을 확인하세요",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnClickListener
                }

                setServerAddress(mPreferenceName!!, address.toString())

                val port = mPort!!.text.toString().toInt()
                setServerPort(mPreferenceName!!, port)

                // 해상도 저장
                if (mRadioGroup!!.checkedRadioButtonId == mRes1080!!.id) {
                    setResolution(mPreferenceName!!, 1080)
                } else {
                    setResolution(mPreferenceName!!, 720)
                }

                finish()
            } else if (id == R.id.cancel) {
                finish()
            }
        }
}