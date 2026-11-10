package dev.devforge.core.data

import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.evaluation.EvaluateAnswer
import dev.devforge.core.domain.evaluation.ReferenceAnswers
import dev.devforge.core.model.Content
import dev.devforge.core.model.Exercise
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Charge le vrai contenu du dépôt (devforge/content) et le fait passer dans le moteur. */
class RealContentTest {
    private val content: Content by lazy {
        val dir = File(System.getProperty("devforge.content") ?: "../../../content")
        YamlContentLoader(DirectoryContentFiles(dir)).load()
    }
    private val evaluate = EvaluateAnswer()

    @Test
    fun `le contenu se charge entièrement`() {
        assertEquals(31, content.curriculum.tracks.size)
        assertEquals(31, content.tracks.size) // chaque parcours a au moins son programme (track.yaml)
        assertTrue(content.exercises.size >= 61)
        assertTrue(content.lessons.size >= 12)
        assertTrue(content.lessons.all { it.markdown.isNotBlank() && !it.markdown.startsWith("---") })
        // les 9 types utilisés par le contenu sont bien reconnus
        val types = content.exercises.map { it.exercise::class.simpleName }.toSet()
        assertTrue(types.containsAll(listOf("Qcm", "PredictOutput", "FindBug", "Parsons", "FillBlanks", "Match", "CodeReview", "Scenario", "DesignChallenge")))
    }

    @Test
    fun `chaque exercice est réussi avec sa propre solution`() {
        for (placed in content.exercises) {
            val exercise = placed.exercise
            val reference = assertNotNull(ReferenceAnswers.of(exercise), exercise.id)
            val verdict = evaluate(exercise, reference)
            assertTrue(verdict.correct, "${exercise.id} : la solution de référence est refusée")
            assertEquals(1.0, verdict.score, "${exercise.id} : score incomplet")
        }
    }

    @Test
    fun `une mauvaise réponse est refusée`() {
        for (placed in content.exercises) {
            val wrong: Answer = when (val ex = placed.exercise) {
                is Exercise.Qcm -> Answer.Choices(ex.choices.filterNot { it.correct }.take(1).map { it.id }.toSet())
                is Exercise.PredictOutput -> Answer.Choices(ex.choices.filterNot { it.correct }.take(1).map { it.id }.toSet())
                is Exercise.FindBug -> Answer.Lines(setOf(999))
                is Exercise.Parsons -> Answer.Ordering(ex.lines.reversed())
                is Exercise.FillBlanks -> Answer.Blanks(ex.blanks.mapValues { "???" })
                is Exercise.Match -> Answer.Pairs(ex.pairs.zip(ex.pairs.drop(1) + ex.pairs.first()).associate { (a, b) -> a.left to b.right })
                is Exercise.CodeReview -> Answer.Flags(emptySet())
                is Exercise.Scenario -> Answer.Path(emptyList())
                is Exercise.DesignChallenge -> Answer.SelfAssessment(emptySet())
                is Exercise.Code -> continue
            }
            assertFalse(evaluate(placed.exercise, wrong).correct, "${placed.exercise.id} : une mauvaise réponse est acceptée")
        }
    }

    @Test
    fun `les boss référencent des exercices existants`() {
        for ((trackId, track) in content.tracks) {
            for (boss in track.levels.mapNotNull { it.boss }) {
                for (id in boss.exercises) assertNotNull(content.exercise(id), "$trackId : $id")
            }
        }
    }
}
