package dev.devforge.core.markdown

enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION, PLAIN }

data class Token(val text: String, val kind: TokenKind)

/**
 * Coloration syntaxique minimale : mots-clés, chaînes, commentaires, nombres, annotations.
 * Volontairement simple : elle sert à lire du code sur un téléphone, pas à le compiler.
 */
object SyntaxHighlighter {
    private val kotlinKeywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum", "open",
        "abstract", "override", "private", "protected", "internal", "public", "return", "if",
        "else", "when", "for", "while", "do", "in", "is", "as", "null", "true", "false", "this",
        "super", "import", "package", "try", "catch", "finally", "throw", "companion", "by",
        "suspend", "inline", "typealias", "init", "constructor", "lateinit", "const", "until",
        "static", "final", "new", "void", "int", "long", "boolean", "extends", "implements",
    )
    private val shellKeywords = setOf(
        "FROM", "RUN", "COPY", "ADD", "WORKDIR", "ENV", "ARG", "USER", "CMD", "ENTRYPOINT",
        "EXPOSE", "AS", "LABEL", "VOLUME", "HEALTHCHECK", "if", "then", "else", "fi", "for",
        "do", "done", "echo", "export", "java", "kubectl", "docker", "git",
    )

    private val cKeywords = setOf(
        "int", "char", "void", "long", "short", "unsigned", "signed", "float", "double", "struct",
        "const", "static", "return", "if", "else", "for", "while", "do", "sizeof", "NULL",
        "typedef", "enum", "switch", "case", "break", "continue", "free", "malloc",
    )
    private val rustKeywords = setOf(
        "fn", "let", "mut", "impl", "struct", "enum", "trait", "match", "pub", "use", "mod",
        "self", "Self", "for", "in", "if", "else", "loop", "while", "return", "move", "ref",
        "as", "where", "true", "false", "unsafe", "dyn", "Box", "Rc", "Vec", "String",
    )
    private val pythonKeywords = setOf(
        "def", "class", "import", "from", "return", "if", "elif", "else", "for", "in", "while",
        "None", "True", "False", "del", "pass", "with", "as", "lambda", "not", "and", "or", "is",
        "try", "except", "finally", "raise", "yield",
    )

    private val javascriptKeywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "of", "in",
        "this", "new", "class", "extends", "super", "true", "false", "null", "undefined",
        "async", "await", "import", "export", "from", "typeof", "instanceof",
    )

    fun tokenize(code: String, language: String?): List<Token> {
        val hashComments = language in setOf("bash", "dockerfile", "yaml", "python", "text")
        val keywords = when (language) {
            "bash", "dockerfile" -> shellKeywords
            "c" -> cKeywords
            "rust" -> rustKeywords
            "python" -> pythonKeywords
            "javascript", "js" -> javascriptKeywords
            else -> kotlinKeywords
        }
        val tokens = mutableListOf<Token>()
        var i = 0

        fun add(text: String, kind: TokenKind) {
            val last = tokens.lastOrNull()
            if (last != null && last.kind == kind && kind == TokenKind.PLAIN) {
                tokens[tokens.lastIndex] = Token(last.text + text, kind)
            } else {
                tokens += Token(text, kind)
            }
        }

        while (i < code.length) {
            val c = code[i]
            when {
                !hashComments && code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    add(code.substring(i, end), TokenKind.COMMENT)
                    i = end
                }
                !hashComments && code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                    add(code.substring(i, end), TokenKind.COMMENT)
                    i = end
                }
                hashComments && c == '#' && (i == 0 || code[i - 1].isWhitespace()) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    add(code.substring(i, end), TokenKind.COMMENT)
                    i = end
                }
                c == '"' -> {
                    var end = i + 1
                    while (end < code.length && code[end] != '"' && code[end] != '\n') {
                        if (code[end] == '\\') end++
                        end++
                    }
                    end = minOf(end + 1, code.length)
                    add(code.substring(i, end), TokenKind.STRING)
                    i = end
                }
                c == '@' && i + 1 < code.length && code[i + 1].isLetter() -> {
                    var end = i + 1
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                    add(code.substring(i, end), TokenKind.ANNOTATION)
                    i = end
                }
                c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit()) -> {
                    var end = i
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_' || code[end] == '.')) end++
                    add(code.substring(i, end), TokenKind.NUMBER)
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    var end = i
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                    val word = code.substring(i, end)
                    add(word, if (word in keywords) TokenKind.KEYWORD else TokenKind.PLAIN)
                    i = end
                }
                else -> {
                    add(c.toString(), TokenKind.PLAIN)
                    i++
                }
            }
        }
        return tokens
    }
}
