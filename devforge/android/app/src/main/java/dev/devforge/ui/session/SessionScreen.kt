package dev.devforge.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.devforge.AppContainer
import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.evaluation.Verdict
import dev.devforge.core.domain.session.SessionKind
import dev.devforge.core.domain.session.SessionState
import dev.devforge.core.model.Exercise
import dev.devforge.core.model.PlacedExercise
import dev.devforge.ui.common.CodeBlock
import dev.devforge.ui.common.Loading
import dev.devforge.ui.common.MarkdownView
import dev.devforge.ui.common.stars
import dev.devforge.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    container: AppContainer,
    request: SessionRequest,
    routeKey: String,
    onClose: () -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(
        key = routeKey,
        factory = viewModelFactory { initializer { SessionViewModel(container, request) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state?.plan?.title ?: "Session", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Quitter") }
                },
                actions = {
                    val seconds = remaining
                    if (seconds != null && state !is SessionState.Finished) {
                        Text(
                            "⏱ %d:%02d".format(seconds / 60, seconds % 60),
                            color = if (seconds < 60) AppColors.bad else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                null -> Loading("Préparation de la session…")
                is SessionState.Answering -> ExerciseStep(
                    placed = current.current,
                    index = current.index,
                    total = current.plan.exercises.size,
                    feedback = null,
                    onSubmit = viewModel::submit,
                    onNext = {},
                )
                is SessionState.Feedback -> ExerciseStep(
                    placed = current.current,
                    index = current.index,
                    total = current.plan.exercises.size,
                    feedback = FeedbackData(current.answer, current.last.verdict, current.last.xp, current.isLast),
                    onSubmit = { _, _ -> },
                    onNext = viewModel::next,
                )
                is SessionState.Finished -> Summary(current, onClose)
            }
        }
    }
}

data class FeedbackData(val answer: Answer, val verdict: Verdict, val xp: Int, val isLast: Boolean)

@Composable
private fun ExerciseStep(
    placed: PlacedExercise,
    index: Int,
    total: Int,
    feedback: FeedbackData?,
    onSubmit: (Answer, Int) -> Unit,
    onNext: () -> Unit,
) {
    val exercise = placed.exercise
    // Tout l'état local (brouillon de réponse, indices) repart de zéro à chaque exercice.
    key(exercise.id) {
        var draft by remember { mutableStateOf<Answer?>(null) }
        var hintsShown by remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        LaunchedEffect(feedback != null) {
            if (feedback != null) scroll.animateScrollTo(scroll.maxValue)
        }

        Column(Modifier.fillMaxSize()) {
            LinearProgressIndicator(
                progress = { (index + if (feedback != null) 1 else 0).toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Exercice ${index + 1}/$total · ${stars(exercise.difficulty)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(exercise.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                MarkdownView(exercise.prompt)
                val code = exercise.code
                if (code != null && showsCodeAbove(exercise)) CodeBlock(code, exercise.language)

                ExerciseView(exercise, revealed = feedback?.answer, onDraft = { draft = it })

                if (exercise.hints.isNotEmpty()) {
                    exercise.hints.take(hintsShown).forEach { hint ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Text("💡 $hint", modifier = Modifier.padding(12.dp))
                        }
                    }
                    if (feedback == null && hintsShown < exercise.hints.size) {
                        TextButton(onClick = { hintsShown++ }) {
                            Text("💡 Indice (${exercise.hints.size - hintsShown} restant(s), −25 % d'XP)")
                        }
                    }
                }

                if (feedback != null) FeedbackCard(exercise, feedback)
                Spacer(Modifier.height(8.dp))
            }
            Surface(tonalElevation = 3.dp) {
                val answer = draft
                Button(
                    onClick = {
                        if (feedback != null) onNext() else if (answer != null) onSubmit(answer, hintsShown)
                    },
                    enabled = feedback != null || answer != null,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Text(
                        when {
                            feedback == null -> "Valider"
                            feedback.isLast -> "Voir le résultat"
                            else -> "Continuer"
                        },
                    )
                }
            }
        }
    }
}

/** Les types qui affichent eux-mêmes le code (lignes cliquables, trous) n'ont pas de bloc au-dessus. */
private fun showsCodeAbove(exercise: Exercise): Boolean =
    exercise !is Exercise.FindBug && exercise !is Exercise.CodeReview && exercise !is Exercise.FillBlanks

@Composable
private fun FeedbackCard(exercise: Exercise, feedback: FeedbackData) {
    val correct = feedback.verdict.correct
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (correct) AppColors.goodBackground else AppColors.badBackground,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (correct) "✅ Bravo !" else "❌ Pas tout à fait",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("+${feedback.xp} XP", fontWeight = FontWeight.Bold)
            }
            if (!correct && feedback.verdict.score > 0) {
                Text("Score partiel : ${(feedback.verdict.score * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
            }
            MarkdownView(exercise.explanation)
        }
    }
}

@Composable
private fun Summary(state: SessionState.Finished, onClose: () -> Unit) {
    val outcome = state.outcome
    val ratio = if (outcome.total == 0) 0.0 else outcome.correct.toDouble() / outcome.total
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (outcome.total == 0) {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
            Text(
                if (state.plan.kind == SessionKind.REVIEW) "Rien à réviser pour l'instant." else "Aucun exercice disponible ici pour l'instant.",
                textAlign = TextAlign.Center,
            )
        } else {
            val emoji = when {
                outcome.bossPassed == true -> "🏆"
                outcome.bossPassed == false -> "⚔️"
                ratio >= 0.8 -> "🎉"
                ratio >= 0.5 -> "💪"
                else -> "📚"
            }
            Text(emoji, style = MaterialTheme.typography.displayMedium)
            Text(
                "${outcome.correct}/${outcome.total} réussis",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("+${outcome.xp} XP", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            val message = when {
                outcome.bossPassed == true -> "Boss vaincu ! Le niveau est validé."
                outcome.timeUp -> "Temps écoulé… Le boss t'attend pour une revanche."
                outcome.bossPassed == false -> "Pas cette fois. Révise les exercices ratés, puis retente ta chance."
                ratio >= 0.8 -> "Excellent travail."
                ratio >= 0.5 -> "Bien joué. Les exercices ratés reviendront dans tes révisions."
                else -> "Relis la leçon, puis réessaie : c'est comme ça qu'on progresse."
            }
            Text(message, textAlign = TextAlign.Center)
        }
        Button(onClick = onClose) { Text("Terminer") }
    }
}
