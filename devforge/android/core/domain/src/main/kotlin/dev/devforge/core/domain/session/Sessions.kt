package dev.devforge.core.domain.session

import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.evaluation.EvaluateAnswer
import dev.devforge.core.domain.evaluation.Verdict
import dev.devforge.core.domain.levels.LevelRules
import dev.devforge.core.domain.progress.ProgressRecorder
import dev.devforge.core.model.BossRecord
import dev.devforge.core.model.Content
import dev.devforge.core.model.Exercise
import dev.devforge.core.model.PlacedExercise
import dev.devforge.core.model.Progress
import kotlin.random.Random

enum class SessionKind { LEVEL, LESSON, REVIEW, BOSS, DAILY }

/** Ce qu'une session va proposer. */
data class SessionPlan(
    val kind: SessionKind,
    val title: String,
    val exercises: List<PlacedExercise>,
    val trackId: String? = null,
    val level: Int? = null,
    val bossId: String? = null,
    val passRatio: Double? = null,
    val timeLimitSeconds: Int? = null,
)

/** Pattern Builder : construit le bon plan de session selon le contexte. */
class SessionBuilder(
    private val content: Content,
    private val random: Random = Random.Default,
) {
    private fun playable(list: List<PlacedExercise>) = list.filter { it.exercise !is Exercise.Code }

    /** Les exercices jamais réussis d'abord, puis les autres, mélangés. */
    private fun prioritize(list: List<PlacedExercise>, progress: Progress, limit: Int): List<PlacedExercise> {
        val (done, todo) = playable(list).partition { progress.isSucceeded(it.exercise.id) }
        return (todo.shuffled(random) + done.shuffled(random)).take(limit)
    }

    fun forLevel(trackId: String, level: Int, progress: Progress, limit: Int = 10): SessionPlan {
        val title = content.tracks[trackId]?.levels?.firstOrNull { it.level == level }?.title ?: "Entraînement"
        return SessionPlan(
            kind = SessionKind.LEVEL,
            title = title,
            exercises = prioritize(content.exercisesOf(trackId, level), progress, limit),
            trackId = trackId,
            level = level,
        )
    }

    /** Les exercices du même niveau qui travaillent les compétences de la leçon. */
    fun forLesson(lessonId: String, progress: Progress, limit: Int = 8): SessionPlan {
        val lesson = content.lesson(lessonId) ?: return SessionPlan(SessionKind.LESSON, "Leçon", emptyList())
        val related = content.exercisesOf(lesson.trackId, lesson.level)
            .filter { placed -> placed.exercise.skills.any { it in lesson.meta.skills } }
        return SessionPlan(
            kind = SessionKind.LESSON,
            title = lesson.meta.title,
            exercises = prioritize(related, progress, limit),
            trackId = lesson.trackId,
            level = lesson.level,
        )
    }

    fun dueReviews(progress: Progress, today: Long): List<PlacedExercise> =
        playable(
            progress.reviews.filter { it.value.dueDay <= today }
                .entries.sortedBy { it.value.dueDay }
                .mapNotNull { content.exercise(it.key) },
        )

    fun forReview(progress: Progress, today: Long, limit: Int = 10): SessionPlan =
        SessionPlan(SessionKind.REVIEW, "Révisions du jour", dueReviews(progress, today).take(limit))

    fun forBoss(trackId: String, level: Int): SessionPlan {
        val boss = content.tracks[trackId]?.levels?.firstOrNull { it.level == level }?.boss
            ?: return SessionPlan(SessionKind.BOSS, "Boss", emptyList())
        return SessionPlan(
            kind = SessionKind.BOSS,
            title = boss.title,
            exercises = playable(boss.exercises.mapNotNull { content.exercise(it) }).shuffled(random),
            trackId = trackId,
            level = level,
            bossId = boss.id,
            passRatio = boss.passRatio,
            timeLimitSeconds = boss.timeLimitMin?.let { it * 60 },
        )
    }

    /** Un exercice par jour, le même toute la journée, choisi parmi ceux pas encore réussis. */
    fun daily(progress: Progress, today: Long): SessionPlan {
        val all = playable(content.exercises)
        val pool = all.filterNot { progress.isSucceeded(it.exercise.id) }.ifEmpty { all }
        val pick = if (pool.isEmpty()) emptyList() else listOf(pool[Random(today).nextInt(pool.size)])
        return SessionPlan(SessionKind.DAILY, "Défi du jour", pick)
    }
}

data class ExerciseResult(val exerciseId: String, val verdict: Verdict, val xp: Int)

