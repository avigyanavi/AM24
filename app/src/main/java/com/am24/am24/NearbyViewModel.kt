package com.am24.am24

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.*
import com.am24.am24.calculateAge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.roundToInt
import java.util.concurrent.TimeUnit
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class MapBootstrapState(
    val isPlus: Boolean,
    val isPremium: Boolean,
    val userCountry: String?,
    val isIndian: Boolean,
    val remainingSwipes: Int,
    val loginPlusExpiry: Long,
    val entryFeePaidAt: Long,
    val entryFeePlusIntroSeen: Boolean,
    val entryFeeOfferExpiry: Long,
    val entryFeeOfferSeen: Boolean,
    val nextRenewal: Long,
    val swipesDayOfYear: Int,
)

class NearbyViewModel : ViewModel() {
    private data class NearbyQueryKey(
        val userId: String,
        val latBucket: Int,
        val lngBucket: Int,
        val radiusBucket: Int,
        val filterHash: Int,
        val sortMode: SortMode,
    )

    private data class NearbyUiState(
        val people: List<NearbyUser>,
        val sortMode: SortMode,
        val lastActiveHours: Double,
        val genderFilter: String,
        val orientationFilter: String,
    )

    val people = mutableStateListOf<NearbyUser>()
    var sortMode by mutableStateOf(SortMode.NEARBY)
    // Default radius shown in the People tab
    var radiusKm by mutableStateOf(50.0)
    var lastActiveHours by mutableStateOf(48.0)
    var excludedUserIds by mutableStateOf<Set<String>>(emptySet())
    var isPlus by mutableStateOf(false)
    var isPremium by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var currentProfile: Profile? = null
    var mapBootstrapState by mutableStateOf<MapBootstrapState?>(null)
        private set

    private var _datingFilters by mutableStateOf(DatingFilterSettings())
    var datingFilters: DatingFilterSettings
        get() = _datingFilters
        set(value) {
            if (_datingFilters != value) {
                _datingFilters = value
                invalidateCache()
                lastQueryKey = null
            }
        }
    var currentLimit by mutableStateOf(10)
    var hasAttemptedInitialLoad by mutableStateOf(false)
        private set

    var hasLoadedFirstResult by mutableStateOf(false)
        private set

    private var lastQueryKey: NearbyQueryKey? = null
    private var _hasLoadedExcludes = false
    private var pendingFetches = 0
    private var geoQueryCompleted = false
    private var lastIndexKey: String? = null   // pagination anchor for server index modes

    private fun resetRefreshTracking() {
        pendingFetches = 0
        geoQueryCompleted = false
        isRefreshing = true
    }

    private fun markFetchStarted() {
        pendingFetches += 1
        isRefreshing = true
    }

    private fun markFetchFinished() {
        if (pendingFetches > 0) {
            pendingFetches -= 1
        }
        if (pendingFetches == 0 && geoQueryCompleted) {
            isRefreshing = false
        }
    }

    private fun markQueryCompleted() {
        geoQueryCompleted = true
        if (pendingFetches == 0) {
            isRefreshing = false
        }
    }

    private fun likesCountFrom(snapshot: DataSnapshot, p: Profile): Int {
        // Handle both possible casings and fallbacks
        val upper = snapshot.child("UsersWhoLikeMe")
        val lower = snapshot.child("usersWhoLikeMe")

        val countFromMap =
            when {
                upper.exists() -> upper.childrenCount.toInt()
                lower.exists() -> lower.childrenCount.toInt()
                else -> null
            }

        return countFromMap
            ?: when (val m = p.UsersWhoLikeMe) { // if your Profile may deserialize a map field
                is Map<*, *> -> m.size
                is Collection<*> -> m.size
                else -> (p.numberOfUsersWhoSwiped ?: 0L).toInt()
            }
    }

