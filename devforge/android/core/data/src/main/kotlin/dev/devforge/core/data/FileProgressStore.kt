package dev.devforge.core.data

import dev.devforge.core.domain.ProgressStore
import dev.devforge.core.model.Progress
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Progression enregistrée dans un fichier JSON (voir docs/adr/0006-progression-en-json.md).
 * L'écriture passe par un fichier temporaire renommé ensuite : un arrêt brutal ne
 * laisse jamais un fichier à moitié écrit.
 */
class FileProgressStore(private val file: File) : ProgressStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    override fun load(): Progress {
        if (!file.exists()) return Progress()
        return try {
            json.decodeFromString(Progress.serializer(), file.readText())
        } catch (e: Exception) {
            // Fichier illisible : on le garde de côté plutôt que de l'écraser.
            file.copyTo(File(file.parentFile, file.name + ".corrompu"), overwrite = true)
            Progress()
        }
    }

    @Synchronized
    override fun save(progress: Progress) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(Progress.serializer(), progress))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
