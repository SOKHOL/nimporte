package dev.devforge.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.devforge.core.domain.progress.displayed
import dev.devforge.core.domain.stats.DomainScore
import dev.devforge.core.domain.stats.Stats
import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress
import dev.devforge.ui.common.SectionTitle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    content: Content,
    progress: Progress,
    today: Long,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    val stats = remember(content) { Stats(content) }
    val global = stats.global(progress)
    val domains = stats.byDomain(progress)
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⭐ ${progress.xp} XP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("🔥 Série : ${progress.streak.displayed(today)} jour(s) · record : ${progress.streak.best}")
                        Text("✅ Exercices réussis : ${global.exercisesSucceeded}/${global.exercisesTotal}")
                        Text("🎯 Taux de réussite : ${(global.successRate * 100).toInt()} % sur ${global.attempts} tentatives")
                        Text("📖 Leçons lues : ${global.lessonsRead}/${global.lessonsTotal}")
                        Text("🏅 Niveaux validés : ${global.levelsValidated}")
                    }
                }
            }
            item { SectionTitle("Radar de compétences") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Radar(domains, Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp))
                }
            }
            items(domains, key = { it.domainId }) { domain ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(domain.title, modifier = Modifier.weight(1f))
                        Text(if (domain.total == 0) "à venir" else "${domain.succeeded}/${domain.total}")
                    }
                    LinearProgressIndicator(progress = { domain.ratio.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Réinitialiser ma progression")
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Tout effacer ?") },
            text = { Text("XP, série, niveaux validés et révisions seront perdus. Cette action est définitive.") },
            confirmButton = {
                TextButton(onClick = { onReset(); confirmReset = false }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annuler") }
            },
        )
    }
}

/** Radar dessiné à la main : un axe par domaine, la surface montre la part d'exercices réussis. */
@Composable
private fun Radar(scores: List<DomainScore>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    if (scores.size < 3) return

    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 * 0.72f
        val count = scores.size
        fun point(index: Int, ratio: Float): Offset {
            val angle = -PI / 2 + 2 * PI * index / count
            return Offset(center.x + radius * ratio * cos(angle).toFloat(), center.y + radius * ratio * sin(angle).toFloat())
        }

        // grille : 4 polygones concentriques et les axes
        for (step in 1..4) {
            val path = Path()
            for (i in 0 until count) {
                val p = point(i, step / 4f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, grid, style = Stroke(width = 1.dp.toPx()))
        }
        for (i in 0 until count) drawLine(grid, center, point(i, 1f), strokeWidth = 1.dp.toPx())

        // valeurs (un minimum visible pour que la forme reste lisible)
        val values = Path()
        scores.forEachIndexed { i, score ->
            val p = point(i, maxOf(score.ratio.toFloat(), 0.03f))
            if (i == 0) values.moveTo(p.x, p.y) else values.lineTo(p.x, p.y)
        }
        values.close()
        drawPath(values, primary.copy(alpha = 0.3f))
        drawPath(values, primary, style = Stroke(width = 2.dp.toPx()))

        // libellés
        scores.forEachIndexed { i, score ->
            val layout = measurer.measure(score.title, TextStyle(fontSize = 11.sp, color = labelColor))
            val p = point(i, 1.22f)
            drawText(
                layout,
                topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f),
            )
        }
    }
}
