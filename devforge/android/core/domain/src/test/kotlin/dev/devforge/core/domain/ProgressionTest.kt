package dev.devforge.core.domain

import dev.devforge.core.domain.evaluation.Verdict
import dev.devforge.core.domain.levels.LevelRules
import dev.devforge.core.domain.levels.Requirement
import dev.devforge.core.domain.progress.Leitner
import dev.devforge.core.domain.progress.ProgressRecorder
import dev.devforge.core.domain.progress.XpCalculator
import dev.devforge.core.domain.progress.XpContext
import dev.devforge.core.domain.progress.displayed
import dev.devforge.core.domain.progress.touch
import dev.devforge.core.domain.stats.Stats
import dev.devforge.core.model.Progress
import dev.devforge.core.model.Streak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionTest {
    private val ex = Fixtures.qcm("t.n1.a", difficulty = 2) // 20 XP de base

    @Test
    fun `xp - bonus premier essai, malus d'indices, réussite partielle`() {
        val xp = XpCalculator()
        assertEquals(30, xp.compute(XpContext(ex, Verdict(true, 1.0), hintsUsed = 0, firstAttempt = true)))
        assertEquals(20, xp.compute(XpContext(ex, Verdict(true, 1.0), hintsUsed = 0, firstAttempt = false)))
        assertEquals(15, xp.compute(XpContext(ex, Verdict(true, 1.0), hintsUsed = 1, firstAttempt = true)))
        assertEquals(5, xp.compute(XpContext(ex, Verdict(false, 0.5), hintsUsed = 0, firstAttempt = true)))
        assertEquals(0, xp.compute(XpContext(ex, Verdict(false, 0.0), hintsUsed = 0, firstAttempt = true)))
    }

    @Test
    fun `leitner - monter de boîte ou recommencer`() {
        val first = Leitner.next(null, correct = true, today = 100)
        assertEquals(1, first.box)
        assertEquals(102, first.dueDay)
        val second = Leitner.next(first, correct = true, today = 102)
        assertEquals(2, second.box)
        assertEquals(106, second.dueDay)
        val failed = Leitner.next(second, correct = false, today = 106)
        assertEquals(0, failed.box)
        assertEquals(107, failed.dueDay)
    }

    @Test
    fun `série de jours`() {
        val s1 = Streak().touch(10)
        assertEquals(1, s1.current)
        assertEquals(1, s1.touch(10).current)
        val s2 = s1.touch(11)
        assertEquals(2, s2.current)
        assertEquals(2, s2.best)
        val s3 = s2.touch(15)
        assertEquals(1, s3.current)
        assertEquals(2, s3.best)
        assertEquals(1, s3.displayed(16))
        assertEquals(0, s3.displayed(17))
    }

    @Test
    fun `enregistrer une tentative`() {
        val recorder = ProgressRecorder()
        val (p1, xp1) = recorder.record(Progress(), ex, Verdict(false, 0.0), 0, today = 50)
        assertEquals(0, xp1)
        assertFalse(p1.isSucceeded(ex.id))
        val (p2, xp2) = recorder.record(p1, ex, Verdict(true, 1.0), 0, today = 50)
        assertEquals(20, xp2) // ce n'est plus le premier essai
        assertTrue(p2.isSucceeded(ex.id))
        assertEquals(2, p2.records.getValue(ex.id).attempts)
        assertEquals(1, p2.streak.current)
        assertEquals(1, p2.reviews.getValue(ex.id).box)
    }

    @Test
    fun `niveau sans boss validé à 80 pour cent`() {
        val content = Fixtures.content()
        val rules = LevelRules(content)
        val recorder = ProgressRecorder()
        var progress = Progress()
        progress = recorder.record(progress, content.exercise("t.n2.a")!!.exercise, Verdict.of(true), 0, 1).progress
        assertFalse(2 in rules.refresh(progress).validatedLevels["t"].orEmpty())
        progress = recorder.record(progress, content.exercise("t.n2.b")!!.exercise, Verdict.of(true), 0, 1).progress
        assertTrue(2 in rules.refresh(progress).validatedLevels.getValue("t"))
        // le niveau 1 a un boss : réussir ses exercices ne suffit pas
        assertFalse(1 in rules.refresh(progress).validatedLevels.getValue("t"))
    }

    @Test
    fun `prérequis combinés (Specification)`() {
        val rules = LevelRules(Fixtures.content())
        assertEquals(Requirement("poo", 2), Requirement.parse("poo@2"))
        val none = Progress()
        assertEquals(2, rules.missingRequirements("u", none).size)
        val partial = Progress(validatedLevels = mapOf("t" to setOf(1)))
        assertFalse(rules.unlockRule("u").isSatisfiedBy(partial))
        assertEquals(listOf(Requirement("t", 2)), rules.missingRequirements("u", partial))
        val full = Progress(validatedLevels = mapOf("t" to setOf(1, 2)))
        assertTrue(rules.unlockRule("u").isSatisfiedBy(full))
    }

    @Test
    fun `statistiques par axe`() {
        val content = Fixtures.content()
        val progress = ProgressRecorder().record(Progress(), content.exercise("t.n1.a")!!.exercise, Verdict.of(true), 0, 1).progress
        val code = Stats(content).byDomain(progress).single()
        assertEquals(1, code.succeeded)
        assertEquals(5, code.total)
        assertEquals(1, Stats(content).global(progress).exercisesSucceeded)
    }
}
