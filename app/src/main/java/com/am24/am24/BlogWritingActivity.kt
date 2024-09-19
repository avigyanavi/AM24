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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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

class BlogWritingActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var documentPickerLauncher: ActivityResultLauncher<String>
    private var mediaUrl: String? = null // Store uploaded document URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference
        storage = FirebaseStorage.getInstance()

        // Set up document picker launcher
        documentPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadDocument(it) }
        }
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            BlogWritingScreen(
                onSubmit = { blogTitle, blogContent ->
                    // Navigate to PostDetailsActivity and pass data
                    val intent = Intent(this, PostDetailsActivity::class.java).apply {
                        putExtra("postType", PostType.TEXT.name)
                        putExtra("title", blogTitle)
                        putExtra("content", blogContent)
                        putExtra("mediaUrl", mediaUrl) // Pass document URL if uploaded
                    }
                    startActivity(intent)
                    finish()
                },
                onUploadDocument = { launchDocumentPicker() } // Pass the function to upload a document
            )
        }
    }

    // Launch the document picker
    private fun launchDocumentPicker() {
        documentPickerLauncher.launch("*/*") // You can filter to specific file types here
    }

    // Upload document to Firebase Storage
    private fun uploadDocument(uri: Uri) {
        val userId: String = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("blog_documents/$userId/${UUID.randomUUID()}")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    mediaUrl = downloadUri.toString() // Store the document URL for later use
                    Toast.makeText(this, "Document uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Document upload failed", Toast.LENGTH_SHORT).show()
            }
    }
}


@Composable
fun BlogWritingScreen(
    onSubmit: (String, String) -> Unit,
    onUploadDocument: () -> Unit // Add the new parameter here
) {
    var blogTitle by remember { mutableStateOf("") }
    var blogContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Write your blog",
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Title Input
        OutlinedTextField(
            value = blogTitle,
            onValueChange = { blogTitle = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Blog Title") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Blog Content Input
        OutlinedTextField(
            value = blogContent,
            onValueChange = { blogContent = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            label = { Text("Blog Content") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Document Upload Button
        Button(onClick = onUploadDocument) {
            Text("Upload Document (Excel, Doc, PDF)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit button
        Button(
            onClick = {
                if (blogTitle.isNotEmpty() && blogContent.isNotEmpty()) {
                    onSubmit(blogTitle, blogContent)
                } else {
                    Toast.makeText(null, "Please fill out the title and content", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Blog")
        }
    }
}
