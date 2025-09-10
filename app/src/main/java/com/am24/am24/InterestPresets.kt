package com.am24.am24

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun interestPresets(): Map<String, List<Interest>> = mapOf(
    // ─────────── Arts & Entertainment ───────────
    "Arts & Entertainment" to listOf(
        Interest(stringResource(R.string.interest_art),            "🎨"),
        Interest(stringResource(R.string.interest_blogging),       "📝"),
        Interest(stringResource(R.string.interest_books),          "📚"),
        Interest(stringResource(R.string.interest_dancing),        "💃"),
        Interest(stringResource(R.string.interest_fashion),        "👗"),
        Interest(stringResource(R.string.interest_movies),         "🎥"),
        Interest(stringResource(R.string.interest_music),          "🎵"),
        Interest(stringResource(R.string.interest_photography),    "📷"),
        Interest(stringResource(R.string.interest_podcasting),     "🎙️"),
        Interest(stringResource(R.string.interest_watching_tv),    "📺"),
        Interest(stringResource(R.string.interest_writing),        "✍️")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Food & Drink ───────────
    "Food & Drink" to listOf(
        Interest(stringResource(R.string.interest_baking),         "🍰"),
        Interest(stringResource(R.string.interest_coffee),         "☕"),
        Interest(stringResource(R.string.interest_cooking),        "🍳"),
        Interest(stringResource(R.string.interest_craft_beer),     "🍺"),
        Interest(stringResource(R.string.interest_food),           "🍔"),
        Interest(stringResource(R.string.interest_wine_tasting),   "🍷")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Games & Puzzles ───────────
    "Games & Puzzles" to listOf(
        Interest(stringResource(R.string.interest_board_games),    "🎲"),
        Interest(stringResource(R.string.interest_gaming),         "🎮"),
        Interest(stringResource(R.string.interest_puzzles),        "🧩"),
        Interest(stringResource(R.string.interest_video_games),    "🎮")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Home & DIY ───────────
    "Home & DIY" to listOf(
        Interest(stringResource(R.string.interest_diy),            "🛠️"),
        Interest(stringResource(R.string.interest_gardening),      "🌱"),
        Interest(stringResource(R.string.interest_home_improvement),"🏠"),
        Interest(stringResource(R.string.interest_interior_design),"🛋️"),
        Interest(stringResource(R.string.interest_thrift_shopping),"🛍️"),
        Interest(stringResource(R.string.interest_vintage_clothing),"🧥")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Mind & Knowledge ───────────
    "Mind & Knowledge" to listOf(
        Interest(stringResource(R.string.interest_economics),      "💰"),
        Interest(stringResource(R.string.interest_history),        "📜"),
        Interest(stringResource(R.string.interest_philosophy),     "🧠"),
        Interest(stringResource(R.string.interest_politics),       "🗳️"),
        Interest(stringResource(R.string.interest_science),        "🔬")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Nature & Animals ───────────
    "Nature & Animals" to listOf(
        Interest(stringResource(R.string.interest_gardening),      "🌱"),
        Interest(stringResource(R.string.interest_nature),         "🌳"),
        Interest(stringResource(R.string.interest_pets),           "🐾")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Sports & Outdoors ───────────
    "Sports & Outdoors" to listOf(
        Interest(stringResource(R.string.interest_beach_days),     "🏖️"),
        Interest(stringResource(R.string.interest_camping),        "⛺"),
        Interest(stringResource(R.string.interest_car_racing),     "🏎️"),
        Interest(stringResource(R.string.interest_extreme_sports), "🏂"),
        Interest(stringResource(R.string.interest_fishing),        "🎣"),
        Interest(stringResource(R.string.interest_hiking),         "🥾"),
        Interest(stringResource(R.string.interest_hunting),        "🏹"),
        Interest(stringResource(R.string.interest_mountain_biking),"🚵"),
        Interest(stringResource(R.string.interest_motorcycling),   "🏍️"),
        Interest(stringResource(R.string.interest_rock_climbing),  "🧗"),
        Interest(stringResource(R.string.interest_scuba_diving),   "🤿"),
        Interest(stringResource(R.string.interest_skiing),         "⛷️"),
        Interest(stringResource(R.string.interest_skydiving),      "🪂"),
        Interest(stringResource(R.string.interest_snowboarding),   "🏂"),
        Interest(stringResource(R.string.interest_sports),         "⚽"),
        Interest(stringResource(R.string.interest_surfing),        "🏄"),
        Interest(stringResource(R.string.interest_travel),         "✈️"),
        Interest(stringResource(R.string.interest_traveling),      "🧳")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Tech & Innovation ───────────
    "Tech & Innovation" to listOf(
        Interest(stringResource(R.string.interest_coding),         "⌨️"),
        Interest(stringResource(R.string.interest_robotics),       "🤖"),
        Interest(stringResource(R.string.interest_space),          "🚀"),
        Interest(stringResource(R.string.interest_technology),     "💻")
    ).sortedBy { it.name.lowercase() },

    // ─────────── Wellness & Lifestyle ───────────
    "Wellness & Lifestyle" to listOf(
        Interest(stringResource(R.string.interest_astrology),      "♈"),
        Interest(stringResource(R.string.interest_charity),        "❤️"),
        Interest(stringResource(R.string.interest_crystals),       "💎"),
        Interest(stringResource(R.string.interest_environmentalism),"🌍"),
        Interest(stringResource(R.string.interest_fitness),        "💪"),
        Interest(stringResource(R.string.interest_meditation),     "🧘‍♂️"),
        Interest(stringResource(R.string.interest_napping),        "😴"),
        Interest(stringResource(R.string.interest_networking),     "🤝"),
        Interest(stringResource(R.string.interest_picnics),        "🧺"),
        Interest(stringResource(R.string.interest_public_speaking),"🎤"),
        Interest(stringResource(R.string.interest_romance),        "💋"),
        Interest(stringResource(R.string.interest_social_media),   "📱"),
        Interest(stringResource(R.string.interest_online_communities),"💬"),
        Interest(stringResource(R.string.interest_spa_days),       "💆"),
        Interest(stringResource(R.string.interest_volunteering),   "🤝"),
        Interest(stringResource(R.string.interest_yoga),           "🧘")
    ).sortedBy { it.name.lowercase() }
)

@Composable
fun allInterestOptions(): List<Interest> =
    interestPresets().values.flatten().distinctBy { it.name }.sortedBy { it.name.lowercase() }