package com.am24.am24

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("users")

    // GeoFire reference under "geoFireLocations"
    private val geoFireDatabaseRef = FirebaseDatabase.getInstance().getReference("geoFireLocations")
    private val geoFire = GeoFire(geoFireDatabaseRef)

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 10000 // 10 seconds
        fastestInterval = 5000 // 5 seconds
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private lateinit var userId: String

    fun updateUserLocation(userId: String) {
        this.userId = userId

        // Check if location permissions are granted
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001 // Request code for permissions
            )
            return
        }

        // Request location updates
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // Location callback to handle updates
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location: Location? = locationResult.lastLocation
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                updateLocationInFirebase(userId, latitude, longitude)
                updateLocationInGeoFire(userId, latitude, longitude) // Store in GeoFire
            } else {
                println("Location could not be retrieved.")
            }
        }
    }

    // Function to update Firebase with new location
    private fun updateLocationInFirebase(userId: String, latitude: Double, longitude: Double) {
        val locationUpdates = mapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )

        database.child(userId).updateChildren(locationUpdates).addOnSuccessListener {
            println("Location updated successfully in Firebase.")
        }.addOnFailureListener { exception ->
            println("Failed to update location in Firebase: ${exception.localizedMessage}")
        }
    }

    // Function to update GeoFire with new location
    private fun updateLocationInGeoFire(userId: String, latitude: Double, longitude: Double) {
        geoFire.setLocation(userId, GeoLocation(latitude, longitude)) { key, error ->
            if (error != null) {
                println("Failed to update GeoFire location: ${error.message}")
            } else {
                println("Location updated successfully in GeoFire for user $key.")
            }
        }
    }
}
