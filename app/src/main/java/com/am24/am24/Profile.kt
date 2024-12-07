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
    val lastActive: Long = System.currentTimeMillis(),
    val badges: List<String> = emptyList(),
    val profilepicUrl: String? = null,
    val voiceNoteUrl: String? = null,
    val optionalPhotoUrls: List<String> = emptyList(),  // URLs of optional photos
    val matches: List<String> = emptyList(),  // List of matched user IDs
    val religion: String = "",
    val community: String = "",
    val politics: String = "",  // Political preferences (e.g., liberal, conservative)
    val fitnessLevel: String = "",  // Fitness level (e.g., active, moderate)
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
    val claimedIncomeLevel: String? = null, // Optional income level per annum
    val lifestyle: Lifestyle? = null, // Lifestyle Section
    val jobRole: String = "",
    val customJobRole: String? = null,
    val work: String = "",
    val customWork: String? = null,
    val socialCauses: List<String> = emptyList(),
    val politicalViews: String = "",  // Conservative, Liberal, Centrist, etc.
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
    val locality: String = "", // Current locality (NEW FIELD)
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    var averageRating: Double = 0.0,
    val privateAccount: Boolean = false, // Whether the account is private
    var relationship: String? = null, // Add this to hold "friend", "match", etc.
    val datingLocalities: List<String> = emptyList(),
    val feedLocalities: List<String> = emptyList(),
    val datingRating: String = "",
    val feedRating: String = "",
    val datingGender: String = "",
    val feedGender: String = "",
    val datingHighSchool: String = "",
    val feedHighSchool: String = "",
    val datingCollege: String = "",
    val feedCollege: String = "",
    val datingAgeStart: Int = 18,
    val datingAgeEnd: Int = 30,
    val feedAgeStart: Int = 18,
    val feedAgeEnd: Int = 30,
    val datingDistancePreference: Int = 10,
    val feedDistancePreference: Int = 10,

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
                claimedIncomeLevel, jobRole, work, lookingFor
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
    val read: Boolean = false
)

