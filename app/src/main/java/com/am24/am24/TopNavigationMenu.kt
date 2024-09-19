// TopNavigationMenu.kt

package com.am24.am24

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TopNavigationMenu(
    onNavigateToExplore: () -> Unit, // Now points to Media (Explore) page
    onNavigateToProfile: () -> Unit,
    onNavigateToBlog: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Replace "Create Post" with "Media" (Explore)
        Button(onClick = onNavigateToExplore) {
            Text(text = "Media")
        }
        Button(onClick = onNavigateToProfile) {
            Text(text = "Profile")
        }
        Button(onClick = onNavigateToBlog) {
            Text(text = "Blog")
        }
    }
}
