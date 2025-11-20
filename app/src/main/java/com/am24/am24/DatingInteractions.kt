package com.am24.am24

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.am24.am24.ui.theme.DarkGrayBackground
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.LocationCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** Lightweight swipe payload persisted under swipes/ */
data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

/** Summary persisted to aiMatchCheck/{uid}/{otherUid} */
data class AiMatchCheckResult(
    val summary: String = "",
    val totalMatchPercentage: Int = 0,
    val compatibilityBreakdown: String = "",
    val timestamp: Long = 0L
)

data class MatchInsight(val emoji: String, val text: String, val isPositive: Boolean)

@Composable
fun ShowAiMatchAnalysis(result: AiMatchCheckResult) {

    val rawLines = result.compatibilityBreakdown
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val strengths = rawLines.filter { it.startsWith("✅") }
    val concerns  = rawLines.filter { it.startsWith("⚠️") }
    val notes     = rawLines.filter { it.startsWith("ℹ️") }

    Column(modifier = Modifier.padding(8.dp)) {

        Text(
            text  = stringResource(R.string.total_match_label, result.totalMatchPercentage),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(10.dp))

        if (strengths.isNotEmpty()) {
            Text(stringResource(R.string.strengths), color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.fillMaxWidth()) {
                strengths.forEach {
                    Text(
                        text = "\u2022 " + it.removePrefix("✅").trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (concerns.isNotEmpty()) {
            Text(stringResource(R.string.concerns),
                color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.fillMaxWidth()) {
                concerns.forEach {
                    Text(
                        text = "\u2022 " + it.removePrefix("⚠️").trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (notes.isNotEmpty()) {
            Text(stringResource(R.string.notes), color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.fillMaxWidth()) {
                notes.forEach {
                    Text(
                        text = "\u2022 " + it.removePrefix("ℹ️").trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = stringResource(R.string.analyzed_on, formatTime(result.timestamp)),
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
internal fun AutoMarqueeRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(500)
        while (true) {
            scroll.animateScrollTo(scroll.maxValue)
            delay(1_500)
            scroll.animateScrollTo(0)
            delay(1_500)
        }
    }

    Row(
        modifier = modifier.horizontalScroll(scroll, enabled = true),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}


@Composable
fun PerformanceMetricsSectionDating(profile: Profile, showLocation: Boolean = true) {
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    CollapsibleSection(
        title = stringResource(R.string.performance_metrics),
        icon = Icons.Default.Assessment
    ) {
        ProfileDetailRow(
            label = stringResource(R.string.matches),
            value = stringResource(R.string.number, profile.matchCount),
            icon = Icons.Default.People
        )
        ProfileDetailRow(
            label = stringResource(R.string.rating),
            value = String.format(
                "%.2f",
                profile.averageRating
            ),
            icon = Icons.Default.Star
        )
        ProfileDetailRow(
            label = stringResource(R.string.swipe_right_percentage),
            value = stringResource(
                R.string.percentage,
                (profile.averageSwipeRightsOnUser * 100).roundToInt()
            ),
            icon = Icons.Default.Swipe
        )
        ProfileDetailRow(
            label = stringResource(R.string.west_bengal_ranking),
            value = stringResource(R.string.number, profile.am24Ranking),
            icon = Icons.Default.Public
        )

        if (showLocation) {
            val cityRank = if (profile.city == "Other")
                profile.am24RankingCustomCity
            else
                profile.am24RankingCity
            if (cityRank > 0) {
                ProfileDetailRow(
                    label = stringResource(
                        R.string.city_ranking,
                        profile.city.ifBlank { stringResource(R.string.city) }),
                    value = stringResource(R.string.number, cityRank),
                    icon = Icons.Default.LocationCity
                )
            }

            val hoodRank = if (profile.hometown == "Other")
                profile.am24RankingCustomHometown
            else
                profile.am24RankingHometown
            if (hoodRank > 0) {
                ProfileDetailRow(
                    label = stringResource(
                        R.string.locality_ranking,
                        profile.hometown.ifBlank { stringResource(R.string.locality) }),
                    value = stringResource(R.string.number, hoodRank),
                    icon = Icons.Default.Home
                )
            }
        }
        ProfileDetailRow(
            label = stringResource(R.string.age_ranking),
            value = stringResource(R.string.number, profile.am24RankingAge),
            icon = Icons.Default.Cake
        )

        if (profile.highSchool.isNotBlank()) {
            ProfileDetailRow(
                label = stringResource(R.string.high_school_ranking, profile.highSchool),
                value = stringResource(R.string.number, profile.am24RankingHighSchool),
                icon = Icons.Default.School
            )
            if (!profile.highSchoolGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    label = stringResource(
                        R.string.high_school_graduation_year,
                        profile.highSchool
                    ),
                    value = profile.highSchoolGraduationYear,
                    icon = Icons.Default.School
                )
            }
        }

        if (profile.college.isNotBlank()) {
            ProfileDetailRow(
                label = stringResource(R.string.college_ranking, profile.college),
                value = stringResource(R.string.number, profile.am24RankingCollege),
                icon = Icons.Default.Book
            )
            if (!profile.collegeGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    label = stringResource(R.string.college_graduation_year, profile.college),
                    value = profile.collegeGraduationYear,
                    icon = Icons.Default.Book
                )
            }
        }
    }
}

@Composable
fun showVoiceBio(profile: Profile) {
    Column {
        if (!profile.voiceNoteUrl.isNullOrEmpty()) {
            VoicePlayer(url = profile.voiceNoteUrl)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (profile.bio.isNotBlank()) {
            ProfileDetailRow(
                label = stringResource(R.string.bio),
                value = profile.bio,
                icon = Icons.Default.BlurOn
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (profile.averageRating > 0.0 && profile.numberOfRatings > 0) {
            RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PostsOverlay(posts: List<Post>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkGrayBackground),
            color = DarkGrayBackground
        ) {
            Box {
                if (posts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_posts), color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp)
                    ) {
                        items(posts) { post ->
                            PostItemInProfile(post)
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(32.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

fun handleSwipeRight(
    currentUserId: String,
    otherUserId: String,
    profileViewModel: ProfileViewModel
) {
    val database = FirebaseRefs.db
    val timestamp = System.currentTimeMillis()

    val swipeData = SwipeData(liked = true, timestamp = timestamp)

    // Basic swipe + like writes
    database.getReference("swipes/$currentUserId/$otherUserId").setValue(swipeData)
    database.getReference("likesGiven/$currentUserId/$otherUserId").setValue(timestamp)
    database.getReference("likesReceived/$otherUserId/$currentUserId").setValue(timestamp)
    profileViewModel.sendLikeNotification(currentUserId, otherUserId, {}, {})
    database.getReference("swipesReceived/$otherUserId/$currentUserId").setValue(true)

    // Track per-user swipe counts
    val swipeCountRef = database.getReference("users/$currentUserId/swipeCounts/$otherUserId")
    swipeCountRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val current = mutableData.getValue(Int::class.java) ?: 0
            mutableData.value = current + 1
            return Transaction.success(mutableData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (committed) {
                val count = snapshot?.getValue(Int::class.java) ?: 0
                if (count >= 3) {
                    database.getReference("users/$currentUserId/permanentExcludes/$otherUserId")
                        .setValue(true)
                }
            }
        }
    })

    // Increment their swipe stats
    database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
        .get().addOnSuccessListener { snap ->
            val cnt = snap.getValue(Double::class.java) ?: 0.0
            database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
                .setValue(cnt + 1)
        }
    database.getReference("users/$otherUserId/numberOfSwipeRights")
        .get().addOnSuccessListener { snap ->
            val cnt = snap.getValue(Int::class.java) ?: 0
            database.getReference("users/$otherUserId/numberOfSwipeRights")
                .setValue(cnt + 1)
        }

    // ✅ Check if they already liked you → mutual like → MATCH
    database.getReference("swipes/$otherUserId/$currentUserId")
        .get().addOnSuccessListener { snap ->
            val theyLikedYou = snap.getValue(SwipeData::class.java)?.liked == true
            if (theyLikedYou) {
                // Create match on both sides
                database.getReference("matches/$currentUserId/$otherUserId")
                    .setValue(timestamp)
                database.getReference("matches/$otherUserId/$currentUserId")
                    .setValue(timestamp)

                // ✅ NEW: clean up all like records once it becomes a match
                val cleanupUpdates = hashMapOf<String, Any?>(
                    // from their perspective
                    "likesReceived/$currentUserId/$otherUserId" to null,
                    "likesGiven/$otherUserId/$currentUserId" to null,
                    // from your perspective
                    "likesReceived/$otherUserId/$currentUserId" to null,
                    "likesGiven/$currentUserId/$otherUserId" to null
                )
                database.reference.updateChildren(cleanupUpdates)

                // Notifications
                profileViewModel.sendMatchNotification(currentUserId, otherUserId, {}, {})
                profileViewModel.sendMatchNotification(otherUserId, currentUserId, {}, {})

                // Stats
                incrementMatchStats(currentUserId)
                incrementMatchStats(otherUserId)

                // DM popup
                profileViewModel.triggerMatchPopUp(currentUserId, otherUserId)

                // If they had sent a compliment before match, auto-drop into chat as first message
                database.getReference("compliments/$currentUserId/$otherUserId")
                    .get().addOnSuccessListener { cSnap ->
                        val text = cSnap.child("text").getValue(String::class.java) ?: ""
                        val voiceUrl = cSnap.child("voiceUrl").getValue(String::class.java)

                        if (text.isNotBlank() || voiceUrl != null) {
                            val chatId = getChatId(currentUserId, otherUserId)

                            val pushKey = database.reference
                                .child("messages/$chatId")
                                .push().key
                                ?: return@addOnSuccessListener

                            val msg = Message(
                                id = pushKey,
                                senderId = currentUserId,
                                receiverId = otherUserId,
                                text = text,
                                timestamp = timestamp,
                                read = false,
                                mediaType = if (voiceUrl != null) "voice" else null,
                                mediaUrl = voiceUrl,
                                processed = false
                            )

                            database.getReference("messages/$chatId/$pushKey")
                                .setValue(msg)
                        }
                    }
            }
        }

    ExclusionEventBus.emit(otherUserId)
}

fun handleSwipeLeft(currentUserId: String, otherUserId: String) {
    val database = FirebaseRefs.db
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")
    currentUserSwipesRef.setValue(SwipeData(liked = false, timestamp = timestamp))

    val swipeCountRef = database.getReference("users/$currentUserId/swipeCounts/$otherUserId")
    swipeCountRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val current = mutableData.getValue(Int::class.java) ?: 0
            mutableData.value = current + 1
            return Transaction.success(mutableData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (committed) {
                val count = snapshot?.getValue(Int::class.java) ?: 0
                if (count >= 3) {
                    database.getReference("users/$currentUserId/permanentExcludes/$otherUserId")
                        .setValue(true)
                }
            }
        }
    })

    database.getReference("dislikesGiven/$currentUserId/$otherUserId").setValue(timestamp)
    database.getReference("swipesReceived/$otherUserId/$currentUserId").setValue(true)

    val otherUserProfileRef = database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
    otherUserProfileRef.get().addOnSuccessListener { snapshot ->
        val currentCount = snapshot.getValue(Double::class.java) ?: 0.0
        otherUserProfileRef.setValue(currentCount + 1)
    }
}

private fun incrementMatchStats(userId: String) {
    val db = FirebaseRefs.db
    val matchCountRef = db.getReference("users/$userId/matchCount")
    matchCountRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val current = mutableData.getValue(Int::class.java) ?: 0
            mutableData.value = current + 1
            return Transaction.success(mutableData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (!committed) {
                Log.e("incrementMatchStats", "Failed for $userId: ${error?.message}")
                return
            }
            val newCount = snapshot?.getValue(Int::class.java) ?: 0
            db.getReference("users/$userId/numberOfSwipeRights")
                .get().addOnSuccessListener { srSnap ->
                    val swipes = srSnap.getValue(Int::class.java) ?: 0
                    val ratio = if (swipes > 0) newCount.toDouble() / swipes else 0.0
                    db.getReference("users/$userId/matchCountPerSwipeRight")
                        .setValue(ratio)
                }
        }
    })
}

fun runAiMatchCheck(
    context: Context,
    coroutineScope: CoroutineScope,
    currentUserId: String,
    currentUserProfile: Profile,
    otherProfile: Profile,
    onComplete: (AiMatchCheckResult) -> Unit
) {
    val (finalScore, insights) = calculateExhaustiveCompatibilityScore(
        context,
        currentUserProfile,
        otherProfile
    )

    val breakdownText = formatInsights(context, insights)

    val summaryText = context.getString(
        R.string.match_summary_format,
        finalScore,
        breakdownText
    )

    Log.d("runAiMatchCheck", "Compatibility Summary: $summaryText")

    val result = AiMatchCheckResult(
        summary = summaryText,
        totalMatchPercentage = finalScore,
        compatibilityBreakdown = breakdownText,
        timestamp = System.currentTimeMillis()
    )

    FirebaseRefs.db
        .getReference("aiMatchCheck/$currentUserId/${otherProfile.userId}")
        .setValue(result)

    coroutineScope.launch(Dispatchers.Main) {
        onComplete(result)
    }
}

fun calculateExhaustiveCompatibilityScore(
    context: Context,
    profileA: Profile,
    profileB: Profile
): Pair<Int, List<MatchInsight>> {
    var score = 0.0
    var possible = 0.0
    val insights = mutableListOf<MatchInsight>()
    var kinkScore = 0.0
    var kinkWeight = 0.0

    fun compareStringField(a: String?, b: String?, label: String, pts: Double) {
        val aTrim = a.orEmpty().trim()
        val bTrim = b.orEmpty().trim()
        if (aTrim.isEmpty() || bTrim.isEmpty()) return
        possible += pts
        if (aTrim.equals(bTrim, true)) {
            score += pts
            insights += MatchInsight("✅", "$label: $aTrim", true)
        }
    }

    fun rolesAreCompatible(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val normA = a.map { canonicalRole(it) }
        val normB = b.map { canonicalRole(it) }
        val flexible = setOf("switch", "vers", "open", "side")
        if (normA.any { it in flexible } || normB.any { it in flexible }) return true
        val topA = "top" in normA
        val bottomA = "bottom" in normA
        val domA = "dom" in normA
        val subA = "sub" in normA
        val topB = "top" in normB
        val bottomB = "bottom" in normB
        val domB = "dom" in normB
        val subB = "sub" in normB
        return (topA && bottomB) || (bottomA && topB) || (domA && subB) || (subA && domB)
    }

    val rolesA = profileA.roles.toSet()
    val rolesB = profileB.roles.toSet()
    if (rolesAreCompatible(rolesA, rolesB)) {
        insights += MatchInsight("✅", context.getString(R.string.roles_compatible), true)
    }

    val tribesA = profileA.tribes.map { canonicalTribe(it) to it }.filter { it.first.isNotEmpty() }.toMap()
    val tribesB = profileB.tribes.map { canonicalTribe(it) to it }.filter { it.first.isNotEmpty() }.toMap()
    val sharedTribes = tribesA.keys intersect tribesB.keys
    if (sharedTribes.isNotEmpty()) {
        val display = sharedTribes.map { key -> tribesA[key] ?: tribesB[key]!! }
        insights += MatchInsight("✅", context.getString(R.string.shared_tribes_prefix, display.joinToString()), true)
    }

    val kinksA = profileA.kinks.map { canonicalKink(it) to it }.filter { it.first.isNotEmpty() }.toMap()
    val kinksB = profileB.kinks.map { canonicalKink(it) to it }.filter { it.first.isNotEmpty() }.toMap()
    if (kinksA.isNotEmpty() && kinksB.isNotEmpty()) {
        kinkWeight = 0.25
        val shared = kinksA.keys intersect kinksB.keys
        val union = kinksA.keys union kinksB.keys
        kinkScore = if (union.isNotEmpty()) (shared.size.toDouble() / union.size.toDouble()) * 100.0 else 0.0
        if (shared.isNotEmpty()) {
            val display = shared.map { key -> kinksA[key] ?: kinksB[key]!! }
            insights += MatchInsight("✅", context.getString(R.string.shared_kinks_prefix, display.joinToString()), true)
        }
    }

    val ageA = profileA.age
    val ageB = profileB.age
    if (ageA > 0 && ageB > 0) {
        possible += 5.0
        val aScore = ageCompatibilityScore(ageA, ageB)
        score += 5.0 * aScore
        val pct = (aScore * 100).roundToInt()
        if (aScore >= 0.5) {
            insights += MatchInsight(
                "✅",
                context.getString(R.string.age_compatibility_prefix, ageA, ageB, pct),
                true
            )
        }
    }

    val (lifeScore, lifeCount, lifeCommonKeys) = lifestyleCompatibilityMetrics(profileA.lifestyle, profileB.lifestyle)
    if (lifeCount > 0) {
        possible += 8.0
        score += 8.0 * lifeScore

        val keyToLabel = mapOf(
            "smoking_habit" to R.string.lifestyle_smoking,
            "drinking_habit" to R.string.lifestyle_drinking,
            "indoor_outdoor_orientation" to R.string.lifestyle_indoor_outdoor,
            "sexual_activity_level" to R.string.lifestyle_sexual_activity,
            "sociability" to R.string.lifestyle_sociability,
            "social_media_engagement" to R.string.lifestyle_social_media,
            "sleep_pattern" to R.string.lifestyle_sleep,
            "work_life_balance" to R.string.lifestyle_work_life_balance,
            "exercise_frequency" to R.string.lifestyle_exercise,
            "adventurousness" to R.string.lifestyle_adventurousness,
            "family_orientated" to R.string.lifestyle_family_oriented,
            "intellectual_curiosity" to R.string.lifestyle_intellectual_curiosity,
            "creative_expression" to R.string.lifestyle_creative_expression,
            "physical_fitness" to R.string.lifestyle_physical_fitness,
            "spirituality_mindfulness" to R.string.lifestyle_spirituality,
            "easy_goingness" to R.string.lifestyle_humor,
            "professional_ambition" to R.string.lifestyle_professional_ambition,
            "environmental_awareness" to R.string.lifestyle_environmental_awareness,
            "culinary_enthusiasm" to R.string.lifestyle_culinary_enthusiasm,
            "political_awareness" to R.string.lifestyle_political_awareness,
            "community_engagement" to R.string.lifestyle_community_engagement,
            "sports_enthusiasm" to R.string.lifestyle_sports,
            "dietary_preferences" to R.string.lifestyle_dietary_preferences,
        )
        val commonNames = lifeCommonKeys.mapNotNull { keyToLabel[it] }.map { context.getString(it) }
        val listStr = if (commonNames.isEmpty()) context.getString(R.string.lifestyle_none_common) else commonNames.joinToString()
        val pct = (lifeScore * 100).roundToInt()
        if (pct >= 50) {
            insights += MatchInsight(
                "✅",
                context.getString(R.string.lifestyle_similarity_format, pct, lifeCount, listStr),
                true
            )
        }
    }

    compareStringField(profileA.work, profileB.work, context.getString(R.string.workplace_label), 8.0)
    compareStringField(profileA.jobRole, profileB.jobRole, context.getString(R.string.job_role_label), 5.0)
    compareStringField(profileA.loveLanguage, profileB.loveLanguage, context.getString(R.string.love_language_label), 4.0)
    compareStringField(profileA.lookingFor, profileB.lookingFor, context.getString(R.string.relationship_intent_label), 6.0)
    compareStringField(profileA.community, profileB.community, context.getString(R.string.community), 5.0)
    compareStringField(profileA.religion, profileB.religion, context.getString(R.string.religion), 4.0)
    compareStringField(profileA.city, profileB.city, context.getString(R.string.city), 4.0)
    compareStringField(profileA.preferredLanguage, profileB.preferredLanguage, context.getString(R.string.preferred_language_label), 3.0)

    val interestsA = profileA.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    val interestsB = profileB.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    if (interestsA.isNotEmpty() && interestsB.isNotEmpty()) {
        possible += 8.0
        val shared = interestsA intersect interestsB
        if (shared.isNotEmpty()) {
            val pts = (shared.size * 2).coerceAtMost(8)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_interests_prefix, shared.joinToString()), true)
        }
    }

    val causesA = profileA.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val causesB = profileB.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (causesA.isNotEmpty() && causesB.isNotEmpty()) {
        possible += 6.0
        val shared = causesA intersect causesB
        if (shared.isNotEmpty()) {
            val pts = (shared.size * 2).coerceAtMost(6)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_social_causes_prefix, shared.joinToString()), true)
        }
    }

    val zodiacA = if (!profileA.zodiac.isNullOrBlank()) profileA.zodiac!! else deriveZodiac(profileA.dob)
    val zodiacB = if (!profileB.zodiac.isNullOrBlank()) profileB.zodiac!! else deriveZodiac(profileB.dob)
    if (zodiacA != "Unknown" && zodiacB != "Unknown") {
        possible += 5.0
        val zScore = zodiacCompatibilityScore(zodiacA, zodiacB)
        score += 5.0 * zScore
        val pct = (zScore * 100).roundToInt()
        if (zScore >= 0.5) {
            insights += MatchInsight(
                "✅",
                context.getString(
                    R.string.zodiac_compatibility_prefix,
                    zodiacA,
                    zodiacB,
                    pct
                ),
                true
            )
        }
    }

    if (profileA.isMatrimonyMode && profileB.isMatrimonyMode) {
        possible += 3.0
        score += 3.0
        compareStringField(profileA.marriageTimeline, profileB.marriageTimeline, context.getString(R.string.marriage_timeline_label), 3.0)
    }

    val relocA = profileA.relocationPreference.orEmpty().trim()
    val relocB = profileB.relocationPreference.orEmpty().trim()
    if (relocA.isNotBlank() && relocB.isNotBlank()) {
        possible += 4.0
        if (relocA.equals(relocB, true)) score += 4.0
        else insights += MatchInsight("⚠️", context.getString(R.string.relocation_mismatch_format, relocA, relocB), false)
    }

    val careerA = profileA.postMarriageCareerPlan.orEmpty().trim()
    val careerB = profileB.postMarriageCareerPlan.orEmpty().trim()
    if (careerA.isNotBlank() && careerB.isNotBlank()) {
        possible += 3.0
        if (careerA.equals(careerB, true)) score += 3.0
        else insights += MatchInsight("⚠️", context.getString(R.string.career_plan_mismatch_format, careerA, careerB), false)
    }

    val cultureA = profileA.traditionalVsLiberal.orEmpty().trim()
    val cultureB = profileB.traditionalVsLiberal.orEmpty().trim()
    if (cultureA.isNotBlank() && cultureB.isNotBlank()) {
        possible += 3.0
        if (cultureA.equals(cultureB, true)) score += 3.0
        else insights += MatchInsight("⚠️", context.getString(R.string.cultural_mindset_mismatch_format, cultureA, cultureB), false)
    }

    val tagsA = profileA.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val tagsB = profileB.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (tagsA.isNotEmpty() && tagsB.isNotEmpty()) {
        possible += 2.0
        val shared = tagsA intersect tagsB
        if (shared.isNotEmpty()) {
            val pts = shared.size.coerceAtMost(2)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_tags_prefix, shared.joinToString()), true)
        }
    }

    val base = if (possible == 0.0) 0.0 else (score / possible) * 100.0
    val combinedBase = base * (1 - kinkWeight) + kinkScore * kinkWeight
    val targetCompositePct = profileB.compositeScorePct
    val blended = ((2 * combinedBase) + targetCompositePct) / 3.0
    val finalScore = blended.roundToInt().coerceIn(0, 100)
    return finalScore to insights
}

private fun formatInsights(ctx: Context, list: List<MatchInsight>): String {
    val positives = list.filter { it.isPositive }.take(4)
    val concerns = list.filter { !it.isPositive && it.emoji == "⚠️" }.take(4)
    val infos = list.filter { it.emoji == "ℹ️" }.take(2)

    fun block(title: Int, items: List<MatchInsight>) =
        if (items.isEmpty()) "" else
            ctx.getString(title) + ":\n" +
                    items.joinToString("\n") { "${it.emoji} ${it.text}" }

    return listOf(
        block(R.string.strengths, positives),
        block(R.string.concerns, concerns),
        block(R.string.notes, infos)
    ).filter { it.isNotBlank() }
        .joinToString("\n\n")
}

suspend fun loadAndResetSwipesDaily(userId: String): Int {
    val userRef  = FirebaseRefs.db.getReference("users/$userId")
    val userSnap = userRef.get().await()

    val isPremium = userSnap.child("isPremium").getValue(Boolean::class.java) ?: false
    val isPlus    = userSnap.child("isPlus").getValue(Boolean::class.java) ?: false
    val quota     = when {
        isPremium -> Int.MAX_VALUE
        isPlus    -> 50
        else      -> 20
    }

    val swipesRef = userRef.child("swipesInfo")
    val snap      = swipesRef.get().await()

    val snapRemaining = snap.child("remainingSwipes").getValue(Int::class.java) ?: quota
    val lastReset     = snap.child("lastResetDayOfYear").getValue(Int::class.java) ?: -1
    val today   = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val newDay  = today != lastReset

    var remaining = snapRemaining

    if (newDay) {
        remaining = quota
    }

    if (newDay || remaining != snapRemaining) {
        swipesRef.child("remainingSwipes").setValue(remaining)
        swipesRef.child("lastResetDayOfYear").setValue(today)
    }

    return remaining
}

@Composable
fun TagBox(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(Color.Black, shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFFF6F00), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 10,
            softWrap = true
        )
    }
}
/** Updates the user's remainingSwipes in Firebase. */
fun updateSwipesInFirebase(newSwipesCount: Int) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val swipesRef = FirebaseRefs.db.getReference("users/$userId/swipesInfo")
    swipesRef.child("remainingSwipes").setValue(newSwipesCount)
}

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return (earthRadius * c).toFloat()
}

suspend fun calculateDistance(userId1: String, userId2: String, geoFire: GeoFire): Float? =
    withContext(Dispatchers.Default) {
        val loc1 = getUserLocation(userId1, geoFire)
        val loc2 = getUserLocation(userId2, geoFire)
        if (loc1 != null && loc2 != null) {
            haversine(loc1.latitude, loc1.longitude, loc2.latitude, loc2.longitude)
        } else null
    }

suspend fun getUserLocation(userId: String, geoFire: GeoFire): GeoLocation? =
    suspendCancellableCoroutine { continuation ->
        geoFire.getLocation(userId, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                continuation.resume(location)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                continuation.resumeWithException(databaseError.toException())
            }
        })
    }