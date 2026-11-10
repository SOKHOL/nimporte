package dev.devforge.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Contenu d'un fichier `exercises.yaml`. */
@Serializable
data class ExercisesFile(val exercises: List<Exercise>)

/**
 * Un exercice. Le champ YAML `type` choisit la sous-classe (voir docs/05-format-du-contenu.md).
 * Les champs communs sont déclarés ici ; chaque type ajoute les siens.
 */
@Serializable
sealed class Exercise {
    abstract val id: String
    abstract val title: String
    abstract val skills: List<String>
    abstract val difficulty: Int
    abstract val xp: Int?
    abstract val language: String?
    abstract val prompt: String
    abstract val code: String?
    abstract val hints: List<String>
    abstract val explanation: String

    @Serializable
    @SerialName("qcm")
    data class Qcm(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val choices: List<Choice>,
        val multiple: Boolean = false,
    ) : Exercise()

    @Serializable
    @SerialName("predict-output")
    data class PredictOutput(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val choices: List<Choice> = emptyList(),
        @SerialName("expected_output") val expectedOutput: String? = null,
    ) : Exercise()

    @Serializable
    @SerialName("find-bug")
    data class FindBug(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        @SerialName("bug_lines") val bugLines: List<Int>,
    ) : Exercise()

    @Serializable
    @SerialName("parsons")
    data class Parsons(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val lines: List<String>,
        val distractors: List<String> = emptyList(),
    ) : Exercise()

    @Serializable
    @SerialName("fill-blanks")
    data class FillBlanks(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val blanks: Map<String, Blank>,
    ) : Exercise()

    @Serializable
    @SerialName("match")
    data class Match(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val pairs: List<MatchPair>,
    ) : Exercise()

    @Serializable
    @SerialName("code-review")
    data class CodeReview(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val issues: List<Issue>,
        @SerialName("min_found") val minFound: Int? = null,
    ) : Exercise() {
        /** Nombre de problèmes à trouver ; par défaut, la moitié (arrondie au-dessus). */
        val required: Int get() = minFound ?: ((issues.size + 1) / 2)
    }

    @Serializable
    @SerialName("scenario")
    data class Scenario(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val start: String,
        val nodes: Map<String, ScenarioNode>,
    ) : Exercise()

    @Serializable
    @SerialName("design-challenge")
    data class DesignChallenge(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        val requirements: List<String>,
        val constraints: List<String> = emptyList(),
        val rubric: List<Criterion>,
        @SerialName("reference_solution") val referenceSolution: String,
    ) : Exercise()

    /** Écrire du vrai code validé par des tests : nécessite le backend (phase 3). */
    @Serializable
    @SerialName("code")
    data class Code(
        override val id: String,
        override val title: String,
        override val skills: List<String>,
        override val difficulty: Int,
        override val xp: Int? = null,
        override val language: String? = null,
        override val prompt: String,
        override val code: String? = null,
        override val hints: List<String> = emptyList(),
        override val explanation: String,
        @SerialName("starter_code") val starterCode: String,
        val tests: String,
        val solution: String,
        @SerialName("timeout_ms") val timeoutMs: Int? = null,
    ) : Exercise()
}

@Serializable
data class Choice(
    val id: String,
    val text: String,
    val correct: Boolean = false,
    val feedback: String? = null,
)

@Serializable
data class Blank(
    val answers: List<String>,
    val options: List<String> = emptyList(),
)

@Serializable
data class MatchPair(val left: String, val right: String)

@Serializable
data class Issue(
    val lines: List<Int>,
    val category: String,
    val description: String,
)

@Serializable
data class ScenarioNode(
    val text: String,
    val ending: String? = null,
    val options: List<ScenarioOption> = emptyList(),
)

@Serializable
data class ScenarioOption(
    val text: String,
    val next: String,
    val points: Int = 0,
    val feedback: String? = null,
)

@Serializable
data class Criterion(
    val criterion: String,
    val points: Int,
    val hint: String? = null,
)

/** Catégories d'une revue de code, avec leur libellé affiché. */
object ReviewCategories {
    val labels: Map<String, String> = linkedMapOf(
        "bug" to "Bug",
        "securite" to "Sécurité",
        "performance" to "Performance",
        "lisibilite" to "Lisibilité",
        "conception" to "Conception",
        "test" to "Tests",
    )
}
