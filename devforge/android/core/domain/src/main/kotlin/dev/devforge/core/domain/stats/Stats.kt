package dev.devforge.core.domain.stats

import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress

data class DomainScore(val domainId: String, val title: String, val succeeded: Int, val total: Int) {
    val ratio: Double get() = if (total == 0) 0.0 else succeeded.toDouble() / total
}

data class GlobalStats(
    val exercisesSucceeded: Int,
    val exercisesTotal: Int,
    val attempts: Int,
    val successRate: Double,
    val lessonsRead: Int,
    val lessonsTotal: Int,
    val levelsValidated: Int,
)

class Stats(private val content: Content) {
    /** Progression par axe du radar, d'après les exercices réussis dans les parcours de cet axe. */
    fun byDomain(progress: Progress): List<DomainScore> = content.curriculum.domains.map { domain ->
        val trackIds = content.curriculum.tracks.filter { it.domain == domain.id }.map { it.id }.toSet()
        val exercises = content.exercises.filter { it.trackId in trackIds }
        DomainScore(
            domainId = domain.id,
            title = domain.title,
            succeeded = exercises.count { progress.isSucceeded(it.exercise.id) },
            total = exercises.size,
        )
    }

    fun global(progress: Progress): GlobalStats {
        val attempts = progress.records.values.sumOf { it.attempts }
        val successes = progress.records.values.sumOf { it.successes }
        return GlobalStats(
            exercisesSucceeded = content.exercises.count { progress.isSucceeded(it.exercise.id) },
            exercisesTotal = content.exercises.size,
            attempts = attempts,
            successRate = if (attempts == 0) 0.0 else successes.toDouble() / attempts,
            lessonsRead = progress.lessonsRead.count { id -> content.lessons.any { it.meta.id == id } },
            lessonsTotal = content.lessons.size,
            levelsValidated = progress.validatedLevels.values.sumOf { it.size },
        )
    }
}
