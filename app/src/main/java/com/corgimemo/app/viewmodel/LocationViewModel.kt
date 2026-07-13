package com.corgimemo.app.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel

class LocationViewModel(private val context: Context) : ViewModel() {

    private val _currentLocation = mutableStateOf<Location?>(null)
    val currentLocation: State<Location?> = _currentLocation

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    fun requestCurrentLocation() {
        if (checkPermissions()) {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    _currentLocation.value = location
                    locationManager?.removeUpdates(this)
                }

                @Suppress("DEPRECATION")
                @Deprecated("Use newer location APIs")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestCurrentLocationModern(LocationManager.GPS_PROVIDER)
            } else {
                @Suppress("DEPRECATION")
                requestSingleUpdateLegacy(LocationManager.GPS_PROVIDER)
            }
        }
    }

    /**
     * API 30+ 使用 LocationManager.getCurrentLocation() 获取单次定位。
     * 若 GPS 无结果则降级到 NETWORK_PROVIDER。
     */
    private fun requestCurrentLocationModern(provider: String) {
        try {
            locationManager?.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context)
            ) { location ->
                if (location != null) {
                    _currentLocation.value = location
                } else if (provider == LocationManager.GPS_PROVIDER) {
                    requestCurrentLocationModern(LocationManager.NETWORK_PROVIDER)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    /**
     * API 26-29 走旧的 requestSingleUpdate 路径（已废弃但仍可用）。
     * 移除 LocationListener 即可停止后续更新。
     */
    @Suppress("DEPRECATION")
    private fun requestSingleUpdateLegacy(provider: String) {
        try {
            locationManager?.requestSingleUpdate(
                provider,
                locationListener!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            if (provider == LocationManager.GPS_PROVIDER) {
                requestSingleUpdateLegacy(LocationManager.NETWORK_PROVIDER)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onCleared() {
        super.onCleared()
        locationListener?.let {
            locationManager?.removeUpdates(it)
        }
    }
}