    val nearbyUsers: Flow<List<NearbyUser>> = snapshotFlow {
        NearbyUiState(
            people = people.toList(),
            sortMode = sortMode,
            lastActiveHours = lastActiveHours,
            genderFilter = datingFilters.gender,
            orientationFilter = datingFilters.orientation,
            )
    }.map { state ->
        var list: List<NearbyUser> = state.people
        val genderFilter = canonicalGender(state.genderFilter)
        if (genderFilter.isNotBlank()) {
            list = list.filter { matchesCanonicalGender(it.gender, genderFilter) }
        }
        val orientationFilter = canonicalOrientation(state.orientationFilter)
        if (orientationFilter.isNotBlank()) {
            list = list.filter { canonicalOrientation(it.sexualOrientation) == orientationFilter }
        }
        // Time window for ACTIVE and POPULAR (e.g., last X hours/days)
        if (state.sortMode == SortMode.ACTIVE || state.sortMode == SortMode.POPULAR) {
            val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(state.lastActiveHours.toLong())
            list = list.filter { it.lastActiveAt >= cutoff }
        }
        val tierComparator = compareByDescending<NearbyUser> { it.isPremium }
            .thenByDescending { it.isPlus }
        when (state.sortMode) {
            SortMode.NEARBY -> list.sortedWith(
                tierComparator.thenBy { it.distanceMeters }
            )
            SortMode.ACTIVE -> list.sortedWith(
                tierComparator
                    .thenByDescending { it.lastActiveAt }
                    .thenBy { it.distanceMeters } // nice secondary for stable UI
            )
            SortMode.POPULAR -> list.sortedWith(
                tierComparator
                    .thenByDescending { it.totalLikes ?: 0 } // main: popularity
                    .thenBy { it.distanceMeters }            // tie-breaker 1
                    .thenByDescending { it.lastActiveAt }    // tie-breaker 2
            )
        }
    }.flowOn(Dispatchers.Default)

    fun setTier(isPlus: Boolean, isPremium: Boolean) {
        this.isPlus = isPlus
        this.isPremium = isPremium
    }

    fun setCurrentUserProfile(profile: Profile?) {
        currentProfile = profile
    }
    fun updateMapBootstrap(state: MapBootstrapState) {
        mapBootstrapState = state
        setTier(state.isPlus, state.isPremium)
    }

    fun hasLoadedExcludes(): Boolean = _hasLoadedExcludes

