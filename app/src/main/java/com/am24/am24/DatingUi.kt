package com.am24.am24

import DatingViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.util.Calendar


@Composable
fun DatingProfileHeader(
    profile: Profile,
    userDistance: Float,
    sortedByUpvotes: List<Post>
) {
    val community = profile.community
    val religion = profile.religion
    val caste = profile.caste

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkGrayBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Spacer(modifier = Modifier.width(6.dp))
                if (community.isNotBlank()) {
                    TagBox(text = community)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (religion.isNotBlank()) {
                    TagBox(text = religion)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (caste.isNotBlank()) {
                    TagBox(text = caste)
                }
            }
        }
    }
}

@Composable
fun ProfileCollapsibleSectionsAll(
    profile: Profile,
    currentUserProfile: Profile?,
    aiMatchResult: AiMatchCheckResult?,
    showLocation: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val selectedCountry = remember(currentUserProfile?.country) {
        canonicalCountry(currentUserProfile?.country).takeIf { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        CollapsibleSection(
            title = stringResource(R.string.basic_information),
            icon = Icons.Default.Person
        ) {
            BasicInfoSection(profile, showLocation)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (currentUserProfile?.isPremium == true) {
            PerformanceMetricsSectionDating(profile, showLocation)
            Spacer(modifier = Modifier.height(12.dp))
        }
        CollapsibleSection(
            title = stringResource(R.string.bio),
            icon = Icons.Default.Mic
        ) {
            showVoiceBio(profile = profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        /** ─────────── Compatibility ─────────── */
        LaunchedEffect(profile.userId) {
            runAiMatchCheck(
                context = context,
                coroutineScope = coroutineScope,
                currentUserId = FirebaseAuth.getInstance().uid
                    ?: return@LaunchedEffect,
                currentUserProfile = currentUserProfile!!,
                otherProfile = profile
            ) { }
        }
        CollapsibleSection(
            title = stringResource(R.string.compatibility_check),
            icon = Icons.Default.Info,
        ) {
            if (aiMatchResult != null) {
                ShowAiMatchAnalysis(aiMatchResult)
            } else {
                androidx.compose.material.Text(
                    stringResource(R.string.run_analysis),
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.preferences),
            icon = Icons.Default.Favorite
        ) {
            PreferencesSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.lifestyle_attributes),
            icon = Icons.Default.Nature
        ) {
            LifestyleSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.interests),
            icon = Icons.Default.Star
        ) {
            InterestsSectionInProfile(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.social_causes),
            icon = Icons.Default.Favorite
        ) {
            SocialCausesSection(profile)
        }
    }
}
@Composable
fun PhotoWithTwoOverlays(
    profile: Profile,
    userDistance: Float,
    aiMatchResult: AiMatchCheckResult?,
    sortedByUpvotes: List<Post>,
    currentProfile: Profile? = null,
    onCompliment: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val placeholderRes = R.drawable.local_placeholder
    val photoUrls: List<Any> = buildList {
        if (profile.profilepicUrl.isNullOrBlank()) add(placeholderRes) else add(profile.profilepicUrl!!)
        addAll(profile.optionalPhotoUrls)
    }
    var idx by remember(photoUrls) { mutableStateOf(0) }
    val datingViewModel: DatingViewModel = viewModel()
    val compliment = remember(profile.userId) {
        datingViewModel.complimentsReceived.value[profile.userId]
    }
    val age = calculateAge(profile.dob)
    var showPostsOverlay by remember { mutableStateOf(false) }
    val interestsTexts = profile.interests.map { "${it.emoji} ${it.name}" }
    val preferencesTexts = listOfNotNull(
        profile.lookingFor.takeIf(String::isNotBlank)?.let { "🎯 $it" },
        profile.loveLanguage.takeIf(String::isNotBlank)?.let { "🗣️ $it" },
        profile.jobRole.takeIf(String::isNotBlank)?.let { "💼 $it" }
    )
    val smokingNouns = listOf(
        stringResource(R.string.non_smoker),
        stringResource(R.string.rare_smoker),
        stringResource(R.string.social_smoker),
        stringResource(R.string.frequent_smoker),
        stringResource(R.string.heavy_smoker)
    )
    val drinkingNouns = listOf(
        stringResource(R.string.non_drinker),
        stringResource(R.string.rare_drinker),
        stringResource(R.string.social_drinker),
        stringResource(R.string.frequent_drinker),
        stringResource(R.string.heavy_drinker)
    )
    val exerciseNouns = listOf(
        stringResource(R.string.inactive),
        stringResource(R.string.rarely_active),
        stringResource(R.string.moderately_active),
        stringResource(R.string.active),
        stringResource(R.string.very_active)
    )
    val lifestyleList = listOfNotNull(
        profile.lifestyle?.smoking_habit?.takeIf { it >= 0 }?.let { Triple(it, smokingNouns, "🚬") },
        profile.lifestyle?.drinking_habit?.takeIf { it >= 0 }?.let { Triple(it, drinkingNouns, "🍷") },
        profile.lifestyle?.exercise_frequency?.takeIf { it >= 0 }?.let { Triple(it, exerciseNouns, "🏃") }
    )
    val lifestyleTexts = buildList {
        addAll(
            lifestyleList.sortedByDescending { it.first }.take(3)
                .map { (v, nouns, e) -> "$e ${nouns.getOrNull(v) ?: ""}" }
        )
    }
    val lifestyleTexts2 = lifestyleList.sortedByDescending { it.first }.drop(3).take(6)
        .map { (v, nouns, e) -> "$e ${nouns.getOrNull(v) ?: ""}" }

    LaunchedEffect(photoUrls) {
        photoUrls.filterIsInstance<String>().forEach { url ->
            ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(DarkGrayBackground)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .pointerInput(photoUrls) {
                    detectTapGestures {
                        if (photoUrls.size > 1) {
                            idx = if (it.x > size.width / 2) (idx + 1) % photoUrls.size
                            else (idx - 1 + photoUrls.size) % photoUrls.size
                        }
                    }
                }
        ) {
            when (val model = photoUrls[idx]) {
                is Int -> Image(
                    painter = painterResource(model),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> AsyncImage(
                    model = model,
                    placeholder = painterResource(placeholderRes),
                    error = painterResource(placeholderRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                photoUrls.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .width(if (i == idx) 30.dp else 10.dp)
                            .height(4.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i == idx) Color.White else Color.Gray)
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                val displayName = profile.name.ifBlank { profile.username }
                val nameAndAge = age?.let { "$displayName, $it" } ?: displayName
                androidx.compose.material.Text(
                    text = nameAndAge,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black)
                        )
                    )
            ) {
                AutoMarqueeRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val overlayStrings = when (idx) {
                        0 -> lifestyleTexts
                        1 -> preferencesTexts.take(3)
                        2 -> interestsTexts.take(4)
                        3 -> interestsTexts.drop(4).take(5)
                        4 -> lifestyleTexts2
                        else -> emptyList()
                    }
                    val tags = overlayStrings.filter { it.isNotBlank() }
                    if (tags.isEmpty()) {
                        profile.kinks.forEach { TagBox(it) }
                    } else {
                        tags.forEach { TagBox(it) }
                    }
                }
            }

            compliment?.let { c ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFFE91E63), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        androidx.compose.material.Text(
                            c.text,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (showPostsOverlay) {
            PostsOverlay(sortedByUpvotes) { showPostsOverlay = false }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material.Text(
                if (userDistance.isNaN())
                    stringResource(R.string.worldwide)
                else
                    DistanceUtil.formatDistance(ctx, userDistance),
                color = Color.White,
                fontSize = 18.sp
            )
            IconButton(
                onClick  = { showPostsOverlay = true },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                    .size(28.dp)
            ) {
                Icon(Icons.Default.PostAdd,
                    stringResource(R.string.posts_button),
                    tint = Color.White
                )
            }
        }
    }
}
@Composable
fun MatchPopUp(
    currentUserProfilePic: String,
    otherUserProfilePic: String,
    onChatClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.its_a_match),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.Center) {
                    val placeholder = painterResource(R.drawable.local_placeholder)
                    AsyncImage(
                        model = currentUserProfilePic.takeIf { it.isNotBlank() },
                        contentDescription = stringResource(R.string.your_profile_picture),
                        placeholder = placeholder,
                        error = placeholder,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6F00), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    AsyncImage(
                        model = otherUserProfilePic.takeIf { it.isNotBlank() },
                        contentDescription = stringResource(R.string.matched_profile_picture),
                        placeholder = placeholder,
                        error = placeholder,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6F00), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onChatClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text(stringResource(R.string.chat_now), color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close), color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SwipeLimitOverlay(
    remainingSwipes: Int,
    isPlus: Boolean,
    isPremium: Boolean,
    isIndian: Boolean,
    onUpgrade: () -> Unit
) {
    val quota = when {
        isPremium -> Int.MAX_VALUE
        isPlus -> 50
        else -> 20
    }

    val timeLeft = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diff = cal.timeInMillis - now
            val h = diff / 3_600_000
            val m = (diff % 3_600_000) / 60_000
            val s = (diff % 60_000) / 1000
            timeLeft.value = String.format("%02d:%02d:%02d", h, m, s)
            delay(1000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DarkGrayBackground.copy(alpha = 0.8f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                Modifier
                    .background(Color(0xFF1A1A1A))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Swipes Remaining", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                val quotaDisplay = if (quota == Int.MAX_VALUE) "∞" else quota.toString()
                Text("$remainingSwipes / $quotaDisplay", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("Resets in: ${timeLeft.value}", color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onUpgrade,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                    ) {
                        val labelRes = if (isIndian) {
                            R.string.swipe_overlay_buy_swipes_button
                        } else {
                            R.string.swipe_overlay_upgrade_button
                        }
                        Text(stringResource(labelRes), color = Color.Black)
                    }
                }
            }
        }
    }
}