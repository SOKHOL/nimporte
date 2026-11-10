package dev.devforge.core.model

import kotlinx.serialization.Serializable

/**
 * Toute la progression de l'utilisateur. Immuable : chaque action produit une
 * nouvelle instance. Les jours sont des « epoch days » (LocalDate.toEpochDay()).
 */
@Serializable
data class Progress(
    val xp: Int = 0,
    val records: Map<String, ExerciseRecord> = emptyMap(),
    val validatedLevels: Map<String, Set<Int>> = emptyMap(),
    val lessonsRead: Set<String> = emptySet(),
    val bosses: Map<String, BossRecord> = emptyMap(),
    val streak: Streak = Streak(),
    val reviews: Map<String, ReviewCard> = emptyMap(),
    val dailyDoneDay: Long? = null,
) {
    fun isSucceeded(exerciseId: String): Boolean = (records[exerciseId]?.successes ?: 0) > 0

    fun rank(trackId: String): Int = validatedLevels[trackId]?.maxOrNull() ?: 0
}

@Serializable
data class ExerciseRecord(
    val attempts: Int = 0,
    val successes: Int = 0,
    val lastCorrect: Boolean = false,
    val bestScore: Double = 0.0,
    val lastDay: Long = 0,
)

@Serializable
data class BossRecord(
    val attempts: Int = 0,
    val bestRatio: Double = 0.0,
    val passed: Boolean = false,
)

@Serializable
data class Streak(
    val current: Int = 0,
    val best: Int = 0,
    val lastActiveDay: Long? = null,
)

/** Carte de révision espacée : boîte de Leitner et prochain jour de révision. */
@Serializable
data class ReviewCard(
    val box: Int,
    val dueDay: Long,
)
