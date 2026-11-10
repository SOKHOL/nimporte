package dev.devforge.core.domain

import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.evaluation.CodeReviewEvaluator
import dev.devforge.core.domain.evaluation.EvaluateAnswer
import dev.devforge.core.domain.evaluation.Normalizer
import dev.devforge.core.domain.evaluation.ReviewFlag
import dev.devforge.core.domain.evaluation.ScenarioWalker
import dev.devforge.core.model.Blank
import dev.devforge.core.model.Choice
import dev.devforge.core.model.Criterion
import dev.devforge.core.model.Exercise
import dev.devforge.core.model.Issue
import dev.devforge.core.model.MatchPair
import dev.devforge.core.model.ScenarioNode
import dev.devforge.core.model.ScenarioOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluationTest {
    private val evaluate = EvaluateAnswer()

    @Test
    fun `qcm à réponses multiples`() {
        val ex = Fixtures.qcm("q").copy(
            multiple = true,
            choices = listOf(Choice("a", "A", true), Choice("b", "B", true), Choice("c", "C")),
        )
        assertTrue(evaluate(ex, Answer.Choices(setOf("a", "b"))).correct)
        assertFalse(evaluate(ex, Answer.Choices(setOf("a"))).correct)
        assertFalse(evaluate(ex, Answer.Choices(setOf("a", "b", "c"))).correct)
        assertFalse(evaluate(ex, Answer.Text("a")).correct)
    }

    @Test
    fun `prédiction en texte libre, espaces normalisés`() {
        val ex = Exercise.PredictOutput("p", "p", listOf("s"), 1, prompt = "?", explanation = "!", code = "x", expectedOutput = "11 1\n")
        assertTrue(evaluate(ex, Answer.Text("  11   1  ")).correct)
        assertFalse(evaluate(ex, Answer.Text("11 11")).correct)
        assertEquals("a b\nc", Normalizer.normalize("a  b  \r\nc\n"))
    }

    @Test
    fun `trouver le bug`() {
        val ex = Exercise.FindBug("f", "f", listOf("s"), 1, prompt = "?", explanation = "!", code = "a\nb\nc", bugLines = listOf(2, 3))
        assertTrue(evaluate(ex, Answer.Lines(setOf(2))).correct)
        assertTrue(evaluate(ex, Answer.Lines(setOf(2, 3))).correct)
        assertFalse(evaluate(ex, Answer.Lines(setOf(1, 2))).correct)
        assertFalse(evaluate(ex, Answer.Lines(emptySet())).correct)
    }

    @Test
    fun `parsons et score partiel`() {
        val ex = Exercise.Parsons("p", "p", listOf("s"), 1, prompt = "?", explanation = "!", lines = listOf("a", "b", "c", "d"))
        assertEquals(1.0, evaluate(ex, Answer.Ordering(listOf("a", "b", "c", "d"))).score)
        val partial = evaluate(ex, Answer.Ordering(listOf("a", "b", "d", "c")))
        assertFalse(partial.correct)
        assertTrue(partial.score in 0.4..0.5)
    }

    @Test
    fun `trous`() {
        val ex = Exercise.FillBlanks(
            "f", "f", listOf("s"), 1, prompt = "?", explanation = "!", code = "{{1}} {{2}}",
            blanks = mapOf("1" to Blank(listOf("val")), "2" to Blank(listOf("init", "init()"))),
        )
        assertTrue(evaluate(ex, Answer.Blanks(mapOf("1" to "val", "2" to " init() "))).correct)
        val half = evaluate(ex, Answer.Blanks(mapOf("1" to "var", "2" to "init")))
        assertFalse(half.correct)
        assertEquals(0.5, half.score)
    }

    @Test
    fun `relier`() {
        val ex = Exercise.Match("m", "m", listOf("s"), 1, prompt = "?", explanation = "!", pairs = listOf(MatchPair("a", "1"), MatchPair("b", "2"), MatchPair("c", "3")))
        assertTrue(evaluate(ex, Answer.Pairs(mapOf("a" to "1", "b" to "2", "c" to "3"))).correct)
        assertFalse(evaluate(ex, Answer.Pairs(mapOf("a" to "2", "b" to "1", "c" to "3"))).correct)
    }

    @Test
    fun `revue de code - ligne et catégorie`() {
        val ex = Exercise.CodeReview(
            "r", "r", listOf("s"), 1, prompt = "?", explanation = "!", code = "a\nb\nc",
            issues = listOf(Issue(listOf(1), "bug", "x"), Issue(listOf(2, 3), "conception", "y"), Issue(listOf(3), "test", "z")),
            minFound = 2,
        )
        assertEquals(setOf(0, 1), CodeReviewEvaluator.foundIssues(ex, setOf(ReviewFlag(1, "bug"), ReviewFlag(3, "conception"))))
        assertTrue(evaluate(ex, Answer.Flags(setOf(ReviewFlag(1, "bug"), ReviewFlag(3, "conception")))).correct)
        assertFalse(evaluate(ex, Answer.Flags(setOf(ReviewFlag(1, "bug"), ReviewFlag(2, "bug")))).correct)
    }

    @Test
    fun `scénario - meilleur chemin et score`() {
        val ex = Exercise.Scenario(
            "s", "s", listOf("s"), 1, prompt = "?", explanation = "!", start = "a",
            nodes = mapOf(
                "a" to ScenarioNode("A", options = listOf(ScenarioOption("x", "b", 1), ScenarioOption("y", "c", 3))),
                "b" to ScenarioNode("B", ending = "partial"),
                "c" to ScenarioNode("C", options = listOf(ScenarioOption("z", "d", 2))),
                "d" to ScenarioNode("D", ending = "success"),
            ),
        )
        assertEquals(5, ScenarioWalker.maxPoints(ex))
        assertEquals(listOf(1, 0), ScenarioWalker.bestPath(ex))
        assertEquals(1.0, evaluate(ex, Answer.Path(listOf(1, 0))).score)
        val partial = evaluate(ex, Answer.Path(listOf(0)))
        assertFalse(partial.correct)
        assertEquals(0.2, partial.score)
        assertFalse(evaluate(ex, Answer.Path(emptyList())).correct) // pas arrivé au bout
    }

    @Test
    fun `défi de conception auto-évalué`() {
        val ex = Exercise.DesignChallenge(
            "d", "d", listOf("s"), 1, prompt = "?", explanation = "!", requirements = listOf("r"),
            rubric = listOf(Criterion("a", 2), Criterion("b", 2), Criterion("c", 1)), referenceSolution = "sol",
        )
        assertTrue(evaluate(ex, Answer.SelfAssessment(setOf(0, 1))).correct)
        assertTrue(evaluate(ex, Answer.SelfAssessment(setOf(0, 2))).correct) // 3/5 = 60 % : juste assez
        assertFalse(evaluate(ex, Answer.SelfAssessment(setOf(0))).correct)
        assertFalse(evaluate(ex, Answer.SelfAssessment(setOf(7))).correct) // index hors grille ignoré
    }
}
