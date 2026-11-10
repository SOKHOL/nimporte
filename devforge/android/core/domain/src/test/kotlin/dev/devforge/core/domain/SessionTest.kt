package dev.devforge.core.domain

import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.session.SessionBuilder
import dev.devforge.core.domain.session.SessionKind
import dev.devforge.core.domain.session.SessionMachine
import dev.devforge.core.domain.session.SessionState
import dev.devforge.core.domain.session.Transition
import dev.devforge.core.model.Progress
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionTest {
    private val content = Fixtures.content()
    private val builder = SessionBuilder(content, Random(42))
    private val machine = SessionMachine(content)
    private val good = Answer.Choices(setOf("a"))
    private val bad = Answer.Choices(setOf("b"))

    /** Répond à chaque exercice avec les réponses données, puis termine. */
    private fun play(start: Transition, answers: List<Answer>, today: Long = 1): Transition {
        var t = start
        for (answer in answers) {
            val answering = assertIs<SessionState.Answering>(t.state)
            t = machine.submit(answering, answer, 0, t.progress, today)
            t = machine.next(assertIs<SessionState.Feedback>(t.state), t.progress, today)
        }
        return t
    }

    @Test
    fun `session de niveau - les exercices jamais réussis d'abord`() {
        val progress = Progress(records = mapOf("t.n1.a" to dev.devforge.core.model.ExerciseRecord(1, 1, true, 1.0, 0)))
        val plan = builder.forLevel("t", 1, progress)
        assertEquals(3, plan.exercises.size)
        assertEquals("t.n1.a", plan.exercises.last().exercise.id)
    }

    @Test
    fun `boss réussi - le niveau est validé`() {
        val start = machine.start(builder.forBoss("t", 1), Progress())
        assertEquals(300, (start.state.plan.timeLimitSeconds))
        val end = play(start, listOf(good, good, good))
        val finished = assertIs<SessionState.Finished>(end.state)
        assertEquals(true, finished.outcome.bossPassed)
        assertEquals(3, finished.outcome.correct)
        assertTrue(1 in end.progress.validatedLevels.getValue("t"))
        assertEquals(1, end.progress.rank("t"))
        assertTrue(end.progress.bosses.getValue("t.n1.boss").passed)
    }

    @Test
    fun `boss raté à 2 sur 3 avec un seuil de 80 pour cent`() {
        val end = play(machine.start(builder.forBoss("t", 1), Progress()), listOf(good, good, bad))
        val finished = assertIs<SessionState.Finished>(end.state)
        assertEquals(false, finished.outcome.bossPassed)
        assertFalse(1 in end.progress.validatedLevels["t"].orEmpty())
    }

    @Test
    fun `temps écoulé - le boss est perdu`() {
        val start = machine.start(builder.forBoss("t", 1), Progress())
        val afterOne = machine.submit(assertIs(start.state), good, 0, start.progress, 1)
        val end = machine.timeUp(afterOne.state, afterOne.progress, 1)
        val finished = assertIs<SessionState.Finished>(end.state)
        assertTrue(finished.outcome.timeUp)
        assertEquals(false, finished.outcome.bossPassed)
        assertEquals(1, finished.outcome.correct)
        assertEquals(3, finished.outcome.total)
    }

    @Test
    fun `révisions - seuls les exercices dus reviennent`() {
        val after = play(machine.start(builder.forLevel("t", 2, Progress()), Progress()), listOf(good, bad), today = 10)
        val reviewTomorrow = builder.forReview(after.progress, today = 11)
        assertEquals(1, reviewTomorrow.exercises.size) // le raté revient le lendemain
        assertEquals(2, builder.forReview(after.progress, today = 12).exercises.size)
        assertEquals(SessionKind.REVIEW, reviewTomorrow.kind)
    }

    @Test
    fun `défi du jour - stable sur la journée et marqué comme fait`() {
        val a = builder.daily(Progress(), today = 99)
        assertEquals(a, builder.daily(Progress(), today = 99))
        val end = play(machine.start(a, Progress()), listOf(good), today = 99)
        assertEquals(99, end.progress.dailyDoneDay)
    }

    @Test
    fun `session vide - terminée tout de suite`() {
        val t = machine.start(builder.forLevel("t", 5, Progress()), Progress())
        assertIs<SessionState.Finished>(t.state)
    }
}
