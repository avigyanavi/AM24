// LeaderboardViewModel.kt
package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Leaderboard screen: pulls all profiles once,
 * computes performance metrics & rankings, then exposes a `leaderboard` StateFlow.
 */
class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    // ─── raw & processed profiles ───────────────────────────────────
    private val _allProfiles = MutableStateFlow<List<Profile>>(emptyList())

    private var listener: ValueEventListener? = null
    private var usersRef: com.google.firebase.database.DatabaseReference? = null

    // ─── filter states ──────────────────────────────────────────────
    private val _countryFilter   = MutableStateFlow<String?>(null)
    private val _genderFilter     = MutableStateFlow<String?>(null)
    private val _cityFilter       = MutableStateFlow<String?>(null)
    private val _localityFilter   = MutableStateFlow<String?>(null)
    private val _highSchoolFilter = MutableStateFlow<String?>(null)
    private val _collegeFilter    = MutableStateFlow<String?>(null)
    private val _minAgeFilter     = MutableStateFlow(18)
    private val _maxAgeFilter     = MutableStateFlow(30)
    private val _minCompositePct  = MutableStateFlow(0.0)
    private val _minSwipeRights   = MutableStateFlow(0)

    val allProfiles: StateFlow<List<Profile>> = _allProfiles


    /** Filter setters */
    fun setCountryFilter(c: String?)    { _countryFilter.value    = c }
    fun setGenderFilter(g: String?)      { _genderFilter.value     = g }
    fun setCityFilter(c: String?)        { _cityFilter.value       = c }
    fun setLocalityFilter(l: String?)    { _localityFilter.value   = l }
    fun setHighSchoolFilter(h: String?)  { _highSchoolFilter.value = h }
    fun setCollegeFilter(c: String?)     { _collegeFilter.value    = c }
    fun setAgeRangeFilter(min: Int, max: Int) {
        _minAgeFilter.value = min
        _maxAgeFilter.value = max
    }
    fun setMinCompositePct(p: Double)    { _minCompositePct.value  = p }
    fun setMinSwipeRights(value: Int)    { _minSwipeRights.value   = value }

    // combine string filters
    private val stringFilters = combine(
        _genderFilter, _countryFilter, _cityFilter, _localityFilter,
        _highSchoolFilter, _collegeFilter
    ) { values ->                                   // values: Array<Any?>
    @Suppress("UNCHECKED_CAST")
    Filters(
    gender      = values[0] as String?,
    country     = values[1] as String?,
    city        = values[2] as String?,
    locality    = values[3] as String?,
    highSchool  = values[4] as String?,
    college     = values[5] as String?
    )
}
    // combine numeric filters
    private val numericFilters = combine(
        _minAgeFilter, _maxAgeFilter, _minCompositePct, _minSwipeRights
    ) { minAge, maxAge, minPct, minLikes ->
        NumericFilters(minAge, maxAge, minPct, minLikes)
    }

    /**
     * Exposed leaderboard: applies filters then returns sorted list.
     */
    val leaderboard: StateFlow<List<Profile>> = combine(
        _allProfiles, stringFilters, numericFilters
    ) { all, sf, nf ->
        var list = all.filterNot { it.userId.endsWith("Ai") }

        sf.gender?.takeIf(String::isNotBlank)?.let { g ->
            val gId = canonicalGenderRes(g)
            list = list.filter { prof ->
                val pId = canonicalGenderRes(prof.gender)
                when (pId) {
                    null -> true
                    R.string.male_option -> gId == R.string.male_option
                    R.string.female_option -> gId == R.string.female_option
                    else -> gId == R.string.male_option || gId == R.string.female_option || gId == R.string.gender_either
                }
            }
        }

        sf.country?.takeIf(String::isNotBlank)?.let { ct ->
            list = list.filter { it.country.sameCountry(ct) }
        }

        sf.city?.takeIf(String::isNotBlank)?.let { c ->
            list = list.filter { it.city.sameCity(c) }
        }
        sf.locality?.takeIf(String::isNotBlank)?.let { l ->
            list = list.filter {
                it.hometown.equals(l, true) || it.customHometown?.equals(l, true) == true
            }
        }
        sf.highSchool?.takeIf(String::isNotBlank)?.let { s ->
            list = list.filter {
                it.highSchool.equals(s, true) || it.customHighSchool?.equals(s, true) == true
            }
        }
        sf.college?.takeIf(String::isNotBlank)?.let { s ->
            list = list.filter {
                it.college.equals(s, true) || it.customCollege?.equals(s, true) == true
            }
        }

        // age range
        list = list.filter { it.age in nf.minAge..nf.maxAge }
        // composite score filter
        list = list.filter { it.compositeScorePct >= nf.minCompositePct }
        list = list.filter { it.numberOfSwipeRights >= nf.minSwipeRights }

        // final sort by composite score
        list.sortedByDescending { it.compositeScore }
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Listen to precomputed leaderboard entries instead of the full users node
        Log.d("LeaderboardVM", ">>> init LeaderboardViewModel")
        val ref = FirebaseRefs.db
            .getReference("leaderboard")
            listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    viewModelScope.launch {
                        val raw = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                        _allProfiles.value = raw.sortedBy { it.am24Ranking }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    // TODO: handle error
                }
            }
        ref.orderByChild("am24Ranking").limitToFirst(100).addValueEventListener(listener!!)
        usersRef = ref
    }


    override fun onCleared() {
        super.onCleared()
        listener?.let { l -> usersRef?.removeEventListener(l) }
        listener = null
        usersRef = null
    }
}

private data class Filters(
    val gender: String?,
    val country: String?,
    val city: String?,
    val locality: String?,
    val highSchool: String?,
    val college: String?
)

private data class NumericFilters(
    val minAge: Int,
    val maxAge: Int,
    val minCompositePct: Double,
    val minSwipeRights: Int
)