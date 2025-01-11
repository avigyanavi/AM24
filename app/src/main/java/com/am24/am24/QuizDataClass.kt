// QuizDataClass.kt
package com.am24.am24

data class Quiz(
    val id: String = "",
    val category: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val type: String = "",
    val level: String = "",
    val locality: String? = null,
    val city: String? = null,

    // Additional fields for logic
    val popularityScore: Int = 0,
    val creationTime: Long = 0,

    // NEW field for sub-categorization:
    val pollGrouping: String = ""  // e.g. "Culture", "Food", "Heritage", etc.
)

data class UserResponse(
    val quizId: String = "",
    val selectedOptions: List<String> = emptyList(),
    val timestamp: Long = 0L,
    val userId: String = "",
    val city: String = "",
    val locality: String? = null
)
