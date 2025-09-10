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
import kotlin.math.roundToInt

class NearbyViewModel : ViewModel() {
    val people = mutableStateListOf<NearbyUser>()
    var sortMode by mutableStateOf(SortMode.NEARBY)
    // Default radius shown in the People tab
    var radiusKm by mutableStateOf(50.0)
    var lastActiveHours by mutableStateOf(24.0)
    var excludedUserIds by mutableStateOf<Set<String>>(emptySet())
    var isPlus by mutableStateOf(false)
    var isPremium by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var currentProfile: Profile? = null
    var datingFilters by mutableStateOf(DatingFilterSettings())

    fun setTier(isPlus: Boolean, isPremium: Boolean) {
        this.isPlus = isPlus
        this.isPremium = isPremium
    }

    fun setCurrentUserProfile(profile: Profile?) {
        currentProfile = profile
    }


    private var geoQuery: GeoQuery? = null
    private val userCache = mutableMapOf<String, NearbyUser>()
    private val cacheTimestamps = mutableMapOf<String, Long>()
    private val cacheTtlMs = 5 * 60 * 1000L

    /**
     * Refresh the People tab:
     * - NEARBY: live GeoFire radius query (ignores allowLocationPublic/allowLocationForMatches).
     * - ACTIVE: we still use the same list and the UI filters/sorts by lastActiveHours.
     */
    fun refreshNearbyUsers(
        userId: String,
        center: LatLng,
        geoFireDatabaseRef: DatabaseReference,
        forceRefresh: Boolean = false
    ) {
        if (forceRefresh) invalidateCache()
        // Reset
        geoQuery?.removeAllListeners()
        geoQuery = null
        isRefreshing = true
        people.clear()

        val limit = Int.MAX_VALUE
        // Always run the NEARBY GeoFire query (People tab dataset),
        // and let the UI's toggle handle last-active filtering/sorting.
        geoQuery = observeNearbyUsers(
            currentUserId = userId,
            center = center,
            radiusKm = radiusKm,
            geoFireDatabaseRef = geoFireDatabaseRef,
            limit = limit,
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

    fun invalidateCache(userId: String? = null) {
        if (userId == null) {
            userCache.clear()
            cacheTimestamps.clear()
        } else {
            userCache.remove(userId)
            cacheTimestamps.remove(userId)
        }
    }

    /**
     * GeoFire listener that IGNORES visibility/matches and only honors radius/exclusions.
     */
    private fun observeNearbyUsers(
        currentUserId: String,
        center: LatLng,
        radiusKm: Double,
        geoFireDatabaseRef: DatabaseReference,
        limit: Int,
        onEnterOrMove: (NearbyUser) -> Unit,
        onExit: (String) -> Unit
    ): GeoQuery {
        val geoFire = GeoFire(geoFireDatabaseRef)
        val query: GeoQuery = geoFire.queryAtLocation(
            GeoLocation(center.latitude, center.longitude),
            radiusKm
        )
        var reachedLimit = false

        fun buildUser(uid: String, loc: GeoLocation?) {
            if (reachedLimit || people.size >= limit) {
                if (!reachedLimit) {
                    reachedLimit = true
                    query.removeAllListeners()
                    isRefreshing = false
                }
                return
            }
            if (uid == currentUserId) return
            if (uid in excludedUserIds) return
            val now = System.currentTimeMillis()
            val latLng = if (loc != null) LatLng(loc.latitude, loc.longitude) else null
            val distM = if (latLng != null) distanceMeters(center, latLng) else Double.POSITIVE_INFINITY

            val cached = userCache[uid]
            val timestamp = cacheTimestamps[uid] ?: 0L
            if (cached != null && now - timestamp < cacheTtlMs) {
                val updated = cached.copy(latLng = latLng, distanceMeters = distM)
                userCache[uid] = updated
                onEnterOrMove(updated)
                if (people.size >= limit) {
                    reachedLimit = true
                    query.removeAllListeners()
                    isRefreshing = false
                }
                return
            }

            val usersRef = FirebaseRefs.db.getReference("users").child(uid)
            usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (reachedLimit || people.size >= limit) {
                        if (!reachedLimit) {
                            reachedLimit = true
                            query.removeAllListeners()
                            isRefreshing = false
                        }
                        return
                    }
                    val p = snapshot.getValue(Profile::class.java) ?: return
                    if (p.isPrivate) {
                        onExit(uid)
                        return
                    }

                    // No allowLocationPublic/allowLocationForMatches checks here
                    val username = (p.username ?: "").ifBlank { p.name ?: "" }
                    if (username.isBlank()) {
                        onExit(uid)
                        return
                    }
                    val age = calculateAge(p.dob)
                    if (isPlus && !matchesFilters(p, age)) {
                        onExit(uid)
                        return
                    }
                    val lastActive = snapshot.child("lastActive").getValue(Long::class.java) ?: p.lastActive

                    val compat = currentProfile?.let { cp ->
                        val ageCompat = ageCompatibilityScore(calculateAge(cp.dob), age)
                        val zodiacCompat = zodiacCompatibilityScore(cp.zodiac ?: "", p.zodiac ?: "")
                        (((ageCompat + zodiacCompat) / 2.0) * 100).roundToInt()
                    }
                    val detailCandidates = buildList {
                        add(p.bio)
                        add(p.jobRole)
                        add(p.work)
                        add(p.college)
                        add(p.religion)
                        add(p.community)
                        if (p.allowLocationPublic) add(p.hometown)
                    }.filter { it.isNotBlank() }
                    val randomDetail = detailCandidates.randomOrNull()

                    val user = NearbyUser(
                        userId = uid,
                        username = username,
                        age = age,
                        photoUrl = p.profilepicUrl,
                        lastActiveAt = lastActive,
                        latLng = latLng,
                        distanceMeters = distM,
                        interests = p.interests,
                        compatibilityPct = compat,
                        randomDetail = randomDetail
                    )
                    userCache[uid] = user
                    cacheTimestamps[uid] = now
                    onEnterOrMove(user)
                    if (people.size >= limit) {
                        reachedLimit = true
                        query.removeAllListeners()
                        isRefreshing = false
                    }
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

    private fun matchesFilters(p: Profile, age: Int): Boolean {
        val f = datingFilters
        if (age < f.ageStart || age > f.ageEnd) return false
        if (f.religion.isNotBlank() && canonicalReligion(p.religion) != canonicalReligion(f.religion)) return false
        if (f.ethnicity.isNotBlank() && canonicalEthnicity(p.ethnicity) != canonicalEthnicity(f.ethnicity)) return false
        if (f.roles.isNotEmpty()) {
            val canon = f.roles.map { canonicalRole(it) }
            if (p.roles.none { canonicalRole(it) in canon }) return false
        }
        if (f.tribes.isNotEmpty()) {
            val canon = f.tribes.map { canonicalTribe(it) }
            if (p.tribes.none { canonicalTribe(it) in canon }) return false
        }
        if (f.kinks.isNotEmpty()) {
            val canon = f.kinks.map { canonicalKink(it) }
            if (p.kinks.none { canonicalKink(it) in canon }) return false
        }
        return true
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
