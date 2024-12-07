import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.DatingFilterSettings
import com.am24.am24.Profile
import com.am24.am24.calculateAge
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

    // StateFlow for profiles fetched from Firebase
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> get() = _profiles

    // StateFlow for dating filters
    private val _datingFilters = MutableStateFlow(DatingFilterSettings())
    val datingFilters: StateFlow<DatingFilterSettings> get() = _datingFilters

    // A state to indicate if filters have been loaded
    private val _filtersLoaded = MutableStateFlow(false)
    val filtersLoaded: StateFlow<Boolean> get() = _filtersLoaded

    // Combine profiles and datingFilters to produce filteredProfiles
    val filteredProfiles: StateFlow<List<Profile>> = combine(_profiles, _datingFilters) { profiles, filters ->
        applyDatingFilters(profiles, filters)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Load all profiles from Firebase once
    fun loadProfiles(currentUserId: String) {
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allProfiles = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                val filteredOutSelf = allProfiles.filter { it.userId != currentUserId }
                viewModelScope.launch {
                    _profiles.value = filteredOutSelf
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load profiles: ${error.message}")
            }
        })
    }

    // Load dating filters from Firebase
    fun loadDatingFiltersFromFirebase(userId: String) {
        val userRef = usersRef.child(userId).child("datingFilters")
        userRef.get().addOnSuccessListener { snapshot ->
            val datingSettings = snapshot.getValue(DatingFilterSettings::class.java)
            if (datingSettings != null) {
                Log.d(TAG, "Loaded Dating Filters: $datingSettings")
                _datingFilters.value = datingSettings
            } else {
                Log.d(TAG, "No Dating Filters found for user.")
                // Use default dating filters if none found
                _datingFilters.value = DatingFilterSettings()
            }
            _filtersLoaded.value = true
        }.addOnFailureListener {
            Log.e(TAG, "Failed to load dating filters: ${it.message}")
            _filtersLoaded.value = true
        }
    }

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
        return result
    }
}
