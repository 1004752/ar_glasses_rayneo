package com.rayneo.arsdk.android.demo.ui.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rayneo.arsdk.android.core.ViewPair
import com.rayneo.arsdk.android.demo.NativeManager
import com.rayneo.arsdk.android.demo.databinding.LayoutRecyclerviewMovedFocusBinding
import com.rayneo.arsdk.android.demo.ui.adapter.MovedFocusPosAdapter
import com.rayneo.arsdk.android.demo.ui.entity.GolfCourse
import com.rayneo.arsdk.android.demo.ui.service.golfCourseService
import com.rayneo.arsdk.android.demo.ui.service.LocationService
import com.rayneo.arsdk.android.ui.toast.FToast
import com.rayneo.arsdk.android.ui.util.RecyclerViewFocusTracker
import com.rayneo.arsdk.android.touch.TempleAction
import com.rayneo.arsdk.android.touch.TempleActionViewModel
import com.rayneo.arsdk.android.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * RecyclerView with fixed focus position
 */
class MovedFocusPosRVActivity : BaseMirrorActivity<LayoutRecyclerviewMovedFocusBinding>() {
    private lateinit var favoriteTracker: RecyclerViewFocusTracker
    private var locationService: LocationService? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            isBound = true
            useLocation()
            fetchDataFromApi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favoriteTracker = RecyclerViewFocusTracker(
            ViewPair(mBindingPair.left.recyclerView, mBindingPair.right.recyclerView),
            ignoreDelta = 70
        )

        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
        val bindResult = bindService(Intent(this, LocationService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("MovedFocusPosRVActivity", "Bind result: $bindResult")

        initView()
        initEvent()
        favoriteTracker.focusObj.hasFocus = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun initEvent() {
        // 监听原始事件，实现跟手效果

        lifecycleScope.launchWhenResumed {
            val templeActionViewModel =
                ViewModelProvider(this@MovedFocusPosRVActivity).get<TempleActionViewModel>()
            templeActionViewModel.state.collectLatest {
                if (!favoriteTracker.focusObj.hasFocus || !this.isActive || it.consumed) {
                    return@collectLatest
                }
                favoriteTracker.handleActionEvent(it) { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> {
                            finish()
                        }
                        is TempleAction.Click -> {
                            if (!action.consumed) {
                                (mBindingPair.left.recyclerView.adapter as MovedFocusPosAdapter)
                                    .getCurrentData()?.apply {
                                        FToast.show(distance)
                                    }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun initView() {
        val mPair = mBindingPair
        mPair.updateView {
            val isLeft = mPair.checkIsLeft(this)
            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = MovedFocusPosAdapter(context, isLeft, favoriteTracker)
                itemAnimator = null
            }
            favoriteTracker.setCurrentSelectPos(0)
        }
    }

    private fun fetchDataFromApi() {
        lifecycleScope.launch {
            try {
                val distanceInKm = 5.0
                val (NW, SE) = calculateNWandSEPoints(currentLat, currentLon, distanceInKm)
//                FToast.show("BOX(${NW.second},${NW.first},${SE.second},${SE.first})")
                Log.d("Circle", "$currentLat / $currentLon")
                Log.d("Circle", "BOX(${NW.second},${NW.first},${SE.second},${SE.first})")

                val response = golfCourseService.getGolfCourses(
                    service="data",
                    version="2.0",
                    request="GetFeature",
                    format="json",
                    errorformat="json",
                    size="10",
                    page="1",
                    data="LT_P_SGISGOLF",
                    geomfilter="BOX(${NW.second},${NW.first},${SE.second},${SE.first})",
                    columns="golf_name,ag_geom",
                    geometry="true",
                    attribute="true",
                    crs="EPSG:4019",
                    key="814270A2-3CDC-3BAA-A6F2-81F54C823AE4",
                    domain=""
                )
                val features = response.response.result.featureCollection.features
                val golfCourses = features.mapIndexed { index, feature ->
                    GolfCourse(
                        displayName = feature.properties.golf_name,
                        distance = calculateDistance(
                            feature.geometry.coordinates[1],
                            feature.geometry.coordinates[0]
                        ),
                        id = index.toLong()
                    )
                }
                updateAdapterData(golfCourses)
            } catch (e: Exception) {
                Log.e("MovedFocusPosRVActivity", "Error fetching data from API", e)
            }
        }
    }

    private fun calculateDistance(lat: Double, lon: Double): String {
        val lastLocation = locationService?.lastLocation ?: return "Unknown distance"
        return locationService?.calculateDistance(lastLocation.latitude, lastLocation.longitude, lat, lon) ?: "Unknown distance"
    }

    private fun updateAdapterData(golfCourses: List<GolfCourse>) {
        runOnUiThread {
            var adapterLeft = mBindingPair.left.recyclerView.adapter as MovedFocusPosAdapter
            adapterLeft.setData(golfCourses)
            var adapterRight = mBindingPair.right.recyclerView.adapter as MovedFocusPosAdapter
            adapterRight.setData(golfCourses)
        }
    }

    private fun useLocation() {
        val location = locationService?.lastLocation
        if (location != null) {
            currentLat = location.latitude
            currentLon = location.longitude
//            FToast.show("latitcaude: ${location.latitude}\nlongitude:${location.longitude}")
            Log.d("MovedFocusPosRVActivity", "Current Location: ${location.latitude}, ${location.longitude}")
        } else {
            Log.d("MovedFocusPosRVActivity", "Location is null")
        }
    }

    private fun calculateOffsetCoordinates(lat: Double, lon: Double, distanceInKm: Double, bearingInDegrees: Double): Pair<Double, Double> {
        val R = 6371.0 // Radius of the Earth in kilometers
        val bearing = Math.toRadians(bearingInDegrees)

        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)

        val lat2 = asin(sin(lat1) * cos(distanceInKm / R) +
                cos(lat1) * sin(distanceInKm / R) * cos(bearing))
        val lon2 = lon1 + atan2(sin(bearing) * sin(distanceInKm / R) * cos(lat1),
            cos(distanceInKm / R) - sin(lat1) * sin(lat2))

        return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun calculateNWandSEPoints(currentLat: Double, currentLon: Double, distanceInKm: Double): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        // Bearing for NW is 315 degrees (360 - 45)
        val NW = calculateOffsetCoordinates(currentLat, currentLon, distanceInKm, 315.0)
        // Bearing for SE is 135 degrees (180 - 45)
        val SE = calculateOffsetCoordinates(currentLat, currentLon, distanceInKm, 135.0)

        return Pair(NW, SE)
    }
}
