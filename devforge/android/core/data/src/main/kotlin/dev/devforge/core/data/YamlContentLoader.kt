package dev.devforge.core.data

import dev.devforge.core.domain.ContentSource
import dev.devforge.core.model.Content
import dev.devforge.core.model.Curriculum
import dev.devforge.core.model.ExercisesFile
import dev.devforge.core.model.Lesson
import dev.devforge.core.model.LessonMeta
import dev.devforge.core.model.PlacedExercise
import dev.devforge.core.model.TrackDetail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File

/** Accès aux fichiers de contenu : un dossier en test, les assets sur Android (pattern Adapter). */
interface ContentFiles {
    fun read(path: String): String

    /** Noms des entrées d'un dossier (fichiers et sous-dossiers), triés. */
    fun list(dir: String): List<String>
}

class DirectoryContentFiles(private val root: File) : ContentFiles {
    override fun read(path: String): String = File(root, path).readText()
    override fun list(dir: String): List<String> = File(root, dir).list()?.sorted().orEmpty()
}

class ContentLoadingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Convertit l'arbre produit par le parseur YAML en arbre JSON, pour kotlinx.serialization. */
object YamlToJson {
    fun convert(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to convert(v) })
        is List<*> -> JsonArray(value.map(::convert))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}

/**
 * Charge le contenu directement depuis les fichiers YAML et Markdown du dépôt
 * (voir docs/adr/0005-lecture-du-yaml-par-l-app.md).
 */
class YamlContentLoader(private val files: ContentFiles) : ContentSource {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
    }
    private val frontMatter = Regex("\\A---\\r?\\n(.*?)\\r?\\n---\\r?\\n?", RegexOption.DOT_MATCHES_ALL)
    private val levelDir = Regex("^n([1-5])$")

    private fun yaml(path: String): JsonElement = try {
        YamlToJson.convert(Load(LoadSettings.builder().build()).loadFromString(files.read(path)))
    } catch (e: Exception) {
        throw ContentLoadingException("Impossible de lire $path : ${e.message}", e)
    }

    private inline fun <reified T> decode(path: String): T = try {
        json.decodeFromJsonElement<T>(yaml(path))
    } catch (e: ContentLoadingException) {
        throw e
    } catch (e: Exception) {
        throw ContentLoadingException("Format inattendu dans $path : ${e.message}", e)
    }

    override fun load(): Content {
        val curriculum = decode<Curriculum>("curriculum.yaml")
        val tracks = mutableMapOf<String, TrackDetail>()
        val exercises = mutableListOf<PlacedExercise>()
        val lessons = mutableListOf<Lesson>()

        for (trackId in files.list("tracks")) {
            val trackEntries = files.list("tracks/$trackId")
            if ("track.yaml" !in trackEntries) continue
            tracks[trackId] = decode<TrackDetail>("tracks/$trackId/track.yaml")

            for (entry in trackEntries) {
                val level = levelDir.find(entry)?.groupValues?.get(1)?.toInt() ?: continue
                for (file in files.list("tracks/$trackId/$entry")) {
                    val path = "tracks/$trackId/$entry/$file"
                    when {
                        file.endsWith(".yaml") || file.endsWith(".yml") ->
                            decode<ExercisesFile>(path).exercises.forEach {
                                exercises += PlacedExercise(trackId, level, it)
                            }
                        file.endsWith(".md") -> lessons += lesson(path, trackId, level)
                    }
                }
            }
        }
        return Content(curriculum, tracks, exercises, lessons)
    }

    private fun lesson(path: String, trackId: String, level: Int): Lesson {
        val text = files.read(path)
        val match = frontMatter.find(text) ?: throw ContentLoadingException("En-tête manquant dans $path")
        val meta = try {
            val tree = YamlToJson.convert(Load(LoadSettings.builder().build()).loadFromString(match.groupValues[1]))
            json.decodeFromJsonElement<LessonMeta>(tree)
        } catch (e: Exception) {
            throw ContentLoadingException("En-tête invalide dans $path : ${e.message}", e)
        }
        return Lesson(trackId, level, meta, text.substring(match.range.last + 1).trim())
    }
}
