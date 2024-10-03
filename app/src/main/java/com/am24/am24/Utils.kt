package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.text.SimpleDateFormat
import java.util.*

//// Utility function to format timestamp
//fun formatTimestamp(timestamp: Long): String {
//    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
//    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST
//    return sdf.format(Date(timestamp))
//}

//// Composable Top Navigation Bar
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun TopNavBar(
//    title: String,
//    onProfileClick: () -> Unit,
//    onNotificationClick: () -> Unit
//) {
//    CenterAlignedTopAppBar(
//        title = {
//            Text(
//                text = title,
//                color = Color.White,
//                fontSize = 24.sp
//            )
//        },
//        navigationIcon = {
//            IconButton(onClick = onProfileClick) {
//                Icon(
//                    Icons.Default.Person,
//                    contentDescription = "Profile",
//                    tint = Color(0xFF00bf63)
//                )
//            }
//        },
//        actions = {
//            IconButton(onClick = onNotificationClick) {
//                Icon(
//                    Icons.Default.Favorite,
//                    contentDescription = "Notifications",
//                    tint = Color(0xFF00bf63)
//                )
//            }
//        },
//        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
//            containerColor = Color.Transparent
//        )
//    )
//}
//
//// Composable Bottom Navigation Bar
//@Composable
//fun BottomNavBar(
//    selectedIndex: Int = 1,
//    onItemSelected: (Int) -> Unit = {}
//) {
//    NavigationBar(
//        containerColor = Color.Black,
//        contentColor = Color.White
//    ) {
//        val items = listOf(
//            NavigationItem("DMs", Icons.Default.Chat),
//            NavigationItem("Feed", Icons.Default.Feed),
//            NavigationItem("Dating", Icons.Default.Favorite),
//            NavigationItem("Settings", Icons.Default.Settings)
//        )
//
//        items.forEachIndexed { index, item ->
//            NavigationBarItem(
//                icon = {
//                    Icon(
//                        imageVector = item.icon,
//                        contentDescription = item.label,
//                        tint = if (selectedIndex == index) Color(0xFF00bf63) else Color.White
//                    )
//                },
//                label = {
//                    Text(item.label, color = if (selectedIndex == index) Color(0xFF00bf63) else Color.White)
//                },
//                selected = selectedIndex == index,
//                onClick = { onItemSelected(index) }
//            )
//        }
//    }
//}
//
//data class NavigationItem(val label: String, val icon: ImageVector)
