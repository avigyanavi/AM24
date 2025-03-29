// ----------------------------------
//  MainNavGraph.kt
// ----------------------------------
package com.am24.am24

import DatingViewModel
import EditPicAndVoiceBioScreen
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.am24.am24.profiles.GenericProfileScreen
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

// Initialize GeoFire instance globally
val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    postViewModel: PostViewModel
) {
    // Re-initialize postViewModel
    val postViewModel: PostViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

    // Initialize `datingViewModel`
    val datingViewModel: DatingViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

    // Initialize `profileViewModel`
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

    NavHost(
        navController = navController,
        startDestination = "dating",
        modifier = modifier
    ) {
        composable("dms") {
            DMScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("editProfile") {
            EditProfileScreen(navController = navController)
        }
        composable("create_post") {
            CreatePostScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/text") {
            TextPostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/voice") {
            VoicePostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("profile") {
            ProfileScreen(
                navController = navController,
                profileViewModel = profileViewModel,
                postViewModel = postViewModel
            )
        }
        composable("dating_screen?initialQuery={initialQuery}") { backStackEntry ->
            val initialQuery = backStackEntry.arguments?.getString("initialQuery") ?: ""
            DatingScreen(
                navController = navController,
                geoFire = geoFire,
                initialQuery = initialQuery
            )
        }
        composable("dating") {
            DatingScreen(navController = navController, geoFire = geoFire)
        }
        composable("editPicAndVoiceBio") {
            EditPicAndVoiceBioScreen(navController, profileViewModel)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("peopleWhoLikedMe") {
            PeopleWhoLikeMeScreen(navController = navController)
        }

        // ----- The AI route for KupidXChatScreen -----
        composable("aiProfile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            val chatAIViewModel: ChatAIViewModel = viewModel()
            val modelingState = chatAIViewModel.getModelingState(userId)
            val aiEnum = when (userId) {
                "zaraAi" -> AI.ZARA
                "kabirAi" -> AI.KABIR
                else -> return@composable
            }
            val avatarRes = when (aiEnum) {
                AI.ZARA -> R.drawable.zara_avatar
                AI.KABIR -> R.drawable.kabir_avatar
                else -> R.drawable.zara_avatar // Fallback
            }
            GenericProfileScreen(
                title = aiEnum.name,
                modelingState = modelingState,
                avatarRes = avatarRes,
                onNavigateBack = { navController.popBackStack() },
                ai = aiEnum,
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                messageCount = chatAIViewModel.getMessageCount(userId)
            )
        }
        composable("notifications") {
            NotificationsScreen(navController = navController)
        }
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            if (otherUserId != null) {
                ChatScreen(navController, otherUserId)
            }
        }
        composable("matchedUserProfile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            MatchedUserProfile(
                navController = navController,
                userId = userId,
                profileViewModel = profileViewModel
            )
        }
    }
}
