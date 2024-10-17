package com.am24.am24

import com.google.firebase.database.Exclude
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Profile(
    val userId: String = "",
    val username: String = "",  // Unique username (e.g., MS1)
    val name: String = "",
    val dob: String = "",  // Date of birth for age calculation
    val bio: String = "",  // One-liner bio
    val interests: List<Interest> = emptyList(),  // Interests for matching purposes
    val hometown: String = "",  // User's locality (searchable dropdown)
    val customHometown: String? = null,  // Custom value for locality if not found in the dropdown
    val highSchool: String = "",  // User's high school (searchable dropdown)
    val customHighSchool: String? = null,  // Custom value for high school if not found in the dropdown
    val gender: String = "",
    val college: String = "",  // User's college (searchable dropdown)
    val customCollege: String? = null,  // Custom value for college if not found in the dropdown
    val profilepicUrl: String? = null,
    val optionalPhotoUrls: List<String> = emptyList(),  // URLs of optional photos
    val matches: List<String> = emptyList(),  // List of matched user IDs
    val religion: String = "",
    val community: String = "",
    val country: String = "",
    val city: String = "",
    val customCity: String? = "",
    val followers: MutableMap<String, Boolean> = mutableMapOf(),  // Map of userId to Boolean (true if followed)
    val following: MutableMap<String, Boolean> = mutableMapOf(),   // Map of userId to Boolean (true if following)

    // New Fields
    val followersCount: Int = 0,    // Total number of followers
    val followingCount: Int = 0,    // Total number of following
    val isBoosted: Boolean = false, // Indicates if the profile is boosted

    // Work Details
    val jobRole: String = "",             // Job role (e.g., Director)
    val customJobRole: String? = null,    // Custom job role if not listed in dropdown
    val work: String = "",                // Company or organization (e.g., Microsoft)
    val customWork: String? = null,       // Custom Company if not listed in dropdown

    // New Field: Claimed Income Level
    val claimedIncomeLevel: String? = null, // Optional income level per annum

    // Metrics and Rankings
    val am24RankingGlobal: Int = 0,        // Global AM24 ranking (All World)
    val am24RankingAge: Int = 0,           // Ranking per age group (All World)
    val am24RankingHighSchool: Int = 0,    // Ranking per high school
    val am24RankingCollege: Int = 0,       // Ranking per college
    val am24RankingGender: Int = 0,        // Ranking per gender
    val am24RankingHometown: Int = 0,      // Ranking per hometown (locality)
    val am24RankingCountry: Int = 0,       // Ranking per country (All World)
    val am24RankingCity: Int = 0,          // Ranking per city (All World)
    val rating: Double = 0.0,              // Average rating given by others in DMs (from Review Bar)
    val numberOfRatings: Int = 0,          // Number of people who rated the user

    // Swipe and Match Metrics
    val numberOfSwipeRights: Int = 0,      // Number of swipe right actions performed by this user
    val numberOfSwipeLefts: Int = 0,       // Number of swipe left actions performed by this user
    val swipeRightToSwipeLeftRatio: Double = 0.0,  // Calculated as numberOfSwipeRights / numberOfSwipeLefts
    val matchCount: Int = 0,               // Total number of matches this user has
    val matchCountPerSwipeRight: Double = 0.0,    // Calculated as matchCount / numberOfSwipeRights

    // Post Metrics
    val cumulativeUpvotes: Int = 0,        // Cumulative upvotes received on all posts
    val cumulativeDownvotes: Int = 0,      // Cumulative downvotes received on all posts
    val averageUpvoteCount: Double = 0.0,  // Average number of upvotes per post
    val averageDownvoteCount: Double = 0.0, // Average number of downvotes per post

    // Tags
    val userTags: List<String> = emptyList(),    // User tags aggregated from posts
    val zodiac: String? = null, // Derived Zodiac

    // AM24 Metrics
    val dateOfJoin: Long = System.currentTimeMillis(),  // Date when the user joined
    val am24RankingCompositeScore: Double = 0.0,        // Composite score for AM24 ranking (Kupid Score)
    val level: Int = 1, // Level determined by composite score


    // New Fields for Geolocation
    val latitude: Double = 0.0,   // User's current latitude
    val longitude: Double = 0.0,  // User's current longitude
    val distancePreference: Float = 10f, // Add distance preference field

    @Exclude
    var ratingsGiven: Map<String, Float> = emptyMap(), // Map of userId to rating

    // Ratings received from others
    @Exclude
    var ratingsReceived: Map<String, Float> = emptyMap() // Map of userId to rating
) {

    @Exclude
    fun getCalculatedSwipeRightToLeftRatio(): Double {
        return if (numberOfSwipeLefts > 0) {
            numberOfSwipeRights.toDouble() / numberOfSwipeLefts
        } else 0.0
    }

    @Exclude
    fun getCalculatedMatchCountPerSwipeRight(): Double {
        return if (numberOfSwipeRights > 0) {
            matchCount.toDouble() / numberOfSwipeRights
        } else 0.0
    }

    @Exclude
    fun getAverageRating(): Double {
        return if (ratingsReceived.isNotEmpty()) {
            ratingsReceived.values.average()
        } else 0.0
    }

    // Updated composite score calculation to include followers and following counts, and income level
    @Exclude
    fun calculateCompositeScore(
        matchCountPerSwipeRight: Double,
        averageUpvoteCount: Double,
        swipeRightToSwipeLeftRatio: Double,
        cumulativeUpvotes: Int,
        cumulativeDownvotes: Int,
        gender: String,
        followersCount: Int,
        followingCount: Int,
        claimedIncomeLevel: String? = null
    ): Double {
        val ratingScore = getAverageRating() * 0.1

        // Gender-specific multipliers for match score
        val matchScoreMultiplier = when (gender.lowercase()) {
            "male" -> 1.5
            "female" -> 1.0
            "non-binary" -> 1.2
            else -> 1.0
        }
        val matchScore = matchCountPerSwipeRight * matchScoreMultiplier * 0.1

        val upvoteScore = averageUpvoteCount * 0.1

        // Gender-specific multipliers for swipe ratio score
        val swipeRatioMultiplier = when (gender.lowercase()) {
            "male" -> 1.5
            "female" -> 1.0
            "non-binary" -> 1.2
            else -> 1.0
        }
        val swipeRatioScore = swipeRightToSwipeLeftRatio * swipeRatioMultiplier * 0.1

        // Incorporate followers and following counts
        val followersScore = followersCount * 0.05
        val followingScore = followingCount * 0.03

        // Incorporate claimed income level
        val incomeScore = when (claimedIncomeLevel?.lowercase()) {
            "low" -> 0.0
            "medium" -> 0.2
            "high" -> 0.4
            else -> 0.0
        }

        val engagementScore = (cumulativeUpvotes - cumulativeDownvotes) * 0.05

        return ratingScore + matchScore + upvoteScore + swipeRatioScore + followersScore + followingScore + incomeScore + engagementScore
    }

    // Updated level determination to use the new composite score
    @Exclude
    fun determineLevel(): Int {
        val score = calculateCompositeScore(
            matchCountPerSwipeRight = this.getCalculatedMatchCountPerSwipeRight(),
            averageUpvoteCount = this.averageUpvoteCount,
            swipeRightToSwipeLeftRatio = this.getCalculatedSwipeRightToLeftRatio(),
            cumulativeUpvotes = this.cumulativeUpvotes,
            cumulativeDownvotes = this.cumulativeDownvotes,
            gender = this.gender,
            followersCount = this.followersCount,
            followingCount = this.followingCount,
            claimedIncomeLevel = this.claimedIncomeLevel
        )
        return when {
            score >= 7 -> 7
            score >= 6 -> 6
            score >= 5 -> 5
            score >= 4 -> 4
            score >= 3 -> 3
            score >= 2 -> 2
            else -> 1
        }
    }
}

