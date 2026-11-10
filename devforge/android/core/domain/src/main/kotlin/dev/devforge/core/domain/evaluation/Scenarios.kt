package dev.devforge.core.domain.evaluation

import dev.devforge.core.model.Exercise
import dev.devforge.core.model.ScenarioOption

/** Le parcours d'un utilisateur dans un scénario. */
data class ScenarioRun(
    val visited: List<String>,
    val chosen: List<ScenarioOption>,
    val points: Int,
    val ending: String?,
) {
    val currentNode: String get() = visited.last()
}

object ScenarioWalker {
    /** Rejoue les choix (index d'option à chaque nœud) depuis le départ. */
    fun walk(exercise: Exercise.Scenario, optionIndexes: List<Int>): ScenarioRun {
        val visited = mutableListOf(exercise.start)
        val chosen = mutableListOf<ScenarioOption>()
        var points = 0
        for (index in optionIndexes) {
            val node = exercise.nodes[visited.last()] ?: break
            val option = node.options.getOrNull(index) ?: break
            chosen += option
            points += option.points
            visited += option.next
        }
        val ending = exercise.nodes[visited.last()]?.ending
        return ScenarioRun(visited, chosen, points, ending)
    }

    /** Meilleur score possible depuis le départ (le graphe est sans cycle, vérifié en CI). */
    fun maxPoints(exercise: Exercise.Scenario): Int {
        val memo = HashMap<String, Int>()
        fun best(id: String, depth: Int): Int {
            memo[id]?.let { return it }
            val node = exercise.nodes[id] ?: return 0
            if (node.ending != null || depth > exercise.nodes.size) return 0
            val value = node.options.maxOfOrNull { it.points + best(it.next, depth + 1) } ?: 0
            memo[id] = value
            return value
        }
        return best(exercise.start, 0)
    }

    /** Les index d'options du meilleur chemin. */
    fun bestPath(exercise: Exercise.Scenario): List<Int> {
        val path = mutableListOf<Int>()
        var id = exercise.start
        repeat(exercise.nodes.size) {
            val node = exercise.nodes[id] ?: return path
            if (node.ending != null) return path
            val index = node.options.indices.maxByOrNull { i ->
                val option = node.options[i]
                val sub = exercise.copy(start = option.next)
                option.points + maxPoints(sub)
            } ?: return path
            path += index
            id = node.options[index].next
        }
        return path
    }
}
