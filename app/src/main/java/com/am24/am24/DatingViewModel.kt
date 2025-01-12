import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.DatingFilterSettings
import com.am24.am24.Profile
import com.am24.am24.calculateAge
import com.am24.am24.calculateDistance
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    init {
        loadFilters()
    }

    /**
     * Load dating filters from Firebase
     */
    private fun loadFilters() {
        viewModelScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                val snapshot = usersRef.child(userId).child("datingFilters").get().await()
                snapshot.getValue(DatingFilterSettings::class.java)?.let {
                    _datingFilters.value = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filters: ${e.message}")
            }
        }
    }

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
     * Start real-time profile updates
     */
    fun startRealTimeProfileUpdates(currentUserId: String) {
        if (profilesListener == null) {
            profilesListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profiles = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                    _allProfiles.value = profiles.filter { it.userId != currentUserId }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error listening for profile updates: ${error.message}")
                }
            }
            usersRef.addValueEventListener(profilesListener!!)
        }
    }

    /**
     * Refresh profiles manually
     */
    fun refreshFilteredProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = usersRef.get().await()
                val profiles = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                _allProfiles.value = profiles.filter { it.userId != FirebaseAuth.getInstance().currentUser?.uid }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing profiles: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun applyDatingFilters(profiles: List<Profile>, filters: DatingFilterSettings): List<Profile> = coroutineScope {
        var result = profiles

        // Apply city filter
        if (filters.city.isNotEmpty() && filters.city != "All") {
            result = result.filter { profile ->
                profile.city?.equals(filters.city, ignoreCase = true) == true
            }
        }

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