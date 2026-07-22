package semantictokens

import com.uabutler.Analyzer
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.resolver.scope.SemanticTokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Finds the 1-indexed line / 0-indexed column of the nth (0-indexed) occurrence of `needle`, to
// avoid hand-counting positions in test source strings.
private fun positionOf(text: String, needle: String, occurrence: Int = 0): Pair<Int, Int> {
    var seen = 0
    text.lines().forEachIndexed { lineIndex, line ->
        var fromIndex = 0
        while (true) {
            val idx = line.indexOf(needle, fromIndex)
            if (idx == -1) break
            if (seen == occurrence) return (lineIndex + 1) to idx
            seen++
            fromIndex = idx + 1
        }
    }
    error("'$needle' occurrence $occurrence not found")
}

class SemanticTokensTest {

    private val options = Analyzer.Options(includeStdLib = false)

    @Test
    fun `an unused top-level function is still classified at its declaration`() {
        // "top" is never referenced anywhere else in the file - this is exactly the gap that
        // classifying declarations separately (not just riding along on Scope.resolve() usages)
        // exists to close.
        val gapl = """
            function top() i: wire => o: wire {
                i => o;
            }
        """.trimIndent()

        val tokens = Analyzer.analyze(gapl, options).semanticTokens
        val (line, col) = positionOf(gapl, "top")

        assertTrue(tokens.any { it.kind == SemanticTokenKind.FUNCTION && it.span.startLine == line && it.span.startColumn == col })
    }

    @Test
    fun `declarations and usages are both classified`() {
        val gapl = """
            function top() i: wire => o: wire {
                i => declare x: wire;
                x => o;
            }
        """.trimIndent()

        val tokens = Analyzer.analyze(gapl, options).semanticTokens

        fun hasTokenAt(needle: String, occurrence: Int, kind: SemanticTokenKind): Boolean {
            val (line, col) = positionOf(gapl, needle, occurrence)
            return tokens.any { it.kind == kind && it.span.startLine == line && it.span.startColumn == col }
        }

        // Declarations.
        assertTrue(hasTokenAt("top", 0, SemanticTokenKind.FUNCTION))
        assertTrue(hasTokenAt("i:", 0, SemanticTokenKind.PARAMETER)) // input function IO
        assertTrue(hasTokenAt("o:", 0, SemanticTokenKind.PARAMETER)) // output function IO
        assertTrue(hasTokenAt("x:", 0, SemanticTokenKind.VARIABLE)) // declared circuit node

        // Usages (resolved via Scope.resolve()).
        assertTrue(hasTokenAt("i ", 0, SemanticTokenKind.PARAMETER)) // `i => declare x: wire;`
        assertTrue(hasTokenAt("x ", 0, SemanticTokenKind.VARIABLE)) // `x => o;`
        assertTrue(hasTokenAt("o;", 0, SemanticTokenKind.PARAMETER)) // `x => o;`

        // Keywords and operators, from the independent lexical pass.
        assertTrue(hasTokenAt("function", 0, SemanticTokenKind.KEYWORD))
        assertTrue(hasTokenAt("declare", 0, SemanticTokenKind.KEYWORD))
        assertTrue(hasTokenAt("wire", 0, SemanticTokenKind.KEYWORD))
        assertTrue(hasTokenAt("=>", 0, SemanticTokenKind.OPERATOR))
    }

    @Test
    fun `a generic parameter is classified as a type parameter at declaration and usage`() {
        val gapl = """
            function double(size: integer) i: wire[size] => o: wire[size] {
                i => o;
            }
        """.trimIndent()

        val tokens = Analyzer.analyze(gapl, options).semanticTokens

        fun hasTokenAt(needle: String, occurrence: Int, kind: SemanticTokenKind): Boolean {
            val (line, col) = positionOf(gapl, needle, occurrence)
            return tokens.any { it.kind == kind && it.span.startLine == line && it.span.startColumn == col }
        }

        assertTrue(hasTokenAt("size", 0, SemanticTokenKind.TYPE_PARAMETER)) // declaration
        assertTrue(hasTokenAt("size", 1, SemanticTokenKind.TYPE_PARAMETER)) // usage in i: wire[size]
    }

    @Test
    fun `a line comment is classified, and parsing still works alongside it`() {
        val gapl = """
            // a comment above the function
            function top() i: wire => o: wire {
                i => o; // a trailing comment
            }
        """.trimIndent()

        val tokens = Analyzer.analyze(gapl, options).semanticTokens

        val (aboveLine, aboveCol) = positionOf(gapl, "// a comment above the function")
        assertTrue(tokens.any {
            it.kind == SemanticTokenKind.COMMENT && it.span.startLine == aboveLine && it.span.startColumn == aboveCol
        })

        val (trailingLine, trailingCol) = positionOf(gapl, "// a trailing comment")
        assertTrue(tokens.any {
            it.kind == SemanticTokenKind.COMMENT && it.span.startLine == trailingLine && it.span.startColumn == trailingCol
        })

        // Parsing/resolution weren't disrupted by the comments - the function is still classified normally.
        assertTrue(tokens.any { it.kind == SemanticTokenKind.FUNCTION })
    }

    @Test
    fun `a multi-line block comment is split into one token per physical line`() {
        val gapl = """
            /*
             * a block comment
             * spanning several lines
             */
            function top() i: wire => o: wire {
                i => o;
            }
        """.trimIndent()

        val tokens = Analyzer.analyze(gapl, options).semanticTokens
        val commentTokens = tokens.filter { it.kind == SemanticTokenKind.COMMENT }.sortedBy { it.span.startLine }

        assertEquals(4, commentTokens.size)

        assertEquals(SourceSpan(1, 0, 1, 2), commentTokens[0].span) // "/*"
        assertEquals(SourceSpan(2, 0, 2, 18), commentTokens[1].span) // " * a block comment"
        assertEquals(SourceSpan(3, 0, 3, 25), commentTokens[2].span) // " * spanning several lines"
        assertEquals(SourceSpan(4, 0, 4, 3), commentTokens[3].span) // " */"

        // Parsing/resolution weren't disrupted by the comment - the function is still classified normally.
        assertTrue(tokens.any { it.kind == SemanticTokenKind.FUNCTION })
    }
}