data class Lifestyle(
    // Habits & Preferences
    val smoking: Int = 0, // Min: 0 (Non-Smoker), Max: 10 (Heavy Smoker)
    // Example Mappings: 0–2: "Non-Smoker", 3–6: "Social Smoker", 7–10: "Regular Smoker"

    val drinking: Int = 0, // Min: 0 (Non-Drinker), Max: 10 (Heavy Drinker)
    // Example Mappings: 0–2: "Non-Drinker", 3–6: "Occasional Drinker", 7–10: "Heavy Drinker"

    val alcoholType: String = "", // Preferred alcohol type (Beer, Wine, Vodka, etc.)

    val cannabisFriendly: Boolean = false, // True if cannabis-friendly

    // Personality & Social Style
    val indoorsyToOutdoorsy: Int = 5, // Min: 0 (Homebody), Max: 10 (Outdoorsy)
    // Example Mappings: 0–2: "Homebody", 3–6: "Balanced", 7–10: "Outdoorsy"

    val socialButterfly: Int = 5, // Min: 0 (Introverted), Max: 10 (Extroverted)
    // Example Mappings: 0–2: "Introverted", 3–6: "Ambivert", 7–10: "Extroverted"

    val diet: String = "", // Dietary preference (Vegan, Vegetarian, etc.)

    val sleepCycle: Int = 5, // Min: 0 (Early Riser), Max: 10 (Night Owl)
    // Example Mappings: 0–2: "Early Riser", 3–6: "Balanced", 7–10: "Night Owl"

    val workLifeBalance: Int = 5, // Min: 0 (Workaholic), Max: 10 (Relaxed)
    // Example Mappings: 0–2: "Workaholic", 3–6: "Balanced", 7–10: "Relaxed"

    val exerciseFrequency: Int = 0, // Min: 0 (Never Exercises), Max: 10 (Daily)
    // Example Mappings: 0–2: "Never Exercises", 3–6: "Occasionally Exercises", 7–10: "Exercises Daily"

    // Adventure & Activity
    val adventurous: Int = 5, // Min: 0 (Cautious), Max: 10 (Adventurous)
    // Example Mappings: 0–2: "Cautious", 3–6: "Moderate", 7–10: "Adventurous"

    val petFriendly: Boolean = false, // True if pet-friendly

    // Family & Community Orientation
    val familyOriented: Int = 5, // Min: 0 (Independent), Max: 10 (Family-Oriented)
    // Example Mappings: 0–2: "Independent", 3–6: "Balanced", 7–10: "Family-Oriented"

    // Intellectual, Creativity & Health
    val intellectual: Int = 5, // Min: 0 (Casual), Max: 10 (Intellectual)
    // Example Mappings: 0–2: "Casual", 3–6: "Inquisitive", 7–10: "Intellectual"

    val creativeArtistic: Int = 5, // Min: 0 (Practical), Max: 10 (Artistic)
    // Example Mappings: 0–2: "Practical", 3–6: "Occasionally Creative", 7–10: "Artistic"

    val healthFitnessEnthusiast: Int = 5, // Min: 0 (Occasional), Max: 10 (Dedicated)
    // Example Mappings: 0–2: "Occasional", 3–6: "Moderate", 7–10: "Dedicated"

    // Spirituality, Humor & Ambition
    val spiritualMindful: Int = 5, // Min: 0 (Non-Spiritual), Max: 10 (Mindful)
    // Example Mappings: 0–2: "Non-Spiritual", 3–6: "Occasionally Mindful", 7–10: "Deeply Mindful"

    val humorousEasyGoing: Int = 5, // Min: 0 (Serious), Max: 10 (Humorous)
    // Example Mappings: 0–2: "Serious", 3–6: "Balanced", 7–10: "Humorous"

    val professionalAmbitious: Int = 5, // Min: 0 (Relaxed), Max: 10 (Ambitious)
    // Example Mappings: 0–2: "Relaxed", 3–6: "Balanced", 7–10: "Ambitious"

    // Environmental & Cultural Orientation
    val environmentallyConscious: Int = 5, // Min: 0 (Casual), Max: 10 (Eco-Conscious)
    // Example Mappings: 0–2: "Not Conscious", 3–6: "Occasionally Conscious", 7–10: "Eco-Conscious"

    val culturalHeritageOriented: Int = 5, // Min: 0 (Open-Minded), Max: 10 (Culturally Rooted)
    // Example Mappings: 0–2: "Open-Minded", 3–6: "Balanced", 7–10: "Culturally Rooted"

    // Interests & Passions
    val foodieCulinaryEnthusiast: Int = 5, // Min: 0 (Basic), Max: 10 (Food Enthusiast)
    // Example Mappings: 0–2: "Basic", 3–6: "Moderate", 7–10: "Food Enthusiast"

    val singleParent: Boolean = false, // True if single parent

    // New Attributes for City-Specific Lifestyles
    val culturalConnoisseur: Int = 5, // Min: 0 (Not Interested), Max: 10 (Cultural Enthusiast)
    // Example Mappings: 0–2: "Not Interested", 3–6: "Occasionally Interested", 7–10: "Cultural Enthusiast"

    val streetFoodExplorer: Int = 5, // Min: 0 (Basic), Max: 10 (Food Explorer)
    // Example Mappings: 0–2: "Basic", 3–6: "Interested", 7–10: "Food Explorer"

    val traditionallyRooted: Int = 5, // Min: 0 (Modern), Max: 10 (Traditionally Rooted)
    // Example Mappings: 0–2: "Modern", 3–6: "Balanced", 7–10: "Traditionally Rooted"

    val entrepreneurialSpirit: Int = 5, // Min: 0 (Casual), Max: 10 (Entrepreneurial)
    // Example Mappings: 0–2: "Casual", 3–6: "Moderate", 7–10: "Entrepreneurial"

    val academicallyAspirational: Int = 5, // Min: 0 (Casual), Max: 10 (Aspirational)
    // Example Mappings: 0–2: "Casual", 3–6: "Focused", 7–10: "Aspirational"

    val natureLover: Int = 5, // Min: 0 (City Lover), Max: 10 (Nature Lover)
    // Example Mappings: 0–2: "City Lover", 3–6: "Balanced", 7–10: "Nature Lover"

    val beachLover: Int = 5, // Min: 0 (Mountain Lover), Max: 10 (Beach Enthusiast)
    // Example Mappings: 0–2: "Mountain Lover", 3–6: "Neutral", 7–10: "Beach Enthusiast"

    val festivalEnthusiast: Int = 5, // Min: 0 (Not Festive), Max: 10 (Festival Enthusiast)
    // Example Mappings: 0–2: "Not Festive", 3–6: "Occasionally Festive", 7–10: "Festival Enthusiast"

    val urbanWanderer: Int = 5, // Min: 0 (Homebody), Max: 10 (City Explorer)
    // Example Mappings: 0–2: "Homebody", 3–6: "Balanced", 7–10: "City Explorer"

    val politicallyAware: Int = 5, // Min: 0 (Not Interested), Max: 10 (Engaged)
    // Example Mappings: 0–2: "Not Interested", 3–6: "Aware", 7–10: "Engaged"

    val nightlifeLover: Int = 5, // Min: 0 (Homebody), Max: 10 (Nightlife Enthusiast)
    // Example Mappings: 0–2: "Homebody", 3–6: "Occasional", 7–10: "Nightlife Enthusiast"

    val pilgrimageFocused: Int = 5, // Min: 0 (Non-Spiritual), Max: 10 (Pilgrimage Lover)
    // Example Mappings: 0–2: "Non-Spiritual", 3–6: "Balanced", 7–10: "Pilgrimage Lover"

    val languageHeritageLover: Int = 5, // Min: 0 (Casual), Max: 10 (Language Enthusiast)
    // Example Mappings: 0–2: "Casual", 3–6: "Moderate", 7–10: "Language Enthusiast"

    val communityOriented: Int = 5 // Min: 0 (Individualist), Max: 10 (Community-Oriented)
    // Example Mappings: 0–2: "Individualist", 3–6: "Balanced", 7–10: "Community-Oriented"
) {
    fun isComplete(): Boolean {
        val fields = listOf(
            smoking, drinking, indoorsyToOutdoorsy, socialButterfly, diet,
            sleepCycle, workLifeBalance, exerciseFrequency, adventurous, familyOriented,
            intellectual, creativeArtistic, healthFitnessEnthusiast, spiritualMindful,
            humorousEasyGoing, professionalAmbitious, environmentallyConscious,
            culturalHeritageOriented, foodieCulinaryEnthusiast, culturalConnoisseur,
            streetFoodExplorer, traditionallyRooted, entrepreneurialSpirit, academicallyAspirational,
            natureLover, beachLover, festivalEnthusiast, urbanWanderer, politicallyAware,
            nightlifeLover, pilgrimageFocused, languageHeritageLover, communityOriented
        )
        return fields.any { it != 0 }
    }
}

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
