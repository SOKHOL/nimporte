package dev.devforge.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.evaluation.CodeReviewEvaluator
import dev.devforge.core.domain.evaluation.FillBlanksEvaluator
import dev.devforge.core.domain.evaluation.ReviewFlag
import dev.devforge.core.domain.evaluation.ScenarioWalker
import dev.devforge.core.domain.parsons.ParsonsBoard
import dev.devforge.core.domain.parsons.Piece
import dev.devforge.core.model.Choice
import dev.devforge.core.model.Exercise
import dev.devforge.core.model.ReviewCategories
import dev.devforge.ui.common.CodeBlock
import dev.devforge.ui.common.CodeLines
import dev.devforge.ui.common.LineMark
import dev.devforge.ui.common.MarkdownText
import dev.devforge.ui.common.MarkdownView
import dev.devforge.ui.common.SelectableCard
import dev.devforge.ui.common.Status
import dev.devforge.ui.theme.AppColors

/**
 * Affiche le bon composant selon le type d'exercice (Factory). Chaque composant remonte
 * sa réponse en cours via [onDraft] (null tant qu'elle est incomplète). Quand [revealed]
 * est fourni, l'exercice est corrigé : il affiche la réponse donnée et la solution.
 */
@Composable
fun ExerciseView(exercise: Exercise, revealed: Answer?, onDraft: (Answer?) -> Unit) {
    when (exercise) {
        is Exercise.Qcm -> ChoicesView(exercise.choices, exercise.multiple, revealed as? Answer.Choices, onDraft)
        is Exercise.PredictOutput ->
            if (exercise.choices.isNotEmpty()) {
                ChoicesView(exercise.choices, multiple = false, revealed = revealed as? Answer.Choices, onDraft = onDraft)
            } else {
                TextAnswerView(exercise.expectedOutput.orEmpty(), revealed as? Answer.Text, onDraft)
            }
        is Exercise.FindBug -> FindBugView(exercise, revealed as? Answer.Lines, onDraft)
        is Exercise.Parsons -> ParsonsView(exercise, revealed as? Answer.Ordering, onDraft)
        is Exercise.FillBlanks -> FillBlanksView(exercise, revealed as? Answer.Blanks, onDraft)
        is Exercise.Match -> MatchView(exercise, revealed as? Answer.Pairs, onDraft)
        is Exercise.CodeReview -> CodeReviewView(exercise, revealed as? Answer.Flags, onDraft)
        is Exercise.Scenario -> ScenarioView(exercise, revealed as? Answer.Path, onDraft)
        is Exercise.DesignChallenge -> DesignView(exercise, revealed as? Answer.SelfAssessment, onDraft)
        is Exercise.Code -> Text("Ce type d'exercice arrivera avec le serveur d'exécution de code (phase 3).")
    }
}

