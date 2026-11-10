package dev.devforge.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Contenu de `curriculum.yaml` : rangs, axes du radar et carte des parcours. */
@Serializable
data class Curriculum(
    val ranks: List<Rank>,
    val domains: List<Domain>,
    val tracks: List<TrackRef>,
)

@Serializable
data class Rank(val level: Int, val name: String, val verb: String, val description: String)

@Serializable
data class Domain(val id: String, val title: String)

/** Un parcours tel que déclaré dans la carte (même s'il n'a pas encore de contenu). */
@Serializable
data class TrackRef(
    val id: String,
    val title: String,
    val domain: String,
    val requires: List<String> = emptyList(),
    val status: String,
) {
    val isPilot: Boolean get() = status == "pilot"
}

/** Contenu d'un `track.yaml` : les 5 niveaux d'un parcours. */
@Serializable
data class TrackDetail(
    val id: String,
    val title: String,
    val description: String,
    val levels: List<Level>,
)

@Serializable
data class Level(
    val level: Int,
    val title: String,
    val skills: List<Skill>,
    val boss: Boss? = null,
)

@Serializable
data class Skill(val id: String, val title: String)

@Serializable
data class Boss(
    val id: String,
    val title: String,
    val exercises: List<String>,
    @SerialName("pass_ratio") val passRatio: Double,
    @SerialName("time_limit_min") val timeLimitMin: Int? = null,
)

/** En-tête YAML d'une leçon Markdown. */
@Serializable
data class LessonMeta(
    val id: String,
    val title: String,
    val skills: List<String>,
    @SerialName("duration_min") val durationMin: Int,
)

data class Lesson(
    val trackId: String,
    val level: Int,
    val meta: LessonMeta,
    val markdown: String,
)

/** Un exercice et sa place dans le curriculum. */
data class PlacedExercise(
    val trackId: String,
    val level: Int,
    val exercise: Exercise,
)

/** Tout le contenu chargé, en lecture seule. */
data class Content(
    val curriculum: Curriculum,
    val tracks: Map<String, TrackDetail>,
    val exercises: List<PlacedExercise>,
    val lessons: List<Lesson>,
) {
    private val exercisesById = exercises.associateBy { it.exercise.id }
    private val trackRefsById = curriculum.tracks.associateBy { it.id }

    fun exercise(id: String): PlacedExercise? = exercisesById[id]

    fun trackRef(id: String): TrackRef? = trackRefsById[id]

    fun exercisesOf(trackId: String, level: Int? = null): List<PlacedExercise> =
        exercises.filter { it.trackId == trackId && (level == null || it.level == level) }

    fun lessonsOf(trackId: String, level: Int? = null): List<Lesson> =
        lessons.filter { it.trackId == trackId && (level == null || it.level == level) }

    fun lesson(id: String): Lesson? = lessons.firstOrNull { it.meta.id == id }

    fun rankName(level: Int): String =
        curriculum.ranks.firstOrNull { it.level == level }?.name ?: "Débutant"
}
