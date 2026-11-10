package dev.devforge.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.devforge.core.markdown.Block
import dev.devforge.core.markdown.Inline
import dev.devforge.core.markdown.Markdown

@Composable
fun inlineText(inlines: List<Inline>): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(inlines, codeBackground, linkColor) {
        buildAnnotatedString {
            for (inline in inlines) {
                when (inline) {
                    is Inline.Text -> append(inline.text)
                    is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.text) }
                    is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.text) }
                    is Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(inline.text)
                    }
                    is Inline.Link -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(inline.text)
                    }
                }
            }
        }
    }
}

/** Une ligne de Markdown (gras, italique, code, liens), sans blocs. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val inlines = remember(text) { Markdown.parseInline(text.trim()) }
    Text(inlineText(inlines), modifier = modifier, style = style)
}

/** Un texte Markdown complet (titres, listes, tableaux, code…). */
@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { MarkdownBlock(it) }
    }
}

@Composable
fun MarkdownBlock(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            inlineText(block.text),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
        )
        is Block.Paragraph -> Text(inlineText(block.text), style = MaterialTheme.typography.bodyLarge)
        is Block.Code -> Column {
            if (block.language == "mermaid") {
                Text(
                    "Diagramme (Mermaid)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CodeBlock(block.code, block.language)
        }
        is Block.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(inlineText(item), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is Block.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text("${index + 1}.  ", style = MaterialTheme.typography.bodyLarge)
                    Text(inlineText(item), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is Block.Quote -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(inlineText(block.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        is Block.Table -> TableView(block)
        Block.Rule -> HorizontalDivider()
    }
}

@Composable
private fun TableView(table: Block.Table) {
    val columnWidth = if (table.header.size <= 2) 170.dp else 150.dp
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            TableRow(table.header, columnWidth, header = true)
            table.rows.forEach {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TableRow(it, columnWidth, header = false)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<List<Inline>>, columnWidth: androidx.compose.ui.unit.Dp, header: Boolean) {
    Row(
        Modifier.then(
            if (header) Modifier else Modifier,
        ),
    ) {
        cells.forEach { cell ->
            Text(
                inlineText(cell),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.primary else Color.Unspecified,
                modifier = Modifier.width(columnWidth).padding(6.dp),
            )
        }
    }
}
