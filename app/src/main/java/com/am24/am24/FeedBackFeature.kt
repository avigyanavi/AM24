package com.am24.am24

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/* ────────────────────────── data model ────────────────────────── */

data class Feedback(
    val id: String? = null,
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

/* ────────────────────────── view model ─────────────────────────── */

class FeedbackViewModel : ViewModel() {
    private val ref = FirebaseRefs.db.getReference("feedbacks")
    private val _feedbacks = MutableStateFlow<List<Feedback>>(emptyList())
    val feedbacks: StateFlow<List<Feedback>> = _feedbacks

    init {
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { c ->
                    val id = c.key ?: return@mapNotNull null
                    Feedback(
                        id        = id,
                        userId    = c.child("userId").getValue(String::class.java) ?: "",
                        text      = c.child("text").getValue(String::class.java) ?: "",
                        timestamp = c.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.timestamp }
                _feedbacks.value = list
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        })
    }
}

/* ─────────────────────────── helper ───────────────────────────── */

suspend fun submitFeedback(userId: String, text: String) {
    val ref = FirebaseRefs.db.getReference("feedbacks")
    val id = ref.push().key ?: return
    val data = mapOf(
        "userId" to userId,
        "text" to text,
        "timestamp" to System.currentTimeMillis()
    )
    ref.child(id).setValue(data).await()
}

/* ────────────────────────── screen ────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackListScreen(viewModel: FeedbackViewModel = viewModel()) {
    val feedbacks = viewModel.feedbacks.collectAsState(initial = emptyList()).value

    Scaffold(
        topBar = { TopAppBar(title = { Text("Feedbacks") }) }
    ) { padding ->
        if (feedbacks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No feedback yet", color = Color.LightGray) }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(feedbacks) { fb ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("User: ${fb.userId}", color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text(fb.text, color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}