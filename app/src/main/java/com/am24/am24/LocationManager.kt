package com.am24.am24

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

public class LocationManager(private val context: Context) {
    companion object {

        /**
         * Synchronously returns *(lat, lng)* from the device’s cached
         * location, or **null** when unavailable / no permission.
         *
         * (It blocks for up to 2 s to give Google Play-Services a chance
         *  to hand us a value; change the timeout if you wish.)
         */
        @SuppressLint("MissingPermission")
        suspend fun getLastKnownLocation(ctx: Context): Pair<Double, Double>? {
            val fused = LocationServices.getFusedLocationProviderClient(ctx)
            return try {
                // Wait (max 2 s) for the Task to finish without blocking a thread
                val loc = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(2)) {
                    fused.lastLocation.await()
                }
                loc?.let { Pair(it.latitude, it.longitude) }
            } catch (_: Exception) {
                null          // timeout, security-exception, etc.
            }
        }
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val database: DatabaseReference = FirebaseRefs.db.getReference("users")

    // GeoFire reference under "geoFireLocations"
    private val geoFireDatabaseRef = FirebaseRefs.db.getReference("geoFireLocations")
    private val geoFire = GeoFire(geoFireDatabaseRef)

    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 600000 // 10 minutes
        fastestInterval = 60000 // 1 minute
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private lateinit var userId: String
    private var isPaused = false

    fun updateUserLocation(userId: String) {
        this.userId = userId
        // Check if updates are paused via profile flag
        database.child(userId).child("isLocationSpoofed").get().addOnSuccessListener { snap ->
            isPaused = snap.getValue(Boolean::class.java) == true
            if (!isPaused) requestLocationUpdates()
        }.addOnFailureListener {
            if (!isPaused) requestLocationUpdates()
        }
    }

    private fun requestLocationUpdates() {

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

    fun pauseUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isPaused = true
    }

    fun resumeUpdates() {
        if (!isPaused) return
        isPaused = false
        if (::userId.isInitialized) {
            requestLocationUpdates()
        }
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

    fun getUserLocationFromGeoFire(
        userId: String,
        onLocationResult: (latitude: Double?, longitude: Double?) -> Unit
    ) {
        geoFire.getLocation(userId, object : com.firebase.geofire.LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                if (location != null) {
                    onLocationResult(location.latitude, location.longitude)
                } else {
                    // Means no location found for that user
                    onLocationResult(null, null)
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                println("Error retrieving location from GeoFire: ${error.message}")
                onLocationResult(null, null)
            }
        })
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


    fun setCustomLocation(userId: String, latitude: Double, longitude: Double) {
        updateLocationInFirebase(userId, latitude, longitude)
        updateLocationInGeoFire(userId, latitude, longitude)
    }
}
