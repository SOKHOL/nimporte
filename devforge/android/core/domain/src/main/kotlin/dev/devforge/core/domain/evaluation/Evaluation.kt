package dev.devforge.core.domain.evaluation

import dev.devforge.core.model.Choice
import dev.devforge.core.model.Exercise

/** La réponse de l'utilisateur. Chaque type d'exercice attend une forme précise. */
sealed interface Answer {
    data class Choices(val ids: Set<String>) : Answer
    data class Text(val value: String) : Answer
    data class Lines(val numbers: Set<Int>) : Answer
    data class Ordering(val lines: List<String>) : Answer
    data class Blanks(val values: Map<String, String>) : Answer
    data class Pairs(val rightByLeft: Map<String, String>) : Answer
    data class Flags(val flags: Set<ReviewFlag>) : Answer
    data class Path(val optionIndexes: List<Int>) : Answer
    data class SelfAssessment(val checked: Set<Int>) : Answer
}

/** Un problème signalé dans une revue de code : une ligne et une catégorie. */
data class ReviewFlag(val line: Int, val category: String)

/** Le résultat d'une évaluation : réussi ou non, et un score entre 0 et 1. */
data class Verdict(val correct: Boolean, val score: Double) {
    companion object {
        val WRONG = Verdict(false, 0.0)
        fun of(correct: Boolean) = Verdict(correct, if (correct) 1.0 else 0.0)
    }
}

/** Pattern Strategy : un évaluateur par type d'exercice. */
fun interface Evaluator<E : Exercise> {
    fun evaluate(exercise: E, answer: Answer): Verdict
}

/**
 * Normalisation des réponses texte, en chaîne : chaque étape transforme le
 * résultat de la précédente (Chain of Responsibility simplifiée).
 */
object Normalizer {
    private val steps: List<(String) -> String> = listOf(
        { it.replace("\r\n", "\n") },
        { text -> text.lines().joinToString("\n") { it.trimEnd() } },
        { it.trim() },
        { it.replace(Regex("[ \\t]+"), " ") },
    )

    fun normalize(text: String): String = steps.fold(text) { acc, step -> step(acc) }

    fun same(a: String, b: String): Boolean = normalize(a) == normalize(b)
}

private fun evaluateChoices(choices: List<Choice>, answer: Answer): Verdict {
    if (answer !is Answer.Choices) return Verdict.WRONG
    val expected = choices.filter { it.correct }.map { it.id }.toSet()
    return Verdict.of(answer.ids == expected)
}

object QcmEvaluator : Evaluator<Exercise.Qcm> {
    override fun evaluate(exercise: Exercise.Qcm, answer: Answer) = evaluateChoices(exercise.choices, answer)
}

object PredictOutputEvaluator : Evaluator<Exercise.PredictOutput> {
    override fun evaluate(exercise: Exercise.PredictOutput, answer: Answer): Verdict {
        val expected = exercise.expectedOutput
        return when {
            exercise.choices.isNotEmpty() -> evaluateChoices(exercise.choices, answer)
            answer is Answer.Text && expected != null -> Verdict.of(Normalizer.same(answer.value, expected))
            else -> Verdict.WRONG
        }
    }
}

/** Réussi si au moins une ligne est sélectionnée et que toutes sont des lignes fautives. */
object FindBugEvaluator : Evaluator<Exercise.FindBug> {
    override fun evaluate(exercise: Exercise.FindBug, answer: Answer): Verdict {
        if (answer !is Answer.Lines || answer.numbers.isEmpty()) return Verdict.WRONG
        return Verdict.of(exercise.bugLines.containsAll(answer.numbers))
    }
}

/** Réussi si l'ordre est exact ; le score compte les lignes bien placées. */
object ParsonsEvaluator : Evaluator<Exercise.Parsons> {
    override fun evaluate(exercise: Exercise.Parsons, answer: Answer): Verdict {
        if (answer !is Answer.Ordering) return Verdict.WRONG
        val wellPlaced = exercise.lines.indices.count { answer.lines.getOrNull(it) == exercise.lines[it] }
        val correct = answer.lines == exercise.lines
        return Verdict(correct, wellPlaced.toDouble() / exercise.lines.size * if (correct) 1.0 else 0.9)
    }
}

