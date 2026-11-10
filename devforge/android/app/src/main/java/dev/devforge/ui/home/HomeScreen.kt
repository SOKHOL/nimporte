package dev.devforge.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.devforge.core.domain.levels.LevelRules
import dev.devforge.core.domain.progress.displayed
import dev.devforge.core.domain.session.SessionBuilder
import dev.devforge.core.domain.session.SessionKind
import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress
import dev.devforge.core.model.TrackRef
import dev.devforge.ui.common.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    content: Content,
    progress: Progress,
    today: Long,
    onOpenTrack: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onStartSession: (SessionKind) -> Unit,
) {
    val rules = remember(content) { LevelRules(content) }
    val dueReviews = remember(content, progress, today) { SessionBuilder(content).dueReviews(progress, today).size }
    val domains = content.curriculum.domains

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DevForge", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Person, contentDescription = "Profil") }
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("🔥", "${progress.streak.displayed(today)} j", "série", Modifier.weight(1f))
                    StatCard("⭐", "${progress.xp}", "XP", Modifier.weight(1f))
                    StatCard("🏅", "${progress.validatedLevels.values.sumOf { it.size }}", "niveaux", Modifier.weight(1f))
                }
            }
            item {
                ActionCard(
                    title = "Défi du jour",
                    subtitle = if (progress.dailyDoneDay == today) "Réussi aujourd'hui ✓ — tu peux le refaire" else "Un exercice tiré parmi ceux que tu n'as pas encore réussis",
                    button = "Relever le défi",
                    onClick = { onStartSession(SessionKind.DAILY) },
                )
            }
            item {
                ActionCard(
                    title = "Révisions du jour",
                    subtitle = if (dueReviews == 0) "Rien à réviser aujourd'hui. Reviens demain !" else "$dueReviews exercice(s) à revoir avant de les oublier",
                    button = if (dueReviews == 0) null else "Réviser",
                    onClick = { onStartSession(SessionKind.REVIEW) },
                )
            }
            for (domain in domains) {
                val tracks = content.curriculum.tracks.filter { it.domain == domain.id }
                if (tracks.isEmpty()) continue
                item(key = "domain-${domain.id}") { SectionTitle(domain.title) }
                items(tracks, key = { it.id }) { track ->
                    TrackCard(track, content, progress, rules, onClick = { onOpenTrack(track.id) })
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, button: String?, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (button != null) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onClick) { Text(button) }
            }
        }
    }
}

@Composable
private fun TrackCard(track: TrackRef, content: Content, progress: Progress, rules: LevelRules, onClick: () -> Unit) {
    val (done, total) = rules.completion(track.id, progress)
    val lessons = content.lessonsOf(track.id).size
    val rank = progress.rank(track.id)
    val missing = rules.missingRequirements(track.id, progress)

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (rank > 0) {
                    Text(
                        "${content.rankName(rank)} · $rank/5",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (total > 0 || lessons > 0) {
                Text(
                    "$done/$total exercices réussis · $lessons leçon(s)",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val skills = content.tracks[track.id]?.levels?.sumOf { it.skills.size } ?: 0
                Text(
                    if (skills > 0) "Contenu à venir · $skills compétences au programme" else "Contenu à venir",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (missing.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        "Conseillé après : " + missing.joinToString { req ->
                            "${content.trackRef(req.trackId)?.title ?: req.trackId} niv. ${req.level}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
