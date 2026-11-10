package dev.devforge.core.domain.evaluation

import dev.devforge.core.model.Exercise

/**
 * La réponse attendue de chaque exercice. Sert aux tests (chaque exercice du
 * contenu doit être réussi avec sa propre solution) et à afficher la solution.
 */
object ReferenceAnswers {
    fun of(exercise: Exercise): Answer? = when (exercise) {
        is Exercise.Qcm -> Answer.Choices(exercise.choices.filter { it.correct }.map { it.id }.toSet())
        is Exercise.PredictOutput ->
            if (exercise.choices.isNotEmpty()) {
                Answer.Choices(exercise.choices.filter { it.correct }.map { it.id }.toSet())
            } else {
                exercise.expectedOutput?.let { Answer.Text(it) }
            }
        is Exercise.FindBug -> Answer.Lines(setOf(exercise.bugLines.first()))
        is Exercise.Parsons -> Answer.Ordering(exercise.lines)
        is Exercise.FillBlanks -> Answer.Blanks(exercise.blanks.mapValues { it.value.answers.first() })
        is Exercise.Match -> Answer.Pairs(exercise.pairs.associate { it.left to it.right })
        is Exercise.CodeReview ->
            Answer.Flags(exercise.issues.map { ReviewFlag(it.lines.first(), it.category) }.toSet())
        is Exercise.Scenario -> Answer.Path(ScenarioWalker.bestPath(exercise))
        is Exercise.DesignChallenge -> Answer.SelfAssessment(exercise.rubric.indices.toSet())
        is Exercise.Code -> null
    }
}
