package com.corgimemo.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPicker(
    lat: Double?,
    lng: Double?,
    radius: Float?,
    type: Int,
    address: String?,
    enabled: Boolean,
    onLocationChange: (lat: Double?, lng: Double?, address: String?) -> Unit,
    onRadiusChange: (radius: Float) -> Unit,
    onTypeChange: (type: Int) -> Unit,
    onEnabledChange: (enabled: Boolean) -> Unit
) {
    val context = LocalContext.current
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var radiusValue by remember { mutableStateOf(radius?.toString() ?: "100") }
    var addressValue by remember { mutableStateOf(address ?: "") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                getCurrentLocation(context) { lat, lng ->
                    currentLat = lat
                    currentLng = lng
                }
            }
        }
    )

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "位置提醒",
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = {
                        onEnabledChange(it)
                        if (it) {
                            requestLocationPermission(context, locationPermissionLauncher)
                        }
                    }
                )
            }

            if (enabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = addressValue,
                            onValueChange = {
                                addressValue = it
                            },
                            label = { Text("地址") },
                            placeholder = { Text("请输入地址或点击获取当前位置") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                requestLocationPermission(context, locationPermissionLauncher)
                            },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Icon(
                                imageVector = if (hasLocationPermission(context)) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                contentDescription = "获取当前位置",
                                tint = if (hasLocationPermission(context)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (currentLat != null && currentLng != null) {
                        Text(
                            text = "当前位置: ${currentLat!!.toString().take(8)}, ${currentLng!!.toString().take(8)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(
                            onClick = {
                                onLocationChange(currentLat, currentLng, "当前位置")
                                addressValue = "当前位置"
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("使用当前位置")
                        }
                    }

                    OutlinedTextField(
                        value = radiusValue,
                        onValueChange = {
                            radiusValue = it
                            val radiusFloat = it.toFloatOrNull() ?: 100f
                            if (radiusFloat in 50f..500f) {
                                onRadiusChange(radiusFloat)
                            }
                        },
                        label = { Text("触发半径（米）") },
                        placeholder = { Text("100") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )

                    Text(
                        text = "触发方式",
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        RadioOption(
                            text = "到达时",
                            selected = type == 0,
                            onClick = { onTypeChange(0) }
                        )
                        RadioOption(
                            text = "离开时",
                            selected = type == 1,
                            onClick = { onTypeChange(1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RadioOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(end = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(text = text)
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requestLocationPermission(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (!hasLocationPermission(context)) {
        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        getCurrentLocation(context) { lat, lng ->
        }
    }
}

private fun getCurrentLocation(context: Context, callback: (Double, Double) -> Unit) {
    if (!hasLocationPermission(context)) {
        return
    }
    
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    
    try {
        val gpsLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        if (gpsLocation != null) {
            callback(gpsLocation.latitude, gpsLocation.longitude)
            return
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
    
    try {
        val networkLocation = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        if (networkLocation != null) {
            callback(networkLocation.latitude, networkLocation.longitude)
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}
