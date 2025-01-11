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
    val pollGrouping: String = "",  // for accordion grouping

    // We no longer store "popularityScore" or "creationTime" in the quiz object
    // if we’re computing popularity dynamically from the user responses.
)

data class UserResponse(
    val quizId: String = "",
    val selectedOptions: List<String> = emptyList(),
    val timestamp: Long = 0L,
    val userId: String = "",
    val city: String = "",
    val locality: String? = null,

    // The Firebase key of this response record (for editing)
    val responseKey: String = ""   // add this
)
