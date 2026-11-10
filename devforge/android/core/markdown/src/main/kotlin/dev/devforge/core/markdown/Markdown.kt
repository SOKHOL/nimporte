package dev.devforge.core.markdown

/** Bloc de texte Markdown. Seul le sous-ensemble utilisé par les leçons est géré. */
sealed interface Block {
    data class Heading(val level: Int, val text: List<Inline>) : Block
    data class Paragraph(val text: List<Inline>) : Block
    data class Code(val language: String?, val code: String) : Block
    data class BulletList(val items: List<List<Inline>>) : Block
    data class OrderedList(val items: List<List<Inline>>) : Block
    data class Quote(val text: List<Inline>) : Block
    data class Table(val header: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block
    data object Rule : Block
}

/** Élément de texte dans une ligne. */
sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Bold(val text: String) : Inline
    data class Italic(val text: String) : Inline
    data class Code(val text: String) : Inline
    data class Link(val text: String, val url: String) : Inline
}

object Markdown {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^\\s{0,3}[-*]\\s+(.*)$")
    private val ordered = Regex("^\\s{0,3}\\d+\\.\\s+(.*)$")
    private val tableSeparator = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")
    private val rule = Regex("^\\s{0,3}(-{3,}|\\*{3,})\\s*$")

    fun parse(source: String): List<Block> {
        val lines = source.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        var i = 0

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(parseInline(paragraph.joinToString(" ") { it.trim() }))
                paragraph.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val language = trimmed.removePrefix("```").trim().ifEmpty { null }
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code += lines[i]
                        i++
                    }
                    blocks += Block.Code(language, code.joinToString("\n"))
                }
                trimmed.isEmpty() -> flushParagraph()
                heading.matches(trimmed) -> {
                    flushParagraph()
                    val match = heading.find(trimmed)!!
                    blocks += Block.Heading(match.groupValues[1].length, parseInline(match.groupValues[2]))
                }
                rule.matches(line) -> {
                    flushParagraph()
                    blocks += Block.Rule
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val quote = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quote += lines[i].trim().removePrefix(">").trim()
                        i++
                    }
                    blocks += Block.Quote(parseInline(quote.joinToString(" ")))
                    continue
                }
                trimmed.startsWith("|") && i + 1 < lines.size && tableSeparator.matches(lines[i + 1].trim()) -> {
                    flushParagraph()
                    val header = cells(trimmed)
                    i += 2
                    val rows = mutableListOf<List<List<Inline>>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        rows += cells(lines[i].trim())
                        i++
                    }
                    blocks += Block.Table(header, rows)
                    continue
                }
                bullet.matches(line) -> {
                    flushParagraph()
                    blocks += Block.BulletList(listItems(lines, i, bullet).also { i = it.second }.first)
                    continue
                }
                ordered.matches(line) -> {
                    flushParagraph()
                    blocks += Block.OrderedList(listItems(lines, i, ordered).also { i = it.second }.first)
                    continue
                }
                else -> paragraph += line
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    /** Lit des éléments de liste consécutifs ; renvoie les éléments et l'index de la ligne suivante. */
    private fun listItems(lines: List<String>, start: Int, marker: Regex): Pair<List<List<Inline>>, Int> {
        val items = mutableListOf<String>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            val match = marker.find(line)
            when {
                match != null && marker.matches(line) -> items += match.groupValues[1].trim()
                line.isNotBlank() && line.startsWith("  ") && items.isNotEmpty() ->
                    items[items.lastIndex] = items.last() + " " + line.trim()
                else -> break
            }
            i++
        }
        return items.map { parseInline(it) } to i
    }

    private fun cells(row: String): List<List<Inline>> =
        splitCells(row.trim().removePrefix("|").removeSuffix("|")).map { parseInline(it.trim()) }

    /** Découpe une ligne de tableau sur les `|`, sauf ceux qui sont dans du code (`a | b`). */
    private fun splitCells(row: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inCode = false
        for (c in row) {
            when {
                c == '`' -> { inCode = !inCode; current.append(c) }
                c == '|' && !inCode -> { cells += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        cells += current.toString()
        return cells
    }

    fun parseInline(text: String): List<Inline> {
        val result = mutableListOf<Inline>()
        val buffer = StringBuilder()
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                result += Inline.Text(buffer.toString())
                buffer.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        result += Inline.Code(text.substring(i + 1, end))
                        i = end + 1
                        continue
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flush()
                        result += Inline.Bold(text.substring(i + 2, end).replace("`", ""))
                        i = end + 2
                        continue
                    }
                }
                c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1 && !text[end - 1].isWhitespace()) {
                        flush()
                        result += Inline.Italic(text.substring(i + 1, end))
                        i = end + 1
                        continue
                    }
                }
                c == '[' -> {
                    val close = text.indexOf("](", i + 1)
                    val end = if (close > 0) text.indexOf(')', close + 2) else -1
                    if (close > i && end > close) {
                        flush()
                        result += Inline.Link(text.substring(i + 1, close), text.substring(close + 2, end))
                        i = end + 1
                        continue
                    }
                }
            }
            buffer.append(c)
            i++
        }
        flush()
        return result
    }
}