@Composable
private fun Instruction(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// --- QCM et prédiction de sortie ------------------------------------------------

@Composable
private fun ChoicesView(choices: List<Choice>, multiple: Boolean, revealed: Answer.Choices?, onDraft: (Answer?) -> Unit) {
    val order = remember { choices.shuffled() }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val shown = revealed?.ids ?: selected

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Instruction(if (multiple) "Plusieurs réponses possibles" else "Une seule réponse")
        order.forEach { choice ->
            val isSelected = choice.id in shown
            val status = when {
                revealed == null -> null
                choice.correct -> Status.GOOD
                isSelected -> Status.BAD
                else -> null
            }
            SelectableCard(
                selected = isSelected,
                status = status,
                enabled = revealed == null,
                onClick = {
                    selected = when {
                        !multiple -> setOf(choice.id)
                        isSelected -> selected - choice.id
                        else -> selected + choice.id
                    }
                    onDraft(if (selected.isEmpty()) null else Answer.Choices(selected))
                },
            ) {
                MarkdownText(choice.text)
                val feedback = choice.feedback
                if (revealed != null && feedback != null && (isSelected || choice.correct)) {
                    MarkdownText(feedback, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TextAnswerView(expected: String, revealed: Answer.Text?, onDraft: (Answer?) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = revealed?.value ?: text,
            onValueChange = {
                text = it
                onDraft(if (it.isBlank()) null else Answer.Text(it))
            },
            enabled = revealed == null,
            label = { Text("Sortie attendue") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        if (revealed != null) {
            Text("Sortie réelle :", fontWeight = FontWeight.Bold)
            CodeBlock(expected, "text")
        }
    }
}

// --- Trouver le bug ---------------------------------------------------------------

@Composable
private fun FindBugView(exercise: Exercise.FindBug, revealed: Answer.Lines?, onDraft: (Answer?) -> Unit) {
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val shown = revealed?.numbers ?: selected
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Instruction("Touche la ligne fautive")
        CodeLines(
            code = exercise.code.orEmpty(),
            language = exercise.language,
            mark = { n ->
                when {
                    revealed != null && n in exercise.bugLines -> LineMark.GOOD
                    revealed != null && n in shown -> LineMark.BAD
                    n in shown -> LineMark.SELECTED
                    else -> null
                }
            },
            onLineClick = if (revealed == null) {
                { n ->
                    selected = if (n in selected) selected - n else selected + n
                    onDraft(if (selected.isEmpty()) null else Answer.Lines(selected))
                }
            } else {
                null
            },
        )
    }
}

// --- Parsons ------------------------------------------------------------------------

@Composable
private fun ParsonsView(exercise: Exercise.Parsons, revealed: Answer.Ordering?, onDraft: (Answer?) -> Unit) {
    var board by remember { mutableStateOf(ParsonsBoard.start(exercise)) }

    fun change(newBoard: ParsonsBoard) {
        board = newBoard
        onDraft(if (newBoard.solution.isEmpty()) null else Answer.Ordering(newBoard.lines))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Instruction("Ta solution — touche une ligne pour la retirer")
        val solution = revealed?.lines ?: board.lines
        Surface(shape = RoundedCornerShape(8.dp), color = AppColors.codeBackground, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (solution.isEmpty()) {
                    Text("(vide)", color = AppColors.codeComment, fontFamily = FontFamily.Monospace)
                }
                solution.forEachIndexed { index, line ->
                    val status = when {
                        revealed == null -> null
                        exercise.lines.getOrNull(index) == line -> AppColors.goodBackground
                        else -> AppColors.badBackground
                    }
                    val piece: Piece? = if (revealed == null) board.solution.getOrNull(index) else null
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.codeText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = piece != null) { piece?.let { change(board.remove(it)) } }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            background = status ?: androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }
            }
        }
        if (revealed == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { change(board.undo()) }, enabled = board.canUndo) { Text("↶ Annuler") }
            }
            Instruction("Lignes disponibles — touche pour ajouter")
            board.pool.forEach { piece ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { change(board.place(piece)) },
                ) {
                    Text(
                        piece.text.ifBlank { "(ligne vide)" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        } else if (revealed.lines != exercise.lines) {
            Text("Solution :", fontWeight = FontWeight.Bold)
            CodeBlock(exercise.lines.joinToString("\n"), exercise.language)
        }
    }
}

// --- Code à trous -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillBlanksView(exercise: Exercise.FillBlanks, revealed: Answer.Blanks?, onDraft: (Answer?) -> Unit) {
    val keys = remember { exercise.blanks.keys.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE } }
    val options = remember { exercise.blanks.mapValues { it.value.options.shuffled() } }
    val values = remember { mutableStateMapOf<String, String>() }
    val shown: Map<String, String> = revealed?.values ?: values

    fun report() = onDraft(if (keys.all { !values[it].isNullOrBlank() }) Answer.Blanks(values.toMap()) else null)

    val code = keys.fold(exercise.code.orEmpty()) { acc, key -> acc.replace("{{$key}}", shown[key] ?: "［$key］") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CodeBlock(code, exercise.language)
        keys.forEach { key ->
            val value = shown[key]
            val good = revealed != null && FillBlanksEvaluator.isBlankCorrect(exercise, key, value)
            Text(
                "Trou $key" + when {
                    revealed == null -> ""
                    good -> " ✓"
                    else -> " ✗ — réponse : ${exercise.blanks[key]?.answers?.firstOrNull().orEmpty()}"
                },
                fontWeight = FontWeight.Bold,
                color = when {
                    revealed == null -> MaterialTheme.colorScheme.onSurface
                    good -> AppColors.good
                    else -> AppColors.bad
                },
            )
            val choices = options[key].orEmpty()
            if (choices.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { option ->
                        FilterChip(
                            selected = value == option,
                            enabled = revealed == null,
                            onClick = {
                                values[key] = option
                                report()
                            },
                            label = { Text(option, fontFamily = FontFamily.Monospace) },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = value.orEmpty(),
                    onValueChange = {
                        values[key] = it
                        report()
                    },
                    enabled = revealed == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --- Relier --------------------------------------------------------------------------

@Composable
private fun MatchView(exercise: Exercise.Match, revealed: Answer.Pairs?, onDraft: (Answer?) -> Unit) {
    val rights = remember { exercise.pairs.map { it.right }.shuffled() }
    val assignment = remember { mutableStateMapOf<String, String>() }
    var selectedLeft by remember { mutableStateOf<String?>(null) }
    val shown: Map<String, String> = revealed?.rightByLeft ?: assignment
    val lefts = exercise.pairs.map { it.left }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Instruction(if (revealed == null) "Touche un élément, puis sa correspondance plus bas" else "Correction")
        lefts.forEachIndexed { index, left ->
            val assigned = shown[left]
            val expected = exercise.pairs.first { it.left == left }.right
            val status = when {
                revealed == null -> null
                assigned == expected -> Status.GOOD
                else -> Status.BAD
            }
            SelectableCard(
                selected = selectedLeft == left,
                status = status,
                enabled = revealed == null,
                onClick = { selectedLeft = if (selectedLeft == left) null else left },
            ) {
                Text("${index + 1}. $left", fontWeight = FontWeight.Bold)
                Text(
                    assigned ?: "— à relier —",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (assigned == null) FontStyle.Italic else FontStyle.Normal,
                )
                if (status == Status.BAD) {
                    Text("Bonne réponse : $expected", style = MaterialTheme.typography.bodySmall, color = AppColors.good)
                }
            }
        }
        if (revealed == null) {
            Instruction("Correspondances")
            rights.forEach { right ->
                val owner = assignment.entries.firstOrNull { it.value == right }?.key
                SelectableCard(
                    selected = false,
                    status = null,
                    enabled = selectedLeft != null,
                    onClick = {
                        selectedLeft?.let { left ->
                            assignment.keys.filter { assignment[it] == right }.forEach { assignment.remove(it) }
                            assignment[left] = right
                            selectedLeft = null
                            onDraft(if (assignment.size == lefts.size) Answer.Pairs(assignment.toMap()) else null)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (owner != null) {
                            Text("${lefts.indexOf(owner) + 1} → ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(right, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// --- Revue de code -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeReviewView(exercise: Exercise.CodeReview, revealed: Answer.Flags?, onDraft: (Answer?) -> Unit) {
    val flags = remember { mutableStateListOf<ReviewFlag>() }
    var selectedLine by remember { mutableStateOf<Int?>(null) }
    val shown: Set<ReviewFlag> = revealed?.flags ?: flags.toSet()
    val issueLines = exercise.issues.flatMap { it.lines }.toSet()

    fun report() = onDraft(if (flags.isEmpty()) null else Answer.Flags(flags.toSet()))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Instruction("Touche une ligne, puis choisis la catégorie du problème (au moins ${exercise.required} à trouver)")
        CodeLines(
            code = exercise.code.orEmpty(),
            language = exercise.language,
            mark = { n ->
                when {
                    revealed != null && n in issueLines -> LineMark.FLAGGED
                    n == selectedLine -> LineMark.SELECTED
                    shown.any { it.line == n } -> LineMark.FLAGGED
                    else -> null
                }
            },
            onLineClick = if (revealed == null) ({ n -> selectedLine = n }) else null,
        )
        val line = selectedLine
        if (revealed == null && line != null) {
            Text("Ligne $line : quel type de problème ?", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewCategories.labels.forEach { (id, label) ->
                    FilterChip(
                        selected = ReviewFlag(line, id) in flags,
                        onClick = {
                            val flag = ReviewFlag(line, id)
                            if (flag in flags) flags.remove(flag) else flags.add(flag)
                            selectedLine = null
                            report()
                        },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (revealed == null && flags.isNotEmpty()) {
            Instruction("Tes remarques (touche pour retirer)")
            flags.sortedBy { it.line }.forEach { flag ->
                Text(
                    "⚑ Ligne ${flag.line} · ${ReviewCategories.labels[flag.category] ?: flag.category}",
                    modifier = Modifier.fillMaxWidth().clickable {
                        flags.remove(flag)
                        report()
                    }.padding(vertical = 4.dp),
                )
            }
        }
        if (revealed != null) {
            val found = CodeReviewEvaluator.foundIssues(exercise, revealed.flags)
            Text("Les problèmes de ce code :", fontWeight = FontWeight.Bold)
            exercise.issues.forEachIndexed { index, issue ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (index in found) AppColors.goodBackground else AppColors.badBackground,
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            (if (index in found) "✓ Trouvé · " else "✗ Manqué · ") +
                                "ligne(s) ${issue.lines.joinToString()} · ${ReviewCategories.labels[issue.category] ?: issue.category}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        MarkdownView(issue.description)
                    }
                }
            }
        }
    }
}

// --- Scénario ------------------------------------------------------------------------

@Composable
private fun ScenarioView(exercise: Exercise.Scenario, revealed: Answer.Path?, onDraft: (Answer?) -> Unit) {
    val path = remember { mutableStateListOf<Int>() }
    val indexes: List<Int> = revealed?.optionIndexes ?: path.toList()
    val run = ScenarioWalker.walk(exercise, indexes)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        run.chosen.forEachIndexed { step, option ->
            val node = exercise.nodes[run.visited[step]] ?: return@forEachIndexed
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MarkdownView(node.text)
                    Text("➜ ${option.text}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    option.feedback?.let { MarkdownText(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        val current = exercise.nodes[run.currentNode]
        if (current != null) {
            if (current.ending == null) {
                MarkdownView(current.text)
                current.options.forEachIndexed { index, option ->
                    OutlinedButton(
                        onClick = {
                            path.add(index)
                            val next = ScenarioWalker.walk(exercise, path.toList())
                            onDraft(if (next.ending != null) Answer.Path(path.toList()) else null)
                        },
                        enabled = revealed == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MarkdownText(option.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (current.ending) {
                            "success" -> AppColors.goodBackground
                            "failure" -> AppColors.badBackground
                            else -> AppColors.warnBackground
                        },
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MarkdownView(current.text)
                        Text(
                            "Score : ${run.points}/${ScenarioWalker.maxPoints(exercise)} points",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (revealed == null) Instruction("Fin du scénario : valide pour voir le bilan.")
            }
        }
        if (revealed == null && path.isNotEmpty() && run.ending == null) {
            TextButton(onClick = { path.removeAt(path.lastIndex); onDraft(null) }) { Text("↶ Revenir au choix précédent") }
        }
    }
}

// --- Défi de conception --------------------------------------------------------------

@Composable
private fun DesignView(exercise: Exercise.DesignChallenge, revealed: Answer.SelfAssessment?, onDraft: (Answer?) -> Unit) {
    var ready by remember { mutableStateOf(false) }
    val checked = remember { mutableStateListOf<Int>() }
    val shown: Set<Int> = revealed?.checked ?: checked.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Exigences", fontWeight = FontWeight.Bold)
        exercise.requirements.forEach { MarkdownText("• $it", style = MaterialTheme.typography.bodyMedium) }
        if (exercise.constraints.isNotEmpty()) {
            Text("Contraintes", fontWeight = FontWeight.Bold)
            exercise.constraints.forEach { MarkdownText("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (!ready && revealed == null) {
            Text("Conçois ta solution (sur papier, avec un schéma), puis compare-la à la grille d'évaluation.")
            Button(onClick = {
                ready = true
                onDraft(Answer.SelfAssessment(emptySet()))
            }) { Text("J'ai ma solution") }
        } else {
            Text("Grille : coche ce que ta solution couvre", fontWeight = FontWeight.Bold)
            exercise.rubric.forEachIndexed { index, criterion ->
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = index in shown,
                        enabled = revealed == null,
                        onCheckedChange = { isChecked ->
                            if (isChecked) checked.add(index) else checked.remove(index)
                            onDraft(Answer.SelfAssessment(checked.toSet()))
                        },
                    )
                    Column(Modifier.padding(top = 12.dp)) {
                        MarkdownText("${criterion.criterion} (${criterion.points} pt)", style = MaterialTheme.typography.bodyMedium)
                        criterion.hint?.let {
                            Text("Indice : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (revealed != null) {
            Text("Solution de référence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MarkdownView(exercise.referenceSolution)
        }
    }
}
