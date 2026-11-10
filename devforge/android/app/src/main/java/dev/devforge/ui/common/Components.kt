package dev.devforge.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.devforge.ui.theme.AppColors

/** Statut affiché après correction. */
enum class Status { GOOD, BAD }

/** Une carte qu'on touche pour la sélectionner ; après correction, elle prend la couleur du statut. */
@Composable
fun SelectableCard(
    selected: Boolean,
    status: Status?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = when {
        status == Status.GOOD -> AppColors.good
        status == Status.BAD -> AppColors.bad
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val background = when {
        status == Status.GOOD -> AppColors.goodBackground
        status == Status.BAD -> AppColors.badBackground
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected || status != null) 2.dp else 1.dp, borderColor),
        color = background,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
fun Loading(message: String = "Chargement…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Impossible de charger le contenu", style = MaterialTheme.typography.titleMedium)
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

fun stars(difficulty: Int): String = "★".repeat(difficulty.coerceIn(0, 5)) + "☆".repeat((5 - difficulty).coerceIn(0, 5))
