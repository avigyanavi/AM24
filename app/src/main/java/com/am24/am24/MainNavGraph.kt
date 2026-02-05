// ----------------------------------
//  MainNavGraph.kt
// ----------------------------------
package com.am24.am24

import DatingViewModel
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.am24.am24.billing.BillingScreen
import com.am24.am24.safePopBackStack
import com.am24.am24.ui.purchase.OneTimePurchaseScreen
import com.am24.am24.ui.purchase.PurchaseType
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

// Initialize GeoFire instance globally
val geoFire = GeoFire(FirebaseRefs.db.getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    currentUserId: String,               // NEW
    postViewModel: PostViewModel,
    profileViewModel: ProfileViewModel,
    nearbyViewModel: NearbyViewModel,
    datingViewModel: DatingViewModel,
    mainViewModel: MainViewModel,
    chatViewModel: ChatViewModel,
    aiPartnerViewModel: AIPartnerViewModel,
    locationManager: LocationManager
) {
    // Read in the current user's matches from Firebase
    val matchesSet = remember { mutableStateListOf<String>() }
    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        val matchesRef = FirebaseRefs.db.getReference("matches").child(currentUserId)
        matchesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                matchesSet.clear()
                for (child in snapshot.children) {
                    child.key?.let { matchesSet.add(it) }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error if needed
            }
        })
    }
    // Initialize LocationManager reference (already passed from Activity)
    val context = LocalContext.current
    val locationManagerRemembered = remember { locationManager }

    val geoFireDatabaseRef = FirebaseRefs.db.getReference("geoFireLocations")

    NavHost(
        navController = navController,
        startDestination = "map",
        modifier = modifier
    ) {
        composable("dms") {
            DMScreen(
                navController = navController,
                nearbyViewModel = nearbyViewModel,
                profileViewModel = profileViewModel,
                datingViewModel = datingViewModel
            )
        }
        composable("aiPartner") {
            AIPartnerScreen(
                navController = navController,
                aiPartnerViewModel = aiPartnerViewModel,
                profileViewModel = profileViewModel
            )
        }
        composable("leaderboard") {
            LeaderboardScreen(navController)
        }
        composable("home") {
            HomeScreen(
                navController = navController,
                postViewModel = postViewModel,
                profileViewModel = profileViewModel
            )
        }
        composable("explore") {
            ExploreScreen(
                postViewModel = postViewModel,
                profileViewModel = profileViewModel
            )
        }
        composable("create_post") {
            CreatePostScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("aiImageFull/{userId}/{timestamp}") { backStack ->
            val uid = backStack.arguments?.getString("userId")!!
            val ts = backStack.arguments?.getString("timestamp")!!.toLong()

            AIPartnerFullImageScreen(
                navController = navController,
                userId = uid,
                timestamp = ts
            )
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
                profileViewModel = profileViewModel
            )
        }
        composable("feedback_list") {
            val isAdmin by profileViewModel.isAdmin.collectAsState()
            if (isAdmin) {
                FeedbackListScreen()
            } else {
                LaunchedEffect(Unit) {
                    navController.safePopBackStack()
                    Toast.makeText(context, "Unauthorized", Toast.LENGTH_SHORT).show()
                }
            }
        }
        composable(
            route = "post/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            PostDetailScreen(
                navController = navController,
                postId = postId,
                commentId = null,
                postViewModel = postViewModel
            )
        }

        composable(
            route = "post/{postId}/comment/{commentId}",
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType },
                navArgument("commentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            val commentId = backStackEntry.arguments?.getString("commentId")
            PostDetailScreen(
                navController = navController,
                postId = postId,
                commentId = commentId,
                postViewModel = postViewModel
            )
        }
        composable("create_post/image") { ImagePostComposable(navController, postViewModel) }
        composable("create_post/video") { VideoPostComposable(navController, postViewModel) }
        composable(
            route = "subscription?allowIfSubscribed={allowIfSubscribed}&force={force}",
            arguments = listOf(
                navArgument("allowIfSubscribed") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("force") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val allowUpgrade =
                backStackEntry.arguments?.getBoolean("allowIfSubscribed") ?: false
            val force = backStackEntry.arguments?.getBoolean("force") ?: false
            SubscriptionScreen(navController, allowUpgrade, forceSubscription = force)
        }
        composable(
            route = "paywall?toast={toast}",
            arguments = listOf(
                navArgument("toast") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val toast = backStackEntry.arguments?.getString("toast")
            SubscriptionScreen(navController, toastMessage = toast)
        }
        composable("entryFeePlus") {
            EntryFeePlusScreen(navController)
        }
        composable(
            route = "billing?basePlanId={basePlanId}",
            arguments = listOf(
                navArgument("basePlanId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val basePlanId = backStackEntry.arguments?.getString("basePlanId")
            BillingScreen(selectedBasePlanId = basePlanId, navController = navController)
        }
        composable(
            route = "userPosts/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("userId") ?: return@composable
            UserPostsScreen(
                userId = uid,
                navController = navController,
                postViewModel = postViewModel,
                profileViewModel = profileViewModel
            )
        }
        composable("razorpay_web") {
            // rebuild your URL with redirect & callback
            val callback = Uri.encode("kupidx://payment_callback")
            val link = "https://rzp.io/rzp/Qkm9oLK?redirect=true&callback_url=$callback"
            RazorpayWebView(navController, razorpayUrl = link)
        }
        composable(
            route = "payment_callback?status={status}",
            arguments = listOf(navArgument("status") {
                type = NavType.StringType
            }),
            deepLinks = listOf(navDeepLink {
                uriPattern = "kupidx://payment_callback?razorpay_payment_link_status={status}"
            })
        ) { backStack ->
            val status = backStack.arguments?.getString("status") ?: "unknown"
            PaymentResultScreen(
                status = status,
                navController = navController
            )
        }
        composable("policies") {
            PoliciesScreen(navController)
        }
        composable("editPicAndVoiceBio") {
            EditPicAndVoiceBioScreen(navController, profileViewModel)
        }
        composable(
            route = "privateAlbum/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("userId") ?: return@composable
            PrivateAlbumScreen(
                navController = navController,
                userId = uid,
                isOwner = uid == currentUserId        // use global id
            )
        }
        composable("settings") {
            SettingsScreen(
                navController    = navController,
                profileViewModel = profileViewModel,
                currentUserId    = currentUserId
            )
        }
        composable("searchUsername") {
            UsernameSearchScreen(navController = navController)
        }
        composable("verifications_review") {
            val isAdmin by profileViewModel.isAdmin.collectAsState()
            if (isAdmin) {
                VerificationReviewScreen()
            } else {
                LaunchedEffect(Unit) {
                    navController.safePopBackStack()
                    Toast.makeText(context, "Unauthorized", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // new: compose a check-in feed screen, keyed by lat & lng
        composable(
            route = "checkinFeed/{placeId}",
            arguments = listOf(navArgument("placeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getString("placeId")
            if (placeId == null) {
                LaunchedEffect(Unit) {
                    navController.safePopBackStack()
                    Toast.makeText(context, "Missing placeId", Toast.LENGTH_SHORT).show()
                }
                return@composable
            }
            CheckInFeedScreen(
                placeId = placeId,
                navController = navController
            )
        }
        composable("peopleWhoLikedMe") {
            PeopleWhoLikeMeScreen(
                navController = navController,
                profileViewModel = profileViewModel,
                currentUserId    = currentUserId       // use param instead of default
            )
        }
        composable("upgradeLanding") {
            UpgradeLandingScreen(navController)
        }
        composable("govtIdVerification") {
            GovtIdVerificationScreen(
                navController = navController,
                profileViewModel = profileViewModel
            )
        }
        composable("manageSubscription") {
            ManageSubscriptionScreen(navController)
        }
        composable("notifications") {
            NotificationsScreen(navController = navController)
        }
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            if (otherUserId != null) {
                ChatScreen(
                    navController    = navController,
                    otherUserId      = otherUserId,
                    currentUserId    = currentUserId,   // NEW (if ChatScreen wants it)
                    profileViewModel = profileViewModel,
                    chatViewModel    = chatViewModel    // NEW
                )
            }
        }
        composable(
            route = "omegleChat/{chatId}/{otherUserId}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val otherId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            OmegleChatScreen(navController, chatId, otherId)
        }
        composable("omegleUsers") {
            OnlineUsersScreen(navController)
        }
        // 1) West Bengal top-level map
        composable("map") {
            MapScreen(
                userId = currentUserId,
                locationManager = locationManagerRemembered,
                geoFireDatabaseRef = geoFireDatabaseRef,
                navController = navController,
                onProfileMarkerClicked = { profileId ->
                    if (matchesSet.contains(profileId)) {
                        navController.navigate("matchedUserProfile/$profileId")
                    } else {
                        navController.navigate("previewUserProfile/$profileId")
                    }
                },
                nearbyViewModel = nearbyViewModel,
                profileViewModel = profileViewModel,
                datingViewModel = datingViewModel
            )
        }

        composable(
            route = "postVotes/{postId}/{voteType}",
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType },
                navArgument("voteType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPostId = backStackEntry.arguments?.getString("postId").orEmpty()
            val voteType = backStackEntry.arguments?.getString("voteType").orEmpty()
            val postId = Uri.decode(encodedPostId)
            if (postId.isBlank()) {
                LaunchedEffect(Unit) { navController.safePopBackStack() }
                return@composable
            }
            PostVoteUserListScreen(
                navController = navController,
                postId = postId,
                voteType = voteType
            )
        }
        composable(
            route = "groupChat/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupChatScreen(navController = navController, groupId = groupId)
        }
        composable("buyAiMessages") {
            OneTimePurchaseScreen(
                PurchaseType.AiMessages,
                navController
            ) { navController.safePopBackStack() }
        }

        composable("buySwipes") {
            OneTimePurchaseScreen(
                type = PurchaseType.Swipes,
                navController,
                onBack = { navController.safePopBackStack() }
            )
        }
        composable("buyBoosts") {
            OneTimePurchaseScreen(
                type = PurchaseType.Boosts,
                navController,
                onBack = { navController.safePopBackStack() }
            )
        }
        composable("buyCompliments") {
            OneTimePurchaseScreen(
                type = PurchaseType.Compliments,
                navController,
                onBack = { navController.safePopBackStack() }
            )
        }
        composable("buyBoosts") {
            OneTimePurchaseScreen(
                type = PurchaseType.Boosts,
                navController,
                onBack = { navController.safePopBackStack() }
            )
        }
        composable("saved_posts") {
            SavedPostsScreen(
                navController = navController,
                postViewModel = postViewModel
            )
        }

        composable(
            route = "matchedUserProfile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val matchedUserId = backStackEntry.arguments?.getString("userId") ?: return@composable
            var matchedProfile by remember { mutableStateOf<Profile?>(null) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(matchedUserId) {
                profileViewModel.fetchCurrentUserProfile() // Ensure current profile is fetched
                try {
                    println("Fetching profile for userId: $matchedUserId")
                    val snapshot = FirebaseRefs.db
                        .getReference("users")
                        .child(matchedUserId)
                        .get()
                        .await()
                    if (snapshot.exists()) {
                        val profile = snapshot.getValue(Profile::class.java)
                        println("Profile fetched: $profile")
                        matchedProfile = profile
                        println("matchedProfile updated to: $matchedProfile")
                        if (profile == null) {
                            errorMessage = "Profile data could not be parsed"
                        }
                    } else {
                        errorMessage = "No profile found for userId: $matchedUserId"
                    }
                } catch (e: Exception) {
                    errorMessage = "Error fetching profile: ${e.message}"
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (matchedProfile != null) {
                    println("Rendering MatchedUserProfileScreen for: ${matchedProfile?.userId}")
                    MatchedUserProfileScreen(
                        profile = matchedProfile!!,
                        geoFire = geoFire,
                        profileViewModel = profileViewModel,
                        navController = navController,
                        isMatch = true
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Text("Loading profile...", color = Color.White)
                }
            }
        }
        composable(
            route = "previewUserProfile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("userId").orEmpty()
            val targetId = Uri.decode(encoded)
            if (targetId.isBlank()) {
                LaunchedEffect(Unit) { navController.safePopBackStack() }
                return@composable
            }
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@composable

            PreviewUserProfileScreen(
                navController = navController,
                targetUserId = targetId,
                currentUserId = currentUid,
                geoFire = geoFire,
                profileViewModel = profileViewModel,
                datingViewModel  = datingViewModel
            )
        }
    }
}
