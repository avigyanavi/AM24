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
    val gender: String = "",
    val profilepicUrl: String? = null,
    val videoUrl: String? = null,
    val voiceNoteUrl: String? = null,
    val optionalPhotoUrls: List<String> = emptyList(),  // URLs of optional photos
    val matches: List<String> = emptyList(),  // List of matched user IDs
    val religion: String = "",
    val community: String = "",

    val politics: String = "",  // Political preferences (e.g., liberal, conservative)
    val fitnessLevel: String = "",  // Fitness level (e.g., active, moderate)

    // Updated fields
    val country: String = "",  // Current country
    val city: String = "",     // Current city
    val hometown: String = "",  // User's hometown
    val customCity: String? = null, // Custom value for city
    val customHometown: String? = null,  // Custom value for hometown

    // New Fields: Education and Job Role
    val educationLevel: String = "",  // (New) High School, Bachelors, Masters, PhD, etc.
    val highSchool: String = "",  // Removed from the registration process for now, moved to Edit Profile
    val customHighSchool: String? = null,
    val college: String = "",  // Removed from the registration process for now, moved to Edit Profile
    val customCollege: String? = null,
    val postGraduation: String? = "",
    val customPostGraduation: String? = null,

    // New Field: Claimed Income Level
    val claimedIncomeLevel: String? = null, // Optional income level per annum

    // Lifestyle Section
    val lifestyle: Lifestyle? = null,

    // Work Details
    val jobRole: String = "",
    val customJobRole: String? = null,
    val work: String = "",
    val customWork: String? = null,

    // Social Causes, Politics, and Preferences
    val socialCauses: List<String> = emptyList(),
    val politicalViews: String = "",  // Conservative, Liberal, Centrist, etc.
    val lookingFor: String = "",      // What the user is looking for (e.g., Friendship, Dating)

    val followers: MutableMap<String, Boolean> = mutableMapOf(),
    val following: MutableMap<String, Boolean> = mutableMapOf(),

    // New Metrics and Rankings
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val isBoosted: Boolean = false,

    val am24RankingGlobal: Int = 0,
    val am24RankingAge: Int = 0,
    val am24RankingHighSchool: Int = 0,
    val am24RankingCollege: Int = 0,
    val am24RankingGender: Int = 0,
    val am24RankingHometown: Int = 0,
    val am24RankingCountry: Int = 0,
    val am24RankingCity: Int = 0,
    val rating: Double = 0.0,
    val numberOfRatings: Int = 0,

    val numberOfSwipeRights: Int = 0,
    val numberOfSwipeLefts: Int = 0,
    val swipeRightToSwipeLeftRatio: Double = 0.0,
    val matchCount: Int = 0,
    val matchCountPerSwipeRight: Double = 0.0,

    val cumulativeUpvotes: Int = 0,
    val cumulativeDownvotes: Int = 0,
    val averageUpvoteCount: Double = 0.0,
    val averageDownvoteCount: Double = 0.0,

    val userTags: List<String> = emptyList(),
    val zodiac: String? = null,

    val dateOfJoin: Long = System.currentTimeMillis(),
    val am24RankingCompositeScore: Double = 0.0,
    val level: Int = 1,

    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val distancePreference: Float = 10f,

    @Exclude
    var ratingsGiven: Map<String, Float> = emptyMap(),

    @Exclude
    var ratingsReceived: Map<String, Float> = emptyMap()
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

        val engagementScore = (cumulativeUpvotes - cumulativeDownvotes) * 0.05

        return ratingScore + matchScore + upvoteScore + swipeRatioScore + followersScore + followingScore + engagementScore
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

data class Lifestyle(
    val smoking: String = "",               // Smoking habit (e.g., "Non-smoker", "Social Smoker", "Regular Smoker")
    val drinking: String = "",              // Drinking habit (e.g., "Occasional", "Social Drinker", "Heavy Drinker")
    val alcoholType: String = "",           // Type of alcohol (e.g., "Beer", "Vodka", "Wine")
    val cannabisFriendly: Boolean = false,  // Cannabis Friendly
    val laidBack: Boolean = false,          // Personality trait
    val socialButterfly: Boolean = false,   // Social Butterfly (outgoing)
    val diet: String = "",                  // Diet (e.g., "Vegan", "Vegetarian", "Non-Vegetarian", "Keto")
    val sleepCycle: String = "",            // Sleep cycle (e.g., "Early Riser", "Night Owl", "Balanced")
    val workLifeBalance: String = "",       // Work-life balance (e.g., "Workaholic", "Balanced", "Relaxed")
    val exerciseFrequency: String = "",     // Exercise routine (e.g., "Daily", "Occasionally", "Rarely")
    val adventurous: Boolean = false,       // Adventurous personality
    val petFriendly: Boolean = false        // Pet-friendly (likes pets or has pets)
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
