package com.am24.am24

data class AIOption(
    val aiEnum: AI,          // e.g., AI.RHEA or AI.REVAAN
    val displayName: String, // e.g., "Rhea"
    val imageResId: Int      // e.g., R.drawable.rhea_avatar
)

