package dev.devforge.core.domain

import dev.devforge.core.model.Boss
import dev.devforge.core.model.Choice
import dev.devforge.core.model.Content
import dev.devforge.core.model.Curriculum
import dev.devforge.core.model.Domain
import dev.devforge.core.model.Exercise
import dev.devforge.core.model.Level
import dev.devforge.core.model.PlacedExercise
import dev.devforge.core.model.Rank
import dev.devforge.core.model.Skill
import dev.devforge.core.model.TrackDetail
import dev.devforge.core.model.TrackRef

object Fixtures {
    fun qcm(id: String, difficulty: Int = 1) = Exercise.Qcm(
        id = id,
        title = id,
        skills = listOf("s1"),
        difficulty = difficulty,
        prompt = "?",
        explanation = "!",
        choices = listOf(Choice("a", "A", correct = true), Choice("b", "B")),
    )

    /** Un parcours « t » : niveau 1 avec boss (3 exercices), niveau 2 sans boss (2 exercices). */
    fun content(): Content {
        val levels = (1..5).map { n ->
            Level(
                level = n,
                title = "Niveau $n",
                skills = listOf(Skill("s$n", "S$n")),
                boss = if (n == 1) Boss("t.n1.boss", "Boss", listOf("t.n1.a", "t.n1.b", "t.n1.c"), 0.8, 5) else null,
            )
        }
        val exercises = listOf("t.n1.a", "t.n1.b", "t.n1.c").map { PlacedExercise("t", 1, qcm(it)) } +
            listOf("t.n2.a", "t.n2.b").map { PlacedExercise("t", 2, qcm(it, difficulty = 2)) }
        return Content(
            curriculum = Curriculum(
                ranks = (1..5).map { Rank(it, "R$it", "v", "d") },
                domains = listOf(Domain("code", "Code")),
                tracks = listOf(
                    TrackRef("t", "T", "code", emptyList(), "pilot"),
                    TrackRef("u", "U", "code", listOf("t@1", "t@2"), "planned"),
                ),
            ),
            tracks = mapOf("t" to TrackDetail("t", "T", "desc", levels)),
            exercises = exercises,
            lessons = emptyList(),
        )
    }
}