object FillBlanksEvaluator : Evaluator<Exercise.FillBlanks> {
    fun isBlankCorrect(exercise: Exercise.FillBlanks, key: String, value: String?): Boolean {
        val blank = exercise.blanks[key] ?: return false
        return value != null && blank.answers.any { Normalizer.same(it, value) }
    }

    override fun evaluate(exercise: Exercise.FillBlanks, answer: Answer): Verdict {
        if (answer !is Answer.Blanks) return Verdict.WRONG
        val good = exercise.blanks.keys.count { isBlankCorrect(exercise, it, answer.values[it]) }
        return Verdict(good == exercise.blanks.size, good.toDouble() / exercise.blanks.size)
    }
}

object MatchEvaluator : Evaluator<Exercise.Match> {
    override fun evaluate(exercise: Exercise.Match, answer: Answer): Verdict {
        if (answer !is Answer.Pairs) return Verdict.WRONG
        val good = exercise.pairs.count { answer.rightByLeft[it.left] == it.right }
        return Verdict(good == exercise.pairs.size, good.toDouble() / exercise.pairs.size)
    }
}

object CodeReviewEvaluator : Evaluator<Exercise.CodeReview> {
    /** Indices des problèmes trouvés : une ligne signalée du problème, avec la bonne catégorie. */
    fun foundIssues(exercise: Exercise.CodeReview, flags: Set<ReviewFlag>): Set<Int> =
        exercise.issues.indices.filter { index ->
            val issue = exercise.issues[index]
            flags.any { it.category == issue.category && it.line in issue.lines }
        }.toSet()

    override fun evaluate(exercise: Exercise.CodeReview, answer: Answer): Verdict {
        if (answer !is Answer.Flags) return Verdict.WRONG
        val found = foundIssues(exercise, answer.flags).size
        return Verdict(found >= exercise.required, found.toDouble() / exercise.issues.size)
    }
}

object ScenarioEvaluator : Evaluator<Exercise.Scenario> {
    override fun evaluate(exercise: Exercise.Scenario, answer: Answer): Verdict {
        if (answer !is Answer.Path) return Verdict.WRONG
        val run = ScenarioWalker.walk(exercise, answer.optionIndexes)
        if (run.ending == null) return Verdict.WRONG
        val max = ScenarioWalker.maxPoints(exercise).coerceAtLeast(1)
        return Verdict(run.ending == "success", (run.points.toDouble() / max).coerceIn(0.0, 1.0))
    }
}

/** Auto-évaluation avec la grille : réussi à partir de 60 % des points. */
object DesignChallengeEvaluator : Evaluator<Exercise.DesignChallenge> {
    const val PASS = 0.6

    override fun evaluate(exercise: Exercise.DesignChallenge, answer: Answer): Verdict {
        if (answer !is Answer.SelfAssessment) return Verdict.WRONG
        val total = exercise.rubric.sumOf { it.points }.coerceAtLeast(1)
        val points = answer.checked.filter { it in exercise.rubric.indices }.sumOf { exercise.rubric[it].points }
        val score = points.toDouble() / total
        return Verdict(score >= PASS, score)
    }
}

/**
 * Point d'entrée de l'évaluation. Le `when` est exhaustif sur la sealed class :
 * ajouter un type d'exercice sans évaluateur ne compile pas.
 */
class EvaluateAnswer {
    operator fun invoke(exercise: Exercise, answer: Answer): Verdict = when (exercise) {
        is Exercise.Qcm -> QcmEvaluator.evaluate(exercise, answer)
        is Exercise.PredictOutput -> PredictOutputEvaluator.evaluate(exercise, answer)
        is Exercise.FindBug -> FindBugEvaluator.evaluate(exercise, answer)
        is Exercise.Parsons -> ParsonsEvaluator.evaluate(exercise, answer)
        is Exercise.FillBlanks -> FillBlanksEvaluator.evaluate(exercise, answer)
        is Exercise.Match -> MatchEvaluator.evaluate(exercise, answer)
        is Exercise.CodeReview -> CodeReviewEvaluator.evaluate(exercise, answer)
        is Exercise.Scenario -> ScenarioEvaluator.evaluate(exercise, answer)
        is Exercise.DesignChallenge -> DesignChallengeEvaluator.evaluate(exercise, answer)
        is Exercise.Code -> Verdict.WRONG // nécessite le runner de code (phase 3)
    }
}
