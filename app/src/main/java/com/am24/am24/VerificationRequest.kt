package com.am24.am24

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

// --- 0) Data model ---
data class VerificationRequest(
    val uid:       String? = null,  // the Firebase key()
    val photoUrl:  String? = null,  // stored under "photoUrl"
    val selfieUrl: String? = null   // stored under "selfieUrl"
)

// --- 1) ViewModel ---
class VerificationReviewViewModel : ViewModel() {
    private val verifRef = FirebaseRefs.db.getReference("verifications")
    private val usersRef = FirebaseRefs.db.getReference("users")

    private val _pending = MutableStateFlow<List<VerificationRequest>>(emptyList())
    val pending: StateFlow<List<VerificationRequest>> = _pending

    init {
        // Listen only for status == "pending"
        verifRef.orderByChild("status").equalTo("pending")
            .addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    _pending.value = snap.children.mapNotNull { c ->
                        val u = c.key ?: return@mapNotNull null
                        VerificationRequest(
                            uid       = u,
                            photoUrl  = c.child("photoUrl").getValue(String::class.java),
                            selfieUrl = c.child("selfieUrl").getValue(String::class.java)
                        )
                    }
                }
                override fun onCancelled(err: DatabaseError) {
                    Log.e("ReviewVM", "listen error: ${err.message}")
                }
            })
    }

    fun accept(uid: String) = viewModelScope.launch {
        try {
            verifRef.child(uid).child("status").setValue("accepted").await()
            usersRef.child(uid).child("isConsultantVerified").setValue(true).await()
        } catch (e: Exception) {
            Log.e("ReviewVM", "accept failed: ${e.message}")
        }
    }

    fun reject(uid: String) = viewModelScope.launch {
        try {
            verifRef.child(uid).child("status").setValue("rejected").await()
        } catch (e: Exception) {
            Log.e("ReviewVM", "reject failed: ${e.message}")
        }
    }
}

// --- 2) Top-level Composable with full-screen support ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationReviewScreen(
    viewModel: VerificationReviewViewModel = viewModel()
) {
    // 1) collect pending list
    val pending = viewModel.pending.collectAsState(initial = emptyList()).value

    // 2) track which image URL is expanded
    var expandedUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pending Verifications") })
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No pending verifications", color = Color.LightGray)
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(pending) { req ->
                    VerificationCard(
                        req          = req,
                        onImageClick = { url -> expandedUrl = url },
                        onAccept     = { viewModel.accept(req.uid ?: "") },
                        onReject     = { viewModel.reject(req.uid ?: "") }
                    )
                }
            }
        }
    }

    // 3) Full-screen dialog for expanded image
    expandedUrl?.let { url ->
        Dialog(onDismissRequest = { expandedUrl = null }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model               = url,
                    contentDescription  = null,
                    modifier            = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clickable { expandedUrl = null },
                    contentScale        = ContentScale.Fit
                )
            }
        }
    }
}

// --- 3) Card for each request ---
@Composable
fun VerificationCard(
    req: VerificationRequest,
    onImageClick: (String) -> Unit,
    onAccept:    () -> Unit,
    onReject:    () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "User: ${req.uid ?: "?"}",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerificationImage(
                    url        = req.photoUrl,
                    label      = "ID",
                    onClickUrl = onImageClick
                )
                VerificationImage(
                    url        = req.selfieUrl,
                    label      = "Selfie",
                    onClickUrl = onImageClick
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onAccept,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Text("Accept")
                }
                Button(
                    onClick = onReject,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Reject")
                }
            }
        }
    }
}

// --- 4) Image box with click handler ---
@Composable
fun VerificationImage(
    url:        String?,
    label:      String,
    onClickUrl: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (url != null) {
            AsyncImage(
                model               = url,
                contentDescription  = label,
                contentScale        = ContentScale.Crop,
                modifier            = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { onClickUrl(url) }
            )
        } else {
            Box(
                Modifier
                    .size(100.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text("no $label", color = Color.LightGray, fontSize = 12.sp)
            }
        }
        Text(label, color = Color.LightGray, fontSize = 12.sp)
    }
}