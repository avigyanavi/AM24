package com.am24.am24

import android.net.Uri
import com.google.firebase.database.Exclude
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Profile(
    val email: String = "",
    val password: String = "",

    // Keep this: what the user is interested in (Men, Women, etc.)
    val interestedIn: List<String> = emptyList(),

    val userId: String = "",
    val username: String = "",  // Unique username (e.g., MS1)
    val name: String = "",
    val dob: String = "",  // Date of birth for age calculation
    val bio: String = "",  // One-liner bio
    val interests: List<Interest> = emptyList(),  // Interests for matching purposes

    // The user's own gender (Male/Female/Other)
    val gender: String = "",

    val lastActive: Long = System.currentTimeMillis(),
    val badges: List<String> = emptyList(),
    val profilepicUrl: String? = null,
    val voiceNoteUrl: String? = null,
    val optionalPhotoUrls: List<String> = emptyList(),  // URLs of optional photos
    val matches: List<String> = emptyList(),  // List of matched user IDs
    val religion: String = "",
    val community: String = "",
    val city: String = "",     // Current city
    val hometown: String = "",  // User's hometown
    val customCity: String? = null, // Custom value for city
    val customHometown: String? = null,  // Custom value for hometown
    val educationLevel: String = "",  // (New) High School, Bachelors, Masters, PhD, etc.
    val highSchool: String = "",
    val customHighSchool: String? = null,
    val highSchoolGraduationYear: String = "", // New field
    val college: String = "",
    val customCollege: String? = null,
    val collegeGraduationYear: String = "", // New field
    val postGraduation: String? = "",
    val customPostGraduation: String? = null,
    val postGraduationYear: String = "", // New field

    val lifestyle: Lifestyle? = null,  // Lifestyle Section
    val politics: String = "",  // Political preferences (e.g., liberal, conservative)
    val jobRole: String = "",
    val customJobRole: String? = null,
    val work: String = "",
    val customWork: String? = null,
    val socialCauses: List<String> = emptyList(),
    val lookingFor: String = "",      // What the user is looking for (e.g., Friendship, Dating)
    val likedUsers: MutableMap<String, Boolean> = mutableMapOf(),
    val UsersWhoLikeMe: MutableMap<String, Boolean> = mutableMapOf(),
    val isBoosted: Boolean = false,

    val am24RankingAge: Int = 0,
    val am24RankingHighSchool: Int = 0,
    val am24RankingCollege: Int = 0,
    val am24RankingGender: Int = 0,
    val am24RankingHometown: Int = 0,
    val am24Ranking: Int = 0,
    val am24RankingCity: Int = 0,

    val numberOfRatings: Int = 0,
    val numberOfSwipeRights: Int = 0,
    val matchCount: Int = 0,
    val matchCountPerSwipeRight: Double = 0.0,
    val cumulativeUpvotes: Int = 0,
    val cumulativeDownvotes: Int = 0,
    val averageUpvoteCount: Double = 0.0,
    val averageDownvoteCount: Double = 0.0,

    val profileUpvotes: MutableMap<String, Boolean> = mutableMapOf(), // Users who upvoted
    val profileDownvotes: MutableMap<String, Boolean> = mutableMapOf(), // Users who downvoted
    val reportUsers: MutableMap<String, Boolean> = mutableMapOf(), // Users who reported this profile
    val blockedUsers: MutableMap<String, Boolean> = mutableMapOf(), // Users who blocked this profile

    var upvoteCount: Int = 0,
    var downvoteCount: Int = 0,
    val userTags: List<String> = emptyList(),
    val zodiac: String? = null,
    val dateOfJoin: Long = System.currentTimeMillis(),
    val am24RankingCompositeScore: Double = 0.0,
    var vibepoints: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    var averageRating: Double = 0.0,

    // Keep these three for the user's personal dating prefs from registration
    val datingAgeStart: Int = 18,
    val datingAgeEnd: Int = 30,
    val datingDistancePreference: Int = 10,

    val height: Int = 169,
    val height2: List<Int> = emptyList(),
    var caste: String = "",
    var relationship: String? = null, // Add this to hold "friend", "match", etc.

    @Exclude
    var ratingsGiven: Map<String, Float> = emptyMap(),

    @Exclude
    var ratingsReceived: Map<String, Float> = emptyMap()
) {

    @get:Exclude
    val profileCompletionPercentage: Int
        get() {
            val fields = listOf(
                name, username, dob, bio, gender, profilepicUrl, religion, community,
                city, hometown, educationLevel, highSchool, college, postGraduation,
                jobRole, work, lookingFor
            )
            val filledFieldsCount = fields.count { !it.isNullOrEmpty() }
            val lifestyleCompleted = lifestyle != null && lifestyle.isComplete()
            val completedFields = filledFieldsCount + if (lifestyleCompleted) 1 else 0
            val totalFields = fields.size + 1 // Lifestyle is counted as one additional field

            return ((completedFields.toDouble() / totalFields) * 100).toInt()
        }

    @Exclude
    fun getCalculatedMatchCountPerSwipeRight(): Double {
        return if (numberOfSwipeRights > 0) {
            matchCount.toDouble() / numberOfSwipeRights
        } else 0.0
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
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val mediaType: String? = null,
    val mediaUrl: String? = null,
    val isOnline: Boolean = false,
)

data class Lifestyle(
    var smoking: Int = 0,
    var drinking: Int = 0,
    var cannabisFriendly: Boolean = false,
    var indoorsyToOutdoorsy: Int = 3,
    var socialMedia: Int = 3,
    var diet: String = "",
    var sleepCycle: Int = 3,
    var workLifeBalance: Int = 3,
    var exerciseFrequency: Int = 0,
    var adventurous: Int = 3,
    val petFriendly: Boolean = false,
    var familyOriented: Int = 3,
    val intellectual: Int = 3,
    var creativeArtistic: Int = 3,
    val fitnessLevel: Int = 3,
    val spiritualMindful: Int = 3,
    val humorousEasyGoing: Int = 3,
    var professionalAmbitious: Int = 3,
    var environmentallyConscious: Int = 3,
    val foodieCulinaryEnthusiast: Int = 3,
    val politicallyAware: Int = 3,
    val communityOriented: Int = 3,
    var sportsEnthusiast: Int = 3,
    var sal: Int = 3,
    var IE: Int = 3,
    var alcoholType: String = "",
    var caste: String = "",
) {
    fun isComplete(): Boolean {
        val fields = listOf(
            smoking, drinking, indoorsyToOutdoorsy, socialMedia, diet, sportsEnthusiast, sal, IE,
            sleepCycle, workLifeBalance, exerciseFrequency, adventurous, familyOriented,
            intellectual, creativeArtistic, fitnessLevel, spiritualMindful,
            humorousEasyGoing, professionalAmbitious, environmentallyConscious,
            foodieCulinaryEnthusiast, politicallyAware, communityOriented, alcoholType
        )
        return fields.all {
            when (it) {
                is String -> it.isNotBlank()
                is Int -> it != 0
                is Boolean -> true // Booleans are "valid" as true/false
                else -> false
            }
        }
    }
}

/** Utility to derive zodiac from dob if needed. */
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
    val month = calendar.get(Calendar.MONTH) + 1

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
