package com.corgimemo.app.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
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

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                locationManager?.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    locationListener!!,
                    null
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: IllegalArgumentException) {
                try {
                    locationManager?.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER,
                        locationListener!!,
                        null
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
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
