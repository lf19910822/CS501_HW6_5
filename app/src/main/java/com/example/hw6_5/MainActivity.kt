package com.example.hw6_5

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hw6_5.ui.theme.HW6_5Theme
import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HW6_5Theme {
                MapScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    // Set initial position to a default location (will be updated when user location is obtained)
    val defaultLocation = LatLng(37.7749, -122.4194) // San Francisco
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }
    var addressText by remember { mutableStateOf("Fetching address...") }
    var customMarkers by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        locationPermissionsState.launchMultiplePermissionRequest()
    }

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            getUserLocation(fusedLocationClient) { location ->
                userLocation = location
                // Animate camera to user location
                coroutineScope.launch {
                    cameraPositionState.animate(
                        update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(location, 15f),
                        durationMs = 1000
                    )
                }

                // Get address for user location
                getAddressFromLocation(context, location) { address ->
                    addressText = address
                }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                locationPermissionsState.allPermissionsGranted -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Address display
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Text(
                                text = addressText,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Google Map
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(isMyLocationEnabled = true),
                            uiSettings = MapUiSettings(zoomControlsEnabled = true),
                            onMapClick = { latLng ->
                                // Add custom marker on tap
                                customMarkers = customMarkers + latLng

                                // Update address for tapped location
                                getAddressFromLocation(context, latLng) { address ->
                                    addressText = address
                                }
                            }
                        ) {
                            // Marker at user's location
                            userLocation?.let { location ->
                                Marker(
                                    state = MarkerState(position = location),
                                    title = "Your Location",
                                    snippet = "You are here"
                                )
                            }

                            // Custom markers
                            customMarkers.forEachIndexed { index, position ->
                                Marker(
                                    state = MarkerState(position = position),
                                    title = "Custom Marker ${index + 1}",
                                    snippet = "Lat: ${position.latitude}, Lng: ${position.longitude}"
                                )
                            }
                        }
                    }
                }
                locationPermissionsState.shouldShowRationale -> {
                    PermissionRationale(
                        onRequestPermission = { locationPermissionsState.launchMultiplePermissionRequest() }
                    )
                }
                else -> {
                    PermissionDenied(
                        onRequestPermission = { locationPermissionsState.launchMultiplePermissionRequest() }
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun getUserLocation(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocationReceived: (LatLng) -> Unit
) {
    // Try to get last known location first
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            onLocationReceived(LatLng(location.latitude, location.longitude))
        } else {
            // If no last location, request current location
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { currentLocation ->
                if (currentLocation != null) {
                    onLocationReceived(LatLng(currentLocation.latitude, currentLocation.longitude))
                } else {
                    // Default location if unable to get user location
                    onLocationReceived(LatLng(37.7749, -122.4194)) // San Francisco
                }
            }.addOnFailureListener {
                // Default location on failure
                onLocationReceived(LatLng(37.7749, -122.4194)) // San Francisco
            }
        }
    }.addOnFailureListener {
        // Default location on failure
        onLocationReceived(LatLng(37.7749, -122.4194)) // San Francisco
    }
}

private fun getAddressFromLocation(
    context: android.content.Context,
    latLng: LatLng,
    onAddressReceived: (String) -> Unit
) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val addressString = buildString {
                        address.getAddressLine(0)?.let { append(it) }
                    }
                    onAddressReceived(addressString.ifEmpty { "Address not found" })
                } else {
                    onAddressReceived("Address not found")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressString = address.getAddressLine(0) ?: "Address not found"
                onAddressReceived(addressString)
            } else {
                onAddressReceived("Address not found")
            }
        }
    } catch (e: Exception) {
        onAddressReceived("Unable to get address: ${e.message}")
    }
}

@Composable
fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Location permission is required to display the map centered at your location.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun PermissionDenied(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Location permission is required for this app to function.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onRequestPermission) {
            Text("Request Permission")
        }
    }
}