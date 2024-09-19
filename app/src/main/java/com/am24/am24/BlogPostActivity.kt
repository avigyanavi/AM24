package com.am24.am24

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class BlogPostActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var documentPickerLauncher: ActivityResultLauncher<String>
    private var mediaUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference
        storage = FirebaseStorage.getInstance()

        documentPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadDocument(it) }
        }
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            BlogWritingScreen(
                onSubmit = { blogTitle, blogContent ->
                    val intent = Intent(this, PostDetailsActivity::class.java).apply {
                        putExtra("mediaUrl", mediaUrl)  // Pass document URL if available
                        putExtra("postType", PostType.TEXT.name)
                        putExtra("title", blogTitle)  // Pass blog title
                        putExtra("content", blogContent)  // Pass blog content
                    }
                    startActivity(intent)
                    finish()
                },
                onUploadDocument = { launchDocumentPicker() }
            )
        }
    }

    private fun launchDocumentPicker() {
        // Launch document picker for any document type (PDF, DOC, DOCX, etc.)
        documentPickerLauncher.launch("application/*")
    }

    private fun uploadDocument(uri: Uri) {
        val userId: String = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("blog_posts/$userId/${UUID.randomUUID()}")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Store the document URL for later use in the blog post
                    mediaUrl = downloadUri.toString()
                    Toast.makeText(this, "Document uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Document upload failed", Toast.LENGTH_SHORT).show()
            }
    }
}
