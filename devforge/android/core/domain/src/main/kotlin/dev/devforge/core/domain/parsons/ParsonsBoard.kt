package dev.devforge.core.domain.parsons

import dev.devforge.core.model.Exercise
import kotlin.random.Random

/** Une ligne déplaçable. La clé distingue deux lignes au texte identique (comme deux `}`). */
data class Piece(val key: Int, val text: String)

/** Pattern Command : chaque déplacement sait s'exécuter et s'annuler. */
sealed interface Move {
    fun execute(board: ParsonsBoard): ParsonsBoard
    fun undo(board: ParsonsBoard): ParsonsBoard

    data class Place(val piece: Piece) : Move {
        override fun execute(board: ParsonsBoard) =
            board.copy(pool = board.pool - piece, solution = board.solution + piece)

        override fun undo(board: ParsonsBoard) =
            board.copy(pool = board.pool + piece, solution = board.solution - piece)
    }

    data class Remove(val piece: Piece, val index: Int) : Move {
        override fun execute(board: ParsonsBoard) =
            board.copy(pool = board.pool + piece, solution = board.solution - piece)

        override fun undo(board: ParsonsBoard) =
            board.copy(
                pool = board.pool - piece,
                solution = board.solution.toMutableList().apply { add(index.coerceAtMost(size), piece) },
            )
    }
}

/** L'état d'un exercice Parsons : les lignes disponibles, la solution en cours, l'historique. */
data class ParsonsBoard(
    val pool: List<Piece>,
    val solution: List<Piece> = emptyList(),
    val history: List<Move> = emptyList(),
) {
    val canUndo: Boolean get() = history.isNotEmpty()
    val lines: List<String> get() = solution.map { it.text }

    fun place(piece: Piece): ParsonsBoard = run(Move.Place(piece))

    fun remove(piece: Piece): ParsonsBoard = run(Move.Remove(piece, solution.indexOf(piece)))

    fun undo(): ParsonsBoard =
        history.lastOrNull()?.undo(this)?.copy(history = history.dropLast(1)) ?: this

    private fun run(move: Move): ParsonsBoard = move.execute(this).copy(history = history + move)

    companion object {
        fun start(exercise: Exercise.Parsons, random: Random = Random.Default): ParsonsBoard {
            val all = exercise.lines + exercise.distractors
            return ParsonsBoard(pool = all.mapIndexed { index, text -> Piece(index, text) }.shuffled(random))
        }
    }
}
