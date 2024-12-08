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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DatingViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DatingViewModel"
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")

    // StateFlow for all profiles (unfiltered)
    private val _allProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val allProfiles: StateFlow<List<Profile>> get() = _allProfiles

    // StateFlow for profiles fetched from Firebase
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> get() = _profiles

    // StateFlow for dating filters
    private val _datingFilters = MutableStateFlow(DatingFilterSettings())
    val datingFilters: StateFlow<DatingFilterSettings> get() = _datingFilters

    // A state to indicate if filters have been loaded
    private val _filtersLoaded = MutableStateFlow(false)
    val filtersLoaded: StateFlow<Boolean> get() = _filtersLoaded

    private val _refreshTrigger = MutableStateFlow(false)
    val refreshTrigger: StateFlow<Boolean> get() = _refreshTrigger

    // Combine profiles and datingFilters to produce filteredProfiles
    val filteredProfiles: StateFlow<List<Profile>> = combine(_profiles, _datingFilters) { profiles, filters ->
        applyDatingFilters(profiles, filters)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun startRealTimeProfileUpdates(currentUserId: String) {
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allProfiles = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                val filteredOutSelf = allProfiles.filter { it.userId != currentUserId }
                viewModelScope.launch {
                    _allProfiles.value = filteredOutSelf // Update allProfiles
                    _profiles.value = filteredOutSelf // Update profiles
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DatingViewModel", "Failed to listen for profile updates: ${error.message}")
            }
        })
    }


    fun startRealTimeFilterUpdates(userId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("datingFilters")
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val datingFilters = snapshot.getValue(DatingFilterSettings::class.java)
                viewModelScope.launch {
                    if (datingFilters != null) {
                        _datingFilters.value = datingFilters
                    } else {
                        _datingFilters.value = DatingFilterSettings() // Set default filters
                    }
                    // Set filtersLoaded to true after initial load
                    _filtersLoaded.value = true
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to listen for filter updates: ${error.message}")
                // Even on failure, set filtersLoaded to true to avoid infinite loading
                viewModelScope.launch {
                    _filtersLoaded.value = true
                }
            }
        })
    }

    // Apply dating filters to profiles
// Apply dating filters to profiles
    private fun applyDatingFilters(profiles: List<Profile>, filters: DatingFilterSettings): List<Profile> {
        var result = profiles

        // Apply city filter
        if (filters.city.isNotEmpty() && filters.city != "All") {
            result = result.filter { it.city.equals(filters.city, ignoreCase = true) }
        }

        // Apply localities filter
        if (filters.localities.isNotEmpty()) {
            result = result.filter { profile ->
                filters.localities.contains(profile.locality)
            }
        }

        // Apply high school filter
        if (filters.highSchool.isNotBlank()) {
            result = result.filter { it.highSchool.equals(filters.highSchool, ignoreCase = true) }
        }

        // Apply college filter
        if (filters.college.isNotBlank()) {
            result = result.filter { it.college.equals(filters.college, ignoreCase = true) }
        }

        // Apply postGrad filter
        if (filters.postGrad.isNotBlank()) {
            result = result.filter { (it.postGraduation ?: "").equals(filters.postGrad, ignoreCase = true) }
        }

        // Apply work filter
        if (filters.work.isNotBlank()) {
            result = result.filter { it.work.equals(filters.work, ignoreCase = true) }
        }

        // Apply age range filter
        if (filters.ageStart != 0 && filters.ageEnd != 0) {
            result = result.filter { profile ->
                val age = calculateAge(profile.dob)
                age != null && age in filters.ageStart..filters.ageEnd
            }
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
            result = result.filter { it.gender.equals(filters.gender, ignoreCase = true) }
        }

        // Apply distance filter
        if (filters.distance < 100) {
            result = filterByDistance(result, filters.distance)
        }

        return result
    }

    // Distance filtering logic
    private fun filterByDistance(profiles: List<Profile>, maxDistance: Int): List<Profile> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return profiles
        val geoFire = GeoFire(database.getReference("geoFireLocations"))

        val filteredProfiles = mutableListOf<Profile>()

        viewModelScope.launch {
            profiles.map { profile ->
                async {
                    val distance = calculateDistance(currentUserId, profile.userId, geoFire)
                    if (distance != null && distance <= maxDistance) {
                        synchronized(filteredProfiles) {
                            filteredProfiles.add(profile)
                        }
                    }
                }
            }.awaitAll()
        }

        return filteredProfiles
    }

}