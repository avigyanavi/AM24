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
import java.util.UUID

// ─── NEW: data class for holding incoming compliment ───
data class ComplimentData(
    val text: String = "",
    val voiceUrl: String? = null,
    val timestamp: Long = 0L
) // ← NEW

class DatingViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DatingViewModel"
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")
    private val geoFire = GeoFire(database.getReference("geoFireLocations"))

    // StateFlows
    private val _allProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val allProfiles: StateFlow<List<Profile>> get() = _allProfiles

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
            updateBoostedUsers(me)
            loadCompliments(me) // ← NEW
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

            // write both trees
            complimentRef.setValue(complimentData)
            complimentReceivedRef.setValue(complimentData)

            // refresh what compliments we have received
            loadCompliments(senderId) // ← NEW

            // then perform your swipeRight/match logic
            handleSwipeRight(senderId, receiverId, profileViewModel)
        }
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

    /**  Re-builds _boostedUsers & _userDistanceMap  */
    private fun updateBoostedUsers(currentUserId: String) {
        viewModelScope.launch {
            val boosted = _allProfiles.value.filter { it.isBoosted }

            // distance look-ups in parallel
            val distPairs = boosted.mapNotNull { prof ->
                calculateDistance(currentUserId, prof.userId, geoFire)
                    ?.let { prof.userId to it }
            }.toMap()

            _userDistanceMap.value = distPairs
            _boostedUsers.value    =
                boosted.sortedBy { distPairs[it.userId] ?: Float.MAX_VALUE }
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