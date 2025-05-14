// LeaderboardViewModel.kt
package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.FirebaseRefs.db
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Leaderboard screen: pulls all profiles once,
 * computes performance metrics & rankings, then exposes a `leaderboard` StateFlow.
 */
class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    // ─── raw & processed profiles ───────────────────────────────────
    private val _allProfiles = MutableStateFlow<List<Profile>>(emptyList())

    // ─── filter states ──────────────────────────────────────────────
    private val _genderFilter     = MutableStateFlow<String?>(null)
    private val _cityFilter       = MutableStateFlow<String?>(null)
    private val _localityFilter   = MutableStateFlow<String?>(null)
    private val _highSchoolFilter = MutableStateFlow<String?>(null)
    private val _collegeFilter    = MutableStateFlow<String?>(null)
    private val _minAgeFilter     = MutableStateFlow(18)
    private val _maxAgeFilter     = MutableStateFlow(30)
    private val _minCompositePct  = MutableStateFlow(0.0)
    val allProfiles: StateFlow<List<Profile>> = _allProfiles


    /** Filter setters */
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

    // combine string filters
    private val stringFilters = combine(
        _genderFilter, _cityFilter, _localityFilter,
        _highSchoolFilter, _collegeFilter
    ) { gender, city, locality, hs, col ->
        Filters(gender, city, locality, hs, col)
    }
    // combine numeric filters
    private val numericFilters = combine(
        _minAgeFilter, _maxAgeFilter, _minCompositePct
    ) { minAge, maxAge, minPct -> NumericFilters(minAge, maxAge, minPct) }

    /**
     * Exposed leaderboard: applies filters then returns sorted list.
     */
    val leaderboard: StateFlow<List<Profile>> = combine(
        _allProfiles, stringFilters, numericFilters
    ) { all, sf, nf ->
        var list = all.filterNot { it.userId.endsWith("Ai") }

        sf.gender?.takeIf(String::isNotBlank)?.let { g -> list = list.filter { it.gender == g }}
        sf.city?.takeIf(String::isNotBlank)?.let { c -> list = list.filter { it.city.equals(c, true) }}
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

        // final sort by composite score
        list.sortedByDescending { it.compositeScore }
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // fetch and compute metrics
        Log.d("LeaderboardVM", ">>> init LeaderboardViewModel")
        FirebaseDatabase.getInstance()
            .getReference("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("LeaderboardVM", "Using RTDB instance: $db")
                    Log.d("LeaderboardVM", "Root ref URL: ${db.reference.root}")
                    viewModelScope.launch {
                        // raw
                        Log.d("LeaderboardVM", "Got ${snapshot.childrenCount} users from Firebase")
                        val raw = snapshot.children
                            .mapNotNull { it.getValue(Profile::class.java) }
                        Log.d("LeaderboardVM", "Mapped to ${raw.size} Profile objects")

                        // compute various rank maps
                        val compRank   = raw.sortedByDescending { it.compositeScore }
                            .mapIndexed { i,p -> p.userId to i+1 }.toMap()
                        val ageRank    = raw.sortedByDescending { it.age }
                            .mapIndexed   { i,p -> p.userId to i+1 }.toMap()

                        // city grouping
                        val cityRankMap = raw.groupBy { it.city.ifBlank { "Other" } }
                            .flatMap { (_, grp) ->
                                grp.sortedByDescending { it.compositeScore }
                                    .mapIndexed { i,p -> p.userId to i+1 }
                            }.toMap()
                        // custom city
                        val customCityRankMap = raw.filter { it.city == "Other" }
                            .groupBy { it.customCity ?: "Other" }
                            .flatMap { (_, grp) ->
                                grp.sortedByDescending { it.compositeScore }
                                    .mapIndexed { i,p -> p.userId to i+1 }
                            }.toMap()

                        // hometown grouping
                        val homeRankMap = raw.groupBy { it.hometown.ifBlank { "Other" } }
                            .flatMap { (_, grp) ->
                                grp.sortedByDescending { it.compositeScore }
                                    .mapIndexed { i,p -> p.userId to i+1 }
                            }.toMap()
                        val customHomeRankMap = raw.filter { it.hometown == "Other" }
                            .groupBy { it.customHometown ?: "Other" }
                            .flatMap { (_, grp) ->
                                grp.sortedByDescending { it.compositeScore }
                                    .mapIndexed { i,p -> p.userId to i+1 }
                            }.toMap()

                        // high school grouping
                        val hsRankMap = raw.groupBy { it.highSchool.ifBlank { it.customHighSchool ?: "Other" } }
                            .flatMap { (_, grp) ->
                                grp.sortedByDescending { it.compositeScore }
                                    .mapIndexed { i,p -> p.userId to i+1 }
                            }.toMap()
                        // college grouping
                        val colRankMap = raw.groupBy { it.college.ifBlank { it.customCollege ?: "Other" } }
                            .flatMap { (_, grp) ->
                                grp.sortedByDescending { it.compositeScore }
                                    .mapIndexed { i,p -> p.userId to i+1 }
                            }.toMap()

                        // build final list with computed metrics
                        _allProfiles.value = raw.map { p ->
                            p.copy(
                                am24Ranking               = compRank[p.userId]             ?: 0,
                                am24RankingAge            = ageRank[p.userId]             ?: 0,
                                am24RankingCity           = cityRankMap[p.userId]         ?: 0,
                                am24RankingCustomCity     = customCityRankMap[p.userId]   ?: 0,
                                am24RankingHometown       = homeRankMap[p.userId]         ?: 0,
                                am24RankingCustomHometown = customHomeRankMap[p.userId]   ?: 0,
                                am24RankingHighSchool     = hsRankMap[p.userId]           ?: 0,
                                am24RankingCollege        = colRankMap[p.userId]          ?: 0,

                                averageSwipeRightsOnUser  = if (p.numberOfSwipeRights > 0)
                                    p.matchCount.toDouble() / p.numberOfSwipeRights
                                else 0.0,
                                matchCountPerSwipeRight   = if (p.numberOfSwipeRights > 0)
                                    p.matchCount.toDouble() / p.numberOfSwipeRights
                                else 0.0
                            )
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    // TODO: handle error
                }
            })
    }

    /** dynamic composite rank flow */
    fun compositeRankFor(userId: String): Flow<Int> =
        _allProfiles.map { list ->
            list.sortedByDescending { it.compositeScore }
                .indexOfFirst { it.userId == userId }
                .let { idx -> if (idx >= 0) idx + 1 else -1 }
        }.stateIn(viewModelScope, SharingStarted.Lazily, -1)
}

private data class Filters(
    val gender: String?,
    val city: String?,
    val locality: String?,
    val highSchool: String?,
    val college: String?
)

private data class NumericFilters(
    val minAge: Int,
    val maxAge: Int,
    val minCompositePct: Double
)