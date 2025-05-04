// LeaderboardViewModel.kt
package com.am24.am24

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _leaderboard = MutableStateFlow<List<Profile>>(emptyList())
    val leaderboard: StateFlow<List<Profile>> = _leaderboard

    init {
        val usersRef = FirebaseRefs.db
            .getReference("users")
            .orderByChild("am24Ranking")
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    // map into Profile objects and reverse so highest rank first
                    val list = snapshot.children
                        .mapNotNull { it.getValue(Profile::class.java) }
                        .sortedByDescending { it.am24Ranking }
                    _leaderboard.value = list
                }
            }
            override fun onCancelled(error: DatabaseError) { /* handle if you like */ }
        })
    }
}
