package com.rayneo.arsdk.android.demo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rayneo.arsdk.android.core.make3DEffectForSide
import com.rayneo.arsdk.android.demo.databinding.LayoutDemoHomeBinding
import com.rayneo.arsdk.android.demo.ui.service.LocationService
import com.rayneo.arsdk.android.ui.toast.FToast
import com.rayneo.arsdk.android.ui.util.FixPosFocusTracker
import com.rayneo.arsdk.android.ui.util.FocusHolder
import com.rayneo.arsdk.android.ui.util.FocusInfo
import com.rayneo.arsdk.android.focus.reqFocus
import com.rayneo.arsdk.android.touch.TempleAction
import com.rayneo.arsdk.android.util.FLogger
import com.rayneo.arsdk.android.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.launch
import com.ffalconxr.mercury.ipc.Launcher
import com.ffalconxr.mercury.ipc.Response
import com.rayneo.arsdk.android.MercurySDK
import com.rayneo.arsdk.android.demo.ui.activity.MovedFocusPosRVActivity
import org.json.JSONException
import org.json.JSONObject

class NativeManager private constructor(context: Context) {
    private val mLauncher: Launcher = Launcher.getInstance(context)

    init {
        mLauncher.addOnResponseListener { response -> handleResponse(response) }
        Log.d(TAG, "NativeManager initialized")
    }

    private fun handleResponse(response: Response?) {
        try {
            if (response == null || response.data.isEmpty()) {
                Log.d(TAG, "response data is null")
                return
            }
            val jsonStr = response.data
            Log.d(TAG, "response data is: $jsonStr")
            val json = JSONObject(jsonStr)

            if (json.has("mAltitude")) {
                Log.d(TAG, "Altitude: ${json.getDouble("mAltitude")}")
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
        }
    }

    fun request(requestJson: String) {
        if (!mLauncher.isReady) {
            mLauncher.addOnConnectionState { connectionState ->
                if (mLauncher.isReady) {
                    mLauncher.request(requestJson)
                    Log.d(TAG, "Request sent: $requestJson")
                }
            }
        } else {
            mLauncher.request(requestJson)
            Log.d(TAG, "Request sent: $requestJson")
        }
    }

    companion object {
        private var mInstance: NativeManager? = null

        fun getInstance(context: Context): NativeManager {
            if (mInstance == null) {
                mInstance = NativeManager(context.applicationContext)
            }
            return mInstance!!
        }

        private const val TAG = "NativeManager"
    }
}

class DemoHomeActivity : BaseMirrorActivity<LayoutDemoHomeBinding>() {
    private var fixPosFocusTracker: FixPosFocusTracker? = null
    private lateinit var ipc: NativeManager
    private var locationService: LocationService? = null
    private var isBound = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "DemoHomeActivity"
    }

    // 고정된 장소의 위도와 경도
    private val fixedLatitude = 37.40383
    private val fixedLongitude = 127.10296

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MercurySDK.init(application)

        ipc = NativeManager.getInstance(this)
        ipc.request("{\"action\":\"start_location_stream_pushing\"}")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }

        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
        val bindResult = bindService(Intent(this, LocationService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("MovedFocusPosRVActivity", "Bind result: $bindResult")

        initFocusTarget()
        initEvent()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindService(Intent(this, LocationService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            } else {
                Log.d("GPS", "Permission denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect {
                    FLogger.i("DemoActivity", "action = $it")
                    when (it) {
                        is TempleAction.DoubleClick -> {
                            finish()
                        }
                        else -> fixPosFocusTracker?.handleFocusTargetEvent(it)
                    }
                }
            }
        }
    }

    private fun initFocusTarget() {
        val focusHolder = FocusHolder(true)
        mBindingPair.setLeft {
            val btn1Info = FocusInfo(
                btn1,
                eventHandler = { action ->
                    when (action) {
                        is TempleAction.Click -> {
                            val location = locationService?.lastLocation
                            if (location != null) {
                                FToast.show("Latitude: ${location.latitude}\nLongitude: ${location.longitude}")
                                Log.d("GPS", "onLocationChanged Latitude: ${location.latitude}, Longitude: ${location.longitude}")
                            } else {
                                FToast.show("Location is null")
                            }
                        }
                        else -> Unit
                    }
                },
                focusChangeHandler = { hasFocus ->
                    mBindingPair.updateView {
                        triggerFocus(hasFocus, btn1, mBindingPair.checkIsLeft(this))
                    }
                }
            )
            focusHolder.addFocusTarget(
                btn1Info,
                FocusInfo(
                    btn2,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                val location = locationService?.lastLocation
                                if (location != null) {
                                    val distance = locationService?.calculateDistance(location.latitude, location.longitude, fixedLatitude, fixedLongitude)
                                    FToast.show("Distance to fixed location: $distance meters")
                                    Log.d("GPS", "Distance to fixed location: $distance meters")
                                } else {
                                    FToast.show("Location is null")
                                }
                            }
                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btn2, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                FocusInfo(
                    btn3,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                startActivity(
                                    Intent(
                                        this@DemoHomeActivity,
                                        MovedFocusPosRVActivity::class.java
                                    )
                                )
                            }
                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, btn3, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
            )
            focusHolder.currentFocus(mBindingPair.left.btn1)
        }

        fixPosFocusTracker = FixPosFocusTracker(focusHolder).apply {
            focusObj.reqFocus()
        }
    }

    private fun triggerFocus(hasFocus: Boolean, view: View, isLeft: Boolean) {
        view.setBackgroundColor(getColor(if (hasFocus) R.color.purple_200 else R.color.black))
        // 3D 효과
        make3DEffectForSide(view, isLeft, hasFocus)
    }
}
