package com.esper.android.ui.lexical

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockTest {

    // ---------------------------------------------------------------- parsing

    @Test
    fun `parser records the fence line indices of a code block`() {
        val markdown = "intro\n\n```cpp\nint x = 1;\n```\n\nafter"
        val code = LexicalParser.parseToLexicalAst(markdown)
            .filterIsInstance<LexicalCodeBlockNode>()
            .single()

        assertEquals("cpp", code.language)
        assertEquals("int x = 1;", code.code)
        assertEquals(2, code.lineStartIndex)
        assertEquals(4, code.lineEndIndex)
    }

    @Test
    fun `parser keeps the language tag verbatim including plus signs`() {
        val code = LexicalParser.parseToLexicalAst("```C++\nint main(){}\n```")
            .filterIsInstance<LexicalCodeBlockNode>()
            .single()

        assertEquals("C++", code.language)
    }

    @Test
    fun `parser falls back to text for an untagged fence`() {
        val code = LexicalParser.parseToLexicalAst("```\nplain\n```")
            .filterIsInstance<LexicalCodeBlockNode>()
            .single()

        assertEquals("text", code.language)
    }

    // ---------------------------------------------------------- slash command

    @Test
    fun `slash code expands into a fenced block with an escape paragraph`() {
        // The caret sits right behind the typed "/code" (index 11).
        val fresh = TextFieldValue("hello\n/code", TextRange(11))
        val result = expandSlashCode(fresh)

        assertNotNull(result)
        val text = result!!.text
        assertEquals("hello\n```cpp\n\n```\n\n", text)
        // The caret has to land on the empty content line inside the block.
        assertEquals("hello\n```cpp\n".length, result.selection.start)
        assertTrue(text.endsWith("```\n\n"))
    }

    @Test
    fun `slash code accepts a language argument`() {
        val fresh = TextFieldValue("/code python", TextRange(12))
        val result = expandSlashCode(fresh)

        assertNotNull(result)
        assertTrue(result!!.text.startsWith("```python\n"))
    }

    @Test
    fun `slash code does not fire in the middle of a line`() {
        val fresh = TextFieldValue("some /code text", TextRange(14))
        assertNull(expandSlashCode(fresh))
    }

    @Test
    fun `slash code does not fire for a longer word`() {
        val fresh = TextFieldValue("/codes", TextRange(6))
        assertNull(expandSlashCode(fresh))
    }

    @Test
    fun `slash command keeps the text after the cursor`() {
        val fresh = TextFieldValue("/code tail", TextRange(5))
        val result = expandSlashCode(fresh)

        assertNotNull(result)
        assertTrue(result!!.text.endsWith(" tail"))
    }

    // -------------------------------------------------------- escape paragraph

    @Test
    fun `escape paragraph is appended after a closing fence`() {
        val fresh = TextFieldValue("```cpp\ncode\n```", TextRange(13))
        val result = ensureEscapeParagraph(fresh)

        assertEquals("```cpp\ncode\n```\n\n", result.text)
    }

    @Test
    fun `escape paragraph is not appended after an opening fence`() {
        // A single fence line opens a block, it does not close one.
        val fresh = TextFieldValue("```cpp", TextRange(6))
        assertEquals("```cpp", ensureEscapeParagraph(fresh).text)
    }

    @Test
    fun `escape paragraph is not appended twice`() {
        val fresh = TextFieldValue("```cpp\ncode\n```\n\n", TextRange(16))
        assertEquals("```cpp\ncode\n```\n\n", ensureEscapeParagraph(fresh).text)
    }

    @Test
    fun `ends with closed fence detects a balanced fence pair`() {
        assertTrue(endsWithClosedFence("```cpp\ncode\n```"))
        assertFalse(endsWithClosedFence("```cpp"))
        assertFalse(endsWithClosedFence("```cpp\ncode\n```\n"))
    }

    // ------------------------------------------------------------- highlighting

    @Test
    fun `canonical keys map every alias onto a grammar`() {
        assertEquals("cpp", CodeSyntaxHighlighter.canonicalKey("C++"))
        assertEquals("cpp", CodeSyntaxHighlighter.canonicalKey("cpp"))
        assertEquals("python", CodeSyntaxHighlighter.canonicalKey("py"))
        assertEquals("csharp", CodeSyntaxHighlighter.canonicalKey("C#"))
        assertEquals("bash", CodeSyntaxHighlighter.canonicalKey("sh"))
        assertEquals("sql", CodeSyntaxHighlighter.canonicalKey("surrealql"))
        assertEquals("kotlin", CodeSyntaxHighlighter.canonicalKey("Kotlin"))
        assertEquals("kotlin", CodeSyntaxHighlighter.canonicalKey("kt"))
        assertEquals("php", CodeSyntaxHighlighter.canonicalKey("PHP"))
        assertEquals("text", CodeSyntaxHighlighter.canonicalKey("nonsense"))
    }

    @Test
    fun `display name uses the friendly label`() {
        assertEquals("C++", CodeSyntaxHighlighter.displayLanguageName("cpp"))
        assertEquals("C++", CodeSyntaxHighlighter.displayLanguageName("C++"))
        assertEquals("Kotlin", CodeSyntaxHighlighter.displayLanguageName("kt"))
        assertEquals("PHP", CodeSyntaxHighlighter.displayLanguageName("php"))
        assertEquals("Plain Text", CodeSyntaxHighlighter.displayLanguageName("text"))
    }

    @Test
    fun `cpp example is tokenised into the expected colours`() {
        val code = "int main(){\n\tstd::cout << \"Hello World\";\n}"
        val highlighted = CodeSyntaxHighlighter.highlight(code, "C++")

        assertEquals(code, highlighted.text)
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "int"))     // keyword
        assertEquals(Color(0xFF38BDF8), colorOf(highlighted, "main"))    // function
        assertEquals(Color(0xFF67E8F9), colorOf(highlighted, "std"))     // type / namespace
        assertEquals(Color(0xFF67E8F9), colorOf(highlighted, "cout"))    // type
        assertEquals(Color(0xFF818CF8), colorOf(highlighted, "<<"))      // operator
        assertEquals(Color(0xFF34D399), colorOf(highlighted, "\"Hello World\"")) // string
    }

    @Test
    fun `comments and preprocessors are coloured`() {
        val highlighted = CodeSyntaxHighlighter.highlight("#include <iostream>\n// note", "cpp")

        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "#include"))
        assertEquals(Color(0xFF64748B), colorOf(highlighted, "// note"))
    }

    @Test
    fun `python keywords and strings are coloured`() {
        val highlighted = CodeSyntaxHighlighter.highlight("def f():\n    return 'x'", "python")

        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "def"))
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "return"))
        assertEquals(Color(0xFF34D399), colorOf(highlighted, "'x'"))
    }

    @Test
    fun `kotlin keywords, annotations and raw strings are coloured`() {
        val source = "@Composable\nfun Draw(x: Int): Unit {\n    val s = \"\"\"raw\"\"\"\n}"
        val highlighted = CodeSyntaxHighlighter.highlight(source, "kotlin")

        assertEquals(source, highlighted.text)
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "fun"))
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "val"))
        assertEquals(Color(0xFF67E8F9), colorOf(highlighted, "Int"))
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "@Composable"))
        assertEquals(Color(0xFF34D399), colorOf(highlighted, "\"\"\"raw\"\"\""))
    }

    @Test
    fun `php variables, tags and keywords are coloured`() {
        val source = "<?php echo \$name; ?>"
        val highlighted = CodeSyntaxHighlighter.highlight(source, "php")

        assertEquals(source, highlighted.text)
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "<?php"))
        assertEquals(Color(0xFFC084FC), colorOf(highlighted, "echo"))
        assertEquals(Color(0xFF67E8F9), colorOf(highlighted, "\$name"))
    }

    @Test
    fun `json keys keep their original spacing`() {
        val source = """{"a"  :  1}"""
        val highlighted = CodeSyntaxHighlighter.highlight(source, "json")

        assertEquals(source, highlighted.text)
        assertEquals(Color(0xFF38BDF8), colorOf(highlighted, "\"a\""))
        assertEquals(Color(0xFFFBBF24), colorOf(highlighted, "1"))
    }

    @Test
    fun `plain text is not highlighted`() {
        val highlighted = CodeSyntaxHighlighter.highlight("int x = 1;", "text")

        assertEquals("int x = 1;", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }

    @Test
    fun `unknown languages render as plain text`() {
        val highlighted = CodeSyntaxHighlighter.highlight("int x = 1;", "brainfuck")

        assertTrue(highlighted.spanStyles.isEmpty())
    }

    @Test
    fun `highlighting preserves the source text exactly`() {
        val samples = mapOf(
            "cpp" to "int main(){\n\tstd::cout << \"Hello World\";\n}",
            "python" to "def f(a, b):\n    return a ** b  # comment",
            "rust" to "fn main() { let v: Vec<i32> = vec![1, 2]; }",
            "javascript" to "const f = (a) => `tpl \${a}`;",
            "sql" to "SELECT * FROM book WHERE title = 'x';",
            "json" to """{"a":[1,2],"b":null,"c":true}""",
            "bash" to "if [ -f x ]; then echo \"hi\"; fi",
            "kotlin" to "fun main() { val s = \"hi\"; println(s) }",
            "php" to "<?php echo \$x + 1; ?>"
        )

        samples.forEach { (lang, source) ->
            val highlighted = CodeSyntaxHighlighter.highlight(source, lang)
            assertEquals("language $lang must not alter the text", source, highlighted.text)
        }
    }

    // --------------------------------------------------------------- utilities

    /** Colour of the span covering [needle], or null when it is unstyled. */
    private fun colorOf(text: AnnotatedString, needle: String): Color? {
        val start = text.text.indexOf(needle)
        if (start < 0) return null
        val end = start + needle.length
        val span = text.spanStyles.firstOrNull { it.start <= start && it.end >= end }
        return span?.item?.color
    }
}
