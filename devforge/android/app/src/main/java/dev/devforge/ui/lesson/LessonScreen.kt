package dev.devforge.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.devforge.core.domain.session.SessionBuilder
import dev.devforge.core.markdown.Markdown
import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress
import dev.devforge.ui.common.MarkdownBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    content: Content,
    progress: Progress,
    lessonId: String,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onPractice: () -> Unit,
) {
    val lesson = content.lesson(lessonId)
    val blocks = remember(lesson) { lesson?.let { Markdown.parse(it.markdown) }.orEmpty() }
    val practice = remember(content, lessonId) { SessionBuilder(content).forLesson(lessonId, progress).exercises.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lesson?.meta?.title ?: "Leçon", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onRead()
                        if (practice > 0) onPractice() else onBack()
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Text(if (practice > 0) "J'ai compris, je m'entraîne ($practice exercices)" else "J'ai compris")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lesson == null) {
                item { Text("Leçon introuvable.") }
            } else {
                item {
                    Text(
                        "⏱ ${lesson.meta.durationMin} min · " + if (lessonId in progress.lessonsRead) "déjà lue ✓" else "nouvelle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(blocks) { block -> Column { MarkdownBlock(block) } }
            }
        }
    }
}