data class Interest(
    var name: String = "",
    var emoji: String? = "" // Make emoji nullable
) {
    // Firebase requires a no-argument constructor
    constructor() : this("", null)
}

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

fun deriveZodiac(dob: String?): String {
    if (dob.isNullOrBlank()) return "Unknown"

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val date = try {
        sdf.parse(dob)
    } catch (e: Exception) {
        null
    } ?: return "Unknown"

    val calendar = Calendar.getInstance().apply { time = date }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1 // Months are 0-based

    return when (month) {
        1 -> if (day <= 20) "Capricorn" else "Aquarius"
        2 -> if (day <= 19) "Aquarius" else "Pisces"
        3 -> if (day <= 20) "Pisces" else "Aries"
        4 -> if (day <= 20) "Aries" else "Taurus"
        5 -> if (day <= 21) "Taurus" else "Gemini"
        6 -> if (day <= 21) "Gemini" else "Cancer"
        7 -> if (day <= 22) "Cancer" else "Leo"
        8 -> if (day <= 23) "Leo" else "Virgo"
        9 -> if (day <= 23) "Virgo" else "Libra"
        10 -> if (day <= 23) "Libra" else "Scorpio"
        11 -> if (day <= 22) "Scorpio" else "Sagittarius"
        12 -> if (day <= 21) "Sagittarius" else "Capricorn"
        else -> "Unknown"
    }
}
