package com.am24.am24

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityMenuScreen(
    navController: NavHostController,
    cityName: String
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("City Menu: $cityName") })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {
                    // 1) city-level group chat
                    navController.navigate("cityGroupChat/$cityName")
                }) {
                    Text("View City Group Chat")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    // 2) city-level map with bounding box
                    navController.navigate("cityMap/$cityName")
                }) {
                    Text("View Neighborhood Map")
                }
            }
        }
    }
}
