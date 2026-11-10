package dev.devforge.core.domain

import dev.devforge.core.domain.parsons.ParsonsBoard
import dev.devforge.core.model.Exercise
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParsonsBoardTest {
    private val exercise = Exercise.Parsons(
        "p", "p", listOf("s"), 1, prompt = "?", explanation = "!",
        lines = listOf("{", "a", "}", "}"), distractors = listOf("b"),
    )

    @Test
    fun `placer, retirer et annuler`() {
        val start = ParsonsBoard.start(exercise, Random(1))
        assertEquals(5, start.pool.size)
        assertFalse(start.canUndo)

        val brace = start.pool.first { it.text == "{" }
        val a = start.pool.first { it.text == "a" }
        val placed = start.place(brace).place(a)
        assertEquals(listOf("{", "a"), placed.lines)
        assertEquals(3, placed.pool.size)

        val removed = placed.remove(brace)
        assertEquals(listOf("a"), removed.lines)

        val undone = removed.undo()
        assertEquals(listOf("{", "a"), undone.lines) // le « { » revient à sa place
        assertEquals(listOf("{"), undone.undo().lines)
        assertTrue(undone.undo().undo().solution.isEmpty())
        assertFalse(undone.undo().undo().canUndo)
    }

    @Test
    fun `deux lignes identiques restent distinctes`() {
        val start = ParsonsBoard.start(exercise, Random(2))
        val braces = start.pool.filter { it.text == "}" }
        assertEquals(2, braces.size)
        val board = start.place(braces[0])
        assertEquals(1, board.pool.count { it.text == "}" })
    }
}
