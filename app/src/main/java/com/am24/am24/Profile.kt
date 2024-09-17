package com.am24.am24

data class Profile(
    val uid: String = "",
    val username: String = "",
    val name: String = "",
    val gender: String = "",
    val dob: String = "",
    val state: String = "",
    val highSchool: String = "",
    val profilePictureUrl: String = "",
    val numberOfPosts: Int = 0,
    val connectionsDeemedByMe: Map<String, String> = emptyMap(),  // How I see others
    val connectionsDeemedByOthers: Map<String, String> = emptyMap(),  // How others see me
    val ratingScorePerPost: Double = 0.0,
    val avgRating: Double = 0.0,  // Avg rating across all posts
    val qualityMetric: Double = 0.0,  // Based on the type of connections and agreements/disagreements
    val dateOfJoin: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val locationTags: List<String> = emptyList()
)