    private var geoQuery: GeoQuery? = null
    private val userCache = mutableMapOf<String, NearbyUser>()
    private val cacheTimestamps = mutableMapOf<String, Long>()
    private val cacheTtlMs = 5 * 60 * 1000L
    private val onlineThresholdMs = TimeUnit.MINUTES.toMillis(5)
    private val refreshWatchdogMs = 8000L
    internal fun isUserOnline(now: Long, lastActiveAt: Long): Boolean {
        return now - lastActiveAt < onlineThresholdMs
    }
    /**
     * Refresh the People tab:
     * - NEARBY: live GeoFire radius query (ignores allowLocationPublic/allowLocationForMatches).
     * - ACTIVE: we still use the same list and the UI filters/sorts by lastActiveHours.
     */
    fun refreshNearbyUsers(
        userId: String,
        center: LatLng,
        geoFireDatabaseRef: DatabaseReference,
        forceRefresh: Boolean = false,
        previousResults: Map<String, NearbyUser>? = null
    ) {
        hasAttemptedInitialLoad = true
        val filtersSnapshot = datingFilters
        val currentSortMode = sortMode
        val newKey = NearbyQueryKey(
            userId = userId,
            latBucket = (center.latitude * 10_000).roundToInt(),
            lngBucket = (center.longitude * 10_000).roundToInt(),
            radiusBucket = (radiusKm * 100).roundToInt(),
            filterHash = filtersSnapshot.hashCode(),
            sortMode = currentSortMode,
        )

        if (!forceRefresh && newKey == lastQueryKey) {
            if (previousResults != null && people.isEmpty()) {
                people.addAll(previousResults.values)
                markFirstResultIfNeeded()
            }
            pendingFetches = 0
            geoQueryCompleted = true
            isRefreshing = false
            return
        }
        lastQueryKey = newKey

        if (forceRefresh) invalidateCache()
        // Reset
        geoQuery?.removeAllListeners()
        geoQuery = null
        resetRefreshTracking()
        // Watchdog: never let the spinner spin forever
        viewModelScope.launch {
            delay(refreshWatchdogMs)
            if (isRefreshing && !geoQueryCompleted && pendingFetches == 0) {
                markQueryCompleted() // safely flips isRefreshing=false
            }
        }

        people.clear()
        hasLoadedFirstResult = false

        // Prefer prebuilt index (order-only, no filters). Fallback to old path if missing.
        val indexPath = when (currentSortMode) {
                SortMode.NEARBY -> "userFeedIndex/$userId/NEARBY"
                SortMode.ACTIVE -> "feedIndex/ACTIVE/list"
                else -> null
        }

        if (indexPath != null) {
                 markFetchStarted()
                 viewModelScope.launch {
                      try {
                           val rows = fetchFeedPage(
                                   uid = userId,
                                   mode = currentSortMode,
                                   startAfterKey = if (forceRefresh) null else lastIndexKey,
                                   pageSize = currentLimit
                                   )
                            // remember pagination anchor (last key we saw)
                           lastIndexKey = rows.lastOrNull()?.key
                           val users = hydrateProfiles(rows, center)
                           people.addAll(users)
                           markFirstResultIfNeeded()
                           markQueryCompleted()
                           return@launch
                      } catch (e: Exception) {
                           Log.w("MapScreenVM", "Index path missing or failed ($indexPath): ${e.message}")
                           // fall through to legacy behaviour below
                      } finally {
                           markFetchFinished()
                      }
                 }
        // If index attempt returns/throws quickly we’ll still have fallbacks below.
        }

        previousResults?.values
            ?.filter { prev ->
                prev.userId !in excludedUserIds && prev.latLng != null &&
                        distanceMeters(center, prev.latLng!!) <= radiusKm * 1000
            }
            ?.forEach { prev ->
                val loc = prev.latLng!!
                val distM = distanceMeters(center, loc)
                val updated = prev.copy(distanceMeters = distM)
                people.add(updated)
                markFirstResultIfNeeded()
                userCache[prev.userId] = updated
                cacheTimestamps[prev.userId] = System.currentTimeMillis()
            }

        val limit = currentLimit
        // LEGACY FALLBACKS (used only if index isn’t present or failed):
        if (currentSortMode == SortMode.ACTIVE) {
            markFetchStarted()
                 viewModelScope.launch {
                         try {
                                 val users = fetchActiveUsers(
                                         currentUserId = userId,
                                         center = center,
                                         geoFireDatabaseRef = geoFireDatabaseRef,
                                         limit = limit
                                        )
                                 people.addAll(users)
                                 markFirstResultIfNeeded()
                         } catch (e: Exception) {
                             Log.e("MapScreenVM", "Active users fetch failed: ${e.message}", e)
                         } finally {
                             markFetchFinished()
                             markQueryCompleted()
                         }
                 }
            return
        }

        // NEARBY legacy path (GeoFire live radius)
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
        _hasLoadedExcludes = true
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
            lastQueryKey = null
        } else {
            userCache.remove(userId)
            cacheTimestamps.remove(userId)
        }
    }
    private fun markFirstResultIfNeeded() {
        if (!hasLoadedFirstResult && people.isNotEmpty()) {
            hasLoadedFirstResult = true
        }
    }

    fun loadNextPage(
        increment: Int,
        userId: String,
        center: LatLng,
        geoFireDatabaseRef: DatabaseReference
    ) {
        hasAttemptedInitialLoad = true

        val prev = people.associateBy { it.userId }
        currentLimit += increment
        refreshNearbyUsers(
            userId = userId,
            center = center,
            geoFireDatabaseRef = geoFireDatabaseRef,
            forceRefresh = false,
            previousResults = prev
        )
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
                    markQueryCompleted()
                    if (!hasLoadedFirstResult) {
                               hasLoadedFirstResult = true
                           }
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
                val updated = cached.copy(
                    latLng = latLng,
                    distanceMeters = distM,
                    isOnline = isUserOnline(now, cached.lastActiveAt)
                )
                userCache[uid] = updated
                onEnterOrMove(updated)
                if (people.size >= limit) {
                    reachedLimit = true
                    query.removeAllListeners()
                    markQueryCompleted()
                }
                return
            }

            markFetchStarted()
            viewModelScope.launch {
                try {
                    val snapshot = FirebaseRefs.db.getReference("users").child(uid).get().await()
                    if (reachedLimit || people.size >= limit) {
                        if (!reachedLimit) {
                            reachedLimit = true
                            query.removeAllListeners()
                            markQueryCompleted()
                        }
                        return@launch
                    }
                    if (UserDeletionCache.isDeleted(FirebaseRefs.db, uid, snapshot)) {
                        onExit(uid)
                        return@launch
                    }
                    val p = snapshot.getValue(Profile::class.java) ?: run {
                        onExit(uid)
                        return@launch
                    }
                    if (p.isPrivate) {
                        onExit(uid)
                        return@launch
                    }

                    // No allowLocationPublic/allowLocationForMatches checks here
                    val usernameCandidate = resolveUsername(snapshot, p, uid)
                        ?: p.name.takeIf { it.isNotBlank() }
                    val username = usernameCandidate?.takeIf { it.isNotBlank() } ?: run {
                        onExit(uid)
                        return@launch
                    }
                    val age = calculateAge(p.dob)
                    if (!matchesFilters(p, age)) {
                        onExit(uid)
                        return@launch
                    }
                    val lastActive =
                        snapshot.child("lastActive").getValue(Long::class.java) ?: p.lastActive

                    val online = isUserOnline(now, lastActive)

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

                    val rolesForCard = if (p.showRolesOnProfile) p.roles else emptyList()
                    val tribesForCard = if (p.showTribesOnProfile) p.tribes else emptyList()
                    val kinksForCard = if (p.showKinksOnProfile) p.kinks else emptyList()
                    val totalLikes = likesCountFrom(snapshot, p)

                    val user = NearbyUser(
                        userId = uid,
                        username = username,
                        age = age,
                        gender = canonicalGender(p.gender),
                        photoUrl = p.profilepicUrl,
                        lastActiveAt = lastActive,
                        isOnline = online,
                        latLng = latLng,
                        distanceMeters = distM,
                        isPremium = p.isPremium,
                        isPlus = p.isPlus,
                        interests = p.interests,
                        roles = rolesForCard,
                        tribes = tribesForCard,
                        kinks = kinksForCard,
                        sexualOrientation = p.sexualOrientation,
                        compatibilityPct = compat,
                        randomDetail = randomDetail,
                        loveLanguage = p.loveLanguage,
                        socialCauses = p.socialCauses,
                        politics = p.politics,
                        totalLikes = totalLikes
                    )
                    userCache[uid] = user
                    cacheTimestamps[uid] = now
                    onEnterOrMove(user)
                    if (people.size >= limit) {
                        reachedLimit = true
                        query.removeAllListeners()
                        markQueryCompleted()
                    }
                } catch (err: Exception) {
                    Log.e("MapScreenVM", "User fetch cancelled $uid: ${err.message}")
                    onExit(uid)
                } finally {
                    markFetchFinished()
                }
            }
        }

        query.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) = buildUser(key, location)
            override fun onKeyExited(key: String) = onExit(key)
            override fun onKeyMoved(key: String, location: GeoLocation) = buildUser(key, location)
            override fun onGeoQueryReady() {
                markQueryCompleted()
                if (!hasLoadedFirstResult) {
                            hasLoadedFirstResult = true
                        }
            }
            override fun onGeoQueryError(error: DatabaseError) {
                Log.e("MapScreenVM", "GeoQuery error: ${error.message}")
                isRefreshing = false
                markQueryCompleted()
            }
        })
        return query
    }

    private suspend fun fetchActiveUsers(
        currentUserId: String,
        center: LatLng,
        geoFireDatabaseRef: DatabaseReference,
        limit: Int,
    ): List<NearbyUser> {
                val now = System.currentTimeMillis()
                val fetchCount = (limit * 4).coerceAtLeast(limit + 10)
                val snapshot = FirebaseRefs.db.getReference("users")
                    .orderByChild("lastActive")
                    .limitToLast(fetchCount)
                    .get()
                    .await()

                return withContext(Dispatchers.Default) {
                        val results = mutableListOf<NearbyUser>()
                        val seen = mutableSetOf<String>()

                        val children = snapshot.children.toList().asReversed()
                        for (child in children) {
                                if (results.size >= limit) break
                                val uid = child.key ?: continue
                                if (!seen.add(uid)) continue
                                if (uid == currentUserId) continue
                                if (uid in excludedUserIds) continue
                                if (UserDeletionCache.isDeleted(FirebaseRefs.db, uid, child)) continue

                                val profile = child.getValue(Profile::class.java) ?: continue
                                if (profile.isPrivate) continue

                                val usernameCandidate = resolveUsername(child, profile, uid)
                                    ?: profile.name.takeIf { it.isNotBlank() }
                                val username = usernameCandidate?.takeIf { it.isNotBlank() } ?: continue

                                val age = calculateAge(profile.dob)
                                if (!matchesFilters(profile, age)) continue

                                val lastActive = child.child("lastActive").getValue(Long::class.java) ?: profile.lastActive
                                val online = isUserOnline(now, lastActive)

                                // IO (geo read) stays suspend, but surrounding list/compat/distance math is on Default
                                val latLng = profileLatLng(profile) ?: fetchGeoLatLng(uid, geoFireDatabaseRef)
                                val distM = latLng?.let { distanceMeters(center, it) } ?: Double.POSITIVE_INFINITY

                                val compat = currentProfile?.let { cp ->
                                        val ageCompat = ageCompatibilityScore(calculateAge(cp.dob), age)
                                        val zodiacCompat = zodiacCompatibilityScore(cp.zodiac ?: "", profile.zodiac ?: "")
                                        (((ageCompat + zodiacCompat) / 2.0) * 100).roundToInt()
                                    }

                                val detailCandidates = buildList {
                                        add(profile.bio)
                                        add(profile.jobRole)
                                        add(profile.work)
                                        add(profile.college)
                                        add(profile.religion)
                                        add(profile.community)
                                        if (profile.allowLocationPublic) add(profile.hometown)
                                    }.filter { it.isNotBlank() }
                                val randomDetail = detailCandidates.randomOrNull()

                                val rolesForCard = if (profile.showRolesOnProfile) profile.roles else emptyList()
                                val tribesForCard = if (profile.showTribesOnProfile) profile.tribes else emptyList()
                                val kinksForCard = if (profile.showKinksOnProfile) profile.kinks else emptyList()
                            val totalLikes = likesCountFrom(child, profile)

                            val user = NearbyUser(
                                        userId = uid,
                                        username = username,
                                        age = age,
                                        gender = canonicalGender(profile.gender),
                                        photoUrl = profile.profilepicUrl,
                                        lastActiveAt = lastActive,
                                        isOnline = online,
                                        latLng = latLng,
                                        distanceMeters = distM,
                                        isPremium = profile.isPremium,
                                        isPlus = profile.isPlus,
                                        interests = profile.interests,
                                        roles = rolesForCard,
                                        tribes = tribesForCard,
                                        kinks = kinksForCard,
                                        sexualOrientation = profile.sexualOrientation,
                                        compatibilityPct = compat,
                                        randomDetail = randomDetail,
                                        loveLanguage = profile.loveLanguage,
                                        socialCauses = profile.socialCauses,
                                        politics = profile.politics,
                                        totalLikes = totalLikes
                                            )

                                results += user
                               userCache[uid] = user
                                cacheTimestamps[uid] = now
                            }
                        results
                    }
    }

    // -------- Server index helpers (order-only; filters stay client-side) --------
    data class FeedRow(val uid: String, val distanceM: Int? = null, val key: String)

    private suspend fun fetchFeedPage(
        uid: String,
        mode: SortMode,
        startAfterKey: String?,
        pageSize: Int
    ): List<FeedRow> = withContext(Dispatchers.IO) {
        val path = when (mode) {
            SortMode.NEARBY -> "userFeedIndex/$uid/NEARBY"
            SortMode.ACTIVE -> "feedIndex/ACTIVE/list"
            else -> return@withContext emptyList()
        }
        val ref = FirebaseRefs.db.getReference(path)
        val q = if (startAfterKey == null) {
            ref.limitToFirst(pageSize)
        } else {
            ref.orderByKey().startAfter(startAfterKey).limitToFirst(pageSize)
        }
        val snap = q.get().await()
        snap.children.map { c ->
            val otherUid = c.child("uid").getValue(String::class.java) ?: (c.key ?: "")
            val dist = c.child("distanceM").getValue(Int::class.java)
            FeedRow(uid = otherUid, distanceM = dist, key = c.key ?: otherUid)
        }
    }

    private suspend fun hydrateProfiles(
        rows: List<FeedRow>,
        center: LatLng?
    ): List<NearbyUser> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()

        // Build tasks for user nodes
        val tasks: List<com.google.android.gms.tasks.Task<DataSnapshot>> =
            rows.map { FirebaseRefs.db.getReference("users").child(it.uid).get() }

        // Await all tasks concurrently
        // ⬇️ Make types explicit so inference doesn’t fail
        val ds: List<DataSnapshot> = coroutineScope {
            val deferreds: List<Deferred<DataSnapshot>> =
                tasks.map { task: Task<DataSnapshot> ->
                    async<DataSnapshot>(Dispatchers.IO) { task.await() }
                }
            deferreds.awaitAll()
        }

        val results = mutableListOf<NearbyUser>()

        for ((idx, child) in ds.withIndex()) {
            val uid = child.key ?: continue
            if (uid in excludedUserIds) continue
            if (UserDeletionCache.isDeleted(FirebaseRefs.db, uid, child)) continue

            val p = child.getValue(Profile::class.java) ?: continue
            if (p.isPrivate) continue

            val usernameCandidate = resolveUsername(child, p, uid) ?: p.name.takeIf { it.isNotBlank() }
            val username = usernameCandidate?.takeIf { it.isNotBlank() } ?: continue

            val age = calculateAge(p.dob)
            // ⛔️ Client-side filtering only now — remove this:
            // if (!matchesFilters(p, age)) continue

            val lastActive = child.child("lastActive").getValue(Long::class.java) ?: p.lastActive
            val online = isUserOnline(now, lastActive)

            val latLng = profileLatLng(p)
            val distM: Double =
                rows.getOrNull(idx)?.distanceM?.toDouble()
                    ?: (if (center != null && latLng != null)
                        distanceMeters(center, latLng)
                    else Double.POSITIVE_INFINITY)

            val compat = currentProfile?.let { cp ->
                val ageCompat = ageCompatibilityScore(calculateAge(cp.dob), age)
                val zodiacCompat = zodiacCompatibilityScore(cp.zodiac ?: "", p.zodiac ?: "")
                (((ageCompat + zodiacCompat) / 2.0) * 100).roundToInt()
            }

            val detailCandidates = buildList {
                add(p.bio); add(p.jobRole); add(p.work); add(p.college); add(p.religion); add(p.community)
                if (p.allowLocationPublic) add(p.hometown)
            }.filter { it.isNotBlank() }
            val randomDetail = detailCandidates.randomOrNull()

            val rolesForCard = if (p.showRolesOnProfile) p.roles else emptyList()
            val tribesForCard = if (p.showTribesOnProfile) p.tribes else emptyList()
            val kinksForCard = if (p.showKinksOnProfile) p.kinks else emptyList()
            val totalLikes = likesCountFrom(child, p)

            val user = NearbyUser(
                userId = uid,
                username = username,
                age = age,
                gender = canonicalGender(p.gender),
                photoUrl = p.profilepicUrl,
                lastActiveAt = lastActive,
                isOnline = online,
                latLng = latLng,
                distanceMeters = distM,
                isPremium = p.isPremium,
                isPlus = p.isPlus,
                interests = p.interests,
                roles = rolesForCard,
                tribes = tribesForCard,
                kinks = kinksForCard,
                sexualOrientation = p.sexualOrientation,
                compatibilityPct = compat,
                randomDetail = randomDetail,
                loveLanguage = p.loveLanguage,
                socialCauses = p.socialCauses,
                politics = p.politics,
                totalLikes = totalLikes
            )

            results += user
            userCache[uid] = user
            cacheTimestamps[uid] = now
        }
        results
    }

    private suspend fun resolveUsername(
        snapshot: DataSnapshot,
        profile: Profile,
        uid: String,
    ): String? {
        val direct = profile.username.ifBlank {
            snapshot.child("username").getValue(String::class.java).orEmpty()
        }.trim()
        if (direct.isNotBlank()) return direct

        return try {
            val usernameSnap = FirebaseRefs.db.getReference("usernames")
                .orderByValue()
                .equalTo(uid)
                .limitToFirst(1)
                .get()
                .await()
            usernameSnap.children.firstOrNull()?.key?.trim()
        } catch (e: Exception) {
            Log.w("MapScreenVM", "Username lookup failed for $uid: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchGeoLatLng(
        uid: String,
        geoFireDatabaseRef: DatabaseReference,
    ): LatLng? {
        return try {
            val locSnap = geoFireDatabaseRef.child(uid).child("l").get().await()
            val lat = locSnap.child("0").getValue(Double::class.java)
            val lng = locSnap.child("1").getValue(Double::class.java)
            if (lat != null && lng != null) LatLng(lat, lng) else null
        } catch (e: Exception) {
            Log.w("MapScreenVM", "Geo lookup failed for $uid: ${e.message}", e)
            null
        }
    }

    private fun profileLatLng(profile: Profile): LatLng? {
        val lat = profile.latitude
        val lng = profile.longitude
        if (lat == 0.0 && lng == 0.0) return null
        if (!lat.isFinite() || !lng.isFinite()) return null
        if (abs(lat) < 0.0001 && abs(lng) < 0.0001) return null
        return LatLng(lat, lng)
    }

    private fun upsert(list: MutableList<NearbyUser>, item: NearbyUser) {
        val idx = list.indexOfFirst { it.userId == item.userId }
        if (idx >= 0) {
            list[idx] = item
        } else {
            list.add(item)
            markFirstResultIfNeeded()
        }
    }

    private fun matchesFilters(p: Profile, age: Int): Boolean {
        val f = datingFilters
        if (age < f.ageStart || age > f.ageEnd) return false
        val targetGender = canonicalGender(f.gender)
        if (targetGender.isNotBlank()) {
            val userGender = canonicalGender(p.gender)
            if (!matchesCanonicalGender(userGender, targetGender)) return false
        }
        val targetOrientation = canonicalOrientation(f.orientation)
        if (targetOrientation.isNotBlank()) {
            val userOrientation = canonicalOrientation(p.sexualOrientation)
            if (userOrientation != targetOrientation) return false
        }
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
        if (f.interests.isNotEmpty()) {
            val selected = f.interests.map { it.name.trim().lowercase(Locale.ROOT) }
            val userInts = p.interests.map { it.name.trim().lowercase(Locale.ROOT) }
            if (userInts.none { it in selected }) return false
        }
        return true
    }

    private fun matchesCanonicalGender(userGender: String, target: String): Boolean = when (target) {
        "male" -> userGender == "male"
        "female" -> userGender == "female"
        "other" -> userGender != "male" && userGender != "female"
        else -> userGender == target
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