data class SessionOutcome(
    val correct: Int,
    val total: Int,
    val xp: Int,
    /** null si ce n'est pas un boss. */
    val bossPassed: Boolean?,
    val timeUp: Boolean = false,
)

/** Pattern State : une session est toujours dans un de ces trois états. */
sealed interface SessionState {
    val plan: SessionPlan
    val results: List<ExerciseResult>

    data class Answering(
        override val plan: SessionPlan,
        val index: Int,
        override val results: List<ExerciseResult>,
    ) : SessionState {
        val current: PlacedExercise get() = plan.exercises[index]
    }

    data class Feedback(
        override val plan: SessionPlan,
        val index: Int,
        override val results: List<ExerciseResult>,
        val answer: Answer,
    ) : SessionState {
        val current: PlacedExercise get() = plan.exercises[index]
        val last: ExerciseResult get() = results.last()
        val isLast: Boolean get() = index == plan.exercises.lastIndex
    }

    data class Finished(
        override val plan: SessionPlan,
        override val results: List<ExerciseResult>,
        val outcome: SessionOutcome,
    ) : SessionState
}

/** Un changement d'état, avec la progression qui en résulte. */
data class Transition(val state: SessionState, val progress: Progress)

/** Les règles d'enchaînement d'une session. Fonctions pures : état + progression → nouvel état. */
class SessionMachine(
    private val content: Content,
    private val evaluate: EvaluateAnswer = EvaluateAnswer(),
    private val recorder: ProgressRecorder = ProgressRecorder(),
) {
    private val levels = LevelRules(content)

    fun start(plan: SessionPlan, progress: Progress): Transition =
        if (plan.exercises.isEmpty()) finish(plan, emptyList(), progress, timeUp = false)
        else Transition(SessionState.Answering(plan, 0, emptyList()), progress)

    fun submit(
        state: SessionState.Answering,
        answer: Answer,
        hintsUsed: Int,
        progress: Progress,
        today: Long,
    ): Transition {
        val exercise = state.current.exercise
        val verdict = evaluate(exercise, answer)
        val outcome = recorder.record(progress, exercise, verdict, hintsUsed, today)
        val result = ExerciseResult(exercise.id, verdict, outcome.xpGained)
        val updated = levels.refresh(outcome.progress)
        return Transition(SessionState.Feedback(state.plan, state.index, state.results + result, answer), updated)
    }

    fun next(state: SessionState.Feedback, progress: Progress, today: Long): Transition =
        if (state.isLast) finish(state.plan, state.results, progress, timeUp = false, today = today)
        else Transition(SessionState.Answering(state.plan, state.index + 1, state.results), progress)

    /** Le chronomètre du boss est écoulé : les exercices restants comptent comme ratés. */
    fun timeUp(state: SessionState, progress: Progress, today: Long): Transition =
        if (state is SessionState.Finished) Transition(state, progress)
        else finish(state.plan, state.results, progress, timeUp = true, today = today)

    private fun finish(
        plan: SessionPlan,
        results: List<ExerciseResult>,
        progress: Progress,
        timeUp: Boolean,
        today: Long? = null,
    ): Transition {
        val correct = results.count { it.verdict.correct }
        val total = plan.exercises.size
        var updated = progress
        var bossPassed: Boolean? = null

        if (plan.kind == SessionKind.BOSS && plan.bossId != null && plan.trackId != null && plan.level != null) {
            val ratio = if (total == 0) 0.0 else correct.toDouble() / total
            val passed = !timeUp && ratio >= (plan.passRatio ?: 0.8)
            val previous = updated.bosses[plan.bossId] ?: BossRecord()
            updated = updated.copy(
                bosses = updated.bosses + (
                    plan.bossId to previous.copy(
                        attempts = previous.attempts + 1,
                        bestRatio = maxOf(previous.bestRatio, ratio),
                        passed = previous.passed || passed,
                    )
                ),
            )
            if (passed) {
                val levelsOfTrack = updated.validatedLevels[plan.trackId].orEmpty() + plan.level
                updated = updated.copy(validatedLevels = updated.validatedLevels + (plan.trackId to levelsOfTrack))
            }
            bossPassed = passed
        }
        if (plan.kind == SessionKind.DAILY && today != null && correct > 0) {
            updated = updated.copy(dailyDoneDay = today)
        }
        val outcome = SessionOutcome(correct, total, results.sumOf { it.xp }, bossPassed, timeUp)
        return Transition(SessionState.Finished(plan, results, outcome), updated)
    }
}
