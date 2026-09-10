package com.example.data.model

enum class PushupMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val description: String
) {
    BEGINNER(
        id = "BEGINNER",
        title = "Beginner (Easy)",
        subtitle = "Every pushup counts • Forgiving depth",
        badge = "EASY",
        description = "Relaxed depth threshold. Every completed trial counts toward your total."
    ),
    HARD(
        id = "HARD",
        title = "Hard (Strict)",
        subtitle = "Zero compromises • Deep chest & locked core",
        badge = "STRICT",
        description = "Requires full depth (<82°) and locked plank form. Deviations invalidate the rep."
    ),
    WALL(
        id = "WALL",
        title = "Wall Pushup",
        subtitle = "Standing incline • Low joint pressure",
        badge = "INCLINE",
        description = "Upright incline posture against a wall. Ideal for form conditioning and recovery."
    ),
    KNEE(
        id = "KNEE",
        title = "Knee Pushup",
        subtitle = "Floor pivot • Upper body progression",
        badge = "PIVOT",
        description = "Knees anchored to the ground, pivoting cleanly through your upper body."
    );

    val displayName: String
        get() = title

    companion object {
        fun fromId(id: String?): PushupMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BEGINNER
    }
}
