package dev.devforge.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.devforge.core.markdown.SyntaxHighlighter
import dev.devforge.core.markdown.TokenKind
import dev.devforge.ui.theme.AppColors

/** Marque visuelle d'une ligne de code. */
enum class LineMark { SELECTED, GOOD, BAD, FLAGGED }

fun highlight(code: String, language: String?): AnnotatedString = buildAnnotatedString {
    for (token in SyntaxHighlighter.tokenize(code, language)) {
        val color = when (token.kind) {
            TokenKind.KEYWORD -> AppColors.codeKeyword
            TokenKind.STRING -> AppColors.codeString
            TokenKind.COMMENT -> AppColors.codeComment
            TokenKind.NUMBER -> AppColors.codeNumber
            TokenKind.ANNOTATION -> AppColors.codeAnnotation
            TokenKind.PLAIN -> AppColors.codeText
        }
        withStyle(SpanStyle(color = color)) { append(token.text) }
    }
}

private val codeStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    color = AppColors.codeText,
)

/** Un bloc de code coloré, défilable horizontalement. */
@Composable
fun CodeBlock(code: String, language: String?, modifier: Modifier = Modifier) {
    val text = remember(code, language) { highlight(code.trimEnd('\n'), language) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.codeBackground,
    ) {
        Text(
            text = text,
            style = codeStyle,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

/** Du code ligne par ligne, avec numéros, chaque ligne pouvant être touchée et marquée. */
@Composable
fun CodeLines(
    code: String,
    language: String?,
    mark: (Int) -> LineMark?,
    onLineClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val lines = remember(code) { code.trimEnd('\n').lines() }
    val highlighted = remember(code, language) { lines.map { highlight(it, language) } }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.codeBackground,
    ) {
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .width(IntrinsicSize.Max)
                .padding(vertical = 8.dp),
        ) {
            lines.indices.forEach { index ->
                val number = index + 1
                val background = when (mark(number)) {
                    LineMark.SELECTED -> AppColors.selectedBackground
                    LineMark.GOOD -> AppColors.goodBackground
                    LineMark.BAD -> AppColors.badBackground
                    LineMark.FLAGGED -> AppColors.warnBackground
                    null -> Color.Transparent
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(background)
                        .then(if (onLineClick != null) Modifier.clickable { onLineClick(number) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = number.toString().padStart(2),
                        style = codeStyle.copy(color = AppColors.codeLineNumber),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = highlighted[index], style = codeStyle, softWrap = false)
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}
