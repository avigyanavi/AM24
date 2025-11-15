package com.am24.am24

import ComplimentData

/**
 * Shared data models for DM/compliment surfaces. Placed in a standalone file so both the
 * Map/DMScreen composables and the various ViewModels can reference the same types without
 * duplicating definitions inside individual UI files.
 */

data class ComplimentWithProfile(
    val profile: Profile,
    val compliment: ComplimentData
)

data class MatchSummary(
    val profile: Profile,
    val lastMessage: Message?,
    val hasUnread: Boolean
)

data class DmBootstrap(
    val compliments: List<ComplimentWithProfile> = emptyList(),
    val matches: List<MatchSummary> = emptyList(),
    val likedCount: Int = 0
)