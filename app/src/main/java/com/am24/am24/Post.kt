package com.am24.am24

data class Post(
    val postType: String = PostType.TEXT.name, // Default to TEXT for blog
    val tags: List<String> = emptyList(),
    val ratings: List<Double> = emptyList(),  // List of ratings (1-10)
    val avgRating: Double = 0.0,  // Average rating for this post
    val subscriberOnly: Boolean = false,
    val mediaUrl: String? = "",  // For media (photo/video) or document URL for blogs
    val locationTag: List<String> = emptyList(),
    val timeOfPost: Long = System.currentTimeMillis(),
    val username: String? = "",
    val name: String? = "",
    val userId: String? = "",
    val caption: String? = "",  // Used for blog content
    val title: String? = "",  // Title for blog posts
    val postId: String? = "" //new
)

enum class PostType {
    PHOTO, VIDEO, TEXT
}
