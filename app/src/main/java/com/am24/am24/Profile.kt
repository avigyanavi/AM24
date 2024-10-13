package com.am24.am24

import com.google.firebase.database.Exclude

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

    // Metrics and Rankings
    val am24RankingGlobal: Int = 0,  // Global AM24 ranking (All Kolkata)
    val am24RankingAge: Int = 0,     // Ranking per age group (All Kolkata)
    val am24RankingHighSchool: Int = 0,  // Ranking per high school
    val am24RankingCollege: Int = 0,     // Ranking per college
    val am24RankingGender: Int = 0,      // Ranking per gender
    val am24RankingHometown: Int = 0,    // Ranking per hometown (locality)
    val rating: Double = 0.0,            // Average rating given by others in DMs (from Review Bar)
    val numberOfRatings: Int = 0,        // Number of people who rated the user

    // Swipe and Match Metrics
    val numberOfSwipeRights: Int = 0,  // Number of swipe right actions performed by this user
    val numberOfSwipeLefts: Int = 0,   // Number of swipe left actions performed by this user
    val swipeRightToSwipeLeftRatio: Double = 0.0,  // Calculated as numberOfSwipeRights / numberOfSwipeLefts
    val matchCount: Int = 0,           // Total number of matches this user has
    val matchCountPerSwipeRight: Double = 0.0,  // Calculated as matchCount / numberOfSwipeRights

    // Post Metrics
    val cumulativeUpvotes: Int = 0,        // Cumulative upvotes received on all posts
    val cumulativeDownvotes: Int = 0,      // Cumulative downvotes received on all posts
    val averageUpvoteCount: Double = 0.0,  // Average number of upvotes per post
    val averageDownvoteCount: Double = 0.0, // Average number of downvotes per post

    // Tags
    val userTags: List<String> = emptyList(),    // User tags aggregated from posts

    // AM24 Metrics
    val dateOfJoin: Long = System.currentTimeMillis(),  // Date when the user joined
    val am24RankingCompositeScore: Double = 0.0,        // Composite score for AM24 ranking (Kupid Score)
    val level: Int = 1, // Level determined by composite score

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

    // Placeholder for composite score calculation
    @Exclude
    fun calculateCompositeScore(
        matchCountPerSwipeRight: Double,
        averageUpvoteCount: Double,
        swipeRightToSwipeLeftRatio: Double,
        cumulativeUpvotes: Int,
        cumulativeDownvotes: Int,
        gender: String
    ): Double {
        val ratingScore = getAverageRating() * 0.1

        // Gender-specific multipliers for match score
        val matchScoreMultiplier = when (gender.toLowerCase()) {
            "male" -> 1.5
            "female" -> 1.0
            "non-binary" -> 1.2
            else -> 1.0
        }
        val matchScore = matchCountPerSwipeRight * matchScoreMultiplier * 0.1

        val upvoteScore = averageUpvoteCount * 0.1

        // Gender-specific multipliers for swipe ratio score
        val swipeRatioMultiplier = when (gender.toLowerCase()) {
            "male" -> 1.5
            "female" -> 1.0
            "non-binary" -> 1.2
            else -> 1.0
        }
        val swipeRatioScore = swipeRightToSwipeLeftRatio * swipeRatioMultiplier * 0.1

        val engagementScore = (cumulativeUpvotes - cumulativeDownvotes) * 0.05

        return ratingScore + matchScore + upvoteScore + swipeRatioScore + engagementScore
    }



    // Placeholder for level determination
    @Exclude
    fun determineLevel(): Int {
        val score = calculateCompositeScore(
            matchCountPerSwipeRight = this.getCalculatedMatchCountPerSwipeRight(),
            averageUpvoteCount = this.averageUpvoteCount,
            swipeRightToSwipeLeftRatio = this.getCalculatedSwipeRightToLeftRatio(),
            cumulativeUpvotes = this.cumulativeUpvotes,
            cumulativeDownvotes = this.cumulativeDownvotes,
            gender = this.gender
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

