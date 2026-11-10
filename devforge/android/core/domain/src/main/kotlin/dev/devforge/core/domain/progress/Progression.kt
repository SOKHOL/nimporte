package dev.devforge.core.domain.progress

import dev.devforge.core.domain.evaluation.Verdict
import dev.devforge.core.model.Exercise
import dev.devforge.core.model.ExerciseRecord
import dev.devforge.core.model.Progress
import dev.devforge.core.model.ReviewCard
import dev.devforge.core.model.Streak
import kotlin.math.max
import kotlin.math.roundToInt

/** XP par défaut selon la difficulté, quand l'exercice ne fixe pas la sienne. */
val XP_BY_DIFFICULTY = mapOf(1 to 10, 2 to 20, 3 to 35, 4 to 50, 5 to 80)

fun baseXp(exercise: Exercise): Int = exercise.xp ?: XP_BY_DIFFICULTY[exercise.difficulty] ?: 10

data class XpContext(
    val exercise: Exercise,
    val verdict: Verdict,
    val hintsUsed: Int,
    val firstAttempt: Boolean,
)

/** Un modificateur d'XP. Ils s'appliquent les uns après les autres (Decorator). */
fun interface XpModifier {
    fun apply(xp: Double, context: XpContext): Double
}

/** Réussi : XP complète × score. Raté : la moitié, au prorata du score partiel. */
val ScoreModifier = XpModifier { xp, ctx ->
    xp * ctx.verdict.score * if (ctx.verdict.correct) 1.0 else 0.5
}

/** +50 % pour une réussite du premier coup, sans indice. */
val FirstTryBonus = XpModifier { xp, ctx ->
    if (ctx.firstAttempt && ctx.verdict.correct && ctx.hintsUsed == 0) xp * 1.5 else xp
}

/** -25 % par indice utilisé, sans descendre sous 25 %. */
val HintPenalty = XpModifier { xp, ctx ->
    xp * max(0.25, 1.0 - 0.25 * ctx.hintsUsed)
}

class XpCalculator(
    private val modifiers: List<XpModifier> = listOf(ScoreModifier, FirstTryBonus, HintPenalty),
) {
    fun compute(context: XpContext): Int =
        modifiers.fold(baseXp(context.exercise).toDouble()) { xp, modifier -> modifier.apply(xp, context) }
            .roundToInt()
}

/** Pattern Strategy : l'algorithme de répétition espacée est interchangeable (Leitner, SM-2, FSRS…). */
fun interface ReviewStrategy {
    fun next(card: ReviewCard?, correct: Boolean, today: Long): ReviewCard
}

/** Boîtes de Leitner : chaque réussite fait monter d'une boîte et espace la révision suivante. */
object Leitner : ReviewStrategy {
    val intervals = listOf(1L, 2L, 4L, 8L, 16L, 32L)

    override fun next(card: ReviewCard?, correct: Boolean, today: Long): ReviewCard {
        val box = if (correct) minOf((card?.box ?: 0) + 1, intervals.lastIndex) else 0
        return ReviewCard(box, today + intervals[box])
    }
}

/** Met à jour la série de jours d'activité. */
fun Streak.touch(today: Long): Streak = when (lastActiveDay) {
    today -> this
    today - 1 -> copy(current = current + 1, best = max(best, current + 1), lastActiveDay = today)
    else -> copy(current = 1, best = max(best, 1), lastActiveDay = today)
}

/** La série affichée : elle tombe à 0 si l'utilisateur a manqué un jour. */
fun Streak.displayed(today: Long): Int {
    val last = lastActiveDay ?: return 0
    return if (last >= today - 1) current else 0
}

data class AttemptOutcome(val progress: Progress, val xpGained: Int)

/** Enregistre une tentative : statistiques, XP, série, révision espacée. Fonction pure. */
class ProgressRecorder(
    private val xpCalculator: XpCalculator = XpCalculator(),
    private val reviewStrategy: ReviewStrategy = Leitner,
) {
    fun record(
        progress: Progress,
        exercise: Exercise,
        verdict: Verdict,
        hintsUsed: Int,
        today: Long,
    ): AttemptOutcome {
        val previous = progress.records[exercise.id]
        val xp = xpCalculator.compute(XpContext(exercise, verdict, hintsUsed, firstAttempt = previous == null))
        val record = (previous ?: ExerciseRecord()).let {
            it.copy(
                attempts = it.attempts + 1,
                successes = it.successes + if (verdict.correct) 1 else 0,
                lastCorrect = verdict.correct,
                bestScore = max(it.bestScore, verdict.score),
                lastDay = today,
            )
        }
        val updated = progress.copy(
            xp = progress.xp + xp,
            records = progress.records + (exercise.id to record),
            streak = progress.streak.touch(today),
            reviews = progress.reviews +
                (exercise.id to reviewStrategy.next(progress.reviews[exercise.id], verdict.correct, today)),
        )
        return AttemptOutcome(updated, xp)
    }
}
