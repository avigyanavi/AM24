package com.am24.am24

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        val prefs        = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        lifecycleScope.launch(Dispatchers.IO) {
            val dbRef       = FirebaseDatabase.getInstance().reference
            val currentUser = auth.currentUser

            val nextIntent = when {
                // ── Not signed in at all ──
                currentUser == null -> {
                    Intent(this@MainActivity, LandingActivity::class.java)
                }

                else -> {
                    val uid      = currentUser.uid
                    // 1) Check if they've ever set a username
                    val usernameSnap = dbRef
                        .child("users")
                        .child(uid)
                        .child("username")
                        .get()
                        .await()
                    val username = usernameSnap.getValue(String::class.java)

                    if (username.isNullOrBlank()) {
                        // ── Never chose a username: send back into registration at step 11
                        Intent(this@MainActivity, RegistrationActivity::class.java)
                            .putExtra("initialStep", 11)
                    } else {
                        // 2) If they did, make sure registrationFinished == true
                        val finished = dbRef
                            .child("publicUsers")
                            .child(username)
                            .child("registrationFinished")
                            .get()
                            .await()
                            .getValue(Boolean::class.java) ?: false

                        if (finished) {
                            // Fully registered → main app
                            Intent(this@MainActivity, KupidXAppActivity::class.java)
                        } else {
                            // Username exists but not marked finished → back to username screen
                            Intent(this@MainActivity, RegistrationActivity::class.java)
                                .putExtra("initialStep", 11)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                startActivity(nextIntent)
                finish()
            }
        }
    }
}
