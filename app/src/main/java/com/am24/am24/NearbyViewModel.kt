package com.am24.am24

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.*

class NearbyViewModel : ViewModel() {
    val people = mutableStateListOf<NearbyUser>()
    var sortMode by mutableStateOf(SortMode.NEARBY)
    var radiusKm by mutableStateOf(10.0)
    var lastActiveHours by mutableStateOf(24.0)
    var genderFilter by mutableStateOf(GenderFilter.BOTH)
    var excludedUserIds by mutableStateOf<Set<String>>(emptySet())
    var isPlus by mutableStateOf(false)
    var isPremium by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)

    private var geoQuery: GeoQuery? = null

    /**
     * Refresh the People tab:
     * - NEARBY: live GeoFire radius query (ignores allowLocationPublic/allowLocationForMatches).
     * - ACTIVE: we still use the same list and the UI filters/sorts by lastActiveHours.
     */
    fun refreshNearbyUsers(
        userId: String,
        center: LatLng,
        geoFireDatabaseRef: DatabaseReference
    ) {
        // Reset
        geoQuery?.removeAllListeners()
        geoQuery = null
        isRefreshing = true
        people.clear()

        // Always run the NEARBY GeoFire query (People tab dataset),
        // and let the UI's toggle handle last-active filtering/sorting.
        geoQuery = observeNearbyUsers(
            currentUserId = userId,
            center = center,
            radiusKm = radiusKm,
            geoFireDatabaseRef = geoFireDatabaseRef,
            onEnterOrMove = { if (it.userId !in excludedUserIds) upsert(people, it) },
            onExit = { uid -> people.removeAll { it.userId == uid } }
        )
    }

    fun setExcluded(ids: Set<String>) {
        excludedUserIds = ids
        people.removeAll { it.userId in ids }
    }

    fun addExcluded(uid: String) {
        excludedUserIds = excludedUserIds + uid
        people.removeAll { it.userId == uid }
    }

    /**
     * GeoFire listener that IGNORES visibility/matches and only honors radius/exclusions.
     */
    private fun observeNearbyUsers(
        currentUserId: String,
        center: LatLng,
        radiusKm: Double,
        geoFireDatabaseRef: DatabaseReference,
        onEnterOrMove: (NearbyUser) -> Unit,
        onExit: (String) -> Unit
    ): GeoQuery {
        val geoFire = GeoFire(geoFireDatabaseRef)
        val query: GeoQuery = geoFire.queryAtLocation(
            GeoLocation(center.latitude, center.longitude),
            radiusKm
        )

        fun buildUser(uid: String, loc: GeoLocation?) {
            if (uid == currentUserId) return
            if (uid in excludedUserIds) return

            val usersRef = FirebaseRefs.db.getReference("users").child(uid)
            usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val p = snapshot.getValue(Profile::class.java) ?: return

                    // No allowLocationPublic/allowLocationForMatches checks here
                    val username = (p.username ?: "").ifBlank { p.name ?: "" }
                    val age = calculateAge(p.dob)
                    val lastActive = snapshot.child("lastActive").getValue(Long::class.java) ?: p.lastActive
                    val latLng = if (loc != null) LatLng(loc.latitude, loc.longitude) else null
                    val distM = if (latLng != null) distanceMeters(center, latLng) else Double.POSITIVE_INFINITY

                    onEnterOrMove(
                        NearbyUser(
                            userId = uid,
                            username = username,
                            age = age,
                            photoUrl = p.profilepicUrl,
                            lastActiveAt = lastActive,
                            latLng = latLng,
                            distanceMeters = distM,
                            gender = p.gender ?: "",
                            sexualOrientation = p.sexualOrientation ?: ""
                        )
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MapScreenVM", "User fetch cancelled $uid: ${error.message}")
                }
            })
        }

        query.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) = buildUser(key, location)
            override fun onKeyExited(key: String) = onExit(key)
            override fun onKeyMoved(key: String, location: GeoLocation) = buildUser(key, location)
            override fun onGeoQueryReady() { isRefreshing = false }
            override fun onGeoQueryError(error: DatabaseError) {
                Log.e("MapScreenVM", "GeoQuery error: ${error.message}")
                isRefreshing = false
            }
        })
        return query
    }

    private fun upsert(list: MutableList<NearbyUser>, item: NearbyUser) {
        val idx = list.indexOfFirst { it.userId == item.userId }
        if (idx >= 0) list[idx] = item else list.add(item)
    }

    private fun calculateAge(dob: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
            val date = sdf.parse(dob)
            val cal = java.util.Calendar.getInstance().apply { time = date!! }
            val now = java.util.Calendar.getInstance()
            var age = now.get(java.util.Calendar.YEAR) - cal.get(java.util.Calendar.YEAR)
            if (now.get(java.util.Calendar.DAY_OF_YEAR) < cal.get(java.util.Calendar.DAY_OF_YEAR)) age--
            age
        } catch (e: Exception) {
            0
        }
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            results
        )
        return results[0].toDouble()
    }
}
