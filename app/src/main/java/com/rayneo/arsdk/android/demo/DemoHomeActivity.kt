package com.rayneo.arsdk.android.demo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rayneo.arsdk.android.core.make3DEffectForSide
import com.rayneo.arsdk.android.demo.databinding.LayoutDemoHomeBinding
import com.rayneo.arsdk.android.demo.ui.activity.DialogActivity
import com.rayneo.arsdk.android.demo.ui.activity.FixedFocusPosRVActivity
import com.rayneo.arsdk.android.demo.ui.activity.FragmentDemoActivity
import com.rayneo.arsdk.android.demo.ui.activity.MovedFocusPosRVActivity
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
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat

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
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var ipc: NativeManager

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "DemoHomeActivity"
    }

    var DF2 = DecimalFormat("#.##")
    var DF4 = DecimalFormat("#.####")

    // 현재 내 위치의 위도와 경도
    private var myPositionLatitude: Double = 37.40271
    private var myPositionLongitude: Double = 127.10332

    // 고정된 장소의 위도와 경도
    private val fixedLatitude = 37.40383
    private val fixedLongitude = 127.10296

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MercurySDK.init(application)

        ipc = NativeManager.getInstance(this)
        ipc.request("{\"action\":\"start_location_stream_pushing\"}")

        checkLocationPermission()

        initFocusTarget()
        initEvent()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        } else {
            Log.d("GPS", "Permission denied")
        }
    }

    private fun getLocation() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                myPositionLatitude = DF4.format(location.latitude).toDouble()
                myPositionLongitude = DF4.format(location.longitude).toDouble()
                Log.d("GPS", "onLocationChanged Latitude: $myPositionLatitude, Longitude: $myPositionLongitude")

                // 고정된 장소와 현재 위치 간의 거리 계산
                val distance = calculateDistance(myPositionLatitude, myPositionLongitude, fixedLatitude, fixedLongitude)
                FToast.show("Distance: $distance m")
                Log.d(TAG, "Distance: $distance m")

                // Send GPS data to Unity via IPC
                val gpsData = "{\"latitude\":$myPositionLatitude,\"longitude\":$myPositionLongitude}"
                ipc.request(gpsData)
                Log.d("IPC", "GPS data sent: $gpsData")
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
                Log.d("GPS", "Status changed: Provider: $provider, Status: $status")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d("GPS", "Provider enabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d("GPS", "Provider disabled: $provider")
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("GPS", "Requesting location updates")
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, locationListener)
                Log.d("GPS", "GPS_PROVIDER is enabled")
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, locationListener)
                Log.d("GPS", "NETWORK_PROVIDER is enabled")
            }
        } else {
            Log.d("GPS", "Location permission not granted")
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return DF2.format(results[0]).toFloat()
    }

    private fun initEvent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                templeActionViewModel.state.collect {
                    FLogger.i("DemoActivity", "action = $it")
                    when (it) {
                        is TempleAction.DoubleClick -> finish()
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
                            FToast.show("Latitude: $myPositionLatitude\nLongitude: $myPositionLongitude")
                            Log.d("GPS", "onLocationChanged Latitude: $myPositionLatitude, Longitude: $myPositionLongitude")
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
                                val distance = calculateDistance(myPositionLatitude, myPositionLongitude, fixedLatitude, fixedLongitude)
                                FToast.show("Distance: $distance m")
                                Log.d("GPS", "Distance: $distance m")
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
