package dev.devforge.core.domain.levels

import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress
import kotlin.math.ceil

/** Pattern Specification : des règles qu'on peut combiner. */
fun interface Specification<T> {
    fun isSatisfiedBy(candidate: T): Boolean
}

infix fun <T> Specification<T>.and(other: Specification<T>) =
    Specification<T> { isSatisfiedBy(it) && other.isSatisfiedBy(it) }

fun <T> all(specs: List<Specification<T>>) = Specification<T> { c -> specs.all { it.isSatisfiedBy(c) } }

/** Un prérequis du curriculum, par exemple « poo@2 ». */
data class Requirement(val trackId: String, val level: Int) {
    fun reached(): Specification<Progress> = Specification { it.rank(trackId) >= level }

    companion object {
        fun parse(text: String): Requirement {
            val (track, level) = text.split("@")
            return Requirement(track, level.toInt())
        }
    }
}

class LevelRules(private val content: Content) {
    /** Part d'exercices réussis nécessaire pour valider un niveau sans boss. */
    val threshold = 0.8

    fun requirements(trackId: String): List<Requirement> =
        content.trackRef(trackId)?.requires.orEmpty().map(Requirement::parse)

    /** Tous les prérequis du parcours, combinés en une seule règle. */
    fun unlockRule(trackId: String): Specification<Progress> = all(requirements(trackId).map { it.reached() })

    fun missingRequirements(trackId: String, progress: Progress): List<Requirement> =
        requirements(trackId).filterNot { it.reached().isSatisfiedBy(progress) }

    fun hasContent(trackId: String, level: Int): Boolean =
        content.exercisesOf(trackId, level).isNotEmpty() || content.lessonsOf(trackId, level).isNotEmpty()

    /** Nombre d'exercices réussis et total pour un niveau (ou tout le parcours). */
    fun completion(trackId: String, progress: Progress, level: Int? = null): Pair<Int, Int> {
        val exercises = content.exercisesOf(trackId, level)
        return exercises.count { progress.isSucceeded(it.exercise.id) } to exercises.size
    }

    /**
     * Recalcule la validation des niveaux sans boss : validé quand au moins 80 % de
     * ses exercices sont réussis. Les niveaux avec boss se valident en le battant.
     */
    fun refresh(progress: Progress): Progress {
        var validated = progress.validatedLevels
        for ((trackId, track) in content.tracks) {
            for (level in track.levels) {
                if (level.boss != null) continue
                val (done, total) = completion(trackId, progress, level.level)
                if (total > 0 && done >= ceil(total * threshold).toInt()) {
                    validated = validated + (trackId to (validated[trackId].orEmpty() + level.level))
                }
            }
        }
        return progress.copy(validatedLevels = validated)
    }
}
