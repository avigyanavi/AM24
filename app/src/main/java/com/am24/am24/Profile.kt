package com.am24.am24

import android.net.Uri
import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@IgnoreExtraProperties
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
    var loveLanguage: String = "",  // e.g. "Touch", "Words of Affirmation", etc.
    val optionalPhotoUrls: List<String> = emptyList(),  // URLs of optional photos
    val matches: List<String> = emptyList(),  // List of matched user IDs
    val religion: String = "",
    val community: String = "",
    val hometown: String = "",  // User's hometown
    val customHometown: String? = null,  // Custom value for hometown
    val educationLevel: String = "",  // (New) High School, Bachelors, Masters, PhD, etc.
    val highSchool: String = "",
    val customHighSchool: String? = null,
    val highSchoolGraduationYear: String = "", // New field
    val college: String = "",
    val customCollege: String? = null,
    val collegeGraduationYear: String = "", // New field
    val collegeDegree: String? = null,      // <--- NEW FIELD for college degree (e.g. B.Sc, B.A, etc.)
    val postGraduation: String? = "",
    val customPostGraduation: String? = null,
    val postGraduationYear: String = "", // New field
    val postGraduationDegree: String? = null, // <--- NEW FIELD for PG degree (e.g. M.Sc, MBA, etc.)

    val lifestyle: Lifestyle? = null,  // Lifestyle Section
    val politics: String = "",  // Political preferences (e.g., liberal, conservative)
    val jobRole: String = "",
    val customJobRole: String? = null,
    val work: String = "",
    val customWork: String? = null,
    val socialCauses: List<String> = emptyList(),
    val lookingFor: String = "",      // What the user is looking for (e.g., Friendship, Dating)
    val likedUsers: MutableMap<String, Boolean> = mutableMapOf(),
    val numberOfUsersWhoSwiped: Double = 0.0,
    val UsersWhoLikeMe: MutableMap<String, Boolean> = mutableMapOf(),
    var isBoosted: Boolean = false,
    var isPremium: Boolean = false,
    var isPrivate: Boolean = false,

    val am24RankingAge: Int = 0,
    val am24RankingHighSchool: Int = 0,
    val am24RankingCollege: Int = 0,
    val am24RankingHometown: Int = 0,
    val am24Ranking: Int = 0,

    val numberOfRatings: Int = 0,
    val numberOfSwipeRights: Int = 0,
    val matchCount: Int = 0,
    val matchCountPerSwipeRight: Double = 0.0,
    val cumulativeUpvotes: Int = 0,
    val cumulativeDownvotes: Int = 0,
    val averageUpvoteCount: Double = 0.0,
    val averageDownvoteCount: Double = 0.0,

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
    var isMatrimonyMode: Boolean = false, // Toggle button for Matrimony Mode

    // Marriage-related fields (visible only if isMatrimonyMode is true)
    var marriageTimeline: String? = null,  // "Within 6 months", "1 year", "2-3 years", "No rush"
    var relocationPreference: String? = null,  // "Yes", "No", "Maybe"
    var postMarriageCareerPlan: String? = null,  // "Continue working", "Open to discussion", "Prefer homemaking"
    var traditionalVsLiberal: String? = null,  // "Traditional", "Moderate", "Liberal"

    // Family details (optional)
    var fatherOccupation: String? = null,
    var motherOccupation: String? = null,
    var numberOfSiblings: Int? = null,
    var elderSiblings: Int? = null,
    var youngerSiblings: Int? = null,

    // Matrimony Verification
    var isConsultantVerified: Boolean = false,  // You will verify the profile manually

    // Keep these three for the user's personal dating prefs from registration
    val datingAgeStart: Int = 18,
    val datingAgeEnd: Int = 30,
    val datingDistancePreference: Int = 10,

    val height: Int = 169,
    val height2: List<Int> = emptyList(),
    var caste: String = "",
    var relationship: String? = null, // Add this to hold "friend", "match", etc.

    var averageSwipeRightsOnUser: Double = 0.0,


    @Exclude
    var ratingsGiven: Map<String, Float> = emptyMap(),

    @Exclude
    var ratingsReceived: Map<String, Float> = emptyMap()
) {
    /** Calculate compatibility score between two profiles. */
    @Exclude
    fun calculateCompatibility(otherProfile: Profile): Double {
        var score = 0.0

        // 1. Shared interests
        val sharedInterests = interests.map { it.name }.intersect(otherProfile.interests.map { it.name })
        score += sharedInterests.size * 10 // Each shared interest adds 10 points

        // 2. Zodiac compatibility
        val thisZodiac = deriveZodiac(this.dob)
        val otherZodiac = deriveZodiac(otherProfile.dob)
        if (thisZodiac != "Unknown" && otherZodiac != "Unknown") {
            if (isZodiacCompatible(thisZodiac, otherZodiac)) {
                score += 10
            }
        }

        // 3. Lifestyle compatibility
        if (this.lifestyle != null && otherProfile.lifestyle != null) {
            score += this.lifestyle.compareCompatibility(otherProfile.lifestyle) * 20 // Lifestyle compatibility
        }

        // 4. Locality match
        if (this.hometown == otherProfile.hometown) {
            score += 20 // Same locality adds points
        }

        // 5. Education match
        score += calculateEducationCompatibility(otherProfile)

        // Normalize the score to a percentage
        return (score / 100.0) * 100.0
    }

    /** Education compatibility logic. */
    private fun calculateEducationCompatibility(otherProfile: Profile): Double {
        var educationScore = 0.0

        if (this.highSchool == otherProfile.highSchool || this.customHighSchool == otherProfile.customHighSchool) {
            educationScore += 10 // Same high school adds points
        }
        if (this.college == otherProfile.college || this.customCollege == otherProfile.customCollege) {
            educationScore += 10 // Same college adds points
        }
        if (this.postGraduation == otherProfile.postGraduation || this.customPostGraduation == otherProfile.customPostGraduation) {
            educationScore += 10 // Same post-graduation adds points
        }

        return educationScore
    }

    @get:Exclude
    val profileCompletionPercentage: Int
        get() {
            val fields = listOf(
                name, username, dob, bio, gender, profilepicUrl, religion, community, hometown, educationLevel, highSchool, college, postGraduation,
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

/** Zodiac compatibility logic. */
fun isZodiacCompatible(zodiac1: String, zodiac2: String): Boolean {
    val compatiblePairs = mapOf(
        "Aries" to listOf("Leo", "Sagittarius", "Gemini", "Aquarius"),
        "Taurus" to listOf("Virgo", "Capricorn", "Cancer", "Pisces"),
        "Gemini" to listOf("Libra", "Aquarius", "Aries", "Leo"),
        "Cancer" to listOf("Scorpio", "Pisces", "Taurus", "Virgo"),
        "Leo" to listOf("Aries", "Sagittarius", "Gemini", "Libra"),
        "Virgo" to listOf("Taurus", "Capricorn", "Cancer", "Scorpio"),
        "Libra" to listOf("Gemini", "Aquarius", "Leo", "Sagittarius"),
        "Scorpio" to listOf("Cancer", "Pisces", "Virgo", "Capricorn"),
        "Sagittarius" to listOf("Aries", "Leo", "Libra", "Aquarius"),
        "Capricorn" to listOf("Taurus", "Virgo", "Scorpio", "Pisces"),
        "Aquarius" to listOf("Gemini", "Libra", "Aries", "Sagittarius"),
        "Pisces" to listOf("Cancer", "Scorpio", "Taurus", "Capricorn")
    )
    return compatiblePairs[zodiac1]?.contains(zodiac2) == true
}

/** Compare lifestyle attributes for compatibility. */
fun Lifestyle.compareCompatibility(other: Lifestyle): Double {
    var compatibilityScore = 0.0
    val fields = listOf(
        this.smoking to other.smoking,
        this.drinking to other.drinking,
        this.exerciseFrequency to other.exerciseFrequency,
        this.familyOriented to other.familyOriented
    )
    fields.forEach { (field1, field2) ->
        if (field1 == field2) compatibilityScore += 1
    }
    return compatibilityScore / fields.size
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
    var smoking: Int = -1,
    var drinking: Int = -1,
    var cannabisFriendly: Boolean = false,
    var indoorsyToOutdoorsy: Int = -1,
    var sal: Int = -1,
    var IE: Int = -1,
    var socialMedia: Int = -1,
    var diet: String = "",
    var sleepCycle: Int = -1,
    var workLifeBalance: Int = -1,
    var exerciseFrequency: Int = -1,
    var adventurous: Int = -1,
    val petFriendly: Boolean = false,
    var familyOriented: Int = -1,
    val intellectual: Int = -1,
    var creativeArtistic: Int = -1,
    val fitnessLevel: Int = -1,
    val spiritualMindful: Int = -1,
    val humorousEasyGoing: Int = -1,
    var professionalAmbitious: Int = -1,
    var environmentallyConscious: Int = -1,
    val foodieCulinaryEnthusiast: Int = -1,
    val politicallyAware: Int = -1,
    val communityOriented: Int = -1,
    var sportsEnthusiast: Int = -1,
    var alcoholType: String = "",
) {
    fun isComplete(): Boolean {
        val fields = listOf(
            smoking, drinking, indoorsyToOutdoorsy, socialMedia, diet, sportsEnthusiast,
            sleepCycle, workLifeBalance, exerciseFrequency, adventurous, familyOriented,
            intellectual, creativeArtistic, fitnessLevel, spiritualMindful, sal, IE,
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
