package com.am24.am24

data class CityChallenge(
    val id: String = "", // Unique identifier for the challenge
    val title: String = "", // Title of the challenge
    val description: String = "", // A short introduction to the challenge
    val city: String = "", // The city where the challenge is relevant
    val steps: List<Step> = emptyList(), // The sequence of steps for the challenge
    val endings: List<Ending> = emptyList() // Possible endings for the challenge
)

data class Step(
    val id: String = "", // Unique identifier for the step
    val text: String = "", // The content of this step
    val choices: List<Choice> = emptyList() // The choices available at this step
)

data class Choice(
    val id: String = "", // Unique identifier for the choice
    val text: String = "", // The choice text shown to the user
    val nextStepId: String? = null, // The ID of the next step, if applicable
    val endingId: String? = null, // The ID of the ending, if this choice leads to an ending
    val selectionCount: Int = 0 // Total number of times this choice was selected
)

data class Ending(
    val id: String = "", // Unique identifier for the ending
    val title: String = "", // Title or summary of the ending
    val description: String = "", // Detailed explanation or resolution for the ending
    val selectionCount: Int = 0 // Total number of times this ending was reached
)
