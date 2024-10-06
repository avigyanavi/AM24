package com.am24.am24

import com.google.firebase.database.Exclude

data class Profile(
    val userId: String = "",
    val username: String = "",  // Unique username (e.g., MS1)
    val name: String = "",
    val dob: String = "",  // Date of birth for age calculation
    val bio: String = "",  // One-liner bio
    val interests: List<Interest> = emptyList(),  // Interests for matching purposes
    val hometown: String = "",
    val highSchool: String = "",  // User's high school
    val gender: String = "",
    val college: String = "",  // User's college
    val profilepicUrl: String? = null,
    val optionalPhotoUrls: List<String> = emptyList(),  // URLs of optional photos
    val matches: List<String> = emptyList(),  // List of matched user IDs

    // Metrics and Rankings
    val am24RankingGlobal: Int = 0,  // Global AM24 ranking (All India)
    val am24RankingAge: Int = 0,     // Ranking per age group (All India)
    val am24RankingHighSchool: Int = 0,  // Ranking per high school
    val am24RankingCollege: Int = 0,     // Ranking per college
    val am24RankingGender: Int = 0,      // Ranking per gender
    val am24RankingHometown: Int = 0,    // Ranking per hometown (city)
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
    val locationTags: List<String> = emptyList(), // Location tags aggregated from posts

    // AM24 Metrics
    val dateOfJoin: Long = System.currentTimeMillis(),  // Date when the user joined
    val am24RankingCompositeScore: Double = 0.0,        // Composite score for AM24 ranking (Kupid Score)
    val level: Int = 1  // Level determined by composite score
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
        return rating
    }

    // Placeholder for composite score calculation
    @Exclude
    fun calculateCompositeScore(): Double {
        // Implement your formula here
        // For example:
        val ratingScore = rating * 0.3
        val matchScore = matchCountPerSwipeRight * 0.2
        val upvoteScore = averageUpvoteCount * 0.2
        val swipeRatioScore = getCalculatedSwipeRightToLeftRatio() * 0.1
        val engagementScore = (cumulativeUpvotes - cumulativeDownvotes) * 0.2

        return ratingScore + matchScore + upvoteScore + swipeRatioScore + engagementScore
    }

    // Placeholder for level determination
    @Exclude
    fun determineLevel(): Int {
        val score = calculateCompositeScore()
        return when {
            score >= 90 -> 7
            score >= 75 -> 6
            score >= 60 -> 5
            score >= 45 -> 4
            score >= 30 -> 3
            score >= 15 -> 2
            else -> 1
        }
    }
}

data class Interest(
    var name: String = "",
    var emoji: String? = null // Make emoji nullable
) {
    // Firebase requires a no-argument constructor
    constructor() : this("", null)
}
