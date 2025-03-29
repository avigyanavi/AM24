package com.am24.am24

import AI

// Original AIOption (remains unchanged)
data class AIOption(
    val aiEnum: AI,
    val displayName: String,
    val imageResId: Int,
    val relationshipStage: String,
    val age: Int,
    val occupation: String,
    val location: String
)

// StoryNode represents a narrative event or situation, including optional media (images/videos)
data class StoryNode(
    val nodeId: String,
    val narrativeText: String,
    val choices: List<ChoiceNode>,
    val mediaUrl: String? = null // Local or external media (DALL·E images, Sora videos)
)

// ChoiceNode encapsulates user choices leading to the next narrative node and metric impacts
data class ChoiceNode(
    val choiceText: String,
    val nextNodeId: String,
    val metricImpacts: MetricImpact
)

// Explicit definition of how user metrics are impacted by choices
data class MetricImpact(
    val careerProgress: Int = 0,
    val socialInfluence: Int = 0,
    val localPopularity: Int = 0,
    val kolkataReputation: Int = 0,
    val achievementUnlocked: KolkataAchievement? = null
)

data class Character(
    val id: String,  // Unique identifier
    val name: String,
    val age: Int,
    val occupation: String,
    val location: String,
    val careerProgress: List<String>,
    val socialInfluence: Int,
    val localPopularity: Int,
    val kolkataReputation: Int,
    val wealth: Int,
    val power: Int,
    val politicalTies: List<String>,
    val criminalConnections: List<String>,
    val enemies: List<String>,
    val allies: List<String>,
    val uniqueAbilities: String,
    var isPlayable: Boolean  // Dynamic field, can switch based on user selection
)

// Achievements specific to Kolkata, adding gamification elements
enum class KolkataAchievement(val title: String) {
    PARK_STREET_CELEB("Park Street Celeb"),
    PUJA_FAVORITE("Puja Favorite"),
    RABINDRA_SADAN_STAR("Rabindra Sadan Star"),
    MAIDAN_INFLUENCER("Maidan Influencer"),
    BALLYGUNGE_SOCIALITE("Ballygunge Socialite"),
    HOWRAH_HERO("Howrah Hero")
}

// Example of scripted story nodes tailored for Kolkata
object KolkataNarratives {
    val nodes = mapOf(
        "start" to StoryNode(
            nodeId = "start",
            narrativeText = "You find yourself strolling down Park Street during Durga Puja, feeling the vibrant energy of the city...",
            mediaUrl = "local_assets/videos/parkstreet_intro.mp4",
            choices = listOf(
                ChoiceNode(
                    choiceText = "Join the festive crowd",
                    nextNodeId = "pujaCelebration",
                    metricImpacts = MetricImpact(localPopularity = 10, kolkataReputation = 5)
                ),
                ChoiceNode(
                    choiceText = "Head to a quieter cafe",
                    nextNodeId = "quietCafe",
                    metricImpacts = MetricImpact(socialInfluence = 5)
                )
            )
        ),
        "pujaCelebration" to StoryNode(
            nodeId = "pujaCelebration",
            narrativeText = "You immerse yourself in the vibrant Durga Puja celebrations, capturing memorable moments.",
            mediaUrl = "local_assets/videos/durga_puja.mp4",
            choices = listOf(
                ChoiceNode(
                    choiceText = "Post photos on social media",
                    nextNodeId = "socialBuzz",
                    metricImpacts = MetricImpact(socialInfluence = 20, achievementUnlocked = KolkataAchievement.PUJA_FAVORITE)
                ),
                ChoiceNode(
                    choiceText = "Enjoy privately",
                    nextNodeId = "privateJoy",
                    metricImpacts = MetricImpact(careerProgress = 5)
                )
            )
        )
        // Additional nodes follow...
    )
}
