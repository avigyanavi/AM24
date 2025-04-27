import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.DatingFilterSettings
import com.am24.am24.Profile
import com.am24.am24.ProfileViewModel
import com.am24.am24.calculateAge
import com.am24.am24.calculateDistance
import com.am24.am24.handleSwipeRight
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

// ─── NEW: data class for holding incoming compliment ───
data class ComplimentData(
    val text: String = "",
    val voiceUrl: String? = null,
    val timestamp: Long = 0L
) // ← NEW

class DatingViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val BOOST_DURATION_MS = 6 * 60 * 60 * 1000L
        internal const val COMPLIMENT_DAILY_QUOTA = 10
    }

    private val TAG = "DatingViewModel"
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")
    private val geoFire = GeoFire(database.getReference("geoFireLocations"))

    // StateFlows
    private val _allProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val allProfiles: StateFlow<List<Profile>> get() = _allProfiles

    private val _complimentsLeft = MutableStateFlow(COMPLIMENT_DAILY_QUOTA)
    val complimentsLeft: StateFlow<Int> get() = _complimentsLeft

    private val _datingFilters = MutableStateFlow(DatingFilterSettings())
    val datingFilters: StateFlow<DatingFilterSettings> get() = _datingFilters

    val filteredProfiles: StateFlow<List<Profile>> = combine(_allProfiles, _datingFilters) { profiles, filters ->
        applyDatingFilters(profiles, filters)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private var profilesListener: ValueEventListener? = null

    // ─── NEW: track compliments sent *to* me ─────────────
    private val _complimentsReceived = MutableStateFlow<Map<String, ComplimentData>>(emptyMap()) // ← NEW
    val complimentsReceived: StateFlow<Map<String, ComplimentData>> get() = _complimentsReceived // ← NEW


    private val _boostedUsers      = MutableStateFlow<List<Profile>>(emptyList())
    val boostedUsers: StateFlow<List<Profile>>      get() = _boostedUsers

    private val _userDistanceMap   = MutableStateFlow<Map<String, Float>>(emptyMap())
    val userDistanceMap: StateFlow<Map<String, Float>> get() = _userDistanceMap

    init {
        loadFilters()

        FirebaseAuth.getInstance().currentUser?.uid?.let { me ->
            // 1) first make sure we fetch today’s compliment quota ----------------
            viewModelScope.launch {
                _complimentsLeft.value = loadAndResetComplimentsDaily(me)
            }

            // the two lines you already had
            updateBoostedUsers(me)
            loadCompliments(me)
        }
    }

    /**
     * Load dating filters from Firebase
     */
    private fun loadFilters() {
        viewModelScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                val snapshot = usersRef.child(userId)
                    .child("datingFilters")
                    .get()
                    .await()
                snapshot.getValue(DatingFilterSettings::class.java)?.let {
                    _datingFilters.value = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filters: ${e.message}")
            }
        }
    }

    fun sendCompliment(
        receiverId: String,
        textMessage: String?,
        voiceUri: Uri?,
        profileViewModel: ProfileViewModel
    ) {
        viewModelScope.launch {
            val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val timestamp = System.currentTimeMillis()

            val complimentRef =
                database.getReference("compliments/$senderId/$receiverId")
            val complimentReceivedRef =
                database.getReference("complimentsReceived/$receiverId/$senderId")

            val complimentData = hashMapOf<String, Any>(
                "timestamp" to timestamp,
                "text" to textMessage.orEmpty()
            )

            // Upload voice if present
            if (voiceUri != null) {
                val storageRef = FirebaseStorage.getInstance()
                    .getReference("complimentsVoices/$senderId/${UUID.randomUUID()}.aac")
                val uploadResult = storageRef.putFile(voiceUri).await()
                val voiceUrl = uploadResult.storage.downloadUrl.await().toString()
                complimentData["voiceUrl"] = voiceUrl
            }

// write both trees ----------------------------------------------------------
            complimentRef.setValue(complimentData)
            complimentReceivedRef.setValue(complimentData)

            /* ▼▼▼ 2-d: burn one compliment quota & update the StateFlow ▼▼▼ */
            val leftNow = (_complimentsLeft.value - 1).coerceAtLeast(0)
            database.getReference("users/$senderId")
                .child("availableCompliments")
                .setValue(leftNow)
            _complimentsLeft.value = leftNow
            /* ▲▲▲ --------------------------------------------------------------------- */

            // refresh the map of compliments received (optional but nice)
            loadCompliments(senderId)

// continue with your existing swipe-right logic
            handleSwipeRight(senderId, receiverId, profileViewModel)
        }
    }
    suspend fun loadAndResetComplimentsDaily(userId: String): Int {
        val ref   = database.getReference("users/$userId")
        val snap  = ref.get().await()

        var left  = snap.child("availableCompliments")
            .getValue(Int::class.java) ?: COMPLIMENT_DAILY_QUOTA
        var day   = snap.child("lastComplimentResetDayOfYear")
            .getValue(Int::class.java) ?: -1

        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != day) {
            left = COMPLIMENT_DAILY_QUOTA
            day  = today
            ref.child("availableCompliments").setValue(left)
            ref.child("lastComplimentResetDayOfYear").setValue(day)
        }
        return left
    }

    // ─── NEW: load complimentsReceived/$me into _complimentsReceived ───────────
    private fun loadCompliments(receiverId: String) { // ← NEW
        viewModelScope.launch {
            try {
                val snap = database
                    .getReference("complimentsReceived/$receiverId")
                    .get()
                    .await()

                val map = snap.children.associate { child ->
                    val fromId = child.key!!
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val text = child.child("text").getValue(String::class.java).orEmpty()
                    val voice = child.child("voiceUrl").getValue(String::class.java)
                    fromId to ComplimentData(text = text, voiceUrl = voice, timestamp = ts)
                }
                _complimentsReceived.value = map
            } catch (e: Exception) {
                Log.e(TAG, "Error loading compliments: ${e.message}")
            }
        }
    } // ← NEW

    /**
     * Update and save filters in Firebase
     */
    fun updateDatingFilters(updatedFilters: DatingFilterSettings) {
        viewModelScope.launch {
            _datingFilters.value = updatedFilters
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                usersRef.child(userId).child("datingFilters").setValue(updatedFilters).await()
                refreshFilteredProfiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating filters: ${e.message}")
            }
        }
    }

    /**
     * Refresh profiles manually
     */
    fun refreshFilteredProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val me = FirebaseAuth.getInstance().currentUser?.uid
            try {
                val snapshot = usersRef.get().await()
                val profiles = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                _allProfiles.value = profiles.filter { it.userId != me }
                me?.let {
                    updateBoostedUsers(it)
                    loadCompliments(it) // ← NEW: also re-load when refreshing
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing profiles: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** call this when the user presses “Boost” */
    fun boostUser(
        targetUserId: String,
        onFinished: () -> Unit = {}
    ) {
        val me = FirebaseAuth.getInstance().uid ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            // 1) decrement my boost-count and record my lastBoostTimestamp
            usersRef.child(me).apply {
                child("availableBoosts").get().await().getValue(Long::class.java)?.let { current ->
                    child("availableBoosts").setValue((current - 1).coerceAtLeast(0L)).await()
                }
                child("lastBoostTimestamp").setValue(now).await()
            }

            // 2) set the other user’s isBoosted + boostedAt
            usersRef.child(targetUserId).apply {
                child("isBoosted").setValue(true).await()
                child("boostedAt").setValue(now).await()
            }

            // 3) refresh your boosted list
            updateBoostedUsers(me)
            refreshFilteredProfiles()               // <-- add this
            onFinished()
        }
    }

    /** rebuilds `_boostedUsers`, dropping any >6h old */
    private fun updateBoostedUsers(currentUserId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // clean out expired boosts in Firebase too (optional)
            _allProfiles.value
                .filter { it.isBoosted && (it.boostedAt == null || now - it.boostedAt!! > BOOST_DURATION_MS) }
                .forEach {
                    usersRef.child(it.userId).child("isBoosted").setValue(false)
                }

            // only keep the fresh ones
            val boosted = _allProfiles.value.filter {
                it.isBoosted && it.boostedAt != null && now - it.boostedAt!! <= BOOST_DURATION_MS
            }

            // compute distances…
            val distMap = boosted.mapNotNull { p ->
                calculateDistance(currentUserId, p.userId, geoFire)
                    ?.let { p.userId to it }
            }.toMap()

            _userDistanceMap.value = distMap
            _boostedUsers.value    = boosted.sortedBy { distMap[it.userId] ?: Float.MAX_VALUE }
        }
    }

    private suspend fun applyDatingFilters(profiles: List<Profile>, filters: DatingFilterSettings): List<Profile> = coroutineScope {
        var result = profiles

        // Apply localities filter
        if (filters.localities.isNotEmpty()) {
            result = result.filter { profile ->
                filters.localities.contains(profile.hometown)
            }
        }

        // Apply high school filter
        if (filters.highSchool.isNotBlank()) {
            result = result.filter { profile ->
                profile.highSchool?.equals(filters.highSchool, ignoreCase = true) == true
            }
        }

        // Apply college filter
        if (filters.college.isNotBlank()) {
            result = result.filter { profile ->
                profile.college?.equals(filters.college, ignoreCase = true) == true
            }
        }

        // Apply post-grad filter
        if (filters.postGrad.isNotBlank()) {
            result = result.filter { profile ->
                profile.postGraduation?.equals(filters.postGrad, ignoreCase = true) == true
            }
        }

        // Apply work filter
        if (filters.work.isNotBlank()) {
            result = result.filter { profile ->
                profile.work?.equals(filters.work, ignoreCase = true) == true
            }
        }

        // Apply age range filter
        result = result.filter { profile ->
            val age = profile.dob?.let { calculateAge(it) }
            age != null && age in filters.ageStart..filters.ageEnd
        }

        // Apply rating filter
        if (filters.rating.isNotBlank()) {
            val ratingRange = when (filters.rating) {
                "0-1.9" -> 0.0..1.9
                "2-3.9" -> 2.0..3.9
                "4-5" -> 4.0..5.0
                else -> 0.0..5.0
            }
            result = result.filter { profile ->
                profile.averageRating in ratingRange
            }
        }

        // Apply gender filter
        if (filters.gender.isNotBlank()) {
            val genders = filters.gender.split(",").map { it.trim() }
            result = result.filter { profile ->
                profile.gender?.let { genders.contains(it) } == true
            }
        }

        // Apply distance filter
        if (filters.distance < 100) {
            result = filterByDistance(result, filters.distance)
        }

        return@coroutineScope result
    }

    private suspend fun filterByDistance(profiles: List<Profile>, maxDistance: Int): List<Profile> = coroutineScope {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@coroutineScope profiles
        val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geoFireLocations"))

        val filteredProfiles = mutableListOf<Profile>()

        profiles.forEach { profile ->
            launch {
                val distance = calculateDistance(currentUserId, profile.userId, geoFire)
                if (distance != null && distance <= maxDistance) {
                    synchronized(filteredProfiles) {
                        filteredProfiles.add(profile)
                    }
                }
            }
        }
        return@coroutineScope filteredProfiles
    }


    override fun onCleared() {
        super.onCleared()
        profilesListener?.let { usersRef.removeEventListener(it) }
    }
}