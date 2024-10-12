// KupidXApp.kt
package com.am24.am24

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.am24.am24.ui.theme.AppTheme

@Composable
fun KupidXApp() {
    AppTheme {
        val navController = rememberNavController()
        val auth = FirebaseAuth.getInstance()
        var isAuthenticated by remember { mutableStateOf(auth.currentUser != null) }

        // Observe authentication state
        DisposableEffect(auth) {
            val listener = FirebaseAuth.AuthStateListener { authInstance ->
                isAuthenticated = authInstance.currentUser != null
            }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        }

        if (isAuthenticated) {
            MainScreen(navController = navController)
        } else {
            AuthenticationNavGraph(navController = navController)
        }
    }
}
