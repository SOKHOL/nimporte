package dev.devforge.ui.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.devforge.core.domain.levels.LevelRules
import dev.devforge.core.model.Content
import dev.devforge.core.model.Level
import dev.devforge.core.model.Progress
import dev.devforge.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    content: Content,
    progress: Progress,
    trackId: String,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenTrack: (String) -> Unit,
    onTrain: (String, Int) -> Unit,
    onBoss: (String, Int) -> Unit,
) {
    val ref = content.trackRef(trackId)
    val detail = content.tracks[trackId]
    val rules = remember(content) { LevelRules(content) }
    val missing = rules.missingRequirements(trackId, progress)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ref?.title ?: trackId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(detail?.description ?: "Ce parcours n'est pas encore rédigé.")
            }
            if (missing.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔒 Prérequis conseillés", fontWeight = FontWeight.Bold)
                            missing.forEach { req ->
                                Text(
                                    "• ${content.trackRef(req.trackId)?.title ?: req.trackId} — niveau ${req.level}",
                                    modifier = Modifier.clickable { onOpenTrack(req.trackId) },
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text("Tu peux quand même commencer : c'est un conseil, pas un verrou.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (detail != null) {
                items(detail.levels, key = { it.level }) { level ->
                    LevelCard(content, progress, rules, trackId, level, onOpenLesson, onTrain, onBoss)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelCard(
    content: Content,
    progress: Progress,
    rules: LevelRules,
    trackId: String,
    level: Level,
    onOpenLesson: (String) -> Unit,
    onTrain: (String, Int) -> Unit,
    onBoss: (String, Int) -> Unit,
) {
    val validated = level.level in progress.validatedLevels[trackId].orEmpty()
    val (done, total) = rules.completion(trackId, progress, level.level)
    val lessons = content.lessonsOf(trackId, level.level)
    val boss = level.boss
    val bossRecord = boss?.let { progress.bosses[it.id] }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Niveau ${level.level} · ${content.rankName(level.level)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(level.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (validated) Text("✓ Validé", color = AppColors.good, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                level.skills.forEach { skill ->
                    SuggestionChip(onClick = {}, label = { Text(skill.title, style = MaterialTheme.typography.labelSmall) })
                }
            }
            lessons.forEach { lesson ->
                val read = lesson.meta.id in progress.lessonsRead
                Text(
                    (if (read) "✅ " else "📖 ") + "${lesson.meta.title} · ${lesson.meta.durationMin} min",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenLesson(lesson.meta.id) }.padding(vertical = 6.dp),
                )
            }
            if (total > 0) {
                Text("$done/$total exercices réussis", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onTrain(trackId, level.level) }) { Text("S'entraîner") }
                    if (boss != null) {
                        OutlinedButton(onClick = { onBoss(trackId, level.level) }) { Text("⚔️ Boss") }
                    }
                }
                if (boss != null) {
                    Text(
                        when {
                            bossRecord?.passed == true -> "Boss vaincu ✓"
                            bossRecord != null -> "Meilleur score : ${(bossRecord.bestRatio * 100).toInt()} % (il faut ${(boss.passRatio * 100).toInt()} %)"
                            else -> "${boss.title} — ${boss.exercises.size} questions" + (boss.timeLimitMin?.let { " en $it min" } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Niveau validé à 80 % d'exercices réussis.", style = MaterialTheme.typography.bodySmall)
                }
            } else if (lessons.isEmpty()) {
                Text("Contenu à venir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
