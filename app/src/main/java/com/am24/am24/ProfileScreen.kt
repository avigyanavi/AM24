@file:OptIn(
    ExperimentalMaterial3Api::class,
)
package com.am24.am24

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import coil.request.ImageRequest
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import com.am24.am24.util.CachedFullscreenVideoPlayer
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

// ── 1) replace the existing `needsVerification` val with a mutable state ─────────
    val user                 = FirebaseAuth.getInstance().currentUser
    val isPwdUser            = user?.providerData?.any { it.providerId == "password" } == true
//    var needsVerification by remember {         // ← make it mutable
//        mutableStateOf(isPwdUser && user?.isEmailVerified == false)
//    }
    var needsVerification = false

// ── 2) keep the rest of your state ----------------------------------------------
    var showVerifyDialog by remember { mutableStateOf(false) }
    var isSendingEmail   by remember { mutableStateOf(false) }

    // —— your existing state & loading logic ——
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Fetch profile on screen entry
    LaunchedEffect(currentUserId) {
        Log.d("ProfileScreen", "Fetching profile for userId: $currentUserId")
        profileViewModel.fetchCurrentUserProfile()
    }

    if (!filtersLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

    val allPosts by postViewModel.filteredPosts.collectAsState()
    val myPosts = allPosts.filter { it.userId == currentUserId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }
    val featuredPosts = sortedByUpvotes.take(5)
    val remainingPosts = sortedByUpvotes.drop(5)

    when {
        currentUserProfile == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.profile_loading), color = Color.White)
            }
        }
        else -> {
            ProfileLazyScreen(
                navController    = navController,
                profile          = currentUserProfile!!,
                featuredPosts = featuredPosts,
                remainingPosts = remainingPosts,
                profileViewModel = profileViewModel
            )

            // —— 3) if they need to verify, intercept all taps ——
            // ── 3) intercept taps only while we still need verification ---------------------
            if (needsVerification) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { showVerifyDialog = true } }
                )
            }

            // ── 4) revamped AlertDialog ------------------------------------------------------
            if (showVerifyDialog) {
                AlertDialog(
                    onDismissRequest = { showVerifyDialog = false },
                    backgroundColor  = Color(0xFF1A1A1A),
                    contentColor     = Color.White,
                    title  = { Text("Verify Email", fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00)) },
                    text   = { Text("Please verify your email address to use the app.") },

                    /** ------------- BUTTON ROW ------------- **/
                    buttons = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {

                            // a) “Resend link”
                            TextButton(
                                onClick = {
                                    isSendingEmail = true
                                    coroutineScope.launch {
                                        try {
                                            user?.sendEmailVerification()?.await()
                                            Toast.makeText(
                                                context,
                                                "Verification email sent!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Error sending email",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        isSendingEmail = false
                                    }
                                },
                                enabled = !isSendingEmail
                            ) {
                                if (isSendingEmail) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Resend link", color = Color(0xFFFF6F00))
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            // b) “I’ve verified”  ← NEW
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            // ① wait for the network call to finish
                                            user?.reload()?.await()      // <-- suspend until done  ✅

                                            // ② THEN read the fresh auth object
                                            val refreshedUser = FirebaseAuth.getInstance().currentUser
                                            if (refreshedUser?.isEmailVerified == true) {
                                                Toast.makeText(
                                                    context,
                                                    "Email verified – enjoy the app!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                needsVerification = false
                                                showVerifyDialog  = false
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Still not verified — please confirm the link first.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Error checking verification",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) { Text("I’ve verified", color = Color(0xFFFF6F00)) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileLazyScreen(
    navController: NavController,
    profile: Profile,
    featuredPosts: List<Post>,
    remainingPosts: List<Post>,
    profileViewModel: ProfileViewModel,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showPostsOverlay by remember { mutableStateOf(false) }
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()

    // Use ViewModel's profile if available, otherwise fall back to initial profile
    var currentProfile by remember { mutableStateOf(profile) }

    val isPremium by profileViewModel.isPremium.collectAsState(false)
    val leaderboardVm: LeaderboardViewModel? =
        if (isPremium) viewModel() else null
    val allProfilesState = leaderboardVm?.allProfiles?.collectAsState(emptyList())
    val allProfiles = allProfilesState?.value ?: emptyList()

    // whenever our currentProfile changes, compute all the ranks
    var displayProfile by remember { mutableStateOf(profile) }
    // Sync with ViewModel's profile
    LaunchedEffect(currentUserProfile) {
        currentUserProfile?.let { updatedProfile ->
            Log.d("ProfileLazyScreen", "Updating currentProfile with isMatrimonyMode: ${updatedProfile.isMatrimonyMode}")
            currentProfile = updatedProfile
        }
    }
    LaunchedEffect(profile, allProfiles, leaderboardVm) {
        if (leaderboardVm == null) {
            displayProfile = profile
            return@LaunchedEffect
        }
        // 1) global composite rank
        val sortedByComposite = allProfiles.sortedByDescending { it.compositeScore }
        val globalIdx = sortedByComposite.indexOfFirst { it.userId == profile.userId }
        val globalRank = if (globalIdx >= 0) globalIdx + 1 else -1

        // 2) city-level rank
        val cityList = allProfiles
            .filter {
                it.city.equals(profile.city, ignoreCase = true) ||
                        it.customCity.equals(profile.city, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val cityIdx = cityList.indexOfFirst { it.userId == profile.userId }
        val cityRank = if (cityIdx >= 0) cityIdx + 1 else -1

        // 3) custom-city (if city=="Other")
        val customCityList = allProfiles
            .filter { it.customCity.equals(profile.customCity, ignoreCase = true) }
            .sortedByDescending { it.compositeScore }
        val customCityIdx = customCityList.indexOfFirst { it.userId == profile.userId }
        val customCityRank = if (customCityIdx >= 0) customCityIdx + 1 else -1

        // 4) hometown/locality
        val hoodList = allProfiles
            .filter {
                it.hometown.equals(profile.hometown, ignoreCase = true) ||
                        it.customHometown.equals(profile.hometown, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val hoodIdx = hoodList.indexOfFirst { it.userId == profile.userId }
        val hoodRank = if (hoodIdx >= 0) hoodIdx + 1 else -1

        // 5) custom-hood
        val customHoodList = allProfiles
            .filter { it.customHometown.equals(profile.customHometown, ignoreCase = true) }
            .sortedByDescending { it.compositeScore }
        val customHoodIdx = customHoodList.indexOfFirst { it.userId == profile.userId }
        val customHoodRank = if (customHoodIdx >= 0) customHoodIdx + 1 else -1

        // 6) age-ranking (youngest=1? or oldest=1? adjust comparator if you want)
        val ageList = allProfiles.sortedBy { it.age }
        val ageIdx = ageList.indexOfFirst { it.userId == profile.userId }
        val ageRank = if (ageIdx >= 0) ageIdx + 1 else -1

        // 7) high-school
        val hsList = allProfiles
            .filter {
                it.highSchool.equals(profile.highSchool, ignoreCase = true) ||
                        it.customHighSchool.equals(profile.highSchool, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val hsIdx = hsList.indexOfFirst { it.userId == profile.userId }
        val hsRank = if (hsIdx >= 0) hsIdx + 1 else -1

        // 8) college
        val collList = allProfiles
            .filter {
                it.college.equals(profile.college, ignoreCase = true) ||
                        it.customCollege.equals(profile.college, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val collIdx = collList.indexOfFirst { it.userId == profile.userId }
        val collRank = if (collIdx >= 0) collIdx + 1 else -1

        displayProfile = profile.copy(
            am24Ranking                = globalRank,
            am24RankingCity            = cityRank,
            am24RankingCustomCity      = customCityRank,
            am24RankingHometown        = hoodRank,
            am24RankingCustomHometown  = customHoodRank,
            am24RankingAge             = ageRank,
            am24RankingHighSchool      = hsRank,
            am24RankingCollege         = collRank
        )
    }

    currentProfile = displayProfile

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            state = listState
        ) {
            item {
                PhotoCarouselWithOverlay(
                    profile = currentProfile,
                    onEditProfileClick = { navController.navigate("editPicAndVoiceBio") },
                    onPostsClick = { showPostsOverlay = true },
                )
            }
            item {
                MatrimonyToggleRow(
                    isMatrimony = currentProfile.isMatrimonyMode,
                    onToggle = { checked ->
                        val updatedProfile = currentProfile.copy(isMatrimonyMode = checked)
                        currentProfile = updatedProfile
                        scope.launch {
                            Log.d("ProfileLazyScreen", "Toggling matrimonyMode to: $checked")
                            profileViewModel.saveProfileUpdated(
                                updatedProfile = updatedProfile,
                                onSuccess = {
                                    Log.d("ProfileLazyScreen", "Profile saved with isMatrimonyMode: $checked")
                                },
                                onFailure = { error ->
                                    Log.e("ProfileLazyScreen", "Failed to save profile: $error")
                                }
                            )
                        }
                    }
                )
            }
            item {

                ProfileCollapsibleSections(
                    profile = currentProfile,
                    profileViewModel = profileViewModel,
                    onProfileUpdated = { updated ->
                        currentProfile = updated
                        scope.launch {
                            Log.d("ProfileLazyScreen", "Updating collapsible sections")
                            profileViewModel.saveProfileUpdated(
                                updatedProfile = updated,
                                onSuccess = { Log.d("ProfileLazyScreen", "Collapsible sections saved") },
                                onFailure = { error ->
                                    Log.e("ProfileLazyScreen", "Failed to save collapsible sections: $error")
                                }
                            )
                        }
                    }
                )
            }
            if (featuredPosts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.featured_posts),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(featuredPosts) { post ->
                    PostItemInProfile(post)
                }
            }
            if (remainingPosts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { showPostsOverlay = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                        ) {
                            Text(text = stringResource(R.string.view_more_posts), color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        if (showPostsOverlay) {
            PostsOverlay(
                posts = featuredPosts + remainingPosts,
                onDismiss = { showPostsOverlay = false }
            )
        }
    }
}

@Composable
private fun localizedLocality(city: String, storedLocality: String): String {
    // pick the correct array for this city
    val arrayRes = when (localizedCity(city)) {
        // West Bengal
        stringResource(R.string.city_kolkata)        -> R.array.localities_kolkata
        stringResource(R.string.city_howrah)         -> R.array.localities_howrah
        stringResource(R.string.city_durgapur)       -> R.array.localities_durgapur
        stringResource(R.string.city_asansol)        -> R.array.localities_asansol
        stringResource(R.string.city_siliguri)       -> R.array.localities_siliguri
        stringResource(R.string.city_darjeeling)     -> R.array.localities_darjeeling
        stringResource(R.string.city_kharagpur)      -> R.array.localities_kharagpur

        // Other Cities
        stringResource(R.string.city_agartala)       -> R.array.localities_agartala
        stringResource(R.string.city_ahmedabad)      -> R.array.localities_ahmedabad
        stringResource(R.string.city_aizawl)         -> R.array.localities_aizawl
        stringResource(R.string.city_amaravati)      -> R.array.localities_amaravati
        stringResource(R.string.city_amritsar)       -> R.array.localities_amritsar
        stringResource(R.string.city_bengaluru)      -> R.array.localities_bengaluru
        stringResource(R.string.city_bhilai)         -> R.array.localities_bhilai
        stringResource(R.string.city_bhopal)         -> R.array.localities_bhopal
        stringResource(R.string.city_bhubaneswar)    -> R.array.localities_bhubaneswar
        stringResource(R.string.city_bilaspur)       -> R.array.localities_bilaspur
        stringResource(R.string.city_chandigarh)     -> R.array.localities_chandigarh
        stringResource(R.string.city_chennai)        -> R.array.localities_chennai
        stringResource(R.string.city_coimbatore)     -> R.array.localities_coimbatore
        stringResource(R.string.city_cuttack)        -> R.array.localities_cuttack
        stringResource(R.string.city_daman)          -> R.array.localities_daman
        stringResource(R.string.city_dehradun)       -> R.array.localities_dehradun
        stringResource(R.string.city_dibrugarh)      -> R.array.localities_dibrugarh
        stringResource(R.string.city_dharamshala)    -> R.array.localities_dharamshala
        stringResource(R.string.city_faridabad)      -> R.array.localities_faridabad
        stringResource(R.string.city_gangtok)        -> R.array.localities_gangtok
        stringResource(R.string.city_gaya)           -> R.array.localities_gaya
        stringResource(R.string.city_gandhinagar)    -> R.array.localities_gandhinagar
        stringResource(R.string.city_ghaziabad)      -> R.array.localities_ghaziabad
        stringResource(R.string.city_gwalior)        -> R.array.localities_gwalior
        stringResource(R.string.city_gyalshing)      -> R.array.localities_gyalshing
        stringResource(R.string.city_guwahati)       -> R.array.localities_guwahati
        stringResource(R.string.city_gurugram)       -> R.array.localities_gurugram
        stringResource(R.string.city_haridwar)       -> R.array.localities_haridwar
        stringResource(R.string.city_hisar)          -> R.array.localities_hisar
        stringResource(R.string.city_hyderabad)      -> R.array.localities_hyderabad
        stringResource(R.string.city_imphal)         -> R.array.localities_imphal
        stringResource(R.string.city_indore)         -> R.array.localities_indore
        stringResource(R.string.city_itanagar)       -> R.array.localities_itanagar
        stringResource(R.string.city_jaipur)         -> R.array.localities_jaipur
        stringResource(R.string.city_jamshedpur)     -> R.array.localities_jamshedpur
        stringResource(R.string.city_jodhpur)        -> R.array.localities_jodhpur
        stringResource(R.string.city_kancheepuram)   -> R.array.localities_kancheepuram
        stringResource(R.string.city_kanpur)         -> R.array.localities_kanpur
        stringResource(R.string.city_kargil)         -> R.array.localities_kargil
        stringResource(R.string.city_kavaratti)      -> R.array.localities_kavaratti
        stringResource(R.string.city_kochi)          -> R.array.localities_kochi
        stringResource(R.string.city_kohima)         -> R.array.localities_kohima
        stringResource(R.string.city_leh)            -> R.array.localities_leh
        stringResource(R.string.city_ludhiana)       -> R.array.localities_ludhiana
        stringResource(R.string.city_lucknow)        -> R.array.localities_lucknow
        stringResource(R.string.city_madurai)        -> R.array.localities_madurai
        stringResource(R.string.city_mumbai)         -> R.array.localities_mumbai
        stringResource(R.string.city_mangaluru)      -> R.array.localities_mangaluru
        stringResource(R.string.city_mysuru)         -> R.array.localities_mysuru
        stringResource(R.string.city_nainital)       -> R.array.localities_nainital
        stringResource(R.string.city_nagpur)         -> R.array.localities_nagpur
        stringResource(R.string.city_namchi)         -> R.array.localities_namchi
        stringResource(R.string.city_navi_mumbai)    -> R.array.localities_navi_mumbai
        stringResource(R.string.city_nct_of_delhi)   -> R.array.localities_delhi_nct
        stringResource(R.string.city_noida)          -> R.array.localities_noida
        stringResource(R.string.city_panaji)         -> R.array.localities_panaji
        stringResource(R.string.city_pasighat)       -> R.array.localities_pasighat
        stringResource(R.string.city_patna)          -> R.array.localities_patna
        stringResource(R.string.city_prayagraj)      -> R.array.localities_prayagraj
        stringResource(R.string.city_pune)           -> R.array.localities_pune
        stringResource(R.string.city_port_blair)     -> R.array.localities_port_blair
        stringResource(R.string.city_puducherry)     -> R.array.localities_puducherry
        stringResource(R.string.city_raipur)         -> R.array.localities_raipur
        stringResource(R.string.city_ranchi)         -> R.array.localities_ranchi
        stringResource(R.string.city_rourkela)       -> R.array.localities_rourkela
        stringResource(R.string.city_rohtak)         -> R.array.localities_rohtak
        stringResource(R.string.city_shillong)       -> R.array.localities_shillong
        stringResource(R.string.city_shimla)         -> R.array.localities_shimla
        stringResource(R.string.city_silchar)        -> R.array.localities_silchar
        stringResource(R.string.city_sonipat)        -> R.array.localities_sonipat
        stringResource(R.string.city_surat)          -> R.array.localities_surat
        stringResource(R.string.city_secunderabad)   -> R.array.localities_secunderabad
        stringResource(R.string.city_tawang)         -> R.array.localities_tawang
        stringResource(R.string.city_thane)          -> R.array.localities_thane
        stringResource(R.string.city_thiruvananthapuram) -> R.array.localities_thiruvananthapuram
        stringResource(R.string.city_udaipur)        -> R.array.localities_udaipur
        stringResource(R.string.city_vadodara)       -> R.array.localities_vadodara
        stringResource(R.string.city_varanasi)       -> R.array.localities_varanasi
        stringResource(R.string.city_vellore)        -> R.array.localities_vellore
        stringResource(R.string.city_vijayawada)     -> R.array.localities_vijayawada
        stringResource(R.string.city_visakhapatnam)  -> R.array.localities_visakhapatnam
        stringResource(R.string.city_warangal)       -> R.array.localities_warangal
        stringResource(R.string.city_other)          -> R.array.localities_other

        // If no exact match, fall back
        else                                         -> R.array.localities_other
    }

    // load that array and see if our stored value still exists
    val allLocs = stringArrayResource(id = arrayRes)
    return allLocs.firstOrNull { it == storedLocality } ?: storedLocality
}
@Composable
private fun localizedCaste(raw: String): String =
    casteNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedGender(raw: String): String =
    genderNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedCommunity(raw: String): String =
    communityNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedReligion(raw: String): String =
    religionNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedCity(raw: String): String =
    cityNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedLookingFor(raw: String): String =
    lookingForNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedLoveLanguage(raw: String): String =
    loveLanguageNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedPolitics(raw: String): String =
    politicsNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
fun VerificationBadge(
    verified: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(32.dp)
            .background(
                if (verified) Color(0xFF00C853)          // green when verified
                else Color(0xFFFF6F00).copy(alpha = .20f),       // grey when not
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = if (verified) Icons.Default.Verified else Icons.Default.DoNotDisturbOn,
            contentDescription = if (verified)
                stringResource(R.string.verified_profile_cd)
            else
                stringResource(R.string.verify_profile_cd),
            tint = Color.White
        )
    }
}



/**
 * Main LazyColumn structure:
 *  1) Photo carousel
 *  2) Profile completion indicator
 *  3) Collapsible sections (Basic Info, Preferences, Lifestyle, Interests)
 *  4) Featured Posts above Metrics
 *  5) Metrics (collapsed)
 *  6) “More Posts” if leftover
 */

/**
 * A CollapsibleSection with an optional EDIT icon in the header.
 */
@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    editMode: Boolean = false,
    onEditToggle: () -> Unit = {},
    editable: Boolean = true,                       // ← NEW
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(10.dp)) // ← new look
            .background(Color(0xFF1A1A1A))                              // ← new look
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFFF6F00),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))

        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        /* edit icon only when editable == true */
        if (editable) {
            IconButton(onClick = onEditToggle) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (editMode) "Cancel Edit" else "Edit",
                    tint = if (editMode) Color.Red else Color.White
                )
            }
        }
    }
        Spacer(Modifier.height(8.dp))

        Card(
            colors  = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape   = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(Modifier.padding(12.dp)) { content() }
        }

        Spacer(Modifier.height(8.dp))
    }

@Composable
fun PhotoCarouselWithOverlay(
    profile: Profile,
    onEditProfileClick: () -> Unit,
    onPostsClick: () -> Unit,
) {
    val context = LocalContext.current
    val firstPhoto = profile.profilepicUrl.takeIf { !it.isNullOrBlank() }
        ?: "android.resource://${context.packageName}/drawable/local_placeholder"
    val photoUrls = listOf(firstPhoto) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember { mutableStateOf(0) }

    // Pre-cache images
    LaunchedEffect(photoUrls) {
        photoUrls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .diskCacheKey(url)
                .memoryCacheKey(url)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(7f / 10f) // adjust as needed
            .background(Color.Black)
            .pointerInput(photoUrls) {
                detectTapGestures { offset ->
                    // Tap left or right to cycle photos
                    if (photoUrls.size > 1) {
                        currentPhotoIndex = if (offset.x > size.width / 2)
                            (currentPhotoIndex + 1) % photoUrls.size
                        else
                            (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size
                    }
                }
            }
    ) {
        // Main photo
        if (photoUrls.isNotEmpty()) {
            if (photoUrls.isNotEmpty()) {
                CachedProfilePhoto(url = photoUrls[currentPhotoIndex], modifier = Modifier.fillMaxSize())
            }

            // Photo indicators at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                photoUrls.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .width(if (index == currentPhotoIndex) 30.dp else 10.dp)
                            .height(4.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index == currentPhotoIndex) Color.White else Color.Gray)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.TopStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(.65f), Color.Transparent)
                        )
                    )
            ) {
                IconButton(
                    onClick  = onPostsClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 16.dp, start = 16.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                        .size(28.dp)
                ) {
                    Icon(Icons.Default.PostAdd,
                        stringResource(R.string.posts_button),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick  = onEditProfileClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                        .size(28.dp)
                ) {
                    Icon(Icons.Default.Edit,
                        stringResource(R.string.edit_profile_cd),
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom overlay: name, age, hometown, rating bar, zodiac, posts button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Column {
                // Name + age + Posts button
                val age = calculateAge(profile.dob)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1) a fixed-height, clipped box that scrolls its content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min)
                            .clipToBounds()
                    ) {
                        val scroll = rememberScrollState()
                        // 2) when the name changes, continuously animate back-and-forth
                        LaunchedEffect(profile.name) {
                            // tiny pause before you start
                            delay(800)
                            while (true) {
                                scroll.animateScrollTo(scroll.maxValue)
                                delay(800)
                                scroll.animateScrollTo(0)
                                delay(800)
                            }
                        }
                        // 3) your name/age text, but horizontally scrollable
                        val displayName = profile.name.ifBlank { profile.username }
                        Text(
                            text = if (age > 0) "$displayName, $age" else displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White,
                            modifier = Modifier
                                .horizontalScroll(scroll)
                        )
                    }
                }

                // Hometown
                if (profile.hometown.isNotBlank()) {
                    Text(text = stringResource(R.string.from_hometown, profile.hometown), fontSize = 16.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rating Bar + zodiac side by side
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
                    Spacer(Modifier.width(8.dp))

                    // Zodiac next to rating bar
                    val zodiac = profile.zodiac ?: deriveZodiac(profile.dob)
                    if (zodiac.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF6F00)
                        ) {
                            Text(
                                text = zodiac,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CachedProfilePhoto(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = stringResource(R.string.profile_photo)
) {
    // Build and remember your image painter.
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .diskCacheKey(url)   // Use the URL as a stable key
            .memoryCacheKey(url)
            .crossfade(true)
            .build()
    )
    // Use the painter in the Image composable.
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/** Display a horizontal progress for completion. */
@Composable
fun AnimatedProfileCompletion(
    completion: Int,
    modifier: Modifier = Modifier
) {
    if (completion >= 100) return   // <-- early‑exit

    val animated by animateFloatAsState(
        targetValue = completion / 100f,
        animationSpec = tween(600, easing = FastOutSlowInEasing)
    )

    Column(modifier) {
        Text(
            text = stringResource(R.string.profile_percent, completion),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = animated,
            modifier = Modifier
                .fillMaxWidth(.55f)
                .height(4.dp),
            color = Color(0xFFFF6F00),
            trackColor = Color.White.copy(alpha = .0f)
        )
    }
}

@Composable
fun MatrimonyToggleRow(
    isMatrimony: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.matrimony_mode),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Switch(
            checked = isMatrimony,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF6F00),
                checkedTrackColor = Color(0xFFFF6F00).copy(alpha = .5f)
            )
        )
    }
}

@Composable
fun BasicInfoSection(profile: Profile) {
    val genderIcon = when (profile.gender.lowercase()) {
        "male"   -> Icons.Default.Male
        "female" -> Icons.Default.Female
        else     -> Icons.Default.Transgender
    }
    val ctx = LocalContext.current
    val heightString = if (profile.height2.size == 2)
        "${profile.height2[0]} ft ${profile.height2[1]} in"
    else
        "${profile.height} cm"
    val isIndian = profile.country.equals("India", ignoreCase = true)

    // --- Name, caste, gender, city, locality, username, job, work ---
    ProfileDetailRow(stringResource(R.string.label_username),
        profile.username,
        Icons.Default.AccountCircle)

    ProfileDetailRow(stringResource(R.string.label_height),
        heightString,
        Icons.Default.Straighten)

    if (isIndian) {
        ProfileDetailRow(
            stringResource(R.string.caste),
            localizedCaste(profile.caste ?: ""),
            Icons.Default.Groups)
    }

    ProfileDetailRow(
        stringResource(R.string.label_gender),
        localizedGender(profile.gender),
        genderIcon)

    // --- Community, religion, height, date joined ---
    if (isIndian) {
        ProfileDetailRow(
            stringResource(R.string.label_community),
            localizedCommunity(profile.community),
            Icons.Default.Groups)
    }

    ProfileDetailRow(
        stringResource(R.string.label_religion),
        localizedReligion(profile.religion),
        Icons.Default.Church)

    ProfileDetailRow(
        stringResource(R.string.ethnicity_label),
        profile.ethnicity.ifBlank { stringResource(R.string.not_set) },
        Icons.Default.Flag)

    ProfileDetailRow(
        stringResource(R.string.income_level_label),
        profile.incomeLevel.ifBlank { stringResource(R.string.not_set) },
        Icons.Default.AttachMoney)

    ProfileDetailRow(
        stringResource(R.string.city_label),
        localizedCity(profile.city).ifBlank { stringResource(R.string.not_set) },
        Icons.Default.LocationCity)

    ProfileDetailRow(
        stringResource(R.string.label_locality),
        localizedLocality(profile.city, profile.hometown),
        Icons.Default.LocationCity)

    val displayJobRole = profile.customJobRole
        .takeUnless { it.isNullOrBlank() }
        ?: profile.jobRole
    ProfileDetailRow(stringResource(R.string.label_job_role),
        displayJobRole.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.not_set),
        Icons.Default.Work)

    val displayWork = profile.customWork
        .takeUnless { it.isNullOrBlank() }
        ?: profile.work
    ProfileDetailRow(stringResource(R.string.label_work),
        displayWork.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.not_set),
        Icons.Default.Business)

    // --- High School + Year ---
    val highSchoolText = profile.highSchool
        .takeIf { it.isNotBlank() }
        ?.let { name ->
            profile.highSchoolGraduationYear
                .takeIf { it.isNotBlank() }
                ?.let { ", $it" }
                .let { suffix -> name + (suffix ?: "") }
        }
    ProfileDetailRow(stringResource(R.string.label_high_school),
        highSchoolText,
        Icons.Default.School)

    // --- College + Year ---
    val collegeText = profile.college
        .takeIf { it.isNotBlank() }
        ?.let { name ->
            profile.collegeGraduationYear
                .takeIf { it.isNotBlank() }
                ?.let { ", $it" }
                .let { suffix -> name + (suffix ?: "") }
        }
    ProfileDetailRow(stringResource(R.string.label_college),
        collegeText,
        Icons.Default.AccountBalance)

    // College degree if any
    if (!profile.collegeDegree.isNullOrBlank()) {
        ProfileDetailRow(stringResource(R.string.label_college_degree),
            profile.collegeDegree,
            Icons.Default.Book)
    }

    // --- Post‑Graduation + Year ---
    val postGradText = profile.postGraduation
        ?.takeIf { it.isNotBlank() }
        ?.let { name ->
            profile.postGraduationYear
                .takeIf { it.isNotBlank() }
                ?.let { ", $it" }
                .let { suffix -> name + (suffix ?: "") }
        }
    ProfileDetailRow(stringResource(R.string.label_post_graduation),
        postGradText,
        Icons.Default.EmojiObjects)

    // Post‑grad degree if any
    if (!profile.postGraduationDegree.isNullOrBlank()) {
        ProfileDetailRow(stringResource(R.string.label_post_graduation_degree),
            profile.postGraduationDegree,
            Icons.Default.School)
    }

    ProfileDetailRow(
        stringResource(R.string.label_date_joined),
        formatJoinedOn(ctx, profile.dateOfJoin),
        Icons.Default.DateRange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val notSelected = stringResource(R.string.not_selected)
    // State for all fields
    var name by remember { mutableStateOf(tempProfile.name) }
    var city by remember { mutableStateOf(tempProfile.city) }
    var locality by remember { mutableStateOf(tempProfile.hometown) }
    var customCity by remember { mutableStateOf(tempProfile.customCity.orEmpty()) }
    var customLocality by remember { mutableStateOf(tempProfile.customHometown.orEmpty()) }
    var religion by remember { mutableStateOf(tempProfile.religion.ifBlank { notSelected }) }
    var community by remember { mutableStateOf(tempProfile.community.ifBlank { notSelected }) }
    var caste by remember { mutableStateOf(tempProfile.caste.ifBlank { notSelected }) }
    var isFeet by remember { mutableStateOf(tempProfile.height2.isNotEmpty()) }
    var feet by remember { mutableStateOf(tempProfile.height2.getOrNull(0) ?: 0) }
    var inches by remember { mutableStateOf(tempProfile.height2.getOrNull(1) ?: 0) }
    var heightCm by remember { mutableStateOf(tempProfile.height) }
    val isIndian = tempProfile.country.equals("India", ignoreCase = true)
    var ethnicity by remember { mutableStateOf(tempProfile.ethnicity.ifBlank { notSelected }) }
    var incomeLevel by remember { mutableStateOf(tempProfile.incomeLevel.ifBlank { notSelected }) }


    // Gender
    val genderOptions = listOf(
        stringResource(R.string.male_option),
        stringResource(R.string.female_option),
        stringResource(R.string.gender_either)
    )
    var selectedGender by remember {
        mutableStateOf(tempProfile.gender.ifBlank { notSelected })
    }

    // Job Role
    val jobRoleOptions = listOf(
        stringResource(R.string.job_role_option_accountant),
        stringResource(R.string.job_role_option_advocate),
        stringResource(R.string.job_role_option_army_officer),
        stringResource(R.string.job_role_option_barista),
        stringResource(R.string.job_role_option_business_analyst),
        stringResource(R.string.job_role_option_carpenter),
        stringResource(R.string.job_role_option_ceo),
        stringResource(R.string.job_role_option_chef),
        stringResource(R.string.job_role_option_chartered_accountant),
        stringResource(R.string.job_role_option_civil_engineer),
        stringResource(R.string.job_role_option_content_writer),
        stringResource(R.string.job_role_option_data_scientist),
        stringResource(R.string.job_role_option_director),
        stringResource(R.string.job_role_option_doctor),
        stringResource(R.string.job_role_option_electrical_engineer),
        stringResource(R.string.job_role_option_electrician),
        stringResource(R.string.job_role_option_entrepreneur),
        stringResource(R.string.job_role_option_editor),
        stringResource(R.string.job_role_option_flight_attendant),
        stringResource(R.string.job_role_option_firefighter),
        stringResource(R.string.job_role_option_graphic_designer),
        stringResource(R.string.job_role_option_hr_manager),
        stringResource(R.string.job_role_option_intern),
        stringResource(R.string.job_role_option_journalist),
        stringResource(R.string.job_role_option_lawyer),
        stringResource(R.string.job_role_option_marketing_manager),
        stringResource(R.string.job_role_option_mechanical_engineer),
        stringResource(R.string.job_role_option_mechanic),
        stringResource(R.string.job_role_option_nurse),
        stringResource(R.string.job_role_option_other),
        stringResource(R.string.job_role_option_pharmacist),
        stringResource(R.string.job_role_option_photographer),
        stringResource(R.string.job_role_option_pilot),
        stringResource(R.string.job_role_option_plumber),
        stringResource(R.string.job_role_option_product_manager),
        stringResource(R.string.job_role_option_professor),
        stringResource(R.string.job_role_option_project_manager),
        stringResource(R.string.job_role_option_researcher),
        stringResource(R.string.job_role_option_sales_executive),
        stringResource(R.string.job_role_option_scientist),
        stringResource(R.string.job_role_option_software_developer),
        stringResource(R.string.job_role_option_surgeon),
        stringResource(R.string.job_role_option_teacher),
        stringResource(R.string.job_role_option_ux_ui_designer)
    )

    var selectedJobRole by remember {
        mutableStateOf(tempProfile.jobRole.ifBlank { notSelected })
    }
    var customJobRole by remember {
        mutableStateOf(if (selectedJobRole == jobRoleOptions.last()) tempProfile.customJobRole.orEmpty() else "")
    }

    // Search states for high school
    var highSchoolQuery by remember { mutableStateOf(tempProfile.highSchool) }
    var highSchoolResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var highSchoolSearching by remember { mutableStateOf(false) }
    var isHighSchoolFieldFocused by remember { mutableStateOf(false) } // Track focus state for high school
    val highSchoolMenuExpanded = highSchoolResults.isNotEmpty() && isHighSchoolFieldFocused // Only expand if focused
    var customHighSchool by remember { mutableStateOf(tempProfile.customHighSchool.orEmpty()) }
    var highSchoolYear by remember { mutableStateOf(tempProfile.highSchoolGraduationYear) }

    // Search states for college
    var collegeQuery by remember { mutableStateOf(tempProfile.college) }
    var collegeResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var collegeSearching by remember { mutableStateOf(false) }
    var isCollegeFieldFocused by remember { mutableStateOf(false) } // Track focus state for college
    val collegeMenuExpanded = collegeResults.isNotEmpty() && isCollegeFieldFocused // Only expand if focused
    var customCollege by remember { mutableStateOf(tempProfile.customCollege.orEmpty()) }
    var collegeYear by remember { mutableStateOf(tempProfile.collegeGraduationYear) }
    var collegeDegree by remember { mutableStateOf(tempProfile.collegeDegree.orEmpty()) }

    // Search states for post-graduation
    var postGradQuery by remember { mutableStateOf(tempProfile.postGraduation.orEmpty()) }
    var postGradResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var postGradSearching by remember { mutableStateOf(false) }
    var isPostGradFieldFocused by remember { mutableStateOf(false) } // Track focus state for post-grad
    val postGradMenuExpanded = postGradResults.isNotEmpty() && isPostGradFieldFocused // Only expand if focused
    var customPostGrad by remember { mutableStateOf(tempProfile.customPostGraduation.orEmpty()) }
    var postGradYear by remember { mutableStateOf(tempProfile.postGraduationYear) }
    var postGradDegree by remember { mutableStateOf(tempProfile.postGraduationDegree.orEmpty()) }

    // Search states for work
    var workQuery by remember { mutableStateOf(tempProfile.work) }
    var workResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var workSearching by remember { mutableStateOf(false) }
    var isWorkFieldFocused by remember { mutableStateOf(false) } // Track focus state for work
    val workMenuExpanded = workResults.isNotEmpty() && isWorkFieldFocused // Only expand if focused
    var customWork by remember { mutableStateOf(tempProfile.customWork.orEmpty()) }

    // City and Locality options (from old code)
    val cityOptionsList = stringArrayResource(id = R.array.city_names).toList()
    val localityOptionsList: List<String> = when (city) {
        stringResource(R.string.city_kolkata) -> stringArrayResource(R.array.localities_kolkata).toList()
        stringResource(R.string.city_howrah) -> stringArrayResource(R.array.localities_howrah).toList()
        stringResource(R.string.city_durgapur) -> stringArrayResource(R.array.localities_durgapur).toList()
        stringResource(R.string.city_asansol) -> stringArrayResource(R.array.localities_asansol).toList()
        stringResource(R.string.city_siliguri) -> stringArrayResource(R.array.localities_siliguri).toList()
        stringResource(R.string.city_darjeeling) -> stringArrayResource(R.array.localities_darjeeling).toList()
        stringResource(R.string.city_kharagpur) -> stringArrayResource(R.array.localities_kharagpur).toList()
        stringResource(R.string.city_agartala) -> stringArrayResource(R.array.localities_agartala).toList()
        stringResource(R.string.city_ahmedabad) -> stringArrayResource(R.array.localities_ahmedabad).toList()
        stringResource(R.string.city_aizawl) -> stringArrayResource(R.array.localities_aizawl).toList()
        stringResource(R.string.city_amaravati) -> stringArrayResource(R.array.localities_amaravati).toList()
        stringResource(R.string.city_amritsar) -> stringArrayResource(R.array.localities_amritsar).toList()
        stringResource(R.string.city_bengaluru) -> stringArrayResource(R.array.localities_bengaluru).toList()
        stringResource(R.string.city_bhilai) -> stringArrayResource(R.array.localities_bhilai).toList()
        stringResource(R.string.city_bhopal) -> stringArrayResource(R.array.localities_bhopal).toList()
        stringResource(R.string.city_bhubaneswar) -> stringArrayResource(R.array.localities_bhubaneswar).toList()
        stringResource(R.string.city_bilaspur) -> stringArrayResource(R.array.localities_bilaspur).toList()
        stringResource(R.string.city_chandigarh) -> stringArrayResource(R.array.localities_chandigarh).toList()
        stringResource(R.string.city_chennai) -> stringArrayResource(R.array.localities_chennai).toList()
        stringResource(R.string.city_coimbatore) -> stringArrayResource(R.array.localities_coimbatore).toList()
        stringResource(R.string.city_cuttack) -> stringArrayResource(R.array.localities_cuttack).toList()
        stringResource(R.string.city_daman) -> stringArrayResource(R.array.localities_daman).toList()
        stringResource(R.string.city_dehradun) -> stringArrayResource(R.array.localities_dehradun).toList()
        stringResource(R.string.city_dibrugarh) -> stringArrayResource(R.array.localities_dibrugarh).toList()
        stringResource(R.string.city_dharamshala) -> stringArrayResource(R.array.localities_dharamshala).toList()
        stringResource(R.string.city_faridabad) -> stringArrayResource(R.array.localities_faridabad).toList()
        stringResource(R.string.city_gangtok) -> stringArrayResource(R.array.localities_gangtok).toList()
        stringResource(R.string.city_gaya) -> stringArrayResource(R.array.localities_gaya).toList()
        stringResource(R.string.city_gandhinagar) -> stringArrayResource(R.array.localities_gandhinagar).toList()
        stringResource(R.string.city_ghaziabad) -> stringArrayResource(R.array.localities_ghaziabad).toList()
        stringResource(R.string.city_gwalior) -> stringArrayResource(R.array.localities_gwalior).toList()
        stringResource(R.string.city_gyalshing) -> stringArrayResource(R.array.localities_gyalshing).toList()
        stringResource(R.string.city_guwahati) -> stringArrayResource(R.array.localities_guwahati).toList()
        stringResource(R.string.city_gurugram) -> stringArrayResource(R.array.localities_gurugram).toList()
        stringResource(R.string.city_haridwar) -> stringArrayResource(R.array.localities_haridwar).toList()
        stringResource(R.string.city_hisar) -> stringArrayResource(R.array.localities_hisar).toList()
        stringResource(R.string.city_hyderabad) -> stringArrayResource(R.array.localities_hyderabad).toList()
        stringResource(R.string.city_imphal) -> stringArrayResource(R.array.localities_imphal).toList()
        stringResource(R.string.city_indore) -> stringArrayResource(R.array.localities_indore).toList()
        stringResource(R.string.city_itanagar) -> stringArrayResource(R.array.localities_itanagar).toList()
        stringResource(R.string.city_jaipur) -> stringArrayResource(R.array.localities_jaipur).toList()
        stringResource(R.string.city_jamshedpur) -> stringArrayResource(R.array.localities_jamshedpur).toList()
        stringResource(R.string.city_jodhpur) -> stringArrayResource(R.array.localities_jodhpur).toList()
        stringResource(R.string.city_kancheepuram) -> stringArrayResource(R.array.localities_kancheepuram).toList()
        stringResource(R.string.city_kanpur) -> stringArrayResource(R.array.localities_kanpur).toList()
        stringResource(R.string.city_kargil) -> stringArrayResource(R.array.localities_kargil).toList()
        stringResource(R.string.city_kavaratti) -> stringArrayResource(R.array.localities_kavaratti).toList()
        stringResource(R.string.city_kochi) -> stringArrayResource(R.array.localities_kochi).toList()
        stringResource(R.string.city_kohima) -> stringArrayResource(R.array.localities_kohima).toList()
        stringResource(R.string.city_leh) -> stringArrayResource(R.array.localities_leh).toList()
        stringResource(R.string.city_ludhiana) -> stringArrayResource(R.array.localities_ludhiana).toList()
        stringResource(R.string.city_lucknow) -> stringArrayResource(R.array.localities_lucknow).toList()
        stringResource(R.string.city_madurai) -> stringArrayResource(R.array.localities_madurai).toList()
        stringResource(R.string.city_mumbai) -> stringArrayResource(R.array.localities_mumbai).toList()
        stringResource(R.string.city_mangaluru) -> stringArrayResource(R.array.localities_mangaluru).toList()
        stringResource(R.string.city_mysuru) -> stringArrayResource(R.array.localities_mysuru).toList()
        stringResource(R.string.city_nainital) -> stringArrayResource(R.array.localities_nainital).toList()
        stringResource(R.string.city_nagpur) -> stringArrayResource(R.array.localities_nagpur).toList()
        stringResource(R.string.city_namchi) -> stringArrayResource(R.array.localities_namchi).toList()
        stringResource(R.string.city_navi_mumbai) -> stringArrayResource(R.array.localities_navi_mumbai).toList()
        stringResource(R.string.city_nct_of_delhi) -> stringArrayResource(R.array.localities_delhi_nct).toList()
        stringResource(R.string.city_noida) -> stringArrayResource(R.array.localities_noida).toList()
        stringResource(R.string.city_panaji) -> stringArrayResource(R.array.localities_panaji).toList()
        stringResource(R.string.city_pasighat) -> stringArrayResource(R.array.localities_pasighat).toList()
        stringResource(R.string.city_patna) -> stringArrayResource(R.array.localities_patna).toList()
        stringResource(R.string.city_prayagraj) -> stringArrayResource(R.array.localities_prayagraj).toList()
        stringResource(R.string.city_pune) -> stringArrayResource(R.array.localities_pune).toList()
        stringResource(R.string.city_port_blair) -> stringArrayResource(R.array.localities_port_blair).toList()
        stringResource(R.string.city_puducherry) -> stringArrayResource(R.array.localities_puducherry).toList()
        stringResource(R.string.city_raipur) -> stringArrayResource(R.array.localities_raipur).toList()
        stringResource(R.string.city_ranchi) -> stringArrayResource(R.array.localities_ranchi).toList()
        stringResource(R.string.city_rourkela) -> stringArrayResource(R.array.localities_rourkela).toList()
        stringResource(R.string.city_rohtak) -> stringArrayResource(R.array.localities_rohtak).toList()
        stringResource(R.string.city_shillong) -> stringArrayResource(R.array.localities_shillong).toList()
        stringResource(R.string.city_shimla) -> stringArrayResource(R.array.localities_shimla).toList()
        stringResource(R.string.city_silchar) -> stringArrayResource(R.array.localities_silchar).toList()
        stringResource(R.string.city_sonipat) -> stringArrayResource(R.array.localities_sonipat).toList()
        stringResource(R.string.city_surat) -> stringArrayResource(R.array.localities_surat).toList()
        stringResource(R.string.city_secunderabad) -> stringArrayResource(R.array.localities_secunderabad).toList()
        stringResource(R.string.city_tawang) -> stringArrayResource(R.array.localities_tawang).toList()
        stringResource(R.string.city_thane) -> stringArrayResource(R.array.localities_thane).toList()
        stringResource(R.string.city_thiruvananthapuram) -> stringArrayResource(R.array.localities_thiruvananthapuram).toList()
        stringResource(R.string.city_udaipur) -> stringArrayResource(R.array.localities_udaipur).toList()
        stringResource(R.string.city_vadodara) -> stringArrayResource(R.array.localities_vadodara).toList()
        stringResource(R.string.city_varanasi) -> stringArrayResource(R.array.localities_varanasi).toList()
        stringResource(R.string.city_vellore) -> stringArrayResource(R.array.localities_vellore).toList()
        stringResource(R.string.city_vijayawada) -> stringArrayResource(R.array.localities_vijayawada).toList()
        stringResource(R.string.city_visakhapatnam) -> stringArrayResource(R.array.localities_visakhapatnam).toList()
        stringResource(R.string.city_warangal) -> stringArrayResource(R.array.localities_warangal).toList()
        else -> stringArrayResource(R.array.localities_other).toList()
    }

    // Focus management
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // Clear focus on screen entry to prevent automatic focus on any text field
        focusManager.clearFocus()
    }

    // Search side-effects with debouncing for high school, college, post-grad, and work
    LaunchedEffect(highSchoolQuery) {
        if (highSchoolQuery.length < 3) {
            highSchoolResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400)
            highSchoolSearching = true
            highSchoolResults = searchPlacesRich(highSchoolQuery)
            highSchoolSearching = false
        }
    }

    LaunchedEffect(collegeQuery) {
        if (collegeQuery.length < 3) {
            collegeResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400)
            collegeSearching = true
            collegeResults = searchPlacesRich(collegeQuery)
            collegeSearching = false
        }
    }

    LaunchedEffect(postGradQuery) {
        if (postGradQuery.length < 3) {
            postGradResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400)
            postGradSearching = true
            postGradResults = searchPlacesRich(postGradQuery)
            postGradSearching = false
        }
    }

    LaunchedEffect(workQuery) {
        if (workQuery.length < 3) {
            workResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400)
            workSearching = true
            workResults = searchPlacesRich(workQuery)
            workSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Name
        OutlinedTextField(
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            value = name,
            onValueChange = { name = it },
            label = {
                Text(
                    stringResource(R.string.label_name),
                    fontSize = 12.sp,
                    color = Color(0xFFFF6F00)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF6F00),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Height toggle & fields
        Text(
            stringResource(R.string.height_label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = isFeet,
                onCheckedChange = { isFeet = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF6F00),
                    uncheckedThumbColor = Color.Gray,
                    checkedTrackColor = Color(0xFFFF6F00).copy(alpha = 0.54f),
                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.54f)
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isFeet) stringResource(R.string.feet_inches_label)
                else stringResource(R.string.centimeters_label),
                fontSize = 12.sp,
                color = Color.White
            )
        }
        if (isFeet) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = feet.toString(),
                    onValueChange = { feet = it.toIntOrNull() ?: 0 },
                    label = {
                        Text(
                            stringResource(R.string.feet_label),
                            fontSize = 12.sp,
                            color = Color(0xFFFF6F00)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = inches.toString(),
                    onValueChange = { inches = it.toIntOrNull() ?: 0 },
                    label = {
                        Text(
                            stringResource(R.string.inches_label),
                            fontSize = 12.sp,
                            color = Color(0xFFFF6F00)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        } else {
            OutlinedTextField(
                value = heightCm.toString(),
                onValueChange = { heightCm = it.toIntOrNull() ?: 0 },
                label = {
                    Text(
                        stringResource(R.string.centimeters_label),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Religion chips
        Text(
            stringResource(R.string.religion_label),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color(0xFFFF6F00)
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val religionOpts = listOf(
                stringResource(R.string.religion_other),
            stringResource(R.string.religion_buddhist),
            stringResource(R.string.religion_christian),
            stringResource(R.string.religion_christian_catholic),
            stringResource(R.string.religion_christian_protestant_mainline),
            stringResource(R.string.religion_christian_evangelical),
            stringResource(R.string.religion_christian_orthodox),
            stringResource(R.string.religion_christian_latter_day_saint),
            stringResource(R.string.religion_christian_jehovahs_witness),
            stringResource(R.string.religion_christian_other),
            stringResource(R.string.religion_hindu),
            stringResource(R.string.religion_jain),
            stringResource(R.string.religion_jewish),
            stringResource(R.string.religion_muslim),
            stringResource(R.string.religion_muslim_sunni),
            stringResource(R.string.religion_muslim_shia),
            stringResource(R.string.religion_muslim_ahmadiyya),
            stringResource(R.string.religion_muslim_sufi),
            stringResource(R.string.religion_muslim_other),
            stringResource(R.string.religion_no_religion),
            stringResource(R.string.religion_parsi),
            stringResource(R.string.religion_sikh),
            stringResource(R.string.religion_indigenous_tribal),
            stringResource(R.string.religion_santeria),
            stringResource(R.string.religion_voodou),
            stringResource(R.string.religion_candomble),
            stringResource(R.string.religion_umbanda),
            stringResource(R.string.religion_palo_mayombe),
            stringResource(R.string.religion_native_traditional),
            stringResource(R.string.religion_native_church),
            stringResource(R.string.religion_vision_quest),
            stringResource(R.string.religion_african_traditional),
            stringResource(R.string.religion_obeah),
            stringResource(R.string.religion_hoodoo),
            stringResource(R.string.religion_rastafari),
            stringResource(R.string.religion_black_protestant)
            )
            religionOpts.forEach { option ->
                FilterChip(
                    selected = religion == option,
                    onClick = {
                        religion = if (religion == option) notSelected else option
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Community chips
        if (isIndian) {
            // Community chips
            Text(
                stringResource(R.string.community_label),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6F00)
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val commOpts = listOf(
                    stringResource(R.string.community_other),
                    stringResource(R.string.community_adi),
                    stringResource(R.string.community_anglo_indian),
                    stringResource(R.string.community_andamanese),
                    stringResource(R.string.community_assamese),
                    stringResource(R.string.community_awadhi),
                    stringResource(R.string.community_banjara),
                    stringResource(R.string.community_bengali),
                    stringResource(R.string.community_bhil),
                    stringResource(R.string.community_bhojpuri),
                    stringResource(R.string.community_bihari),
                    stringResource(R.string.community_bodo),
                    stringResource(R.string.community_chhattisgarhi),
                    stringResource(R.string.community_coorgi),
                    stringResource(R.string.community_dogra),
                    stringResource(R.string.community_garhwali),
                    stringResource(R.string.community_goan),      // “Goan”
                    stringResource(R.string.community_gond),
                    stringResource(R.string.community_gujarati),
                    stringResource(R.string.community_haryanvi),
                    stringResource(R.string.community_himachali),
                    stringResource(R.string.community_kannadiga),
                    stringResource(R.string.community_kashmiri),
                    stringResource(R.string.community_khasi),
                    stringResource(R.string.community_konkani),
                    stringResource(R.string.community_kumaoni),
                    stringResource(R.string.community_ladakhi),
                    stringResource(R.string.community_lakhadweepi),
                    stringResource(R.string.community_lepcha),
                    stringResource(R.string.community_madhya_pradeshi),
                    stringResource(R.string.community_malayali),
                    stringResource(R.string.community_malayali_mappila),
                    stringResource(R.string.community_manipuri),
                    stringResource(R.string.community_marathi),
                    stringResource(R.string.community_marwari),
                    stringResource(R.string.community_mizo),
                    stringResource(R.string.community_munda),
                    stringResource(R.string.community_naga),
                    stringResource(R.string.community_nepali),
                    stringResource(R.string.community_nyishi),
                    stringResource(R.string.community_odia),
                    stringResource(R.string.community_oraon),
                    stringResource(R.string.community_parsi),
                    stringResource(R.string.community_punjabi),
                    stringResource(R.string.community_rajasthani),
                    stringResource(R.string.community_santhal),
                    stringResource(R.string.community_sikkimese),
                    stringResource(R.string.community_sindhi),
                    stringResource(R.string.community_tamil),
                    stringResource(R.string.community_telugu),
                    stringResource(R.string.community_tibetan),
                    stringResource(R.string.community_tripuri),
                    stringResource(R.string.community_urdu_speaker),
                )
                commOpts.forEach { option ->
                    FilterChip(
                        selected = community == option,
                        onClick = {
                            community = if (community == option) notSelected else option
                        },
                        label = { Text(option, fontSize = 12.sp, color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            disabledContainerColor = Color.Transparent,
                            disabledLabelColor = Color(0xFFFF6F00),
                            selectedContainerColor = Color(0xFFFF6F00),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }


            // Caste chips
            Text(
                stringResource(R.string.caste_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6F00)
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val casteOptions = listOf(
                    stringResource(R.string.caste_other),
                    stringResource(R.string.caste_baidya),
                    stringResource(R.string.caste_bhumihar),
                    stringResource(R.string.caste_brahmin),
                    stringResource(R.string.caste_ezhava),
                    stringResource(R.string.caste_gurjar),
                    stringResource(R.string.caste_jat),
                    stringResource(R.string.caste_kayastha),
                    stringResource(R.string.caste_kshatriya),
                    stringResource(R.string.caste_kurmi),
                    stringResource(R.string.caste_lingayat),
                    stringResource(R.string.caste_mahishya),
                    stringResource(R.string.caste_maratha),
                    stringResource(R.string.caste_nair),
                    stringResource(R.string.caste_obc),
                    stringResource(R.string.caste_other),
                    stringResource(R.string.caste_baidya),
                    stringResource(R.string.caste_bhumihar),
                    stringResource(R.string.caste_brahmin),
                    stringResource(R.string.caste_ezhava),
                    stringResource(R.string.caste_gurjar),
                    stringResource(R.string.caste_jat),
                    stringResource(R.string.caste_kayastha),
                    stringResource(R.string.caste_kshatriya),
                    stringResource(R.string.caste_kurmi),
                    stringResource(R.string.caste_lingayat),
                    stringResource(R.string.caste_mahishya),
                    stringResource(R.string.caste_maratha),
                    stringResource(R.string.caste_nair),
                    stringResource(R.string.caste_obc),
                    stringResource(R.string.caste_rajput),
                    stringResource(R.string.caste_reddy),
                    stringResource(R.string.caste_sadgop),
                    stringResource(R.string.caste_scheduled_caste),
                    stringResource(R.string.caste_scheduled_tribe),
                    stringResource(R.string.caste_vaishya),
                    stringResource(R.string.caste_yadav),
                    stringResource(R.string.caste_general),
                    stringResource(R.string.caste_reddy),
                    stringResource(R.string.caste_sadgop),
                    stringResource(R.string.caste_scheduled_caste),
                    stringResource(R.string.caste_scheduled_tribe),
                    stringResource(R.string.caste_vaishya),
                    stringResource(R.string.caste_yadav),
                    stringResource(R.string.caste_general),
                )
                casteOptions.forEach { option ->
                    FilterChip(
                        selected = caste == option,
                        onClick = {
                            caste = if (caste == option) notSelected else option
                        },
                        label = { Text(option, fontSize = 12.sp, color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            disabledContainerColor = Color.Transparent,
                            disabledLabelColor = Color(0xFFFF6F00),
                            selectedContainerColor = Color(0xFFFF6F00),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Text(stringResource(R.string.ethnicity_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val ethnicityOpts = listOf(
                stringResource(R.string.ethnicity_option_white),
                stringResource(R.string.ethnicity_option_black),
                stringResource(R.string.ethnicity_option_hispanic),
                stringResource(R.string.ethnicity_option_asian),
                stringResource(R.string.ethnicity_option_native_american),
                stringResource(R.string.ethnicity_option_middle_eastern),
                stringResource(R.string.ethnicity_option_pacific_islander),
                stringResource(R.string.ethnicity_option_mixed_other)
            )
            ethnicityOpts.forEach { option ->
                FilterChip(
                    selected = ethnicity == option,
                    onClick = {
                        ethnicity = if (ethnicity == option) notSelected else option
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Text(stringResource(R.string.income_level_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val incomeOpts = listOf(
                stringResource(R.string.income_level_under_25k),
                stringResource(R.string.income_level_25k_50k),
                stringResource(R.string.income_level_50k_75k),
                stringResource(R.string.income_level_75k_100k),
                stringResource(R.string.income_level_100k_150k),
                stringResource(R.string.income_level_over_150k)
            )
            incomeOpts.forEach { option ->
                FilterChip(
                    selected = incomeLevel == option,
                    onClick = { incomeLevel = option },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Gender chips
        Text(
            stringResource(R.string.gender_label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genderOptions.forEach { option ->
                FilterChip(
                    selected = selectedGender == option,
                    onClick = {
                        selectedGender = if (selectedGender == option) notSelected else option
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // City dropdown (old logic)
        if (isIndian) {
            SearchableDropdownWithCustomOption(
                title = stringResource(R.string.city_label),
                options = cityOptionsList,
                selectedOption = city,
                onOptionSelected = { sel -> city = sel },
                customInput = customCity,
                onCustomInputChange = { customCity = it!! }
            )
            Spacer(Modifier.height(6.dp))
            SearchableDropdownWithCustomOption(
                title = stringResource(R.string.label_locality),
                options = localityOptionsList,
                selectedOption = locality,
                onOptionSelected = { sel -> locality = sel },
                customInput = customLocality,
                onCustomInputChange = { customLocality = it!! }
            )
        } else {
            OutlinedTextField(
                value = customCity,
                onValueChange = { customCity = it },
                label = { Text(stringResource(R.string.city_label), color = Color(0xFFFF6F00)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = customLocality,
                onValueChange = { customLocality = it },
                label = { Text(stringResource(R.string.label_locality), color = Color(0xFFFF6F00)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }
        Spacer(Modifier.height(6.dp))

        // Job Role chips
        Text(
            stringResource(R.string.job_role_label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            jobRoleOptions.forEach { option ->
                FilterChip(
                    selected = selectedJobRole == option,
                    onClick = {
                        if (selectedJobRole == option) {
                            selectedJobRole = notSelected
                            customJobRole = ""
                        } else {
                            selectedJobRole = option
                            if (option != jobRoleOptions.last()) customJobRole = ""
                        }
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        if (selectedJobRole == jobRoleOptions.last()) {
            OutlinedTextField(
                value = customJobRole,
                onValueChange = { customJobRole = it },
                label = {
                    Text(
                        stringResource(R.string.label_custom_job_role),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Work search dropdown (new logic)
        Text(
            stringResource(R.string.label_work),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        ExposedDropdownMenuBox(
            expanded = workMenuExpanded,
            onExpandedChange = { /* Controlled by results and focus */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            val focusRequester = remember { FocusRequester() } // For focus tracking
            OutlinedTextField(
                value = workQuery,
                onValueChange = { query ->
                    workQuery = query
                    customWork = query
                },
                label = { Text(stringResource(R.string.select_work), color = Color.White) },
                singleLine = true,
                trailingIcon = {
                    if (workSearching)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    else
                        Icon(Icons.Default.Search, null, tint = Color(0xFFFF6F00))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFFFF6F00),
                    unfocusedLabelColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isWorkFieldFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            workResults = emptyList() // Clear results when focus is lost
                        }
                    }
            )
            ExposedDropdownMenu(
                expanded = workMenuExpanded,
                onDismissRequest = {
                    workResults = emptyList()
                    focusManager.clearFocus() // Clear focus when dismissing the dropdown
                },
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
            ) {
                workResults.forEach { res ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(res.name, color = Color.Black, fontSize = 12.sp)
                                if (res.address.isNotBlank())
                                    Text(
                                        res.address,
                                        color = Color.DarkGray,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 8.sp
                                    )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00))
                        },
                        onClick = {
                            workQuery = res.name
                            customWork = ""
                            workResults = emptyList()
                            focusManager.clearFocus() // Clear focus after selection
                        }
                    )
                }
            }
        }

        // High School search dropdown (new logic)
        Text(
            stringResource(R.string.label_high_school),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        ExposedDropdownMenuBox(
            expanded = highSchoolMenuExpanded,
            onExpandedChange = { /* Controlled by results and focus */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            val focusRequester = remember { FocusRequester() } // For focus tracking
            OutlinedTextField(
                value = highSchoolQuery,
                onValueChange = { query ->
                    highSchoolQuery = query
                    customHighSchool = query
                },
                label = { Text(stringResource(R.string.select_or_type_high_school), color = Color.White) },
                singleLine = true,
                trailingIcon = {
                    if (highSchoolSearching)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    else
                        Icon(Icons.Default.Search, null, tint = Color(0xFFFF6F00))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFFFF6F00),
                    unfocusedLabelColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isHighSchoolFieldFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            highSchoolResults = emptyList() // Clear results when focus is lost
                        }
                    }
            )
            ExposedDropdownMenu(
                expanded = highSchoolMenuExpanded,
                onDismissRequest = {
                    highSchoolResults = emptyList()
                    focusManager.clearFocus() // Clear focus when dismissing the dropdown
                },
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
            ) {
                highSchoolResults.forEach { res ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(res.name, color = Color.Black, fontSize = 12.sp)
                                if (res.address.isNotBlank())
                                    Text(
                                        res.address,
                                        color = Color.DarkGray,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 8.sp
                                    )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00))
                        },
                        onClick = {
                            highSchoolQuery = res.name
                            customHighSchool = ""
                            highSchoolResults = emptyList()
                            focusManager.clearFocus() // Clear focus after selection
                        }
                    )
                }
            }
        }
        if (highSchoolQuery.isNotBlank()) {
            OutlinedTextField(
                value = highSchoolYear,
                onValueChange = { highSchoolYear = it.filter { c -> c.isDigit() }.take(4) },
                label = {
                    Text(
                        stringResource(R.string.high_school_graduation_year),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // College search dropdown (new logic)
        Text(
            stringResource(R.string.college_label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        ExposedDropdownMenuBox(
            expanded = collegeMenuExpanded,
            onExpandedChange = { /* Controlled by results and focus */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            val focusRequester = remember { FocusRequester() } // For focus tracking
            OutlinedTextField(
                value = collegeQuery,
                onValueChange = { query ->
                    collegeQuery = query
                    customCollege = query
                },
                label = { Text(stringResource(R.string.select_or_type_college), color = Color.White) },
                singleLine = true,
                trailingIcon = {
                    if (collegeSearching)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    else
                        Icon(Icons.Default.Search, null, tint = Color(0xFFFF6F00))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFFFF6F00),
                    unfocusedLabelColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isCollegeFieldFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            collegeResults = emptyList() // Clear results when focus is lost
                        }
                    }
            )
            ExposedDropdownMenu(
                expanded = collegeMenuExpanded,
                onDismissRequest = {
                    collegeResults = emptyList()
                    focusManager.clearFocus() // Clear focus when dismissing the dropdown
                },
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
            ) {
                collegeResults.forEach { res ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(res.name, color = Color.Black, fontSize = 12.sp)
                                if (res.address.isNotBlank())
                                    Text(
                                        res.address,
                                        color = Color.DarkGray,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 8.sp
                                    )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00))
                        },
                        onClick = {
                            collegeQuery = res.name
                            customCollege = ""
                            collegeResults = emptyList()
                            focusManager.clearFocus() // Clear focus after selection
                        }
                    )
                }
            }
        }
        if (collegeQuery.isNotBlank()) {
            OutlinedTextField(
                value = collegeYear,
                onValueChange = { collegeYear = it.filter { c -> c.isDigit() }.take(4) },
                label = {
                    Text(
                        stringResource(R.string.college_graduation_year),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            OutlinedTextField(
                value = collegeDegree,
                onValueChange = { if (it.length <= 50) collegeDegree = it },
                label = {
                    Text(
                        stringResource(R.string.label_college_degree),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Post-Graduation search dropdown (new logic)
        Text(
            stringResource(R.string.post_graduation_label),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        ExposedDropdownMenuBox(
            expanded = postGradMenuExpanded,
            onExpandedChange = { /* Controlled by results and focus */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            val focusRequester = remember { FocusRequester() } // For focus tracking
            OutlinedTextField(
                value = postGradQuery,
                onValueChange = { query ->
                    postGradQuery = query
                    customPostGrad = query
                },
                label = { Text(stringResource(R.string.select_or_type_post_grad), color = Color.White) },
                singleLine = true,
                trailingIcon = {
                    if (postGradSearching)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    else
                        Icon(Icons.Default.Search, null, tint = Color(0xFFFF6F00))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFFFF6F00),
                    unfocusedLabelColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isPostGradFieldFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            postGradResults = emptyList() // Clear results when focus is lost
                        }
                    }
            )
            ExposedDropdownMenu(
                expanded = postGradMenuExpanded,
                onDismissRequest = {
                    postGradResults = emptyList()
                    focusManager.clearFocus() // Clear focus when dismissing the dropdown
                },
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
            ) {
                postGradResults.forEach { res ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(res.name, color = Color.Black, fontSize = 12.sp)
                                if (res.address.isNotBlank())
                                    Text(
                                        res.address,
                                        color = Color.DarkGray,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 8.sp
                                    )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00))
                        },
                        onClick = {
                            postGradQuery = res.name
                            customPostGrad = ""
                            postGradResults = emptyList()
                            focusManager.clearFocus() // Clear focus after selection
                        }
                    )
                }
            }
        }
        if (postGradQuery.isNotBlank()) {
            OutlinedTextField(
                value = postGradYear,
                onValueChange = { postGradYear = it.filter { c -> c.isDigit() }.take(4) },
                label = {
                    Text(
                        stringResource(R.string.select_graduation_year_label),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            OutlinedTextField(
                value = postGradDegree,
                onValueChange = { if (it.length <= 50) postGradDegree = it },
                label = {
                    Text(
                        stringResource(R.string.label_post_graduation_degree),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        val oth = stringResource(R.string.college_other)
        // Save / Cancel
        ButtonRow(
            onSave = {
                onSave(
                    tempProfile.copy(
                        name = name,
                        city = city,
                        ethnicity = ethnicity.takeIf { it != notSelected } ?: "",
                        incomeLevel = incomeLevel.takeIf { it != notSelected } ?: "",
                        customCity = customCity.ifBlank { null },
                        hometown = if (locality == oth)
                            customLocality.trim() else locality.trim(),
                        customHometown = customLocality.ifBlank { null },
                        highSchool = highSchoolQuery,
                        customHighSchool = customHighSchool.ifBlank { null },
                        highSchoolGraduationYear = highSchoolYear,
                        college = collegeQuery,
                        customCollege = customCollege.ifBlank { null },
                        collegeGraduationYear = collegeYear,
                        collegeDegree = collegeDegree.ifBlank { null },
                        postGraduation = postGradQuery.ifBlank { null },
                        customPostGraduation = customPostGrad.ifBlank { null },
                        postGraduationYear = postGradYear,
                        postGraduationDegree = postGradDegree.ifBlank { null },
                        religion = religion.takeIf { it != notSelected } ?: "",
                        community = community.takeIf { it != notSelected } ?: "",
                        caste = caste.takeIf { it != notSelected } ?: "",
                        height = heightCm,
                        height2 = if (isFeet) listOf(feet, inches) else emptyList(),
                        gender = selectedGender.takeIf { it != notSelected } ?: "",
                        jobRole = selectedJobRole.takeIf { it != notSelected } ?: "",
                        customJobRole = selectedJobRole.takeIf { it == jobRoleOptions.last() }
                            ?.let { customJobRole },
                        work = workQuery,
                        customWork = customWork.ifBlank { null }
                    )
                )
            },
            onCancel = onCancel
        )
    }
}

@Composable
fun PerformanceMetricsSection(profile: Profile) {
    CollapsibleSection(
        title       = stringResource(R.string.performance_metrics),
        icon        = Icons.Default.Assessment,
        editMode    = false,
        editable    = false                       // ← no pencil icon
    ) {
        ProfileDetailRow(stringResource(R.string.matches), profile.matchCount.toString(), Icons.Default.People)
        ProfileDetailRow(stringResource(R.string.rating), String.format("%.2f", profile.averageRating), Icons.Default.Star)
        ProfileDetailRow(
            label = stringResource(R.string.swipe_right_probability),
            value = "${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%",
            icon  = Icons.Default.Swipe
        )
        ProfileDetailRow(
            label = stringResource(R.string.west_bengal_ranking),
            value = profile.am24Ranking.toString(),
            icon  = Icons.Default.Public
        )

        /* city‑level rank */
        val cityRank = if (profile.city == stringResource(R.string.college_other))
            profile.am24RankingCustomCity else profile.am24RankingCity
        if (cityRank > 0) {
            ProfileDetailRow(
                label = "${profile.city.ifBlank { stringResource(R.string.city_label) }} Ranking",
                value = cityRank.toString(),
                icon  = Icons.Default.LocationCity
            )
        }

        /* hometown/locality rank */
        val hoodRank = if (profile.hometown == stringResource(R.string.college_other))
            profile.am24RankingCustomHometown else profile.am24RankingHometown
        if (hoodRank > 0) {
            ProfileDetailRow(
                label = "${profile.hometown.ifBlank { stringResource(R.string.locality_label) }} Ranking",
                value = hoodRank.toString(),
                icon  = Icons.Default.Home
            )
        }

        ProfileDetailRow(stringResource(R.string.age_ranking), profile.am24RankingAge.toString(), Icons.Default.Cake)

        if (profile.highSchool.isNotBlank()) {
            ProfileDetailRow(
                "${profile.highSchool} Ranking",
                profile.am24RankingHighSchool.toString(),
                Icons.Default.School
            )
        }
        if (profile.college.isNotBlank()) {
            ProfileDetailRow(
                "${profile.college} Ranking",
                profile.am24RankingCollege.toString(),
                Icons.Default.Book
            )
        }
    }
}

/** Preferences (View-Only) */
@Composable
fun PreferencesSection(profile: Profile) {
    ProfileDetailRow(    stringResource(R.string.looking_for_label),
        localizedLookingFor(profile.lookingFor)
            .ifBlank { stringResource(R.string.not_specified) }, Icons.Default.Favorite)
    val displayLoveLanguage = profile.customLoveLanguage
        ?.takeIf { it.isNotBlank() }
        ?: localizedLoveLanguage(profile.loveLanguage)
    ProfileDetailRow(
        stringResource(R.string.label_love_language),
        displayLoveLanguage.ifBlank { stringResource(R.string.not_set) },
        Icons.Default.Favorite
    )
    val displayPolitics = profile.customPolitics
        ?.takeIf { it.isNotBlank() }
        ?: localizedPolitics(profile.politics)
    ProfileDetailRow(
        stringResource(R.string.label_politics),
        displayPolitics.ifBlank { stringResource(R.string.not_set) },
        Icons.Default.HowToVote
    )
}

fun isLifestyleEmpty(lifestyle: Lifestyle?): Boolean {
    if (lifestyle == null) return true
    return listOf(
        lifestyle.smoking_habit,
        lifestyle.drinking_habit,
        lifestyle.indoor_outdoor_orientation,
        lifestyle.social_media_engagement,
        lifestyle.work_life_balance,
        lifestyle.exercise_frequency,
        lifestyle.family_orientated,
        lifestyle.sleep_pattern,
        lifestyle.adventurousness,
        lifestyle.intellectual_curiosity,
        lifestyle.creative_expression,
        lifestyle.physical_fitness,
        lifestyle.spirituality_mindfulness,
        lifestyle.easy_goingness,
        lifestyle.professional_ambition,
        lifestyle.environmental_awareness,
        lifestyle.sports_enthusiasm,
        lifestyle.sociability,
        lifestyle.sexual_activity_level
    ).all { it == -1 }
}

@Composable
fun LifestyleSection(profile: Profile) {
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 7.sp)) {
        Column {
            val lifestyle = profile.lifestyle
            if (isLifestyleEmpty(lifestyle)) {
                Text("No lifestyle specified.", color = Color.Gray, fontSize = 10.sp)
            } else {
                profile.lifestyle?.let { lifestyle ->
                    if (lifestyle.indoor_outdoor_orientation != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_going_out),
                            value = lifestyle.indoor_outdoor_orientation,
                            nouns = listOf(
                                stringResource(R.string.very_indoorsy),
                                stringResource(R.string.mostly_indoorsy),
                                stringResource(R.string.balanced),
                                stringResource(R.string.mostly_outdoorsy),
                                stringResource(R.string.very_outdoorsy)
                            ),
                            icon = Icons.Default.DirectionsWalk
                        )
                    if (lifestyle.social_media_engagement != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_social_media),
                            value = lifestyle.social_media_engagement,
                            nouns = listOf(
                                stringResource(R.string.invisible),
                                stringResource(R.string.watcher),
                                stringResource(R.string.casual_participant),
                                stringResource(R.string.engager),
                                stringResource(R.string.influencer)
                            ),
                            icon = Icons.Default.Groups2
                        )
                    if (lifestyle.work_life_balance != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_work_life_balance),
                            value = lifestyle.work_life_balance,
                            nouns = listOf(
                                stringResource(R.string.workaholic),
                                stringResource(R.string.more_work_oriented),
                                stringResource(R.string.balanced),
                                stringResource(R.string.more_life_oriented),
                                stringResource(R.string.relaxed),
                            ),
                            icon = Icons.Default.WorkOff
                        )
                    if (lifestyle.exercise_frequency != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_exercise),
                            value = lifestyle.exercise_frequency,
                            nouns = listOf(
                                stringResource(R.string.inactive),
                                stringResource(R.string.rarely_active),
                                stringResource(R.string.moderately_active),
                                stringResource(R.string.active),
                                stringResource(R.string.very_active)
                            ),
                            icon = Icons.Default.SportsGymnastics
                        )
                    if (lifestyle.family_orientated != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_family_oriented),
                            value = lifestyle.family_orientated,
                            nouns = listOf(
                                stringResource(R.string.independent),
                                stringResource(R.string.slightly_family_oriented),
                                stringResource(R.string.balanced),
                                stringResource(R.string.more_family_oriented),
                                stringResource(R.string.very_family_oriented)
                            ),
                            icon = Icons.Default.FamilyRestroom
                        )
                    if (lifestyle.sleep_pattern != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sleep),
                            value = lifestyle.sleep_pattern,
                            nouns = listOf(
                                stringResource(R.string.early_riser),
                                stringResource(R.string.morning_person),
                                stringResource(R.string.balanced),
                                stringResource(R.string.night_owl),
                                stringResource(R.string.late_night_enthusiast)
                            ),
                            icon = Icons.Default.Bedtime
                        )
                    if (lifestyle.adventurousness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_adventurousness),
                            value = lifestyle.adventurousness,
                            nouns = listOf(
                                stringResource(R.string.cautious),
                                stringResource(R.string.slightly_adventurous),
                                stringResource(R.string.moderately_adventurous),
                                stringResource(R.string.adventurous),
                                stringResource(R.string.thrill_seeker)
                            ),
                            icon = Icons.Default.Hiking
                        )
                    if (lifestyle.intellectual_curiosity != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_intellectual_curiosity),
                            value = lifestyle.intellectual_curiosity,
                            nouns = listOf(
                                stringResource(R.string.casual_thinker),
                                stringResource(R.string.inquisitive),
                                stringResource(R.string.knowledge_seeker),
                                stringResource(R.string.intellectual),
                                stringResource(R.string.philosopher)
                            ),
                            icon = Icons.Default.School
                        )
                    if (lifestyle.creative_expression != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_creative_expression),
                            value = lifestyle.creative_expression,
                            nouns = listOf(
                                stringResource(R.string.not_creative),
                                stringResource(R.string.somewhat_creative),
                                stringResource(R.string.creative),
                                stringResource(R.string.very_creative),
                                stringResource(R.string.artistic_genius)
                            ),
                            icon = Icons.Default.Palette
                        )
                    if (lifestyle.physical_fitness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_physical_fitness),
                            value = lifestyle.physical_fitness,
                            nouns = listOf(
                                stringResource(R.string.sedentary),
                                stringResource(R.string.somewhat_fit),
                                stringResource(R.string.fit),
                                stringResource(R.string.athletic),
                                stringResource(R.string.peak_fitness)
                            ),
                            icon = Icons.Default.FitnessCenter
                        )
                    if (lifestyle.spirituality_mindfulness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_spirituality),
                            value = lifestyle.spirituality_mindfulness,
                            nouns = listOf(
                                stringResource(R.string.not_spiritual),
                                stringResource(R.string.occasionally_mindful),
                                stringResource(R.string.balanced),
                                stringResource(R.string.spiritual),
                                stringResource(R.string.deeply_mindful)
                            ),
                            icon = Icons.Default.SelfImprovement
                        )
                    if (lifestyle.easy_goingness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_humor),
                            value = lifestyle.easy_goingness,
                            nouns = listOf(
                                stringResource(R.string.serious),
                                stringResource(R.string.somewhat_easygoing),
                                stringResource(R.string.balanced),
                                stringResource(R.string.humorous),
                                stringResource(R.string.life_of_the_party)
                            ),
                            icon = Icons.Default.SentimentVerySatisfied
                        )
                    if (lifestyle.professional_ambition != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_professional_ambition),
                            value = lifestyle.professional_ambition,
                            nouns = listOf(
                                stringResource(R.string.relaxed),
                                stringResource(R.string.occasionally_driven),
                                stringResource(R.string.balanced),
                                stringResource(R.string.ambitious),
                                stringResource(R.string.high_ambitious)
                            ),
                            icon = Icons.Default.Work
                        )
                    if (lifestyle.environmental_awareness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_environmental_awareness),
                            value = lifestyle.environmental_awareness,
                            nouns = listOf(
                                stringResource(R.string.not_conscious),
                                stringResource(R.string.occasionally_conscious),
                                stringResource(R.string.balanced),
                                stringResource(R.string.eco_friendly),
                                stringResource(R.string.eco_champion)
                            ),
                            icon = Icons.Default.Eco
                        )
                    if (lifestyle.culinary_enthusiasm != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_culinary_enthusiasm),
                            value = lifestyle.culinary_enthusiasm,
                            nouns = listOf(
                                stringResource(R.string.not_a_foodie),
                                stringResource(R.string.occasional_foodie),
                                stringResource(R.string.foodie),
                                stringResource(R.string.passionate_foodie),
                                stringResource(R.string.gourmet)
                            ),
                            icon = Icons.Default.LocalDining
                        )
                    if (lifestyle.political_awareness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_political_awareness),
                            value = lifestyle.political_awareness,
                            nouns = listOf(
                                stringResource(R.string.unaware),
                                stringResource(R.string.occasionally_aware),
                                stringResource(R.string.balanced),
                                stringResource(R.string.aware),
                                stringResource(R.string.politically_engaged)
                            ),
                            icon = Icons.Default.Gavel
                        )
                    if (lifestyle.community_engagement != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_community_engagement),
                            value = lifestyle.community_engagement,
                            nouns = listOf(
                                stringResource(R.string.individualistic),
                                stringResource(R.string.occasionally_involved),
                                stringResource(R.string.balanced),
                                stringResource(R.string.community_oriented),
                                stringResource(R.string.community_leader)
                            ),
                            icon = Icons.Default.Groups
                        )
                    if (lifestyle.sports_enthusiasm != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sports),
                            value = lifestyle.sports_enthusiasm,
                            nouns = listOf(
                                stringResource(R.string.non_sports),
                                stringResource(R.string.casual_viewer),
                                stringResource(R.string.occasional_player),
                                stringResource(R.string.sports_enthusiast),
                                stringResource(R.string.sports_fanatic)
                            ),
                            icon = Icons.Default.SportsSoccer
                        )
                    if (lifestyle.sociability != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sociability),
                            value = lifestyle.sociability,
                            nouns = listOf(
                                stringResource(R.string.not_introverted),
                                stringResource(R.string.slightly_introverted),
                                stringResource(R.string.moderately_introverted),
                                stringResource(R.string.very_introverted),
                                stringResource(R.string.extremely_introverted)
                            ),
                            icon = Icons.Default.Person
                        )
                    if (lifestyle.smoking_habit != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_smoking),
                            value = lifestyle.smoking_habit,
                            nouns = listOf(
                                stringResource(R.string.non_smoker),
                                stringResource(R.string.rare_smoker),
                                stringResource(R.string.social_smoker),
                                stringResource(R.string.frequent_smoker),
                                stringResource(R.string.heavy_smoker)
                            ),
                            icon = Icons.Default.SmokingRooms
                        )
                    if (lifestyle.drinking_habit != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_drinking),
                            value = lifestyle.drinking_habit,
                            nouns = listOf(
                                stringResource(R.string.non_drinker),
                                stringResource(R.string.rare_drinker),
                                stringResource(R.string.social_drinker),
                                stringResource(R.string.frequent_drinker),
                                stringResource(R.string.heavy_drinker)
                            ),
                            icon = Icons.Default.LocalDrink
                        )
                    if (lifestyle.sexual_activity_level != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sexual_activity),
                            value = lifestyle.sexual_activity_level,
                            nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.low), stringResource(R.string.moderate), stringResource(R.string.high), stringResource(R.string.very_high)),
                            icon = Icons.Default.Favorite
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsSectionInProfile(profile: Profile) {
    val interestNameToResource = mapOf(
        // Global Interests
        "Music" to R.string.interest_music,
        "সঙ্গীত" to R.string.interest_music,           // Bengali
        "संगीत" to R.string.interest_music,           // Hindi
        "இசை" to R.string.interest_music,             // Tamil
        "ಸಂಗೀತ" to R.string.interest_music,           // Kannada
        "సంగీతం" to R.string.interest_music,          // Telugu

        "Movies" to R.string.interest_movies,
        "সিনেমা" to R.string.interest_movies,          // Bengali
        "फ़िल्में" to R.string.interest_movies,        // Hindi
        "சினிமா" to R.string.interest_movies,         // Tamil
        "ಸಿನೆಮಾ" to R.string.interest_movies,         // Kannada
        "సినిమాలు" to R.string.interest_movies,       // Telugu

        "Sports" to R.string.interest_sports,
        "খেলাধুলা" to R.string.interest_sports,         // Bengali
        "खेल" to R.string.interest_sports,              // Hindi
        "விளையாட்டு" to R.string.interest_sports,     // Tamil
        "ಕ್ರೀಡೆ" to R.string.interest_sports,           // Kannada
        "క్రీడలు" to R.string.interest_sports,          // Telugu

        "Books" to R.string.interest_books,
        "বই" to R.string.interest_books,               // Bengali
        "किताबें" to R.string.interest_books,           // Hindi
        "புத்தகங்கள்" to R.string.interest_books,       // Tamil
        "ಪುಸ್ತಕಗಳು" to R.string.interest_books,        // Kannada
        "పుస్తకాలు" to R.string.interest_books,         // Telugu

        "Travel" to R.string.interest_travel,
        "ভ্রমণ" to R.string.interest_travel,           // Bengali
        "यात्रा" to R.string.interest_travel,           // Hindi
        "பயணம்" to R.string.interest_travel,           // Tamil
        "ಪ್ರಯಾಣ" to R.string.interest_travel,          // Kannada
        "ప్రయాణం" to R.string.interest_travel,         // Telugu

        "Fitness" to R.string.interest_fitness,
        "ফিটনেস" to R.string.interest_fitness,          // Bengali
        "फ़िटनेस" to R.string.interest_fitness,         // Hindi
        "உடற்பயிற்சி" to R.string.interest_fitness,     // Tamil
        "ಫಿಟ್ನೆಸ್" to R.string.interest_fitness,         // Kannada
        "ఫిట్నెస్" to R.string.interest_fitness,        // Telugu

        "Art" to R.string.interest_art,
        "শিল্প" to R.string.interest_art,               // Bengali
        "कला" to R.string.interest_art,                 // Hindi
        "கலை" to R.string.interest_art,                 // Tamil
        "ಕಲೆ" to R.string.interest_art,                  // Kannada
        "కళ" to R.string.interest_art,                   // Telugu

        "Gaming" to R.string.interest_gaming,
        "গেমিং" to R.string.interest_gaming,             // Bengali
        "गेमिंग" to R.string.interest_gaming,            // Hindi
        "வீடியோ கேமிங்" to R.string.interest_gaming,     // Tamil
        "ಗೇಮಿಂಗ್" to R.string.interest_gaming,           // Kannada
        "వీడియో గేమింగ్" to R.string.interest_gaming,    // Telugu

        "Photography" to R.string.interest_photography,
        "ফটোগ্রাফি" to R.string.interest_photography,    // Bengali
        "फ़ोटोग्राफी" to R.string.interest_photography,  // Hindi
        "புகைப்படக்கலை" to R.string.interest_photography, // Tamil
        "ಛಾಯಾಗ್ರಹಣ" to R.string.interest_photography,     // Kannada
        "ఫోటోగ్రఫీ" to R.string.interest_photography,    // Telugu

        "Cooking" to R.string.interest_cooking,
        "রান্না" to R.string.interest_cooking,            // Bengali
        "खाना बनाना" to R.string.interest_cooking,       // Hindi
        "சமைத்தல்" to R.string.interest_cooking,          // Tamil
        "ಅಡಿಗೆ" to R.string.interest_cooking,             // Kannada
        "వండటం" to R.string.interest_cooking,             // Telugu

        "Dancing" to R.string.interest_dancing,
        "নাচ" to R.string.interest_dancing,               // Bengali
        "नृत्य" to R.string.interest_dancing,             // Hindi
        "நிறைவடைதல்" to R.string.interest_dancing,       // Tamil
        "ನೃತ್ಯ" to R.string.interest_dancing,              // Kannada
        "నృత్యం" to R.string.interest_dancing,            // Telugu

        "Gardening" to R.string.interest_gardening,
        "বাগান করা" to R.string.interest_gardening,       // Bengali
        "बागवानी" to R.string.interest_gardening,         // Hindi
        "தோட்டக்கலை" to R.string.interest_gardening,      // Tamil
        "ತೋಟಗಾರಿಕೆ" to R.string.interest_gardening,       // Kannada
        "తోటపనులు" to R.string.interest_gardening,        // Telugu

        "Technology" to R.string.interest_technology,
        "প্রযুক্তি" to R.string.interest_technology,        // Bengali
        "प्रौद्योगिकी" to R.string.interest_technology,     // Hindi
        "தொழில்நுட்பம்" to R.string.interest_technology,    // Tamil
        "ತಂತ್ರಜ್ಞಾನ" to R.string.interest_technology,      // Kannada
        "సాంకేతికత" to R.string.interest_technology,      // Telugu

        "Fashion" to R.string.interest_fashion,
        "ফ্যাশন" to R.string.interest_fashion,              // Bengali
        "फ़ैशन" to R.string.interest_fashion,               // Hindi
        "வசுதேவம்" to R.string.interest_fashion,            // Tamil
        "ಫ್ಯಾಷನ್" to R.string.interest_fashion,              // Kannada
        "ఫ్యాషన్" to R.string.interest_fashion,             // Telugu

        "Volunteering" to R.string.interest_volunteering,
        "স্বেচ্ছাসেবা" to R.string.interest_volunteering,   // Bengali
        "स्वयंसेवा" to R.string.interest_volunteering,       // Hindi
        "தன்னார்வ சேவை" to R.string.interest_volunteering,   // Tamil
        "ಸ್ವಯಂಸೇವೆ" to R.string.interest_volunteering,        // Kannada
        "స్వచ్ఛంద సేవ" to R.string.interest_volunteering,     // Telugu

        "Pets & Animals" to R.string.interest_pets,
        "পোষ্য" to R.string.interest_pets,                   // Bengali
        "पालतू जानवर" to R.string.interest_pets,             // Hindi
        "விலங்குகள்" to R.string.interest_pets,               // Tamil
        "ಜಾನುವಾರುಗಳು" to R.string.interest_pets,             // Kannada
        "పిల్లులు & జంతువులు" to R.string.interest_pets,      // Telugu

        "Food" to R.string.interest_food,
        "খাবার" to R.string.interest_food,                     // Bengali
        "भोजन" to R.string.interest_food,                     // Hindi
        "உணவு" to R.string.interest_food,                      // Tamil
        "ಆಹಾರ" to R.string.interest_food,                      // Kannada
        "ఆహారం" to R.string.interest_food,                      // Telugu

        "Nature" to R.string.interest_nature,
        "প্রকৃতি" to R.string.interest_nature,                 // Bengali
        "प्रकृति" to R.string.interest_nature,                  // Hindi
        "இயற்கை" to R.string.interest_nature,                   // Tamil
        "ಪ್ರಕೃತಿ" to R.string.interest_nature,                  // Kannada
        "ప్రకృతి" to R.string.interest_nature,                  // Telugu

        // Social & Community
        "Charity work" to R.string.interest_charity,
        "দান কার্যক্রম" to R.string.interest_charity,         // Bengali
        "चैरिटी कार्य" to R.string.interest_charity,           // Hindi
        "நன்னை செயல்" to R.string.interest_charity,            // Tamil
        "ಧಾರ್ಮಿಕ ಕೆಲಸ" to R.string.interest_charity,           // Kannada
        "చారిటీ పనులు" to R.string.interest_charity,           // Telugu

        "Community organizing" to R.string.interest_community,
        "কমিউনিটি সংগঠন" to R.string.interest_community,      // Bengali
        "समुदाय आयोजन" to R.string.interest_community,         // Hindi
        "சமூகம் அமைத்தல்" to R.string.interest_community,       // Tamil
        "ಸಮುದಾಯ ಸಂಘಟನೆ" to R.string.interest_community,     // Kannada
        "క‌మ్యూనిటీ నిర్వాహనం" to R.string.interest_community,  // Telugu

        "Networking" to R.string.interest_networking,
        "নেটওয়ার্কিং" to R.string.interest_networking,        // Bengali
        "नेटवर्किंग" to R.string.interest_networking,           // Hindi
        "பிணையம்" to R.string.interest_networking,              // Tamil
        "ನೆಟ್ವರ್ಕಿಂಗ್" to R.string.interest_networking,         // Kannada
        "నెట్‌వర్కింగ్" to R.string.interest_networking,        // Telugu

        "Public speaking" to R.string.interest_public_speaking,
        "পাবলিক স্পিকিং" to R.string.interest_public_speaking, // Bengali
        "पब्लिक स्पीकिंग" to R.string.interest_public_speaking, // Hindi
        "பொது பேச்சு" to R.string.interest_public_speaking,      // Tamil
        "ಸಾರ್ವಜನಿಕ ಭಾಷಣ" to R.string.interest_public_speaking, // Kannada
        "పబ్లిక్ స్పీకింగ్" to R.string.interest_public_speaking, // Telugu

        "Writing" to R.string.interest_writing,
        "লেখা" to R.string.interest_writing,                    // Bengali
        "लेखन" to R.string.interest_writing,                     // Hindi
        "எழுத்து" to R.string.interest_writing,                   // Tamil
        "ಲೇಖನ" to R.string.interest_writing,                      // Kannada
        "రాత" to R.string.interest_writing,                       // Telugu

        "Blogging" to R.string.interest_blogging,
        "ব্লগিং" to R.string.interest_blogging,                  // Bengali
        "ब्लॉगिंग" to R.string.interest_blogging,                // Hindi
        "வலைப்பதிவு" to R.string.interest_blogging,             // Tamil
        "ಬ್ಲಾಗಿಂಗ್" to R.string.interest_blogging,               // Kannada
        "బ్లాగింగ్" to R.string.interest_blogging,               // Telugu

        "Podcasting" to R.string.interest_podcasting,
        "পডকাস্টিং" to R.string.interest_podcasting,             // Bengali
        "पॉडकास्टिंग" to R.string.interest_podcasting,           // Hindi
        "பாட்காஸ்டிங்" to R.string.interest_podcasting,           // Tamil
        "ಪಾಡ್‌ಕಾಸ್ಟಿಂಗ್" to R.string.interest_podcasting,        // Kannada
        "పోడ్కాస్టింగ్" to R.string.interest_podcasting,         // Telugu

        "Social media" to R.string.interest_social_media,
        "সোশ্যাল মিডিয়া" to R.string.interest_social_media,     // Bengali
        "सोशल मीडिया" to R.string.interest_social_media,         // Hindi
        "சமூக ஊடகம்" to R.string.interest_social_media,        // Tamil
        "ಸೋಷಿಯಲ್ ಮೀಡಿಯಾ" to R.string.interest_social_media,     // Kannada
        "సోషల్ మీడియా" to R.string.interest_social_media,      // Telugu

        "Online communities" to R.string.interest_online_communities,
        "অনলাইন কমিউনিটি" to R.string.interest_online_communities,    // Bengali
        "ऑनलाइन समुदाय" to R.string.interest_online_communities,       // Hindi
        "ஆன்லைன் சமூகங்கள்" to R.string.interest_online_communities,   // Tamil
        "ಆನ್‌ಲೈನ್ ಸಮುದಾಯಗಳು" to R.string.interest_online_communities,  // Kannada
        "ఆన్లైన్ కమ్యూనిటీస్" to R.string.interest_online_communities,  // Telugu

        // Adventurous & Thrilling
        "Skydiving" to R.string.interest_skydiving,
        "স্কাইডাইভিং" to R.string.interest_skydiving,              // Bengali
        "स्काईडाइविंग" to R.string.interest_skydiving,             // Hindi
        "வான்வீழ்ச்சி" to R.string.interest_skydiving,               // Tamil
        "ಸ್ಕೈಡೈವಿಂಗ್" to R.string.interest_skydiving,             // Kannada
        "స్కైవ్‌డైవింగ్" to R.string.interest_skydiving,           // Telugu

        "Scuba diving" to R.string.interest_scuba_diving,
        "স্কুবা ডাইভিং" to R.string.interest_scuba_diving,           // Bengali
        "स्कूबा डाइविंग" to R.string.interest_scuba_diving,          // Hindi
        "கடல் மூழ்குதல்" to R.string.interest_scuba_diving,           // Tamil
        "ಸ್ಕೂಬಾ ಡೈವಿಂಗ್" to R.string.interest_scuba_diving,         // Kannada
        "స్కూబా డైవింగ్" to R.string.interest_scuba_diving,         // Telugu

        "Rock climbing" to R.string.interest_rock_climbing,
        "রক ক্লাইমিং" to R.string.interest_rock_climbing,            // Bengali
        "रॉक क्लाइम्बिंग" to R.string.interest_rock_climbing,         // Hindi
        "கல்லூரி ஏறுதல்" to R.string.interest_rock_climbing,         // Tamil
        "ರಾಕ್ ಕ್ಲೈಂಬಿಂಗ್" to R.string.interest_rock_climbing,       // Kannada
        "రాక్ క్లైంబింగ్" to R.string.interest_rock_climbing,         // Telugu

        "Surfing" to R.string.interest_surfing,
        "সার্ফিং" to R.string.interest_surfing,                       // Bengali
        "सर्फिंग" to R.string.interest_surfing,                       // Hindi
        "அலைநோக்கம்" to R.string.interest_surfing,                    // Tamil
        "ಸರ್ಫಿಂಗ್" to R.string.interest_surfing,                     // Kannada
        "సర్ఫింగ్" to R.string.interest_surfing,                      // Telugu

        "Skiing" to R.string.interest_skiing,
        "স্কিইং" to R.string.interest_skiing,                         // Bengali
        "स्कीयिंग" to R.string.interest_skiing,                        // Hindi
        "அறிவியல்" to R.string.interest_skiing,                        // Tamil
        "ಸ್ಕೀಯಿಂಗ್" to R.string.interest_skiing,                     // Kannada
        "స్కీయింగ్" to R.string.interest_skiing,                      // Telugu

        "Snowboarding" to R.string.interest_snowboarding,
        "স্নোবোর্ডিং" to R.string.interest_snowboarding,               // Bengali
        "स्नोबोर्डिंग" to R.string.interest_snowboarding,              // Hindi
        "பனிச்சறுக்கல்" to R.string.interest_snowboarding,             // Tamil
        "ಸ್ನೋಬೋರ್ಡಿಂಗ್" to R.string.interest_snowboarding,            // Kannada
        "స్నోబోర్డింగ్" to R.string.interest_snowboarding,              // Telugu

        "Mountain biking" to R.string.interest_mountain_biking,
        "মাউন্টাইন বাইকিং" to R.string.interest_mountain_biking,         // Bengali
        "माउंटेन बाइकिंग" to R.string.interest_mountain_biking,           // Hindi
        "மலை சைக்கிள் ஓட்டுதல்" to R.string.interest_mountain_biking,       // Tamil
        "ಮೌಂಟೇನ್ ಬೈಕಿಂಗ್" to R.string.interest_mountain_biking,       // Kannada
        "మౌంటైన్ బైకింగ్" to R.string.interest_mountain_biking,         // Telugu

        "Motorcycling" to R.string.interest_motorcycling,
        "মোটরসাইক্লিং" to R.string.interest_motorcycling,                  // Bengali
        "मोटरसाइक्लिंग" to R.string.interest_motorcycling,                // Hindi
        "மோட்டார் சைக்கிள் ஓட்டுதல்" to R.string.interest_motorcycling,       // Tamil
        "ಮೋಟಾರ್ಸೈಕ್ಲಿಂಗ್" to R.string.interest_motorcycling,              // Kannada
        "మోటార్సైక్లింగ్" to R.string.interest_motorcycling,              // Telugu

        "Car racing" to R.string.interest_car_racing,
        "কার রেসিং" to R.string.interest_car_racing,                       // Bengali
        "कार रेसिंग" to R.string.interest_car_racing,                      // Hindi
        "கார் ஓட்டப்போட்டி" to R.string.interest_car_racing,                  // Tamil
        "ಕಾರ್ ರೇಸಿಂಗ್" to R.string.interest_car_racing,                   // Kannada
        "కార్ రేసింగ్" to R.string.interest_car_racing,                    // Telugu

        "Extreme sports" to R.string.interest_extreme_sports,
        "এক্সট্রিম স্পোর্টস" to R.string.interest_extreme_sports,            // Bengali
        "एक्सट्रीम स्पोर्ट्स" to R.string.interest_extreme_sports,            // Hindi
        "கூடுதல் விளையாட்டுகள்" to R.string.interest_extreme_sports,         // Tamil
        "ಎಕ್ಸ್ಟ್ರೀಮ್ ಕ್ರೀಡೆ" to R.string.interest_extreme_sports,             // Kannada
        "ఎక్స్‌ట్రీమ్ స్పోర్ట్స్" to R.string.interest_extreme_sports,         // Telugu

        // Relaxation & Leisure
        "Puzzles" to R.string.interest_puzzles,
        "ধাঁধা" to R.string.interest_puzzles,                               // Bengali
        "पहेलियाँ" to R.string.interest_puzzles,                            // Hindi
        "முயிர்ப்போஷிட்சி" to R.string.interest_puzzles,                      // Tamil
        "ಪ್ರಶ್ನೆಗಳು" to R.string.interest_puzzles,                           // Kannada
        "పజిల్స్" to R.string.interest_puzzles,                              // Telugu

        "Board games" to R.string.interest_board_games,
        "বোর্ড গেমস" to R.string.interest_board_games,                       // Bengali
        "बोर्ड गेम्स" to R.string.interest_board_games,                       // Hindi
        "பிள்ளைகள் விளையாட்டு" to R.string.interest_board_games,              // Tamil
        "ಬೋರ್ಡ್ ಗೇಮ್ಸ್" to R.string.interest_board_games,                     // Kannada
        "బోర్డ్ గేమ్స్" to R.string.interest_board_games,                     // Telugu

        "Video games" to R.string.interest_video_games,
        "ভিডিও গেমস" to R.string.interest_video_games,                       // Bengali
        "वीडियो गेम्स" to R.string.interest_video_games,                      // Hindi
        "வீடியோ விளையாட்டுகள்" to R.string.interest_video_games,              // Tamil
        "ವೀಡಿಯೊ ಗೇಮ್ಸ್" to R.string.interest_video_games,                     // Kannada
        "వీడియో గేమ్స్" to R.string.interest_video_games,                     // Telugu

        "Watching TV" to R.string.interest_watching_tv,
        "টিভি দেখা" to R.string.interest_watching_tv,                         // Bengali
        "टीवी देखना" to R.string.interest_watching_tv,                        // Hindi
        "தொலைக்காட்சி பார்ப்பது" to R.string.interest_watching_tv,             // Tamil
        "ಟಿವಿ ವೀಕ್ಷಣೆ" to R.string.interest_watching_tv,                       // Kannada
        "టీవీ వీక్షణ" to R.string.interest_watching_tv,                       // Telugu

        "Napping" to R.string.interest_napping,
        "ন্যাপিং" to R.string.interest_napping,                                // Bengali
        "नैपिंग" to R.string.interest_napping,                                // Hindi
        "கண்மூசப்பொழுது" to R.string.interest_napping,                        // Tamil
        "ನಾಪಿಂಗ್" to R.string.interest_napping,                               // Kannada
        "నాపింగ్" to R.string.interest_napping,                               // Telugu

        "Spa days" to R.string.interest_spa_days,
        "স্পা ডে" to R.string.interest_spa_days,                               // Bengali
        "स्पा दिन" to R.string.interest_spa_days,                              // Hindi
        "ஸ்பா நாட்கள்" to R.string.interest_spa_days,                          // Tamil
        "ಸ್ಪಾ ದಿನಗಳು" to R.string.interest_spa_days,                           // Kannada
        "స్పా రోజులు" to R.string.interest_spa_days,                           // Telugu

        "Beach days" to R.string.interest_beach_days,
        "বিচ ডে" to R.string.interest_beach_days,                              // Bengali
        "बीच डे" to R.string.interest_beach_days,                               // Hindi
        "கடல் நாட்கள்" to R.string.interest_beach_days,                          // Tamil
        "ಬೀಚ್ ದಿನಗಳು" to R.string.interest_beach_days,                         // Kannada
        "బీచ్ రోజులు" to R.string.interest_beach_days,                          // Telugu

        "Picnics" to R.string.interest_picnics,
        "পিকনিক" to R.string.interest_picnics,                                 // Bengali
        "पिकनिक" to R.string.interest_picnics,                                 // Hindi
        "பட்ஜெட் உணவு" to R.string.interest_picnics,                            // Tamil
        "ಪಿಕ್ನಿಕ್" to R.string.interest_picnics,                                 // Kannada
        "పిక్నిక్" to R.string.interest_picnics,                                 // Telugu

        // Tech & Intellectual
        "Coding" to R.string.interest_coding,
        "কোডিং" to R.string.interest_coding,                                   // Bengali
        "कोडिंग" to R.string.interest_coding,                                   // Hindi
        "குறியாக்கம்" to R.string.interest_coding,                               // Tamil
        "ಕೋಡಿಂಗ್" to R.string.interest_coding,                                   // Kannada
        "కోడింగ్" to R.string.interest_coding,                                   // Telugu

        "Robotics" to R.string.interest_robotics,
        "রোবোটিক্স" to R.string.interest_robotics,                             // Bengali
        "रोबोटिक्स" to R.string.interest_robotics,                             // Hindi
        "இயந்திரவியல்" to R.string.interest_robotics,                          // Tamil
        "ರೋಬೋಟಿಕ್ಸ್" to R.string.interest_robotics,                             // Kannada
        "రోబోటిక్స్" to R.string.interest_robotics,                             // Telugu

        "Space exploration" to R.string.interest_space,
        "মহাকাশ অন্বেষণ" to R.string.interest_space,                            // Bengali
        "अंतरिक्ष अन्वेषण" to R.string.interest_space,                           // Hindi
        "வான் ஆய்வு" to R.string.interest_space,                                  // Tamil
        "ಅಂತರಿಕ್ಷ ಅನ್ವೇಷಣೆ" to R.string.interest_space,                         // Kannada
        "అంతరిక్ష అన్వేషణ" to R.string.interest_space,                           // Telugu

        "Environmentalism" to R.string.interest_environmentalism,
        "পরিবেশবাদ" to R.string.interest_environmentalism,                      // Bengali
        "पर्यावरणवाद" to R.string.interest_environmentalism,                     // Hindi
        "சுற்றுப்புறச் சங்கம்" to R.string.interest_environmentalism,               // Tamil
        "ಪರಿಸರವಾದ" to R.string.interest_environmentalism,                       // Kannada
        "పర్యావరణవాదం" to R.string.interest_environmentalism,                     // Telugu

        // Food & Drink
        "Baking" to R.string.interest_baking,
        "বেকিং" to R.string.interest_baking,                                     // Bengali
        "बैकिंग" to R.string.interest_baking,                                    // Hindi
        "அடக்கு" to R.string.interest_baking,                                      // Tamil
        "ബേക്കിംഗ്" to R.string.interest_baking,                                    // Kannada
        "బేకింగ్" to R.string.interest_baking,                                    // Telugu

        "Wine tasting" to R.string.interest_wine_tasting,
        "ওয়াইন টেস্টিং" to R.string.interest_wine_tasting,                       // Bengali
        "वाइन चखना" to R.string.interest_wine_tasting,                            // Hindi
        "சாரம் சுவைத்தல்" to R.string.interest_wine_tasting,                         // Tamil
        "വൈൻ രുചി" to R.string.interest_wine_tasting,                             // Kannada
        "వైన్ రుచిచూడడం" to R.string.interest_wine_tasting,                         // Telugu

        "Craft beer" to R.string.interest_craft_beer,
        "ক্রাফ্ট বিয়ার" to R.string.interest_craft_beer,                             // Bengali
        "क्राफ्ट बियर" to R.string.interest_craft_beer,                             // Hindi
        "கைவினை மது" to R.string.interest_craft_beer,                             // Tamil
        "ക്രാഫ്റ്റ് ബീർ" to R.string.interest_craft_beer,                              // Kannada
        "క్రాఫ్ట్ బీర్" to R.string.interest_craft_beer,                             // Telugu

        "Coffee" to R.string.interest_coffee,
        "কফি" to R.string.interest_coffee,                                           // Bengali
        "कॉफ़ी" to R.string.interest_coffee,                                          // Hindi
        "காபி" to R.string.interest_coffee,                                           // Tamil
        "காபி" to R.string.interest_coffee,                                           // Kannada
        "కాఫీ" to R.string.interest_coffee,                                           // Telugu

        // Wellness & Spiritual
        "Yoga" to R.string.interest_yoga,
        "যোগ" to R.string.interest_yoga,                                             // Bengali
        "योग" to R.string.interest_yoga,                                             // Hindi
        "யோகா" to R.string.interest_yoga,                                            // Tamil
        "ಯೋಗ" to R.string.interest_yoga,                                             // Kannada
        "యోగ" to R.string.interest_yoga,                                             // Telugu

        "Meditation" to R.string.interest_meditation,
        "ধ্যান" to R.string.interest_meditation,                                      // Bengali
        "ध्यान" to R.string.interest_meditation,                                      // Hindi
        "தியானம்" to R.string.interest_meditation,                                   // Tamil
        "ಧ್ಯಾನ" to R.string.interest_meditation,                                     // Kannada
        "ధ్యానం" to R.string.interest_meditation,                                    // Telugu

        "Astrology" to R.string.interest_astrology,
        "জ্যোতিষ" to R.string.interest_astrology,                                     // Bengali
        "ज्योतिष" to R.string.interest_astrology,                                      // Hindi
        "ஜோதிடம்" to R.string.interest_astrology,                                    // Tamil
        "ಜ್ಯೋತಿಷ್ಯ" to R.string.interest_astrology,                                    // Kannada
        "జ్యోతిష్యం" to R.string.interest_astrology,                                   // Telugu

        "Romance" to R.string.interest_romance,
        "রোমান্স" to R.string.interest_romance,                                        // Bengali
        "रोमांस" to R.string.interest_romance,                                         // Hindi
        "காதல்" to R.string.interest_romance,                                           // Tamil
        "ರೊಮಾಂಸ್" to R.string.interest_romance,                                        // Kannada
        "రోమాన్స్" to R.string.interest_romance,                                         // Telugu

        "Crystals" to R.string.interest_crystals,
        "ক্রিস্টালস" to R.string.interest_crystals,                                     // Bengali
        "क्रिस्टल" to R.string.interest_crystals,                                       // Hindi
        "கிரிஸ்டல்கள்" to R.string.interest_crystals,                                   // Tamil
        "ಕ್ರಿಸ್ಟಲ್ಸ್" to R.string.interest_crystals,                                    // Kannada
        "క్రిస్టల్స్" to R.string.interest_crystals,                                    // Telugu

        // Style & DIY
        "Vintage clothing" to R.string.interest_vintage_clothing,
        "ভিন্টেজ পোশাক" to R.string.interest_vintage_clothing,                         // Bengali
        "विंटेज कपड़े" to R.string.interest_vintage_clothing,                           // Hindi
        "பழமையான ஆடை" to R.string.interest_vintage_clothing,                         // Tamil
        "ವಿಂಟೇಜ್ ಬಟ್ಟೆ" to R.string.interest_vintage_clothing,                          // Kannada
        "వింటేజ్ వస్త్రాలు" to R.string.interest_vintage_clothing,                       // Telugu

        "Thrift shopping" to R.string.interest_thrift_shopping,
        "থ্রিফট শপিং" to R.string.interest_thrift_shopping,                             // Bengali
        "थ्रिफ्ट शॉपिंग" to R.string.interest_thrift_shopping,                           // Hindi
        "தள்ளுபடி ஷாப்பிங்" to R.string.interest_thrift_shopping,                         // Tamil
        "ಥ್ರಿಫ್ಟ್ ಶಾಪಿಂಗ್" to R.string.interest_thrift_shopping,                        // Kannada
        "థ్రిఫ్ట్ షాపింగ్" to R.string.interest_thrift_shopping,                        // Telugu

        "DIY projects" to R.string.interest_diy,
        "ডিআইওয়াই প্রকল্প" to R.string.interest_diy,                                 // Bengali
        "डीआईवाई प्रोजेक्ट" to R.string.interest_diy,                                 // Hindi
        "நீங்கள் செய்து கொள்ளும் திட்டங்கள்" to R.string.interest_diy,                    // Tamil
        "DIY ಯೋಜನೆಗಳು" to R.string.interest_diy,                                       // Kannada
        "DIY ప్రాజెక్టులు" to R.string.interest_diy,                                    // Telugu

        "Home improvement" to R.string.interest_home_improvement,
        "গৃহ উন্নয়ন" to R.string.interest_home_improvement,                           // Bengali
        "गृह सुधार" to R.string.interest_home_improvement,                             // Hindi
        "வீட்டுத் திருத்தம்" to R.string.interest_home_improvement,                      // Tamil
        "ಮನೆ ಸುಧಾರಣೆ" to R.string.interest_home_improvement,                           // Kannada
        "ఇంటిని మెరుగుపరచడం" to R.string.interest_home_improvement,                   // Telugu

        "Interior design" to R.string.interest_interior_design,
        "অভ্যন্তরীণ নকশা" to R.string.interest_interior_design,                         // Bengali
        "इंटीरियर डिज़ाइन" to R.string.interest_interior_design,                        // Hindi
        "உள்ளமைப்பு வடிவமைப்பு" to R.string.interest_interior_design,                    // Tamil
        "ಆಂತರಿಕ ವಿನ್ಯಾಸ" to R.string.interest_interior_design,                            // Kannada
        "అంతర్గత రూపకల్పన" to R.string.interest_interior_design,                        // Telugu

        // Intellectual & Tech
        "History" to R.string.interest_history,
        "ইতিহাস" to R.string.interest_history,                                          // Bengali
        "इतिहास" to R.string.interest_history,                                          // Hindi
        "வரலாறு" to R.string.interest_history,                                           // Tamil
        "ಇತಿಹಾಸ" to R.string.interest_history,                                           // Kannada
        "చరిత్ర" to R.string.interest_history,                                            // Telugu

        "Science" to R.string.interest_science,
        "বিজ্ঞান" to R.string.interest_science,                                         // Bengali
        "विज्ञान" to R.string.interest_science,                                         // Hindi
        "அறிவியல்" to R.string.interest_science,                                        // Tamil
        "ವಿಜ್ಞಾನ" to R.string.interest_science,                                          // Kannada
        "విజ్ఞానం" to R.string.interest_science,                                        // Telugu

        "Philosophy" to R.string.interest_philosophy,
        "দর্শন শাস্ত্র" to R.string.interest_philosophy,                                  // Bengali
        "दर्शनशास्त्र" to R.string.interest_philosophy,                                  // Hindi
        "தத்துவம்" to R.string.interest_philosophy,                                       // Tamil
        "ದರ್ಶನಶಾಸ್ತ್ರ" to R.string.interest_philosophy,                                   // Kannada
        "తత్వశాస్త్రం" to R.string.interest_philosophy,                                   // Telugu

        "Politics" to R.string.interest_politics,
        "রাজনীতি" to R.string.interest_politics,                                         // Bengali
        "राजनीति" to R.string.interest_politics,                                         // Hindi
        "அரசியல்" to R.string.interest_politics,                                         // Tamil
        "ರಾಜಕೀಯ" to R.string.interest_politics,                                         // Kannada
        "రాజకీయ శాస్త్రం" to R.string.interest_politics,                                // Telugu

        "Economics" to R.string.interest_economics,
        "অর্থনীতি" to R.string.interest_economics,                                       // Bengali
        "अर्थशास्त्र" to R.string.interest_economics,                                      // Hindi
        "பொருளாதாரம்" to R.string.interest_economics,                                    // Tamil
        "ಅರ್ಥಶಾಸ್ತ್ರ" to R.string.interest_economics,                                      // Kannada
        "ఆర్థిక శాస్త్రం" to R.string.interest_economics,                                  // Telugu

        // Outdoor & Nature
        "Hiking" to R.string.interest_hiking,
        "হাইকিং" to R.string.interest_hiking,                                            // Bengali
        "हाइकिंग" to R.string.interest_hiking,                                            // Hindi
        "ஹைகிங்" to R.string.interest_hiking,                                            // Tamil
        "ಹೈಕಿಂಗ್" to R.string.interest_hiking,                                            // Kannada
        "హైకింగ్" to R.string.interest_hiking,                                            // Telugu

        "Camping" to R.string.interest_camping,
        "ক্যাম্পিং" to R.string.interest_camping,                                          // Bengali
        "कैंपिंग" to R.string.interest_camping,                                           // Hindi
        "கேம்பிங்" to R.string.interest_camping,                                           // Tamil
        "ಕ್ಯಾಂಪಿಂಗ್" to R.string.interest_camping,                                         // Kannada
        "క్యాంపింగ్" to R.string.interest_camping,                                         // Telugu

        "Fishing" to R.string.interest_fishing,
        "মাছ ধরা" to R.string.interest_fishing,                                          // Bengali
        "मछली पकड़ना" to R.string.interest_fishing,                                      // Hindi
        "மீன் பிடித்தல்" to R.string.interest_fishing,                                     // Tamil
        "ಮೀನಿಂಗ್" to R.string.interest_fishing,                                          // Kannada
        "ఫిషింగ్" to R.string.interest_fishing,                                           // Telugu

        "Hunting" to R.string.interest_hunting,
        "শিকার" to R.string.interest_hunting,                                            // Bengali
        "शिकार" to R.string.interest_hunting,                                            // Hindi
        "வேட்டை" to R.string.interest_hunting,                                            // Tamil
        "ಹಂಟಿಂಗ್" to R.string.interest_hunting,                                          // Kannada
        "హంటింగ్" to R.string.interest_hunting,                                           // Telugu

        // Food & Drink
        "Baking" to R.string.interest_baking,
        "বেকিং" to R.string.interest_baking,                                            // Bengali
        "बैकिंग" to R.string.interest_baking,                                           // Hindi
        "அடுக்கு" to R.string.interest_baking,                                           // Tamil
        "ബേക്കിംഗ്" to R.string.interest_baking,                                           // Kannada
        "బేకింగ్" to R.string.interest_baking,                                           // Telugu

        "Wine tasting" to R.string.interest_wine_tasting,
        "ওয়াইন টেস্টিং" to R.string.interest_wine_tasting,                            // Bengali
        "वाइन चखना" to R.string.interest_wine_tasting,                                 // Hindi
        "சாரம் சுவைத்தல்" to R.string.interest_wine_tasting,                              // Tamil
        "വൈൻ രുചി" to R.string.interest_wine_tasting,                                   // Kannada
        "వైన్ రుచిచూడడం" to R.string.interest_wine_tasting,                              // Telugu

        "Craft beer" to R.string.interest_craft_beer,
        "ক্রাফ্ট বিয়ার" to R.string.interest_craft_beer,                                 // Bengali
        "क्राफ्ट बियर" to R.string.interest_craft_beer,                                 // Hindi
        "கைவினை மது" to R.string.interest_craft_beer,                                 // Tamil
        "ക്രാഫ്റ്റ് ബീർ" to R.string.interest_craft_beer,                                  // Kannada
        "క్రాఫ్ట్ బీర్" to R.string.interest_craft_beer,                                 // Telugu

        "Coffee" to R.string.interest_coffee,
        "কফি" to R.string.interest_coffee,                                              // Bengali
        "कॉफ़ी" to R.string.interest_coffee,                                             // Hindi
        "காபி" to R.string.interest_coffee,                                             // Tamil
        "ಕಾಫಿ" to R.string.interest_coffee,                                              // Kannada
        "కాఫీ" to R.string.interest_coffee,                                              // Telugu

        // Wellness & Spiritual
        "Yoga" to R.string.interest_yoga,
        "যোগ" to R.string.interest_yoga,                                                // Bengali
        "योग" to R.string.interest_yoga,                                                // Hindi
        "யோகா" to R.string.interest_yoga,                                               // Tamil
        "ಯೋಗ" to R.string.interest_yoga,                                                // Kannada
        "యోగ" to R.string.interest_yoga,                                                // Telugu

        "Meditation" to R.string.interest_meditation,
        "ধ্যান" to R.string.interest_meditation,                                         // Bengali
        "ध्यान" to R.string.interest_meditation,                                         // Hindi
        "தியானம்" to R.string.interest_meditation,                                      // Tamil
        "ಧ್ಯಾನ" to R.string.interest_meditation,                                        // Kannada
        "ధ్యానం" to R.string.interest_meditation,                                      // Telugu

        "Astrology" to R.string.interest_astrology,
        "জ্যোতিষ" to R.string.interest_astrology,                                        // Bengali
        "ज्योतिष" to R.string.interest_astrology,                                         // Hindi
        "ஜோதிடம்" to R.string.interest_astrology,                                       // Tamil
        "ಜ್ಯೋತಿಷ್ಯ" to R.string.interest_astrology,                                       // Kannada
        "జ్యోతిష్యం" to R.string.interest_astrology,                                      // Telugu

        "Romance" to R.string.interest_romance,
        "রোমান্স" to R.string.interest_romance,                                          // Bengali
        "रोमांस" to R.string.interest_romance,                                           // Hindi
        "காதல்" to R.string.interest_romance,                                             // Tamil
        "ರೊಮಾಂಸ್" to R.string.interest_romance,                                          // Kannada
        "రోమాన్స్" to R.string.interest_romance,                                           // Telugu

        "Crystals" to R.string.interest_crystals,
        "ক্রিস্টালস" to R.string.interest_crystals,                                       // Bengali
        "क्रिस्टल" to R.string.interest_crystals,                                         // Hindi
        "கிரிஸ்டல்கள்" to R.string.interest_crystals,                                     // Tamil
        "ಕ್ರಿಸ್ಟಲ್ಸ್" to R.string.interest_crystals,                                       // Kannada
        "క్రిస్టల్స్" to R.string.interest_crystals,                                       // Telugu

        // Style & DIY
        "Vintage clothing" to R.string.interest_vintage_clothing,
        "ভিন্টেজ পোশাক" to R.string.interest_vintage_clothing,                           // Bengali
        "विंटेज कपड़े" to R.string.interest_vintage_clothing,                             // Hindi
        "பழமையான ஆடை" to R.string.interest_vintage_clothing,                           // Tamil
        "ವಿಂಟೇಜ್ ಬಟ್ಟೆ" to R.string.interest_vintage_clothing,                              // Kannada
        "వింటేజ్ వస్త్రాలు" to R.string.interest_vintage_clothing,                         // Telugu

        "Thrift shopping" to R.string.interest_thrift_shopping,
        "থ্রিফট শপিং" to R.string.interest_thrift_shopping,                               // Bengali
        "थ्रिफ्ट शॉपिंग" to R.string.interest_thrift_shopping,                             // Hindi
        "தள்ளுபடி ஷாப்பிங்" to R.string.interest_thrift_shopping,                           // Tamil
        "ಥ್ರಿಫ್ಟ್ ಶಾಪಿಂಗ್" to R.string.interest_thrift_shopping,                            // Kannada
        "థ్రిఫ్ట్ షాపింగ్" to R.string.interest_thrift_shopping,                            // Telugu

        "DIY projects" to R.string.interest_diy,
        "ডিআইওয়াই প্রকল্প" to R.string.interest_diy,                                     // Bengali
        "डीआईवाई प्रोजेक्ट" to R.string.interest_diy,                                     // Hindi
        "நீங்கள் செய்து கொள்ளும் திட்டங்கள்" to R.string.interest_diy,                        // Tamil
        "DIY ಯೋಜನೆಗಳು" to R.string.interest_diy,                                            // Kannada
        "DIY ప్రాజెక్టులు" to R.string.interest_diy,                                        // Telugu

        "Home improvement" to R.string.interest_home_improvement,
        "গৃহ উন্নয়ন" to R.string.interest_home_improvement,                               // Bengali
        "गृह सुधार" to R.string.interest_home_improvement,                                 // Hindi
        "வீட்டு மேம்பாடு" to R.string.interest_home_improvement,                             // Tamil
        "ಮನೆ ಸುಧಾರಣೆ" to R.string.interest_home_improvement,                               // Kannada
        "ఇంటి మెరుగుదల" to R.string.interest_home_improvement,                             // Telugu

        "Interior design" to R.string.interest_interior_design,
        "অভ্যন্তরীণ নকশা" to R.string.interest_interior_design,                             // Bengali
        "इंटीरियर डिज़ाइन" to R.string.interest_interior_design,                            // Hindi
        "உள்ளமைத்து வடிவமைப்பு" to R.string.interest_interior_design,                       // Tamil
        "ಆಂತರಿಕ ವಿನ್ಯಾಸ" to R.string.interest_interior_design,                               // Kannada
        "అంతర్గత రూపకల్పన" to R.string.interest_interior_design,                            // Telugu

        // Adventurous & Thrilling
        "Skydiving" to R.string.interest_skydiving,
        "স্কাইডাইভিং" to R.string.interest_skydiving,                                      // Bengali
        "स्काईडाइविंग" to R.string.interest_skydiving,                                     // Hindi
        "வான்வீழ்ச்சி" to R.string.interest_skydiving,                                       // Tamil
        "ಸ್ಕೈಡೈವಿಂಗ್" to R.string.interest_skydiving,                                      // Kannada
        "స్కైవ్‌డైవింగ్" to R.string.interest_skydiving,                                   // Telugu

        "Scuba diving" to R.string.interest_scuba_diving,
        "স্কুবা ডাইভিং" to R.string.interest_scuba_diving,                                   // Bengali
        "स्कूबा डाइविंग" to R.string.interest_scuba_diving,                                  // Hindi
        "கடல் மூழ்குதல்" to R.string.interest_scuba_diving,                                   // Tamil
        "ಸ್ಕೂಬಾ ಡೈವಿಂಗ್" to R.string.interest_scuba_diving,                                 // Kannada
        "స్కూబా డైవింగ్" to R.string.interest_scuba_diving,                                 // Telugu

        "Rock climbing" to R.string.interest_rock_climbing,
        "রক ক্লাইমিং" to R.string.interest_rock_climbing,                                   // Bengali
        "रॉक क्लाइम्बिंग" to R.string.interest_rock_climbing,                                // Hindi
        "கல்லூரி ஏறுதல்" to R.string.interest_rock_climbing,                                // Tamil
        "ರಾಕ್ ಕ್ಲೈಂಬಿಂಗ್" to R.string.interest_rock_climbing,                              // Kannada
        "రాక్ క్లైంబింగ్" to R.string.interest_rock_climbing,                                // Telugu

        "Surfing" to R.string.interest_surfing,
        "সার্ফিং" to R.string.interest_surfing,                                             // Bengali
        "सर्फिंग" to R.string.interest_surfing,                                             // Hindi
        "அலைபாய்ச்சி" to R.string.interest_surfing,                                          // Tamil
        "ಸರ್ಫಿಂಗ್" to R.string.interest_surfing,                                            // Kannada
        "సర్ఫింగ్" to R.string.interest_surfing,                                             // Telugu

        "Skiing" to R.string.interest_skiing,
        "স্কিইং" to R.string.interest_skiing,                                               // Bengali
        "स्कीयिंग" to R.string.interest_skiing,                                              // Hindi
        "அரிசல் செலுத்துதல்" to R.string.interest_skiing,                                    // Tamil
        "ಸ್ಕೀಯಿಂಗ್" to R.string.interest_skiing,                                           // Kannada
        "స్కీయింగ్" to R.string.interest_skiing,                                            // Telugu

        "Snowboarding" to R.string.interest_snowboarding,
        "স্নোবোর্ডিং" to R.string.interest_snowboarding,                                      // Bengali
        "स्नोबोर्डिंग" to R.string.interest_snowboarding,                                     // Hindi
        "மஞ்சுப் பலகை சறுக்குதல்" to R.string.interest_snowboarding,                          // Tamil
        "ಸ್ನೋಬೋರ್ಡಿಂಗ್" to R.string.interest_snowboarding,                                   // Kannada
        "స్నోబోర్డింగ్" to R.string.interest_snowboarding,                                     // Telugu

        "Mountain biking" to R.string.interest_mountain_biking,
        "মাউন্টাইন বাইকিং" to R.string.interest_mountain_biking,                               // Bengali
        "माउंटेन बाइकिंग" to R.string.interest_mountain_biking,                                 // Hindi
        "மலை சைக்கிள் ஓட்டுதல்" to R.string.interest_mountain_biking,                          // Tamil
        "ಮೌಂಟೇನ್ ಬೈಕಿಂಗ್" to R.string.interest_mountain_biking,                             // Kannada
        "మౌంటైన్ బైకింగ్" to R.string.interest_mountain_biking,                             // Telugu

        "Motorcycling" to R.string.interest_motorcycling,
        "মোটরসাইক্লিং" to R.string.interest_motorcycling,                                      // Bengali
        "मोटरसाइक्लिंग" to R.string.interest_motorcycling,                                    // Hindi
        "மோட்டார் சைக்கிள் ஓட்டுதல்" to R.string.interest_motorcycling,                         // Tamil
        "ಮೋಟಾರ್ಸೈಕ್ಲಿಂಗ್" to R.string.interest_motorcycling,                                 // Kannada
        "మోటార్సైక్లింగ్" to R.string.interest_motorcycling,                                   // Telugu

        "Car racing" to R.string.interest_car_racing,
        "কার রেসিং" to R.string.interest_car_racing,                                           // Bengali
        "कार रेसिंग" to R.string.interest_car_racing,                                          // Hindi
        "கார் ஓட்டப்போட்டி" to R.string.interest_car_racing,                                     // Tamil
        "ಕಾರ್ ರೇಸಿಂಗ್" to R.string.interest_car_racing,                                       // Kannada
        "కార్ రేసింగ్" to R.string.interest_car_racing,                                        // Telugu

        "Extreme sports" to R.string.interest_extreme_sports,
        "এক্সট্রিম স্পোর্টস" to R.string.interest_extreme_sports,                                // Bengali
        "एक्सट्रीम स्पोर्ट्स" to R.string.interest_extreme_sports,                                // Hindi
        "அதிக விளையாட்டு" to R.string.interest_extreme_sports,                                  // Tamil
        "ಎಕ್ಸ್ಟ್ರೀಮ್ ಕ್ರೀಡೆ" to R.string.interest_extreme_sports,                                 // Kannada
        "ఎక్స్‌ట్రీమ్ స్పోర్ట్స్" to R.string.interest_extreme_sports,                           // Telugu

        // Relaxation & Leisure
        "Puzzles" to R.string.interest_puzzles,
        "ধাঁধা" to R.string.interest_puzzles,                                                 // Bengali
        "पहेलियाँ" to R.string.interest_puzzles,                                              // Hindi
        "முதிரடைபுதிர்கள்" to R.string.interest_puzzles,                                        // Tamil
        "ಪ್ರಶ್ನೆಗಳು" to R.string.interest_puzzles,                                            // Kannada
        "పజిల్స్" to R.string.interest_puzzles,                                               // Telugu

        "Board games" to R.string.interest_board_games,
        "বোর্ড গেমস" to R.string.interest_board_games,                                         // Bengali
        "बोर्ड गेम्स" to R.string.interest_board_games,                                         // Hindi
        "தகடுப் பந்திகள்" to R.string.interest_board_games,                                    // Tamil
        "ಬೋರ್ಡ್ ಗೇಮ್ಸ್" to R.string.interest_board_games,                                       // Kannada
        "బోర్డ్ గేమ్స్" to R.string.interest_board_games,                                       // Telugu

        "Video games" to R.string.interest_video_games,
        "ভিডিও গেমস" to R.string.interest_video_games,                                         // Bengali
        "वीडियो गेम्स" to R.string.interest_video_games,                                        // Hindi
        "வீடியோ விளையாட்டுகள்" to R.string.interest_video_games,                                 // Tamil
        "ವೀಡಿಯೊ ಗೇಮ್ಸ್" to R.string.interest_video_games,                                      // Kannada
        "వీడియో గేమ్స్" to R.string.interest_video_games,                                       // Telugu

        "Watching TV" to R.string.interest_watching_tv,
        "টিভি দেখা" to R.string.interest_watching_tv,                                           // Bengali
        "टीवी देखना" to R.string.interest_watching_tv,                                          // Hindi
        "தொலைக்காட்சி பார்க்குதல்" to R.string.interest_watching_tv,                             // Tamil
        "ಟಿವಿ ವೀಕ್ಷಣೆ" to R.string.interest_watching_tv,                                         // Kannada
        "టీవీ వీక్షణ" to R.string.interest_watching_tv,                                         // Telugu

        "Napping" to R.string.interest_napping,
        "ন্যাপিং" to R.string.interest_napping,                                                  // Bengali
        "नैपिंग" to R.string.interest_napping,                                                  // Hindi
        "கண்மூசப்பொழுது" to R.string.interest_napping,                                          // Tamil
        "ನಾಪಿಂಗ್" to R.string.interest_napping,                                                 // Kannada
        "నాపింగ్" to R.string.interest_napping,                                                 // Telugu

        "Spa days" to R.string.interest_spa_days,
        "স্পা ডে" to R.string.interest_spa_days,                                                 // Bengali
        "स्पा दिन" to R.string.interest_spa_days,                                                // Hindi
        "ஸ்பா நாட்கள்" to R.string.interest_spa_days,                                              // Tamil
        "ಸ್ಪಾ ದಿನಗಳು" to R.string.interest_spa_days,                                             // Kannada
        "స్పా రోజులు" to R.string.interest_spa_days,                                             // Telugu

        "Beach days" to R.string.interest_beach_days,
        "বিচ ডে" to R.string.interest_beach_days,                                                // Bengali
        "बीच डे" to R.string.interest_beach_days,                                              // Hindi
        "கடல் நாட்கள்" to R.string.interest_beach_days,                                           // Tamil
        "ಬೀಚ್ ದಿನಗಳು" to R.string.interest_beach_days,                                         // Kannada
        "బీచ్ రోజులు" to R.string.interest_beach_days,                                           // Telugu

        "Picnics" to R.string.interest_picnics,
        "পিকনিক" to R.string.interest_picnics,                                                  // Bengali
        "पिकनिक" to R.string.interest_picnics,                                                  // Hindi
        "புனிச்சல்பூட்டி" to R.string.interest_picnics,                                            // Tamil
        "ಪಿಕ್ನಿಕ್" to R.string.interest_picnics,                                                  // Kannada
        "పిక్నిక్" to R.string.interest_picnics,                                                  // Telugu

        // Tech & Intellectual
        "Coding" to R.string.interest_coding,
        "কোডিং" to R.string.interest_coding,                                                     // Bengali
        "कोडिंग" to R.string.interest_coding,                                                     // Hindi
        "குறியாக்கம்" to R.string.interest_coding,                                                 // Tamil
        "ಕೋಡಿಂಗ್" to R.string.interest_coding,                                                     // Kannada
        "కోడింగ్" to R.string.interest_coding,                                                     // Telugu

        "Robotics" to R.string.interest_robotics,
        "রোবোটিক্স" to R.string.interest_robotics,                                               // Bengali
        "रोबोटिक्स" to R.string.interest_robotics,                                               // Hindi
        "இயந்திரவியல்" to R.string.interest_robotics,                                            // Tamil
        "ರೋಬೋಟಿಕ್ಸ್" to R.string.interest_robotics,                                               // Kannada
        "రోబోటిక్స్" to R.string.interest_robotics,                                               // Telugu

        "Space exploration" to R.string.interest_space,
        "মহাকাশ অন্বেষণ" to R.string.interest_space,                                              // Bengali
        "अंतरिक्ष अन्वेषण" to R.string.interest_space,                                             // Hindi
        "வான ஆய்வு" to R.string.interest_space,                                                    // Tamil
        "ಅಂತರಿಕ್ಷ ಅನ್ವೇಷಣೆ" to R.string.interest_space,                                           // Kannada
        "అంతరిక్ష అన్వేషణ" to R.string.interest_space,                                             // Telugu

        "Environmentalism" to R.string.interest_environmentalism,
        "পরিবেশবাদ" to R.string.interest_environmentalism,                                        // Bengali
        "पर्यावरणवाद" to R.string.interest_environmentalism,                                      // Hindi
        "சுற்றுச்சூழலியல்" to R.string.interest_environmentalism,                                  // Tamil
        "ಪರಿಸರವಾದ" to R.string.interest_environmentalism,                                           // Kannada
        "పర్యావరణవాదం" to R.string.interest_environmentalism,                                      // Telugu

        // Wellness & Spiritual
        "Yoga" to R.string.interest_yoga,
        "যোগ" to R.string.interest_yoga,                                                         // Bengali
        "योग" to R.string.interest_yoga,                                                         // Hindi
        "யோகா" to R.string.interest_yoga,                                                        // Tamil
        "ಯೋಗ" to R.string.interest_yoga,                                                         // Kannada
        "యోగ" to R.string.interest_yoga,                                                         // Telugu

        "Meditation" to R.string.interest_meditation,
        "ধ্যান" to R.string.interest_meditation,                                                  // Bengali
        "ध्यान" to R.string.interest_meditation,                                                  // Hindi
        "தியானம்" to R.string.interest_meditation,                                               // Tamil
        "ಧ್ಯಾನ" to R.string.interest_meditation,                                                 // Kannada
        "ధ్యానం" to R.string.interest_meditation,                                               // Telugu

        "Astrology" to R.string.interest_astrology,
        "জ্যোতিষ" to R.string.interest_astrology,                                                 // Bengali
        "ज्योतिष" to R.string.interest_astrology,                                                  // Hindi
        "ஜோதிடம்" to R.string.interest_astrology,                                                // Tamil
        "ಜ್ಯೋತಿಷ್ಯ" to R.string.interest_astrology,                                                // Kannada
        "జ్యోతిష్యం" to R.string.interest_astrology,                                               // Telugu

        "Romance" to R.string.interest_romance,
        "রোমান্স" to R.string.interest_romance,                                                   // Bengali
        "रोमांस" to R.string.interest_romance,                                                   // Hindi
        "காதல்" to R.string.interest_romance,                                                     // Tamil
        "ರೊಮಾಂಸ್" to R.string.interest_romance,                                                  // Kannada
        "రోమాన్స్" to R.string.interest_romance,                                                     // Telugu

        "Crystals" to R.string.interest_crystals,
        "ক্রিস্টালস" to R.string.interest_crystals,                                               // Bengali
        "क्रिस्टल" to R.string.interest_crystals,                                                 // Hindi
        "கிரிஸ்டல்கள்" to R.string.interest_crystals,                                             // Tamil
        "ಕ್ರಿಸ್ಟಲ್ಸ್" to R.string.interest_crystals,                                              // Kannada
        "క్రిస్టల్స్" to R.string.interest_crystals,                                              // Telugu

        // Style & DIY
        "Vintage clothing" to R.string.interest_vintage_clothing,
        "ভিন্টেজ পোশাক" to R.string.interest_vintage_clothing,                                   // Bengali
        "विंटेज कपड़े" to R.string.interest_vintage_clothing,                                     // Hindi
        "பழமையான ஆடை" to R.string.interest_vintage_clothing,                                   // Tamil
        "ವಿಂಟೇಜ್ ಬಟ್ಟೆ" to R.string.interest_vintage_clothing,                                      // Kannada
        "వింటేజ్ వస్త్రాలు" to R.string.interest_vintage_clothing,                                 // Telugu

        "Thrift shopping" to R.string.interest_thrift_shopping,
        "থ্রিফট শপিং" to R.string.interest_thrift_shopping,                                         // Bengali
        "थ्रिफ्ट शॉपिंग" to R.string.interest_thrift_shopping,                                       // Hindi
        "தள்ளுபடி ஷாப்பிங்" to R.string.interest_thrift_shopping,                                   // Tamil
        "ಥ್ರಿಫ್ಟ್ ಶಾಪಿಂಗ್" to R.string.interest_thrift_shopping,                                    // Kannada
        "థ్రిఫ్ట్ షాపింగ్" to R.string.interest_thrift_shopping,                                    // Telugu

        "DIY projects" to R.string.interest_diy,
        "ডিআইওয়াই প্রকল্প" to R.string.interest_diy,                                             // Bengali
        "डीआईवाई प्रोजेक्ट" to R.string.interest_diy,                                             // Hindi
        "நீங்கள் செய்யும் திட்டங்கள்" to R.string.interest_diy,                                       // Tamil
        "DIY ಯೋಜನೆಗಳು" to R.string.interest_diy,                                                  // Kannada
        "DIY ప్రాజెక్టులు" to R.string.interest_diy,                                              // Telugu

        "Home improvement" to R.string.interest_home_improvement,
        "গৃহ উন্নয়ন" to R.string.interest_home_improvement,                                       // Bengali
        "गृह सुधार" to R.string.interest_home_improvement,                                        // Hindi
        "வீட்டு மேம்பாடு" to R.string.interest_home_improvement,                                     // Tamil
        "ಮನೆ ಸುಧಾರಣೆ" to R.string.interest_home_improvement,                                       // Kannada
        "ఇంటి మెరుగుదల" to R.string.interest_home_improvement,                                     // Telugu

        "Interior design" to R.string.interest_interior_design,
        "অভ্যন্তরীণ নকশা" to R.string.interest_interior_design,                                     // Bengali
        "इंटीरियर डिज़ाइन" to R.string.interest_interior_design,                                   // Hindi
        "உள்ளமைப்பு வடிவமைப்பு" to R.string.interest_interior_design,                               // Tamil
        "ಆಂತರಿಕ ವಿನ್ಯಾಸ" to R.string.interest_interior_design,                                      // Kannada
        "అంతర్గత రూపకల్పన" to R.string.interest_interior_design,                                   // Telugu

        // Adventurous & Thrilling
        "Skydiving" to R.string.interest_skydiving,
        "স্কাইডাইভিং" to R.string.interest_skydiving,                                              // Bengali
        "स्काईडाइविंग" to R.string.interest_skydiving,                                            // Hindi
        "வான்வீழ்ச்சி" to R.string.interest_skydiving,                                             // Tamil
        "ಸ್ಕೈಡೈವಿಂಗ್" to R.string.interest_skydiving,                                            // Kannada
        "స్కైవ్‌డైవింగ్" to R.string.interest_skydiving,                                         // Telugu

        "Scuba diving" to R.string.interest_scuba_diving,
        "স্কুবা ডাইভিং" to R.string.interest_scuba_diving,                                         // Bengali
        "स्कूबा डाइविंग" to R.string.interest_scuba_diving,                                        // Hindi
        "கடல்கீழ் மூழ்குதல்" to R.string.interest_scuba_diving,                                    // Tamil
        "ಸ್ಕೂಬಾ ಡೈವಿಂಗ್" to R.string.interest_scuba_diving,                                       // Kannada
        "స్కూబా డైవింగ్" to R.string.interest_scuba_diving,                                       // Telugu

        "Rock climbing" to R.string.interest_rock_climbing,
        "রক ক্লাইমিং" to R.string.interest_rock_climbing,                                          // Bengali
        "रॉक क्लाइम्बिंग" to R.string.interest_rock_climbing,                                       // Hindi
        "கல்லூரிப் பறக்குது" to R.string.interest_rock_climbing,                                    // Tamil
        "ರಾಕ್ ಕ್ಲೈಂಬಿಂಗ್" to R.string.interest_rock_climbing,                                    // Kannada
        "రాక్ క్లైంబింగ్" to R.string.interest_rock_climbing,                                      // Telugu

        "Surfing" to R.string.interest_surfing,
        "সার্ফিং" to R.string.interest_surfing,                                                  // Bengali
        "सर्फिंग" to R.string.interest_surfing,                                                  // Hindi
        "அலைபாய்வு" to R.string.interest_surfing,                                                // Tamil
        "ಸರ್ಫಿಂಗ್" to R.string.interest_surfing,                                                // Kannada
        "సర్ఫింగ్" to R.string.interest_surfing,                                                // Telugu

        "Skiing" to R.string.interest_skiing,
        "স্কিইং" to R.string.interest_skiing,                                                    // Bengali
        "स्कीयिंग" to R.string.interest_skiing,                                                   // Hindi
        "மண்படா சறுக்கல்" to R.string.interest_skiing,                                              // Tamil
        "ಸ್ಕೀಯಿಂಗ್" to R.string.interest_skiing,                                                 // Kannada
        "స్కీయింగ్" to R.string.interest_skiing,                                                  // Telugu

        "Snowboarding" to R.string.interest_snowboarding,
        "স্নোবোর্ডিং" to R.string.interest_snowboarding,                                          // Bengali
        "स्नोबोर्डिंग" to R.string.interest_snowboarding,                                         // Hindi
        "மஞ்சுப் பலகை சறுக்குதல்" to R.string.interest_snowboarding,                                 // Tamil
        "ಸ್ನೋಬೋರ್ಡಿಂಗ್" to R.string.interest_snowboarding,                                        // Kannada
        "స్నోబోర్డింగ్" to R.string.interest_snowboarding,                                        // Telugu

        "Mountain biking" to R.string.interest_mountain_biking,
        "মাউন্টাইন বাইকিং" to R.string.interest_mountain_biking,                                   // Bengali
        "माउंटेन बाइकिंग" to R.string.interest_mountain_biking,                                   // Hindi
        "மலைசுழற்சி ஓட்டம்" to R.string.interest_mountain_biking,                                  // Tamil
        "ಮೌಂಟೇನ್ ಬೈಕಿಂಗ್" to R.string.interest_mountain_biking,                                 // Kannada
        "మౌంటైన్ బైకింగ్" to R.string.interest_mountain_biking,                                 // Telugu

        "Motorcycling" to R.string.interest_motorcycling,
        "মোটরসাইক্লিং" to R.string.interest_motorcycling,                                          // Bengali
        "मोटरसाइक्लिंग" to R.string.interest_motorcycling,                                        // Hindi
        "மோட்டார் சைக்கிள் ஓட்டம்" to R.string.interest_motorcycling,                                 // Tamil
        "ಮೋಟಾರ್ಸೈಕ್ಲಿಂಗ್" to R.string.interest_motorcycling,                                       // Kannada
        "మోటార్సైక్లింగ్" to R.string.interest_motorcycling,                                       // Telugu

        "Car racing" to R.string.interest_car_racing,
        "কার রেসিং" to R.string.interest_car_racing,                                               // Bengali
        "कार रेसिंग" to R.string.interest_car_racing,                                              // Hindi
        "கார் ஓட்டப் போட்டி" to R.string.interest_car_racing,                                        // Tamil
        "ಕಾರ್ ರೇಸಿಂಗ್" to R.string.interest_car_racing,                                            // Kannada
        "కార్ రేసింగ్" to R.string.interest_car_racing,                                            // Telugu

        "Extreme sports" to R.string.interest_extreme_sports,
        "এক্সট্রিম স্পোর্টস" to R.string.interest_extreme_sports,                                     // Bengali
        "एक्सट्रीम स्पोर्ट्स" to R.string.interest_extreme_sports,                                     // Hindi
        "அதிக விளையாட்டு" to R.string.interest_extreme_sports,                                       // Tamil
        "ಎಕ್ಸ್ಟ್ರೀಮ್ ಕ್ರೀಡೆ" to R.string.interest_extreme_sports,                                     // Kannada
        "ఎక్స్‌ట్రీమ్ స్పోర్ట్స్" to R.string.interest_extreme_sports,                                 // Telugu

        // Relaxation & Leisure
        "Puzzles" to R.string.interest_puzzles,
        "ধাঁধা" to R.string.interest_puzzles,                                                       // Bengali
        "पहेलियाँ" to R.string.interest_puzzles,                                                    // Hindi
        "முதிரைபுதிர்கள்" to R.string.interest_puzzles,                                              // Tamil
        "ಪ್ರಶ್ನೆಗಳು" to R.string.interest_puzzles,                                                   // Kannada
        "పజిల్స్" to R.string.interest_puzzles,                                                      // Telugu

        "Board games" to R.string.interest_board_games,
        "বোর্ড গেমস" to R.string.interest_board_games,                                             // Bengali
        "बोर्ड गेम्स" to R.string.interest_board_games,                                             // Hindi
        "தகடுப் பந்திகள்" to R.string.interest_board_games,                                        // Tamil
        "ಬೋರ್ಡ್ ಗೇಮ್ಸ್" to R.string.interest_board_games,                                           // Kannada
        "బోర్డ్ గేమ్స్" to R.string.interest_board_games,                                           // Telugu

        "Video games" to R.string.interest_video_games,
        "ভিডিও গেমস" to R.string.interest_video_games,                                             // Bengali
        "वीडियो गेम्स" to R.string.interest_video_games,                                            // Hindi
        "வீடியோ விளையாட்டுகள்" to R.string.interest_video_games,                                     // Tamil
        "ವೀಡಿಯೊ ಗೇಮ್ಸ್" to R.string.interest_video_games,                                          // Kannada
        "వీడియో గేమ్స్" to R.string.interest_video_games,                                          // Telugu

        "Watching TV" to R.string.interest_watching_tv,
        "টিভি দেখা" to R.string.interest_watching_tv,                                               // Bengali
        "टीवी देखना" to R.string.interest_watching_tv,                                              // Hindi
        "தொலைக்காட்சி பார்ப்பது" to R.string.interest_watching_tv,                                 // Tamil
        "ಟಿವಿ ವೀಕ್ಷಣೆ" to R.string.interest_watching_tv,                                            // Kannada
        "టీవీ వీక్షణ" to R.string.interest_watching_tv,                                            // Telugu

        "Napping" to R.string.interest_napping,
        "ন্যাপিং" to R.string.interest_napping,                                                      // Bengali
        "नैपिंग" to R.string.interest_napping,                                                      // Hindi
        "கண்மூசப்பொழுது" to R.string.interest_napping,                                              // Tamil
        "ನಾಪಿಂಗ್" to R.string.interest_napping,                                                     // Kannada
        "నాపింగ్" to R.string.interest_napping,                                                     // Telugu

        "Spa days" to R.string.interest_spa_days,
        "স্পা ডে" to R.string.interest_spa_days,                                                     // Bengali
        "स्पा दिन" to R.string.interest_spa_days,                                                    // Hindi
        "ஸ்பா நாட்கள்" to R.string.interest_spa_days,                                                // Tamil
        "ಸ್ಪಾ ದಿನಗಳು" to R.string.interest_spa_days,                                                 // Kannada
        "స్పా రోజులు" to R.string.interest_spa_days,                                                 // Telugu

        "Beach days" to R.string.interest_beach_days,
        "বিচ ডে" to R.string.interest_beach_days,                                                    // Bengali
        "बीच डे" to R.string.interest_beach_days,                                                   // Hindi
        "கடல் நாட்கள்" to R.string.interest_beach_days,                                               // Tamil
        "ಬೀಚ್ ದಿನಗಳು" to R.string.interest_beach_days,                                              // Kannada
        "బీచ్ రోజులు" to R.string.interest_beach_days,                                              // Telugu

        "Picnics" to R.string.interest_picnics,
        "পিকনিক" to R.string.interest_picnics,                                                        // Bengali
        "पिकनिक" to R.string.interest_picnics,                                                        // Hindi
        "பட்ஜெட் உணவு" to R.string.interest_picnics,                                                    // Tamil
        "ಪಿಕ್ನಿಕ್" to R.string.interest_picnics,                                                        // Kannada
        "పిక్నిక్" to R.string.interest_picnics,                                                        // Telugu

        // Technological & Intellectual
        "Coding" to R.string.interest_coding,
        "কোডিং" to R.string.interest_coding,                                                           // Bengali
        "कोडिंग" to R.string.interest_coding,                                                           // Hindi
        "குறியாக்கம்" to R.string.interest_coding,                                                         // Tamil
        "ಕೋಡಿಂಗ್" to R.string.interest_coding,                                                           // Kannada
        "కోడింగ్" to R.string.interest_coding,                                                           // Telugu

        "Robotics" to R.string.interest_robotics,
        "রোবোটিক্স" to R.string.interest_robotics,                                                     // Bengali
        "रोबोटिक्स" to R.string.interest_robotics,                                                     // Hindi
        "இயந்திரவியல்" to R.string.interest_robotics,                                                    // Tamil
        "ರೋಬೋಟಿಕ್ಸ್" to R.string.interest_robotics,                                                     // Kannada
        "రోబోటిక్స్" to R.string.interest_robotics,                                                     // Telugu

        "Space exploration" to R.string.interest_space,
        "মহাকাশ অন্বেষণ" to R.string.interest_space,                                                    // Bengali
        "अंतरिक्ष अन्वेषण" to R.string.interest_space,                                                   // Hindi
        "வான ஆய்வு" to R.string.interest_space,                                                          // Tamil
        "ಅಂತರಿಕ್ಷ ಅನ್ವೇಷಣೆ" to R.string.interest_space,                                                 // Kannada
        "అంతరిక్ష అన్వేషణ" to R.string.interest_space,                                                   // Telugu

        "Environmentalism" to R.string.interest_environmentalism,
        "পরিবেশবাদ" to R.string.interest_environmentalism,                                              // Bengali
        "पर्यावरणवाद" to R.string.interest_environmentalism,                                            // Hindi
        "சுற்றுச்சூழலியல்" to R.string.interest_environmentalism,                                        // Tamil
        "ಪರಿಸರವಾದ" to R.string.interest_environmentalism,                                               // Kannada
        "పర్యావరణవాదం" to R.string.interest_environmentalism,                                            // Telugu

        // Global Interest
        "Traveling" to R.string.interest_traveling,
        "ভ্রমণ" to R.string.interest_traveling,                                                         // Bengali
        "यात्रा" to R.string.interest_traveling,                                                         // Hindi
        "பயணம்" to R.string.interest_traveling,                                                         // Tamil
        "ಪ್ರಯಾಣ" to R.string.interest_traveling,                                                        // Kannada
        "ప్రయాణం" to R.string.interest_traveling                                                          // Telugu
    )

    if (profile.interests.isEmpty()) {
        Text(
            text = stringResource(R.string.no_interests),
            color = Color.Gray,
            fontSize = 10.sp
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            profile.interests.forEach { interest ->
                val resourceId = interestNameToResource[interest.name]
                val interestLabel = if (resourceId != null) {
                    stringResource(resourceId)
                } else {
                    interest.name // Fallback to raw name if not found
                }
                InterestTag(
                    label = buildString {
                        if (!interest.emoji.isNullOrEmpty()) append("${interest.emoji} ")
                        append(interestLabel)
                    }
                )
            }
        }
    }
}

@Composable
fun CombinedBioVoiceEditSection(
    currentBio: String?,
    currentVoiceUrl: String?,
    profileViewModel: ProfileViewModel,
    onSave: (String, String?) -> Unit,
    onCancel: () -> Unit
) {
    val context    = LocalContext.current
    val storageRef = FirebaseRefs.storage.reference
    val filePath   = remember { File(context.filesDir, "voice_note_edit.mp3").absolutePath }

    // ——— Bio text —————————————————————————
    var bio by remember { mutableStateOf(currentBio.orEmpty()) }

    // ——— Voice state ——————————————————————
    var isRecording   by remember { mutableStateOf(false) }
    var isPlaying     by remember { mutableStateOf(false) }
    var isVoiceValid  by remember { mutableStateOf(true) }
    var voiceUri      by remember { mutableStateOf<Uri?>(currentVoiceUrl?.let(Uri::parse)) }
    var newVoiceUrl   by remember { mutableStateOf(currentVoiceUrl) }
    var isUploading   by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) }
    val mediaPlayer   = remember { MediaPlayer() }

    // validate duration <= 60s
    fun validateVoice() {
        try {
            MediaPlayer().apply {
                setDataSource(voiceUri?.path ?: filePath)
                prepare()
                voiceDuration = duration.toLong()
                release()
            }
            isVoiceValid = voiceDuration <= 60_000L
        } catch (e: Exception) {
            isVoiceValid = false
        }
    }

    // permissions launcher
    val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            isRecording = true
            profileViewModel.startVoiceRecording(context, filePath)
        }
    }

    val toggleRecording = {
        if (isRecording) {
            // stop recording & validate
            isRecording = false
            profileViewModel.stopVoiceRecording()
            voiceUri = Uri.fromFile(File(filePath))
            validateVoice()

            if (isVoiceValid && voiceUri != null) {
                isUploading = true
                profileViewModel.uploadVoiceToRealtime(storageRef, voiceUri!!) { downloadUrl ->
                    newVoiceUrl = downloadUrl
                    isUploading   = false
                }
            }
        } else {
            permLauncher.launch(permissions)
        }
    }

    // playback toggle
    val togglePlayback = {
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(voiceUri?.path ?: filePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
                isPlaying = true
            } catch (_: IOException) { }
        }
    }

    // track playback progress
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer.isPlaying) {
            voiceProgress = (mediaPlayer.currentPosition / voiceDuration.toFloat()).coerceIn(0f,1f)
            delay(200)
        }
        if (!mediaPlayer.isPlaying) {
            isPlaying     = false
            voiceProgress = 0f
        }
    }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // — Text Bio —
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text(stringResource(R.string.bio), color = Color.White) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(cursorColor = KupidxOrange)
        )
        Spacer(Modifier.height(16.dp))

        // — Voice recorder button —
        IconButton(
            onClick = toggleRecording,
            enabled = !isUploading
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(R.string.record_voice_bio),
                tint = if (isRecording) Color.Red else Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                    .clip(CircleShape)
            )
        }
        if (!isVoiceValid) {
            Text(
                text = stringResource(R.string.voice_bio_duration_error),
                color = Color.Red
            )
        }
        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))

        // — Playback UI with Trash Icon —
        voiceUri?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = togglePlayback) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying)
                            stringResource(R.string.pause_audio)
                        else
                            stringResource(R.string.tap_to_play)
                    )
                }
                Slider(
                    value = voiceProgress,
                    onValueChange = { },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (isPlaying) togglePlayback() // Stop playback if active
                    voiceUri = null
                    newVoiceUrl = null
                    File(filePath).delete() // Delete the local file
                    isVoiceValid = true
                    voiceProgress = 0f
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete voice",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // — Save / Cancel —
        ButtonRow(
            onSave    = { onSave(bio, newVoiceUrl) },
            onCancel  = onCancel,
            modifier  = Modifier.padding(top = 16.dp)
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreferencesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val notSelected = stringResource(R.string.not_selected)

    // Looking For
    val lookingForOptions = listOf(
        stringResource(R.string.looking_for_long_term),
        stringResource(R.string.looking_for_short_to_long),
        stringResource(R.string.looking_for_casual),
        stringResource(R.string.looking_for_dating),
        stringResource(R.string.looking_for_exclusive),
        stringResource(R.string.looking_for_romance),
        stringResource(R.string.looking_for_connection),
        stringResource(R.string.looking_for_partner),
        stringResource(R.string.looking_for_marriage),
    )
    var selectedLookingFor by remember { mutableStateOf(tempProfile.lookingFor.ifBlank { notSelected }) }

    // Love Language
    val loveLanguageOptions = listOf(
        stringResource(R.string.love_language_option_words_of_affirmation),
        stringResource(R.string.love_language_option_acts_of_service),
        stringResource(R.string.love_language_option_receiving_gifts),
        stringResource(R.string.love_language_option_quality_time),
        stringResource(R.string.love_language_option_physical_touch),
        stringResource(R.string.love_language_option_other)
    )
    var selectedLoveLanguage by remember { mutableStateOf(tempProfile.loveLanguage.ifBlank { notSelected }) }
    var customLoveLanguage by remember {
        mutableStateOf(
            if (selectedLoveLanguage == loveLanguageOptions.last())
                tempProfile.customLoveLanguage.orEmpty() else ""
        )
    }

    // Politics
    val politicsOptions = listOf(
        stringResource(R.string.politics_option_far_left),
        stringResource(R.string.politics_option_left),
        stringResource(R.string.politics_option_centre_left),
        stringResource(R.string.politics_option_centre),
        stringResource(R.string.politics_option_centre_right),
        stringResource(R.string.politics_option_right),
        stringResource(R.string.politics_option_far_right),
        stringResource(R.string.politics_option_liberal),
        stringResource(R.string.politics_option_conservative),
        stringResource(R.string.politics_option_moderate),
        stringResource(R.string.politics_option_socialist),
        stringResource(R.string.politics_option_communist),
        stringResource(R.string.politics_option_other)
    )
    var selectedPolitics by remember { mutableStateOf(tempProfile.politics.ifBlank { notSelected }) }
    var customPolitics by remember {
        mutableStateOf(
            if (selectedPolitics == politicsOptions.last())
                tempProfile.customPolitics.orEmpty() else ""
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Looking For ---
        Text(stringResource(R.string.looking_for_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lookingForOptions.forEach { option ->
                FilterChip(
                    selected = selectedLookingFor == option,
                    onClick = {
                        selectedLookingFor = if (selectedLookingFor == option) notSelected else option
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // --- Love Language ---
        Text(stringResource(R.string.love_language_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            loveLanguageOptions.forEach { option ->
                FilterChip(
                    selected = selectedLoveLanguage == option,
                    onClick = {
                        selectedLoveLanguage = if (selectedLoveLanguage == option) notSelected else option
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
        if (selectedLoveLanguage == loveLanguageOptions.last()) {
            OutlinedTextField(
                value = customLoveLanguage,
                onValueChange = { customLoveLanguage = it },
                label = {
                    Text(
                        stringResource(R.string.label_custom_love_language),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // --- Politics ---
        Text(stringResource(R.string.label_politics), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            politicsOptions.forEach { option ->
                FilterChip(
                    selected = selectedPolitics == option,
                    onClick = {
                        selectedPolitics = if (selectedPolitics == option) notSelected else option
                    },
                    label = { Text(option, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
        if (selectedPolitics == politicsOptions.last()) {
            OutlinedTextField(
                value = customPolitics,
                onValueChange = { customPolitics = it },
                label = {
                    Text(
                        stringResource(R.string.label_custom_politics),
                        fontSize = 12.sp,
                        color = Color(0xFFFF6F00)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        ButtonRow(
            onSave = {
                val updated = tempProfile.copy(
                    lookingFor   = selectedLookingFor.takeIf { it != notSelected } ?: "",
                    loveLanguage = selectedLoveLanguage.takeIf { it != notSelected } ?: "",
                    customLoveLanguage = if (selectedLoveLanguage == loveLanguageOptions.last())
                        customLoveLanguage.ifBlank { null } else null,
                    politics     = selectedPolitics.takeIf { it != notSelected } ?: "",
                    customPolitics = if (selectedPolitics == politicsOptions.last())
                        customPolitics.ifBlank { null } else null
                )
                onSave(updated)
            },
            onCancel = onCancel
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialCausesSection(profile: Profile) {
    if (profile.socialCauses.isEmpty()) {
        Text(stringResource(R.string.no_social_causes), color = Color.Gray, fontSize = 10.sp)
    } else {
        Column {
            Text(stringResource(R.string.social_causes), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement   = Arrangement.spacedBy(8.dp)) {
                profile.socialCauses.forEach { cause ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF6F00)
                    ) {
                        Text(
                            text = cause,
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialCausesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    // Load your predefined list from resources:
    val allCauses = stringArrayResource(R.array.social_causes_list).toList()

    // Track which causes are selected:
    val selectedCauses = remember {
        mutableStateListOf<String>().apply { addAll(tempProfile.socialCauses) }
    }

    val maxSelections = 5

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.social_causes),
            color = Color(0xFFFF6F00),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            allCauses.forEach { cause ->
                val isSelected = selectedCauses.contains(cause)
                val canSelectMore = selectedCauses.size < maxSelections

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selectedCauses.remove(cause)
                        } else if (canSelectMore) {
                            selectedCauses.add(cause)
                        }
                    },
                    enabled = isSelected || canSelectMore,
                    label = { Text(cause, fontSize = 12.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        if (selectedCauses.size > maxSelections) {
            Text(
                text = stringResource(R.string.max_social_causes_error, maxSelections),
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        ButtonRow(
            onSave   = { onSave(tempProfile.copy(socialCauses = selectedCauses.toList())) },
            onCancel = onCancel
        )
    }
}

@Composable
fun LifestyleEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var localLifestyle by remember { mutableStateOf(tempProfile.lifestyle ?: Lifestyle()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_indoor_outdoor),
            value = localLifestyle.indoor_outdoor_orientation,
            nouns = listOf(stringResource(R.string.very_indoorsy), stringResource(R.string.mostly_indoorsy), stringResource(R.string.balanced), stringResource(R.string.mostly_outdoorsy), stringResource(R.string.very_outdoorsy))
        ) { localLifestyle = localLifestyle.copy(indoor_outdoor_orientation = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_social_media),
            value = localLifestyle.social_media_engagement,
            nouns = listOf(stringResource(R.string.invisible), stringResource(R.string.watcher), stringResource(R.string.casual_viewer), stringResource(R.string.engager), stringResource(R.string.influencer))
        ) { localLifestyle = localLifestyle.copy(social_media_engagement = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_work_life_balance),
            value = localLifestyle.work_life_balance,
            nouns = listOf(stringResource(R.string.workaholic), stringResource(R.string.more_work_oriented), stringResource(R.string.balanced), stringResource(R.string.more_life_oriented), stringResource(R.string.relaxed))
        ) { localLifestyle = localLifestyle.copy(work_life_balance = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_exercise),
            value = localLifestyle.exercise_frequency,
            nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.rarely_active), stringResource(R.string.moderately_active), stringResource(R.string.active), stringResource(R.string.very_active))
        ) { localLifestyle = localLifestyle.copy(exercise_frequency = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_family_oriented),
            value = localLifestyle.family_orientated,
            nouns = listOf(stringResource(R.string.independent), stringResource(R.string.slightly_family_oriented), stringResource(R.string.balanced), stringResource(R.string.more_family_oriented), stringResource(R.string.very_family_oriented))
        ) { localLifestyle = localLifestyle.copy(family_orientated = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_sleep),
            value = localLifestyle.sleep_pattern,
            nouns = listOf(stringResource(R.string.early_riser), stringResource(R.string.morning_person), stringResource(R.string.balanced), stringResource(R.string.night_owl), stringResource(R.string.late_night_enthusiast))
        ) { localLifestyle = localLifestyle.copy(sleep_pattern = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_adventurousness),
            value = localLifestyle.adventurousness,
            nouns = listOf(stringResource(R.string.cautious), stringResource(R.string.slightly_adventurous), stringResource(R.string.moderately_adventurous), stringResource(R.string.adventurous), stringResource(R.string.thrill_seeker))
        ) { localLifestyle = localLifestyle.copy(adventurousness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_intellectual_curiosity),
            value = localLifestyle.intellectual_curiosity,
            nouns = listOf(stringResource(R.string.casual_thinker), stringResource(R.string.inquisitive), stringResource(R.string.knowledge_seeker), stringResource(R.string.intellectual), stringResource(R.string.philosopher))
        ) { localLifestyle = localLifestyle.copy(intellectual_curiosity = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_creative_expression),
            value = localLifestyle.creative_expression,
            nouns = listOf(stringResource(R.string.not_creative), stringResource(R.string.somewhat_creative), stringResource(R.string.creative), stringResource(R.string.very_creative), stringResource(R.string.artistic_genius))
        ) { localLifestyle = localLifestyle.copy(creative_expression = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_physical_fitness),
            value = localLifestyle.physical_fitness,
            nouns = listOf(stringResource(R.string.sedentary), stringResource(R.string.somewhat_fit), stringResource(R.string.fit), stringResource(R.string.athletic), stringResource(R.string.peak_fitness))
        ) { localLifestyle = localLifestyle.copy(physical_fitness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_spirituality),
            value = localLifestyle.spirituality_mindfulness,
            nouns = listOf(stringResource(R.string.not_spiritual), stringResource(R.string.occasionally_mindful), stringResource(R.string.balanced), stringResource(R.string.spiritual), stringResource(R.string.deeply_mindful))
        ) { localLifestyle = localLifestyle.copy(spirituality_mindfulness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_humor),
            value = localLifestyle.easy_goingness,
            nouns = listOf(stringResource(R.string.serious), stringResource(R.string.somewhat_easygoing), stringResource(R.string.balanced), stringResource(R.string.humorous), stringResource(R.string.life_of_the_party))
        ) { localLifestyle = localLifestyle.copy(easy_goingness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_professional_ambition),
            value = localLifestyle.professional_ambition,
            nouns = listOf(stringResource(R.string.relaxed), stringResource(R.string.occasionally_driven), stringResource(R.string.balanced), stringResource(R.string.ambitious), stringResource(R.string.high_ambitious))
        ) { localLifestyle = localLifestyle.copy(professional_ambition = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_environmental_awareness),
            value = localLifestyle.environmental_awareness,
            nouns = listOf(stringResource(R.string.not_conscious), stringResource(R.string.occasionally_conscious), stringResource(R.string.balanced), stringResource(R.string.eco_friendly), stringResource(R.string.eco_champion))
        ) { localLifestyle = localLifestyle.copy(environmental_awareness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.sports_enthusiast),
            value = localLifestyle.sports_enthusiasm,
            nouns = listOf(stringResource(R.string.non_sports), stringResource(R.string.casual_viewer), stringResource(R.string.occasional_player), stringResource(R.string.sports_enthusiast), stringResource(R.string.sports_fanatic))
        ) { localLifestyle = localLifestyle.copy(sports_enthusiasm = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_political_awareness),
            value = localLifestyle.political_awareness,
            nouns = listOf(stringResource(R.string.unaware), stringResource(R.string.occasionally_aware), stringResource(R.string.balanced), stringResource(R.string.aware), stringResource(R.string.politically_engaged))
        ) { localLifestyle = localLifestyle.copy(political_awareness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_community_engagement),
            value = localLifestyle.community_engagement,
            nouns = listOf(stringResource(R.string.individualistic), stringResource(R.string.occasionally_involved), stringResource(R.string.balanced), stringResource(R.string.community_oriented), stringResource(R.string.community_leader))
        ) { localLifestyle = localLifestyle.copy(community_engagement = it) }
        // NEW SLIDERS ADDED:
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_sociability),
            value = localLifestyle.sociability,
            nouns = listOf(stringResource(R.string.not_introverted), stringResource(R.string.slightly_introverted), stringResource(R.string.moderately_introverted), stringResource(R.string.very_introverted), stringResource(R.string.extremely_introverted))
        ) { localLifestyle = localLifestyle.copy(sociability = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_smoking),
            value = localLifestyle.smoking_habit,
            nouns = listOf(stringResource(R.string.non_smoker), stringResource(R.string.rare_smoker), stringResource(R.string.social_smoker), stringResource(R.string.frequent_smoker), stringResource(R.string.heavy_smoker))
        ) { localLifestyle = localLifestyle.copy(smoking_habit = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_drinking),
            value = localLifestyle.drinking_habit,
            nouns = listOf(stringResource(R.string.non_drinker), stringResource(R.string.rare_drinker), stringResource(R.string.social_drinker), stringResource(R.string.frequent_drinker), stringResource(R.string.heavy_drinker))
        ) { localLifestyle = localLifestyle.copy(drinking_habit = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_sexual_activity),
            value = localLifestyle.sexual_activity_level,
            nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.low), stringResource(R.string.moderate), stringResource(R.string.high), stringResource(R.string.very_high))
        ) { localLifestyle = localLifestyle.copy(sexual_activity_level = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_culinary_enthusiasm),
            value = localLifestyle.culinary_enthusiasm,
            nouns = listOf(
                stringResource(R.string.not_a_foodie),
                stringResource(R.string.occasional_foodie),
                stringResource(R.string.foodie),
                stringResource(R.string.passionate_foodie),
                stringResource(R.string.gourmet)
            )
        ) { localLifestyle = localLifestyle.copy(culinary_enthusiasm = it) }
        Spacer(modifier = Modifier.height(16.dp))
        ButtonRow(
            onSave = {
                val updatedProfile = tempProfile.copy(lifestyle = localLifestyle)
                scope.launch {
                    updateProfileInFirebase(updatedProfile)
                    onSave(updatedProfile)
                }
            },
            onCancel = onCancel
        )
    }
}

@Composable
fun ProfileCollapsibleSections(
    profile: Profile,
    profileViewModel: ProfileViewModel,
    onProfileUpdated: (Profile) -> Unit
) {
    val isPremium by profileViewModel.isPremium.collectAsState(false)

    // keep a local tempProfile and re-sync whenever the parent `profile` updates
    var tempProfile by remember { mutableStateOf(profile) }
    LaunchedEffect(profile) {
        tempProfile = profile
    }

    var editBioVoice by rememberSaveable { mutableStateOf(false) }
    var editBasic by rememberSaveable { mutableStateOf(false) }
    var editPreferences by rememberSaveable { mutableStateOf(false) }
    var editSocialCauses by rememberSaveable { mutableStateOf(false) } // New
    var editLifestyle by rememberSaveable { mutableStateOf(false) }
    var editInterests by rememberSaveable { mutableStateOf(false) }

    // apply the bio’s font size globally to all Text in this tree:
    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(fontSize = 16.sp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        if (tempProfile.isMatrimonyMode) {
            var showMatrimony by rememberSaveable { mutableStateOf(false) }
            var editMatrimony by rememberSaveable { mutableStateOf(false) }
            CollapsibleSection(
                title = stringResource(R.string.section_matrimony_info),
                icon = Icons.Default.Cake,
                editMode = editMatrimony,
                onEditToggle = { editMatrimony = !editMatrimony }
            ) {
                if (editMatrimony) {
                    MatrimonyInfoEditSection(
                        tempProfile = tempProfile,
                        onSave = { updatedProfile ->
                            onProfileUpdated(updatedProfile)
                            editMatrimony = false
                        },
                        onCancel = { editMatrimony = false }
                    )
                } else {
                    MatrimonyInfoSection(profile = tempProfile)
                }
            }
        }
        val isPremium by profileViewModel.isPremium.collectAsState(false)

        CollapsibleSection(
            title       = stringResource(R.string.section_bio_and_voice),
            icon        = Icons.Default.Mic,         // or pick a merged icon
            editMode    = editBioVoice,
            onEditToggle= { editBioVoice = !editBioVoice }
        ) {
            if (editBioVoice) {
                CombinedBioVoiceEditSection(
                    currentBio         = tempProfile.bio,
                    currentVoiceUrl    = tempProfile.voiceNoteUrl,
                    profileViewModel   = profileViewModel,
                    onSave             = { newBio, newVoiceUrl ->
                        val updated = tempProfile.copy(bio = newBio, voiceNoteUrl = newVoiceUrl)
                        profileViewModel.saveProfileUpdated(
                            updated,
                            onSuccess = {
                                tempProfile = updated
                                onProfileUpdated(updated)
                                editBioVoice = false
                            }
                        )
                    },
                    onCancel = {
                        editBioVoice = false
                    }
                )
            } else {
                // display read‐only
                Text(
                    text = tempProfile.bio ?: stringResource(R.string.bio_no_bio),
                    color = Color.White, fontSize = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                if (!tempProfile.voiceNoteUrl.isNullOrEmpty()) {
                    VoicePlayer(url = tempProfile.voiceNoteUrl!!)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_basic_information),
            icon = Icons.Default.Person,
            editMode = editBasic,
            onEditToggle = { editBasic = !editBasic }
        ) {
            if (editBasic) {
                BasicInfoEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editBasic = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editBasic = false
                    }
                )
            } else {
                BasicInfoSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_preferences),
            icon = Icons.Default.Favorite,
            editMode = editPreferences,
            onEditToggle = { editPreferences = !editPreferences }
        ) {
            if (editPreferences) {
                PreferencesEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editPreferences = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editPreferences = false
                    }
                )
            } else {
                PreferencesSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_lifestyle_attributes),
            icon = Icons.Default.Nature,
            editMode = editLifestyle,
            onEditToggle = { editLifestyle = !editLifestyle }
        ) {
            if (editLifestyle) {
                LifestyleEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editLifestyle = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editLifestyle = false
                    }
                )
            } else {
                LifestyleSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_interests),
            icon = Icons.Default.Star,
            editMode = editInterests,
            onEditToggle = { editInterests = !editInterests }
        ) {
            if (editInterests) {
                InterestsEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editInterests = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editInterests = false
                    }
                )
            } else {
                InterestsSectionInProfile(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_social_causes),
            icon = Icons.Default.VolunteerActivism, // New section
            editMode = editSocialCauses,
            onEditToggle = { editSocialCauses = !editSocialCauses }
        ) {
            if (editSocialCauses) {
                SocialCausesEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editSocialCauses = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editSocialCauses = false
                    }
                )
            } else {
                SocialCausesSection(tempProfile)
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    // Reuse the same mapping from raw interest name → stringResource(id)
    val interestNameToResource = mapOf(
        // Global Interests
        "Traveling" to R.string.interest_traveling,
        "ভ্রমণ" to R.string.interest_traveling,        // Bengali
        "यात्रा" to R.string.interest_traveling,       // Hindi
        "பயணம்" to R.string.interest_traveling,       // Tamil
        "ಪ್ರಯಾಣ" to R.string.interest_traveling,      // Kannada
        "ప్రయాణం" to R.string.interest_traveling,     // Telugu

        "Music" to R.string.interest_music,
        "সঙ্গীত" to R.string.interest_music,           // Bengali
        "संगीत" to R.string.interest_music,           // Hindi
        "இசை" to R.string.interest_music,             // Tamil
        "ಸಂಗೀತ" to R.string.interest_music,           // Kannada
        "సంగీతం" to R.string.interest_music,          // Telugu

        "Food" to R.string.interest_food,
        "খাবার" to R.string.interest_food,             // Bengali
        "भोजन" to R.string.interest_food,             // Hindi
        "உணவு" to R.string.interest_food,              // Tamil
        "ಆಹಾರ" to R.string.interest_food,              // Kannada
        "ఆహారం" to R.string.interest_food,              // Telugu

        "Sports" to R.string.interest_sports,
        "খেলাধুলা" to R.string.interest_sports,         // Bengali
        "खेल" to R.string.interest_sports,              // Hindi
        "விளையாட்டு" to R.string.interest_sports,      // Tamil
        "ಕ್ರೀಡೆ" to R.string.interest_sports,           // Kannada
        "క్రీడలు" to R.string.interest_sports,          // Telugu

        "Movies" to R.string.interest_movies,
        "সিনেমা" to R.string.interest_movies,            // Bengali
        "फ़िल्में" to R.string.interest_movies,          // Hindi
        "சினிமா" to R.string.interest_movies,           // Tamil
        "ಸಿನೆಮಾ" to R.string.interest_movies,           // Kannada
        "సినిమాలు" to R.string.interest_movies,         // Telugu

        "Books" to R.string.interest_books,
        "বই" to R.string.interest_books,                // Bengali
        "किताबें" to R.string.interest_books,            // Hindi
        "புத்தகங்கள்" to R.string.interest_books,        // Tamil
        "ಪುಸ್ತಕಗಳು" to R.string.interest_books,         // Kannada
        "పుస్తకాలు" to R.string.interest_books,         // Telugu

        "Art" to R.string.interest_art,
        "শিল্প" to R.string.interest_art,                // Bengali
        "कला" to R.string.interest_art,                  // Hindi
        "கலை" to R.string.interest_art,                 // Tamil
        "ಕಲೆ" to R.string.interest_art,                  // Kannada
        "కళ" to R.string.interest_art,                   // Telugu

        "Photography" to R.string.interest_photography,
        "ফটোগ্রাফি" to R.string.interest_photography,   // Bengali
        "फ़ोटोग्राफी" to R.string.interest_photography, // Hindi
        "புகைப்படக்கலை" to R.string.interest_photography, // Tamil
        "ಛಾಯಾಯ ukudಕಲೆ" to R.string.interest_photography, // Kannada
        "ఫోటోగ్రఫీ" to R.string.interest_photography,   // Telugu

        "Gaming" to R.string.interest_gaming,
        "গেমিং" to R.string.interest_gaming,              // Bengali
        "गेमिंग" to R.string.interest_gaming,             // Hindi
        "வீடியோ கேமிங்" to R.string.interest_gaming,      // Tamil
        "ಗೇಮಿಂಗ್" to R.string.interest_gaming,            // Kannada
        "వీడియో గేమింగ్" to R.string.interest_gaming,     // Telugu

        "Fitness" to R.string.interest_fitness,
        "ফিটনেস" to R.string.interest_fitness,            // Bengali
        "फ़िटनेस" to R.string.interest_fitness,           // Hindi
        "உடற்பயிற்சி" to R.string.interest_fitness,       // Tamil
        "ಫಿಟ್ನೆಸ್" to R.string.interest_fitness,            // Kannada
        "ఫిట్నెస్" to R.string.interest_fitness,           // Telugu

        // Specific Outdoor & Nature
        "Hiking" to R.string.interest_hiking,
        "হাইকিং" to R.string.interest_hiking,              // Bengali
        "हाइकिंग" to R.string.interest_hiking,             // Hindi
        "ஹைக்கிங்" to R.string.interest_hiking,            // Tamil
        "ಹೈಕಿಂಗ್" to R.string.interest_hiking,            // Kannada
        "హైకింగ్" to R.string.interest_hiking,             // Telugu

        "Camping" to R.string.interest_camping,
        "ক্যাম্পিং" to R.string.interest_camping,           // Bengali
        "कैंपिंग" to R.string.interest_camping,            // Hindi
        "கேம்பிங்" to R.string.interest_camping,            // Tamil
        "ಕ್ಯಾಂಪಿಂಗ್" to R.string.interest_camping,          // Kannada
        "క్యాంపింగ్" to R.string.interest_camping,          // Telugu

        "Fishing" to R.string.interest_fishing,
        "মাছ ধরা" to R.string.interest_fishing,            // Bengali
        "मछली पकड़ना" to R.string.interest_fishing,        // Hindi
        "மீன் பிடித்தல்" to R.string.interest_fishing,       // Tamil
        "ಫಿಶಿಂಗ್" to R.string.interest_fishing,             // Kannada
        "ఫిషింగ్" to R.string.interest_fishing,              // Telugu

        "Hunting" to R.string.interest_hunting,
        "শিকার" to R.string.interest_hunting,               // Bengali
        "शिकार" to R.string.interest_hunting,               // Hindi
        "வேட்டை" to R.string.interest_hunting,              // Tamil
        "ಹಂಟಿಂಗ್" to R.string.interest_hunting,            // Kannada
        "హంటింగ్" to R.string.interest_hunting,             // Telugu

        "Gardening" to R.string.interest_gardening,
        "বাগান করা" to R.string.interest_gardening,         // Bengali
        "बागवानी" to R.string.interest_gardening,           // Hindi
        "தோட்டக்கலை" to R.string.interest_gardening,        // Tamil
        "ತೋಟಗಾರಿಕೆ" to R.string.interest_gardening,         // Kannada
        "తోటపనులు" to R.string.interest_gardening,          // Telugu

        // Food & Drink
        "Cooking" to R.string.interest_cooking,
        "রান্না" to R.string.interest_cooking,              // Bengali
        "खाना बनाना" to R.string.interest_cooking,          // Hindi
        "சமைத்தல்" to R.string.interest_cooking,            // Tamil
        "ಅಡಿಗೆ" to R.string.interest_cooking,               // Kannada
        "వండటం" to R.string.interest_cooking,               // Telugu

        "Baking" to R.string.interest_baking,
        "বেকিং" to R.string.interest_baking,                // Bengali
        "बैकिंग" to R.string.interest_baking,               // Hindi
        "அடுக்கு" to R.string.interest_baking,              // Tamil
        "ಬೇಕಿಂಗ್" to R.string.interest_baking,             // Kannada
        "బేకింగ్" to R.string.interest_baking,              // Telugu

        "Wine tasting" to R.string.interest_wine_tasting,
        "ওয়াইন টেস্টিং" to R.string.interest_wine_tasting, // Bengali
        "वाइन चखना" to R.string.interest_wine_tasting,      // Hindi
        "சாரம் சுவைத்தல்" to R.string.interest_wine_tasting,  // Tamil
        "ವೈನ್ ರುಚಿಸು" to R.string.interest_wine_tasting,    // Kannada
        "వైన్ రుచిచూడడం" to R.string.interest_wine_tasting,  // Telugu

        "Craft beer" to R.string.interest_craft_beer,
        "ক্রাফ্ট বিয়ার" to R.string.interest_craft_beer,       // Bengali
        "क्राफ्ट बियर" to R.string.interest_craft_beer,       // Hindi
        "கைவினை மது" to R.string.interest_craft_beer,       // Tamil
        "ಕ್ರಾಫ್ಟ್ ಬಿಯರ್" to R.string.interest_craft_beer,      // Kannada
        "క్రాఫ్ట్ బియర్" to R.string.interest_craft_beer,     // Telugu

        "Coffee" to R.string.interest_coffee,
        "কফি" to R.string.interest_coffee,                     // Bengali
        "कॉफ़ी" to R.string.interest_coffee,                    // Hindi
        "காபி" to R.string.interest_coffee,                    // Tamil
        "ಕಾಫಿ" to R.string.interest_coffee,                     // Kannada
        "కాఫీ" to R.string.interest_coffee,                     // Telugu

        // Wellness & Spiritual
        "Yoga" to R.string.interest_yoga,
        "যোগ" to R.string.interest_yoga,                       // Bengali
        "योग" to R.string.interest_yoga,                       // Hindi
        "யோகா" to R.string.interest_yoga,                      // Tamil
        "ಯೋಗ" to R.string.interest_yoga,                       // Kannada
        "యోగ" to R.string.interest_yoga,                       // Telugu

        "Meditation" to R.string.interest_meditation,
        "ধ্যান" to R.string.interest_meditation,                // Bengali
        "ध्यान" to R.string.interest_meditation,                // Hindi
        "தியானம்" to R.string.interest_meditation,             // Tamil
        "ಧ್ಯಾನ" to R.string.interest_meditation,               // Kannada
        "ధ్యానం" to R.string.interest_meditation,             // Telugu

        "Astrology" to R.string.interest_astrology,
        "জ্যোতিষ" to R.string.interest_astrology,               // Bengali
        "ज्योतिष" to R.string.interest_astrology,               // Hindi
        "ஜோதிடம்" to R.string.interest_astrology,             // Tamil
        "ಜ್ಯೋತಿಷ್ಯ" to R.string.interest_astrology,             // Kannada
        "జ్యోతిష్యం" to R.string.interest_astrology,            // Telugu

        "Romance" to R.string.interest_romance,
        "রোমান্স" to R.string.interest_romance,                // Bengali
        "रोमांस" to R.string.interest_romance,                 // Hindi
        "காதல்" to R.string.interest_romance,                   // Tamil
        "ರೊಮಾಂಸ್" to R.string.interest_romance,                // Kannada
        "రోమాన్స్" to R.string.interest_romance,                 // Telugu

        "Crystals" to R.string.interest_crystals,
        "ক্রিস্টালস" to R.string.interest_crystals,             // Bengali
        "क्रिस्टल" to R.string.interest_crystals,               // Hindi
        "கிரிஸ்டல்" to R.string.interest_crystals,               // Tamil
        "ಕ್ರಿಸ್ಟಲ್" to R.string.interest_crystals,               // Kannada
        "క్రిస్టల్" to R.string.interest_crystals,               // Telugu

        // Style & DIY
        "Vintage clothing" to R.string.interest_vintage_clothing,
        "ভিন্টেজ পোশাক" to R.string.interest_vintage_clothing,  // Bengali
        "विंटेज कपड़े" to R.string.interest_vintage_clothing,    // Hindi
        "பழமையான ஆடை" to R.string.interest_vintage_clothing,  // Tamil
        "ವಿಂಟೇಜ್ ಬಟ್ಟೆ" to R.string.interest_vintage_clothing,    // Kannada
        "వింటేజ్ వస్త్రాలు" to R.string.interest_vintage_clothing, // Telugu

        "Thrift shopping" to R.string.interest_thrift_shopping,
        "থ্রিফট শপিং" to R.string.interest_thrift_shopping,       // Bengali
        "थ्रिफ्ट शॉपिंग" to R.string.interest_thrift_shopping,     // Hindi
        "தள்ளுபடி ஷாப்பிங்" to R.string.interest_thrift_shopping,   // Tamil
        "ಥ್ರಿಫ್ಟ್ ಶಾಪಿಂಗ್" to R.string.interest_thrift_shopping,    // Kannada
        "థ్రిఫ్ట్ షాపింగ్" to R.string.interest_thrift_shopping,    // Telugu

        "DIY projects" to R.string.interest_diy,
        "ডিআইওয়াই প্রকল্প" to R.string.interest_diy,             // Bengali
        "डीआईवाई प्रोजेक्ट" to R.string.interest_diy,             // Hindi
        "நீங்கள் செய்து கொள்ளும் திட்டங்கள்" to R.string.interest_diy, // Tamil
        "DIY ಯೋಜನೆಗಳು" to R.string.interest_diy,                  // Kannada
        "DIY ప్రాజెక్టులు" to R.string.interest_diy,               // Telugu

        "Home improvement" to R.string.interest_home_improvement,
        "গৃহ উন্নয়ন" to R.string.interest_home_improvement,       // Bengali
        "गृह सुधार" to R.string.interest_home_improvement,         // Hindi
        "வீட்டுத்துறை மேம்பாடு" to R.string.interest_home_improvement, // Tamil
        "ಮನೆ ಸುಧಾರಣೆ" to R.string.interest_home_improvement,       // Kannada
        "ఇంటిని మెరుగుపరచడం" to R.string.interest_home_improvement, // Telugu

        "Interior design" to R.string.interest_interior_design,
        "অভ্যন্তরীண নকশা" to R.string.interest_interior_design,    // Bengali
        "इंटीरियर डिज़ाइन" to R.string.interest_interior_design,   // Hindi
        "உள்ளமைப்பு வடிவமைப்பு" to R.string.interest_interior_design, // Tamil
        "ಆಂತರಿಕ ವಿನ್ಯಾಸ" to R.string.interest_interior_design,       // Kannada
        "అంతర్గత రూపకల్పన" to R.string.interest_interior_design,    // Telugu

        // Intellectual & Tech
        "History" to R.string.interest_history,
        "ইতিহাস" to R.string.interest_history,                     // Bengali
        "इतिहास" to R.string.interest_history,                     // Hindi
        "வரலாறு" to R.string.interest_history,                      // Tamil
        "ಇತಿಹಾಸ" to R.string.interest_history,                      // Kannada
        "చరిత్ర" to R.string.interest_history,                       // Telugu

        "Science" to R.string.interest_science,
        "বিজ্ঞান" to R.string.interest_science,                     // Bengali
        "विज्ञान" to R.string.interest_science,                     // Hindi
        "அறிவியல்" to R.string.interest_science,                    // Tamil
        "ವಿಜ್ಞಾನ" to R.string.interest_science,                      // Kannada
        "విజ్ఞానం" to R.string.interest_science,                    // Telugu

        "Philosophy" to R.string.interest_philosophy,
        "দর্শন শাস্ত্র" to R.string.interest_philosophy,             // Bengali
        "दर्शनशास्त्र" to R.string.interest_philosophy,             // Hindi
        "தத்துவம்" to R.string.interest_philosophy,                  // Tamil
        "ದರ್ಶನಶಾಸ್ತ್ರ" to R.string.interest_philosophy,              // Kannada
        "తత్వశాస్త్రం" to R.string.interest_philosophy,              // Telugu

        "Politics" to R.string.interest_politics,
        "রাজনীতি" to R.string.interest_politics,                    // Bengali
        "राजनीति" to R.string.interest_politics,                    // Hindi
        "அரசியல்" to R.string.interest_politics,                    // Tamil
        "ರಾಜಕೀಯ" to R.string.interest_politics,                     // Kannada
        "రాజకీయ శాస్త్రం" to R.string.interest_politics,           // Telugu

        "Economics" to R.string.interest_economics,
        "অর্থনীতি" to R.string.interest_economics,                  // Bengali
        "अर्थशास्त्र" to R.string.interest_economics,                 // Hindi
        "பொருளாதாரம்" to R.string.interest_economics,               // Tamil
        "ಅರ್ಥಶಾಸ್ತ್ರ" to R.string.interest_economics,                 // Kannada
        "ఆర్థిక శాస్త్రం" to R.string.interest_economics,             // Telugu

        "Technology" to R.string.interest_technology,
        "প্রযুক্তি" to R.string.interest_technology,                  // Bengali
        "प्रौद्योगिकी" to R.string.interest_technology,               // Hindi
        "தொழில்நுட்பம்" to R.string.interest_technology,              // Tamil
        "ತಂತ್ರಜ್ಞಾನ" to R.string.interest_technology,                 // Kannada
        "సాంకేతికత" to R.string.interest_technology,                // Telugu

        "Coding" to R.string.interest_coding,
        "কোডিং" to R.string.interest_coding,                         // Bengali
        "कोडिंग" to R.string.interest_coding,                         // Hindi
        "குறியாக்கம்" to R.string.interest_coding,                     // Tamil
        "ಕೋಡಿಂಗ್" to R.string.interest_coding,                         // Kannada
        "కోడింగ్" to R.string.interest_coding,                         // Telugu

        "Robotics" to R.string.interest_robotics,
        "রোবোটিক্স" to R.string.interest_robotics,                   // Bengali
        "रोबोटिक्स" to R.string.interest_robotics,                   // Hindi
        "இயந்திரவியல்" to R.string.interest_robotics,                // Tamil
        "ರೋಬೋಟಿಕ್ಸ್" to R.string.interest_robotics,                   // Kannada
        "రోబోటిక్స్" to R.string.interest_robotics,                   // Telugu

        "Space exploration" to R.string.interest_space,
        "মহাকাশ অন্বেষণ" to R.string.interest_space,                  // Bengali
        "अंतरिक्ष अन्वेषण" to R.string.interest_space,                 // Hindi
        "வான்கோள் ஆய்வு" to R.string.interest_space,                   // Tamil
        "ಅಂತರಿಕ್ಷ ಅನ್ವೇಷಣೆ" to R.string.interest_space,               // Kannada
        "అంతరిక్ష అన్వేషణ" to R.string.interest_space,                // Telugu

        "Environmentalism" to R.string.interest_environmentalism,
        "পরিবেশবাদ" to R.string.interest_environmentalism,            // Bengali
        "पर्यावरणवाद" to R.string.interest_environmentalism,          // Hindi
        "சுற்றுச்சூழல் பாதுகாப்பு" to R.string.interest_environmentalism, // Tamil
        "ಪರಿಸರವಾದ" to R.string.interest_environmentalism,             // Kannada
        "పర్యావరణవాదం" to R.string.interest_environmentalism,         // Telugu

        // Social & Community
        "Volunteering" to R.string.interest_volunteering,
        "স্বেচ্ছাসেবা" to R.string.interest_volunteering,             // Bengali
        "स्वयंसेवा" to R.string.interest_volunteering,                 // Hindi
        "தன்னார்வ சேவை" to R.string.interest_volunteering,             // Tamil
        "ಸ್ವಯಂಸೇವಾ" to R.string.interest_volunteering,                 // Kannada
        "స్వచ్ఛంద సేవ" to R.string.interest_volunteering,               // Telugu

        "Charity work" to R.string.interest_charity,
        "দান কার্যক্রম" to R.string.interest_charity,                 // Bengali
        "चैरिटी कार्य" to R.string.interest_charity,                   // Hindi
        "நன்மை செயல்" to R.string.interest_charity,                    // Tamil
        "ಚಾರಿಟಿ ಕೆಲಸ" to R.string.interest_charity,                    // Kannada
        "దాన పనులు" to R.string.interest_charity,                      // Telugu

        "Community organizing" to R.string.interest_community,
        "কমিউনিটি সংগঠন" to R.string.interest_community,              // Bengali
        "समुदाय आयोजन" to R.string.interest_community,                  // Hindi
        "சமூக ஏற்பாடு" to R.string.interest_community,                  // Tamil
        "ಸಮುದಾಯ ಸಂಘಟನೆ" to R.string.interest_community,             // Kannada
        "సమాజం నిర్వహణ" to R.string.interest_community,                // Telugu

        "Networking" to R.string.interest_networking,
        "নেটওয়ার্কিং" to R.string.interest_networking,               // Bengali
        "नेटवर्किंग" to R.string.interest_networking,                    // Hindi
        "பிணையம்" to R.string.interest_networking,                      // Tamil
        "ನೆಟ್ವರ್ಕಿಂಗ್" to R.string.interest_networking,                  // Kannada
        "నెట్‌వర్కింగ్" to R.string.interest_networking,                 // Telugu

        "Public speaking" to R.string.interest_public_speaking,
        "পাবলিক স্পিকিং" to R.string.interest_public_speaking,        // Bengali
        "पब्लिक स्पीकिंग" to R.string.interest_public_speaking,        // Hindi
        "பொது பேச்சு" to R.string.interest_public_speaking,             // Tamil
        "ಸಾರ್ವಜನಿಕ ಭಾಷಣ" to R.string.interest_public_speaking,       // Kannada
        "పబ్లిక్ స్పీకింగ్" to R.string.interest_public_speaking,       // Telugu

        "Writing" to R.string.interest_writing,
        "লেখা" to R.string.interest_writing,                          // Bengali
        "लेखन" to R.string.interest_writing,                          // Hindi
        "எழுத்து" to R.string.interest_writing,                         // Tamil
        "ಲೇಖನ" to R.string.interest_writing,                           // Kannada
        "రాత" to R.string.interest_writing,                             // Telugu

        "Blogging" to R.string.interest_blogging,
        "ব্লগিং" to R.string.interest_blogging,                        // Bengali
        "ब्लॉगिंग" to R.string.interest_blogging,                      // Hindi
        "வலைப்பதிவு" to R.string.interest_blogging,                   // Tamil
        "ಬ್ಲಾಗಿಂಗ್" to R.string.interest_blogging,                      // Kannada
        "బ్లాగింగ్" to R.string.interest_blogging,                      // Telugu

        "Podcasting" to R.string.interest_podcasting,
        "পডকাস্টিং" to R.string.interest_podcasting,                   // Bengali
        "पॉडकास्टिंग" to R.string.interest_podcasting,                 // Hindi
        "பாட்காஸ்டிங்" to R.string.interest_podcasting,                 // Tamil
        "ಪಾಡ್‌ಕಾಸ್ಟಿಂಗ್" to R.string.interest_podcasting,              // Kannada
        "పొడ్కాస్టింగ్" to R.string.interest_podcasting,                // Telugu

        "Social media" to R.string.interest_social_media,
        "সোশ্যাল মিডিয়া" to R.string.interest_social_media,            // Bengali
        "सोशल मीडिया" to R.string.interest_social_media,                // Hindi
        "சமூக ஊடகம்" to R.string.interest_social_media,                 // Tamil
        "ಸುದ್ದಿ ಮಾಧ್ಯಮ" to R.string.interest_social_media,                // Kannada
        "సోషల్ మీడియా" to R.string.interest_social_media,             // Telugu

"Online communities" to R.string.interest_online_communities,
"অনলাইন কমিউনিটি" to R.string.interest_online_communities,    // Bengali
"ऑनलाइन समुदाय" to R.string.interest_online_communities,       // Hindi
"ஆன்லைன் சமூகங்கள்" to R.string.interest_online_communities,      // Tamil
"ಆನ್‌ಲೈನ್ ಸಮುದಾಯಗಳು" to R.string.interest_online_communities,    // Kannada
"ఆన్లైన్ కమ్యూనిటీస్" to R.string.interest_online_communities,     // Telugu

// Adventurous & Thrilling
"Skydiving" to R.string.interest_skydiving,
"স্কাইডাইভিং" to R.string.interest_skydiving,              // Bengali
"स्काईडाइविंग" to R.string.interest_skydiving,             // Hindi
"வான்வீழ்ச்சி" to R.string.interest_skydiving,               // Tamil
"ಸ್ಕೈಡೈವಿಂಗ್" to R.string.interest_skydiving,             // Kannada
"స్కైవ్‌డైవింగ్" to R.string.interest_skydiving,           // Telugu

"Scuba diving" to R.string.interest_scuba_diving,
"স্কুবা ডাইভিং" to R.string.interest_scuba_diving,            // Bengali
"स्कूबा डाइविंग" to R.string.interest_scuba_diving,           // Hindi
"கடல் உறுள் மூழ்கல்" to R.string.interest_scuba_diving,         // Tamil
"ಸ್ಕೂಬಾ ಹಾರಣೆ" to R.string.interest_scuba_diving,             // Kannada
"స్కూబా డైవింగ్" to R.string.interest_scuba_diving,           // Telugu

"Rock climbing" to R.string.interest_rock_climbing,
"রক ক্লাইমিং" to R.string.interest_rock_climbing,             // Bengali
"रॉक क्लाइम्बिंग" to R.string.interest_rock_climbing,          // Hindi
"சிங்கரிச் ஏறுதல்" to R.string.interest_rock_climbing,          // Tamil
"ರಾಕ್ ಎರಕ climbing" to R.string.interest_rock_climbing,      // Kannada
"రాక్ క్లైంబింగ్" to R.string.interest_rock_climbing,          // Telugu

"Surfing" to R.string.interest_surfing,
"সার্ফিং" to R.string.interest_surfing,                       // Bengali
"सर्फिंग" to R.string.interest_surfing,                       // Hindi
"அலைபாய்ச்சி" to R.string.interest_surfing,                     // Tamil
"ಸರ್ಫಿಂಗ್" to R.string.interest_surfing,                      // Kannada
"సర్ఫింగ్" to R.string.interest_surfing,                       // Telugu

"Skiing" to R.string.interest_skiing,
"স্কিইং" to R.string.interest_skiing,                          // Bengali
"स्कीयिंग" to R.string.interest_skiing,                         // Hindi
"அரிசல் செலுத்துதல்" to R.string.interest_skiing,                // Tamil
"ಸ್ಕೀಯಿಂಗ್" to R.string.interest_skiing,                      // Kannada
"స్కీయింగ్" to R.string.interest_skiing,                       // Telugu

"Snowboarding" to R.string.interest_snowboarding,
"স্নোবোর্ডিং" to R.string.interest_snowboarding,                // Bengali
"स्नोबोर्डिंग" to R.string.interest_snowboarding,               // Hindi
"மஞ்சுப் பலகை சறுக்குதல்" to R.string.interest_snowboarding,       // Tamil
"ಸ್ನೋಬೋರ್ಡಿಂಗ್" to R.string.interest_snowboarding,             // Kannada
"స్నోబోర్డింగ్" to R.string.interest_snowboarding,               // Telugu

"Mountain biking" to R.string.interest_mountain_biking,
"মাউন্টাইন বাইকিং" to R.string.interest_mountain_biking,         // Bengali
"माउंटेन बाइकिंग" to R.string.interest_mountain_biking,           // Hindi
"மலை சைக்கிள் ஓட்டுதல்" to R.string.interest_mountain_biking,       // Tamil
"ಮೌಂಟೇನ್ ಬೈಕಿಂಗ್" to R.string.interest_mountain_biking,       // Kannada
"మౌంటైన్ బైకింగ్" to R.string.interest_mountain_biking,         // Telugu

"Motorcycling" to R.string.interest_motorcycling,
"মোটরসাইক্লিং" to R.string.interest_motorcycling,                  // Bengali
"मोटरसाइक्लिंग" to R.string.interest_motorcycling,                // Hindi
"மோட்டார் சைக்கிள் ஓட்டுதல்" to R.string.interest_motorcycling,       // Tamil
"ಮೋಟಾರ್ಸೈಕ್ಲಿಂಗ್" to R.string.interest_motorcycling,              // Kannada
"మోటార్సైక్లింగ్" to R.string.interest_motorcycling,              // Telugu

"Car racing" to R.string.interest_car_racing,
"কার রেসিং" to R.string.interest_car_racing,                       // Bengali
"कार रेसिंग" to R.string.interest_car_racing,                      // Hindi
"கார் ஓட்டப்போட்டி" to R.string.interest_car_racing,                  // Tamil
"ಕಾರ್ ರೇಸಿಂಗ್" to R.string.interest_car_racing,                   // Kannada
"కార్ రేసింగ్" to R.string.interest_car_racing,                    // Telugu

"Extreme sports" to R.string.interest_extreme_sports,
"এক্সট্রিম স্পোর্টস" to R.string.interest_extreme_sports,            // Bengali
"एक्सट्रीम स्पोर्ट्स" to R.string.interest_extreme_sports,            // Hindi
"மிகுந்த விளையாட்டு" to R.string.interest_extreme_sports,             // Tamil
"ಅತ್ಯಂತ ಕ್ರೀಡೆ" to R.string.interest_extreme_sports,                // Kannada
"అత్యంత క్రీడలు" to R.string.interest_extreme_sports,               // Telugu

// Relaxation & Leisure
"Puzzles" to R.string.interest_puzzles,
"ধাঁধা" to R.string.interest_puzzles,                               // Bengali
"पहेलियाँ" to R.string.interest_puzzles,                            // Hindi
"முயற்சித்துப் புதிர்கள்" to R.string.interest_puzzles,              // Tamil
"ಪುಟ들은" to R.string.interest_puzzles,                            // Kannada
"పజిల్స్" to R.string.interest_puzzles,                              // Telugu

"Board games" to R.string.interest_board_games,
"বোর্ড গেমস" to R.string.interest_board_games,                       // Bengali
"बोर्ड गेम्स" to R.string.interest_board_games,                       // Hindi
"தகடுப் பந்திகள்" to R.string.interest_board_games,                  // Tamil
"ಬೋರ್ಡ್ ಗೇಮ್ಸ್" to R.string.interest_board_games,                     // Kannada
"బోర్డ్ గేమ్స్" to R.string.interest_board_games,                     // Telugu

"Video games" to R.string.interest_video_games,
"ভিডিও গেমস" to R.string.interest_video_games,                       // Bengali
"वीडियो गेम्स" to R.string.interest_video_games,                      // Hindi
"வீடியோ விளையாட்டுகள்" to R.string.interest_video_games,              // Tamil
"ವೀಡಿಯೊ ಗೇಮ್ಸ್" to R.string.interest_video_games,                     // Kannada
"వీడియో గేమ్స్" to R.string.interest_video_games,                     // Telugu

"Watching TV" to R.string.interest_watching_tv,
"টিভি দেখা" to R.string.interest_watching_tv,                         // Bengali
"टीवी देखना" to R.string.interest_watching_tv,                        // Hindi
"தொலைக்காட்சி பார்க்குதல்" to R.string.interest_watching_tv,           // Tamil
"ಟಿವಿ ವೀಕ್ಷಣೆ" to R.string.interest_watching_tv,                       // Kannada
"టీవీ వీక్షణ" to R.string.interest_watching_tv,                       // Telugu

"Napping" to R.string.interest_napping,
"ন্যাপিং" to R.string.interest_napping,                                // Bengali
"नैपिंग" to R.string.interest_napping,                                // Hindi
"கண்மூசப்போழுது" to R.string.interest_napping,                        // Tamil
"ನಾಪಿಂಗ್" to R.string.interest_napping,                               // Kannada
"నాపింగ్" to R.string.interest_napping,                               // Telugu

"Spa days" to R.string.interest_spa_days,
"স্পা ডে" to R.string.interest_spa_days,                               // Bengali
"स्पा दिन" to R.string.interest_spa_days,                              // Hindi
"ஸ்பா நாட்கள்" to R.string.interest_spa_days,                          // Tamil
"ಸ್ಪಾ ದಿನಗಳು" to R.string.interest_spa_days,                           // Kannada
"స్పా రోజులు" to R.string.interest_spa_days,                            // Telugu

"Beach days" to R.string.interest_beach_days,
"বিচ ডে" to R.string.interest_beach_days,                              // Bengali
"बीच डे" to R.string.interest_beach_days,                               // Hindi
"கடல் பலகை நாட்கள்" to R.string.interest_beach_days,                     // Tamil
"ಬೀಚ್ ದಿನಗಳು" to R.string.interest_beach_days,                         // Kannada
"బీచ్ రోజులు" to R.string.interest_beach_days,                           // Telugu

"Picnics" to R.string.interest_picnics,
"পিকনিক" to R.string.interest_picnics,                                 // Bengali
"पिकनिक" to R.string.interest_picnics,                                 // Hindi
"புண்ணிய உணவு" to R.string.interest_picnics,                              // Tamil
"ಪಿಕ್ನಿಕ್" to R.string.interest_picnics,                                 // Kannada
"పిక్నిక్" to R.string.interest_picnics,                                 // Telugu
)

    // 2) Group by resId, pick one rawName per interest
    val availableInterests = remember {
        interestNameToResource
            .entries
            .groupBy({ it.value }, { it.key })    // Map<resId, List<rawNames>>
            .map { (_, rawNames) -> rawNames.first() }
    }

    // Track which are selected
    val localInterests = remember {
        mutableStateListOf<Interest>().apply { addAll(tempProfile.interests) }
    }
    val maxSelections = 9

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.section_interests),
            color = Color(0xFFFF6F00),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            availableInterests.forEach { rawName ->
                val resId = interestNameToResource.getValue(rawName)
                val label = stringResource(resId)
                val isSelected = localInterests.any { it.name == rawName }
                val canToggleOn = localInterests.size < maxSelections

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            localInterests.removeAll { it.name == rawName }
                        } else if (canToggleOn) {
                            localInterests.add(Interest(name = rawName, emoji = ""))
                        }
                    },
                    enabled = isSelected || canToggleOn,
                    label = { Text(label, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                        disabledLabelColor     = Color.LightGray
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        ButtonRow(
            onSave   = { onSave(tempProfile.copy(interests = localInterests.toList())) },
            onCancel = onCancel
        )
    }
}

/** Reusable UI elements */
@Composable
fun ProfileDetailRow(label: String, value: String?, icon: ImageVector) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
//                Text(text = label, color = Color(0xFFFF6F00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = value,
                    color = Color.White,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun InterestTag(label: String) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .background(Color.Black, shape = CircleShape)
            .border(2.dp, Color(0xFFFF6F00), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color.White)
    }
}

@Composable
fun PostItemInProfile(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
        shape  = RoundedCornerShape(4.dp)
    ) {
        var showVideoDialog by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // — 1) TEXT —
            post.contentText
                .takeIf { !it.isNullOrBlank() }
                ?.let {
                    Text(it, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                }

            // — 1.5) CHECK-IN LOCATION —
            post.checkIn
                .takeIf { it?.placeId?.isNotBlank() == true }
                ?.let { checkIn ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Checked in at",
                            tint = Color(0xFFFF6F00),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = checkIn.name
                                .takeIf { it.isNotBlank() }
                                ?: checkIn.address,               // fallback to address if name is blank
                            color = Color(0xFFFF6F00),
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

            // — 2) MEDIA —
            when (post.mediaType.orEmpty().lowercase(Locale.ROOT)) {
                "photo", "image" -> {
                    AsyncImage(
                        model        = post.mediaUrl,
                        contentScale = ContentScale.Crop,
                        placeholder  = painterResource(R.drawable.local_placeholder),
                        error        = painterResource(R.drawable.local_placeholder),
                        contentDescription = "Photo post",
                        modifier     = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 300.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }
                "video" -> {
                    // ② Thumbnail + play button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .clickable { showVideoDialog = true }
                    ) {
                        AsyncImage(
                            model        = post.mediaThumb ?: post.mediaUrl,
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier.matchParentSize(),
                            contentDescription = "Video thumbnail"
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // ③ Fullscreen cache-backed dialog
                    if (showVideoDialog) {
                        CachedFullscreenVideoPlayer(
                            uri       = Uri.parse(post.mediaUrl),
                            onDismiss = { showVideoDialog = false }
                        )
                    }
                }
                "voice" -> {
                    post.mediaUrl?.let { VoicePlayer(url = it) }
                    Spacer(Modifier.height(6.dp))
                }
                else -> { /* no media */ }
            }

            // — 3) UP / DOWN VOTES —
            Row {
                Text("Upvotes: ${post.upvotes}", color = Color(0xFFFFBF00), fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text("Downvotes: ${post.downvotes}", color = Color(0xFFFF6F00), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun LifestyleSlider(label: String, value: Int, nouns: List<String>, icon: ImageVector) {
    val displayText = if (value == -1) stringResource(R.string.not_selected) else nouns.getOrElse(value) { "Unknown" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
        }
        Text(text = displayText, fontSize = 14.sp, color = if (value == -1) Color.Gray else Color.White)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
        value = value.toFloat(),
        onValueChange = {},
        valueRange = 0f..4f,
        steps = 3,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF6F00),
            activeTrackColor = Color(0xFFFF6F00)
        )
    )
}

@Composable
fun RatingBar(
    rating: Double,
    ratingCount: Int
) {
    val starSize = 26.dp
    val orange = Color(0xFFFF6F00)

    // Calculate how many full stars, whether there's a fractional part, and how many empty stars
    val fullStars = kotlin.math.floor(rating).toInt()
    val fraction = rating - fullStars
    // Treat any tiny fractional noise as zero
    val hasFraction = fraction > 0.01
    val emptyStars = 5 - fullStars - if (hasFraction) 1 else 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 1) Draw all the full stars
        repeat(fullStars.coerceIn(0, 5)) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(starSize)
            )
        }

        // 2) Draw a fractional star (outline + clipped fill) if needed
        if (hasFraction && fullStars < 5) {
            Box(modifier = Modifier.size(starSize)) {
                // Outline
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )
                // Filled portion
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RectangleShape)
                        .fractionalClip(fraction.toFloat())
                        .align(Alignment.CenterStart)
                )
            }
        }

        // 3) Draw any remaining empty (border-only) stars
        repeat(emptyStars.coerceAtLeast(0)) {
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(starSize)
            )
        }

        // 4) Spacer + rating text (show "0 (0)" when count is zero)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (ratingCount == 0) {
                "0 (0)"
            } else {
                String.format("%.2f (%d)", rating, ratingCount)
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

// Custom modifier to clip the Icon's canvas to the given fraction of its width
fun Modifier.fractionalClip(fraction: Float) = this.then(
    Modifier.drawWithContent {
        val clipWidth = size.width * fraction
        clipRect(right = clipWidth) {
            this@drawWithContent.drawContent()
        }
    }
)

@Composable
fun VoicePlayer(url: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }
    val durationInSeconds = remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(url) {
        try {
            mediaPlayer.setDataSource(url)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                durationInSeconds.value = it.duration / 1000
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                elapsedTime = 0
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error loading audio: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            coroutineScope.launch {
                while (isPlaying && elapsedTime < durationInSeconds.value) {
                    delay(1000)
                    elapsedTime++
                }
            }
        } else {
            elapsedTime = 0
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
            .clickable {
                isPlaying = if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    false
                } else {
                    mediaPlayer.start()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying)
                stringResource(R.string.pause_audio)
            else
                stringResource(R.string.tap_to_play),
            tint = Color(0xFFFFBF00),
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = if (isPlaying)
                stringResource(R.string.playing_seconds, elapsedTime)
            else
                stringResource(R.string.tap_to_play),
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (durationInSeconds.value > 0) {
            Text(
                text = "${elapsedTime}s / ${durationInSeconds.value}s",
                color = Color.Gray,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun LifestyleSliderEdit(label: String, value: Int, nouns: List<String>, onValueChange: (Int) -> Unit) {
    var sliderValue by remember { mutableStateOf(if (value == -1) 0f else value.toFloat()) }
    val displayText = if (value == -1) stringResource(R.string.not_selected) else nouns.getOrElse(value) { "Unknown" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayText, fontSize = 11.sp, color = if (value == -1) Color.Gray else Color.White)
            if (value != -1) {
                IconButton(onClick = {
                    sliderValue = 0f
                    onValueChange(-1)
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_selection),
                        tint = Color.Red
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChange(sliderValue.toInt()) },
        valueRange = 0f..4f,
        steps = 3,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF6F00),
            activeTrackColor = Color(0xFFFF6F00)
        )
    )
}

@Composable
fun ButtonRow(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ElevatedButton(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(stringResource(R.string.save), color = Color.White)
        }
        ElevatedButton(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(stringResource(R.string.cancel), color = Color.White)
        }
    }
}

suspend fun updateProfileInFirebase(updatedProfile: Profile) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = FirebaseRefs.db.getReference("users").child(currentUserId)

    val updates = mapOf(
        "email" to updatedProfile.email,
        "name" to updatedProfile.name,
        "caste" to updatedProfile.caste,
        "bio" to updatedProfile.bio,
        "gender" to updatedProfile.gender,
        "city" to updatedProfile.city,
        "height"  to updatedProfile.height,
        "height2" to updatedProfile.height2,
        "hometown" to updatedProfile.hometown,
        "highSchool" to updatedProfile.highSchool,
        "highSchoolGraduationYear" to updatedProfile.highSchoolGraduationYear,
        "college" to updatedProfile.college,
        "collegeGraduationYear" to updatedProfile.collegeGraduationYear,
        "loveLanguage" to updatedProfile.loveLanguage,
        "customLoveLanguage" to updatedProfile.customLoveLanguage,
        "politics" to updatedProfile.politics,
        "customPolitics" to updatedProfile.customPolitics,
        "socialCauses" to updatedProfile.socialCauses,

        // NEW: For the college degree
        "collegeDegree" to updatedProfile.collegeDegree,

        "postGraduation" to updatedProfile.postGraduation,
        "postGraduationYear" to updatedProfile.postGraduationYear,

        // NEW: For the post-grad degree
        "postGraduationDegree" to updatedProfile.postGraduationDegree,

        "community" to updatedProfile.community,
        "religion" to updatedProfile.religion,
        "ethnicity" to updatedProfile.ethnicity,
        "incomeLevel" to updatedProfile.incomeLevel,
        "lookingFor" to updatedProfile.lookingFor,
        "interests" to updatedProfile.interests.map {
            mapOf("name" to it.name, "emoji" to it.emoji)
        },
        "lifestyle" to updatedProfile.lifestyle,

        // Job & Work
        "jobRole" to updatedProfile.jobRole,
        "customJobRole" to updatedProfile.customJobRole,
        "work" to updatedProfile.work,
        "customWork" to updatedProfile.customWork,

        // Matrimony toggle + details
        "isMatrimonyMode" to updatedProfile.isMatrimonyMode,
        "marriageTimeline" to updatedProfile.marriageTimeline,
        "relocationPreference" to updatedProfile.relocationPreference,
        "postMarriageCareerPlan" to updatedProfile.postMarriageCareerPlan,
        "traditionalVsLiberal" to updatedProfile.traditionalVsLiberal,
        "fatherOccupation" to updatedProfile.fatherOccupation,
        "motherOccupation" to updatedProfile.motherOccupation,
    )

    userRef.updateChildren(updates).addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            Log.e("ProfileScreen", "Failed to update profile: ${task.exception}")
        } else {
            Log.d("ProfileScreen", "Profile updated successfully!")
        }
    }
}

@Composable
fun MatrimonyInfoSection(profile: Profile) {
    // Show these fields only if they have values
    ProfileDetailRow(
        label = stringResource(R.string.marriage_timeline_label) + " ",
        value = profile.marriageTimeline,
        icon = Icons.Default.Schedule
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_relocation_preference) + " ",
        value = profile.relocationPreference,
        icon = Icons.Default.Map
    )
    ProfileDetailRow(
        label = stringResource(R.string.post_marriage_career_plan_label) + " ",
        value = profile.postMarriageCareerPlan,
        icon = Icons.Default.Work
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_traditional_vs_liberal) + " ",
        value = profile.traditionalVsLiberal,
        icon = Icons.Default.HowToVote // or some suitable icon
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_father_occupation) + " ",
        value = profile.fatherOccupation,
        icon = Icons.Default.Person
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_mother_occupation) + " ",
        value = profile.motherOccupation,
        icon = Icons.Default.Person
    )
}

@Composable
fun MatrimonyInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var marriageTimeline by remember { mutableStateOf(tempProfile.marriageTimeline ?: "") }
    var relocationPref by remember { mutableStateOf(tempProfile.relocationPreference ?: "") }
    var postMarriagePlan by remember { mutableStateOf(tempProfile.postMarriageCareerPlan ?: "") }
    var traditionalVsLiberal by remember { mutableStateOf(tempProfile.traditionalVsLiberal ?: "") }
    var fatherOccupation by remember { mutableStateOf(tempProfile.fatherOccupation ?: "") }
    var motherOccupation by remember { mutableStateOf(tempProfile.motherOccupation ?: "") }

    // No direct edit for isConsultantVerified here; you (the consultant) set it manually elsewhere.

    Column {
        OutlinedTextField(
            value = marriageTimeline,
            onValueChange = { marriageTimeline = it },
            label = { Text(stringResource(R.string.marriage_timeline_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = relocationPref,
            onValueChange = { relocationPref = it },
            label = { Text(stringResource(R.string.relocation_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = postMarriagePlan,
            onValueChange = { postMarriagePlan = it },
            label = { Text(stringResource(R.string.post_marriage_career_plan_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = traditionalVsLiberal,
            onValueChange = { traditionalVsLiberal = it },
            label = { Text(stringResource(R.string.matrimony_traditional_vs_liberal), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.family_information), color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = fatherOccupation,
            onValueChange = { fatherOccupation = it },
            label = { Text(stringResource(R.string.matrimony_father_occupation), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = motherOccupation,
            onValueChange = { motherOccupation = it },
            label = { Text(stringResource(R.string.matrimony_mother_occupation), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 5) Save/Cancel Buttons
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    val updated = tempProfile.copy(
                        marriageTimeline = marriageTimeline.ifBlank { null },
                        relocationPreference = relocationPref.ifBlank { null },
                        postMarriageCareerPlan = postMarriagePlan.ifBlank { null },
                        traditionalVsLiberal = traditionalVsLiberal.ifBlank { null },
                        fatherOccupation = fatherOccupation.ifBlank { null },
                        motherOccupation = motherOccupation.ifBlank { null },
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text(stringResource(R.string.save), color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}