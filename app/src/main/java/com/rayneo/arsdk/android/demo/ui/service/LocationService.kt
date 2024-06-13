package com.rayneo.arsdk.android.demo.ui.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

class LocationService : Service() {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    var lastLocation: Location? = null
        private set

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastLocation = location
                Log.d("LocationService", "New location: ${location.latitude}, ${location.longitude}")
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, locationListener)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1f, locationListener)
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1000).toString() + "km"
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    companion object {
        private const val TAG = "LocationService"
    }
}