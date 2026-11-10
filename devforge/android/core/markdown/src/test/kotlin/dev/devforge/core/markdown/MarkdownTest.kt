package dev.devforge.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownTest {
    @Test
    fun `titres, paragraphes et code`() {
        val blocks = Markdown.parse(
            """
            # Titre

            Un **mot** et du `code`,
            sur deux lignes.

            ```kotlin
            val x = 1
            ```
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        assertEquals(Block.Heading(1, listOf(Inline.Text("Titre"))), blocks[0])
        val paragraph = assertIs<Block.Paragraph>(blocks[1])
        assertEquals(
            listOf(Inline.Text("Un "), Inline.Bold("mot"), Inline.Text(" et du "), Inline.Code("code"), Inline.Text(", sur deux lignes.")),
            paragraph.text,
        )
        assertEquals(Block.Code("kotlin", "val x = 1"), blocks[2])
    }

    @Test
    fun `listes avec continuation`() {
        val blocks = Markdown.parse("- un\n  suite\n- deux\n\n1. premier\n2. second")
        val bullets = assertIs<Block.BulletList>(blocks[0])
        assertEquals(listOf(listOf(Inline.Text("un suite")), listOf(Inline.Text("deux"))), bullets.items)
        assertEquals(2, assertIs<Block.OrderedList>(blocks[1]).items.size)
    }

    @Test
    fun `tableau avec une barre verticale dans du code`() {
        val blocks = Markdown.parse("| A | B |\n|---|---|\n| `a | b` | c |")
        val table = assertIs<Block.Table>(blocks.single())
        assertEquals(2, table.header.size)
        assertEquals(listOf(Inline.Code("a | b")), table.rows.single()[0])
    }

    @Test
    fun `italique et liens, mais pas les multiplications`() {
        assertEquals(
            listOf(Inline.Text("a * b et "), Inline.Italic("mot"), Inline.Text(" "), Inline.Link("doc", "https://x.y")),
            Markdown.parseInline("a * b et *mot* [doc](https://x.y)"),
        )
    }

    @Test
    fun `citations et séparateurs`() {
        val blocks = Markdown.parse("> une\n> citation\n\n---\n\ntexte")
        assertEquals(Block.Quote(listOf(Inline.Text("une citation"))), blocks[0])
        assertEquals(Block.Rule, blocks[1])
    }

    @Test
    fun `coloration syntaxique kotlin`() {
        val tokens = SyntaxHighlighter.tokenize("val x = \"a\" // note\n@Volatile var n = 42", "kotlin")
        assertTrue(Token("val", TokenKind.KEYWORD) in tokens)
        assertTrue(Token("\"a\"", TokenKind.STRING) in tokens)
        assertTrue(Token("// note", TokenKind.COMMENT) in tokens)
        assertTrue(Token("@Volatile", TokenKind.ANNOTATION) in tokens)
        assertTrue(Token("42", TokenKind.NUMBER) in tokens)
        assertEquals("val x = \"a\" // note\n@Volatile var n = 42", tokens.joinToString("") { it.text })
    }

    @Test
    fun `coloration syntaxique rust et python`() {
        assertTrue(Token("fn", TokenKind.KEYWORD) in SyntaxHighlighter.tokenize("fn main() {}", "rust"))
        val python = SyntaxHighlighter.tokenize("def f(): # note", "python")
        assertTrue(Token("def", TokenKind.KEYWORD) in python)
        assertEquals(Token("# note", TokenKind.COMMENT), python.last())
    }

    @Test
    fun `coloration syntaxique dockerfile`() {
        val tokens = SyntaxHighlighter.tokenize("# build\nFROM gradle:8 AS build", "dockerfile")
        assertEquals(Token("# build", TokenKind.COMMENT), tokens.first())
        assertTrue(Token("FROM", TokenKind.KEYWORD) in tokens)
        assertTrue(Token("AS", TokenKind.KEYWORD) in tokens)
    }
}
