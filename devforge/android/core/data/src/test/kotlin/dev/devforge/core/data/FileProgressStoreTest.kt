package dev.devforge.core.data

import dev.devforge.core.model.ExerciseRecord
import dev.devforge.core.model.Progress
import dev.devforge.core.model.ReviewCard
import dev.devforge.core.model.Streak
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileProgressStoreTest {
    private val dir: File = Files.createTempDirectory("devforge").toFile()

    @Test
    fun `aller-retour sur disque`() {
        val store = FileProgressStore(File(dir, "progress.json"))
        assertEquals(Progress(), store.load())
        val progress = Progress(
            xp = 120,
            records = mapOf("poo.n1.x" to ExerciseRecord(2, 1, true, 1.0, 19000)),
            validatedLevels = mapOf("poo" to setOf(1, 2)),
            lessonsRead = setOf("poo.n1.encapsulation"),
            streak = Streak(3, 5, 19000),
            reviews = mapOf("poo.n1.x" to ReviewCard(2, 19004)),
            dailyDoneDay = 19000,
        )
        store.save(progress)
        assertEquals(progress, FileProgressStore(File(dir, "progress.json")).load())
    }

    @Test
    fun `fichier corrompu - on repart de zéro sans l'écraser`() {
        val file = File(dir, "abime.json").apply { writeText("{pas du json") }
        assertEquals(Progress(), FileProgressStore(file).load())
        assertTrue(File(dir, "abime.json.corrompu").exists())
    }
}
