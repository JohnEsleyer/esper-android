package com.esper.android.ui.lexical

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Dependency-free syntax highlighting for code blocks.
 *
 * The web client highlights code with Prism (`src/services/prismSetup.js`).
 * Compose has no equivalent, so the grammars are re-implemented here as plain
 * regular expressions and rendered with an `AnnotatedString`.
 *
 * The token colours are the exact values of the web `editor-dark` palette in
 * `src/index.css`, so a note looks identical on both clients.
 */
object CodeSyntaxHighlighter {

    /** Languages offered by the code block language picker. */
    val SUPPORTED_LANGUAGES = listOf(
        "cpp" to "C++",
        "c" to "C",
        "python" to "Python",
        "rust" to "Rust",
        "javascript" to "JavaScript",
        "typescript" to "TypeScript",
        "java" to "Java",
        "kotlin" to "Kotlin",
        "php" to "PHP",
        "csharp" to "C#",
        "go" to "Go",
        "bash" to "Bash",
        "sql" to "SQL",
        "json" to "JSON",
        "html" to "HTML",
        "markdown" to "Markdown",
        "text" to "Plain Text"
    )

    /**
     * Maps any tag a user may have written (`C++`, `c#`, `py`, `yml`, ...) onto
     * the canonical key used by the grammars below.
     */
    fun canonicalKey(lang: String): String = when (lang.trim().lowercase()) {
        "c++", "cpp" -> "cpp"
        "c" -> "c"
        "python", "py" -> "python"
        "rust", "rs" -> "rust"
        "javascript", "js" -> "javascript"
        "typescript", "ts" -> "typescript"
        "java" -> "java"
        "kotlin", "kt", "kts" -> "kotlin"
        "php" -> "php"
        "c#", "csharp", "cs" -> "csharp"
        "go", "golang" -> "go"
        "bash", "sh", "shell" -> "bash"
        "sql", "surrealql", "surreal" -> "sql"
        "json" -> "json"
        "html", "xml", "markup" -> "html"
        "markdown", "md" -> "markdown"
        else -> "text"
    }

    /** Human readable name for the badge, e.g. `C++` for `cpp`. */
    fun displayLanguageName(lang: String): String {
        val key = canonicalKey(lang)
        return SUPPORTED_LANGUAGES.firstOrNull { it.first == key }?.second ?: lang.uppercase()
    }

    // Token colours — mirrors the web `editor-dark` palette.
    private val KeywordColor = Color(0xFFC084FC)
    private val StringColor = Color(0xFF34D399)
    private val NumberColor = Color(0xFFFBBF24)
    private val BooleanColor = Color(0xFFF43F5E)
    private val FunctionColor = Color(0xFF38BDF8)
    private val ClassTypeColor = Color(0xFF67E8F9)
    private val CommentColor = Color(0xFF64748B)
    private val OperatorColor = Color(0xFF818CF8)
    private val PunctuationColor = Color(0xFF94A3B8)
    private val PreprocessorColor = Color(0xFFC084FC)
    private val DefaultTextColor = Color(0xFFE0E7FF)

    private val cppKeywords = setOf(
        "alignas", "alignof", "asm", "auto", "bool", "break", "case", "catch", "char",
        "char16_t", "char32_t", "char8_t", "class", "co_await", "co_return", "co_yield",
        "concept", "const", "consteval", "constexpr", "constinit", "continue", "decltype",
        "default", "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit",
        "export", "extern", "false", "final", "float", "for", "friend", "goto", "if",
        "import", "inline", "int", "long", "module", "mutable", "namespace", "new",
        "noexcept", "nullptr", "operator", "override", "private", "protected", "public",
        "register", "reinterpret_cast", "requires", "return", "short", "signed", "sizeof",
        "static", "static_assert", "static_cast", "struct", "switch", "template", "this",
        "thread_local", "throw", "true", "try", "typedef", "typeid", "typename", "union",
        "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while"
    )

    private val cppTypes = setOf(
        "std", "cout", "cin", "cerr", "clog", "endl", "string", "wstring", "vector",
        "deque", "list", "forward_list", "array", "set", "multiset", "map", "multimap",
        "unordered_set", "unordered_map", "stack", "queue", "priority_queue", "pair",
        "tuple", "optional", "variant", "any", "unique_ptr", "shared_ptr", "weak_ptr",
        "size_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t",
        "uint32_t", "uint64_t", "ifstream", "ofstream", "fstream", "iostream", "istream", "ostream"
    )

    private val pythonKeywords = setOf(
        "def", "class", "return", "if", "elif", "else", "while", "for", "in", "try",
        "except", "finally", "with", "as", "import", "from", "lambda", "yield", "raise",
        "pass", "continue", "break", "assert", "async", "await", "global", "nonlocal",
        "and", "or", "not", "is"
    )

    private val pythonBuiltins = setOf(
        "print", "len", "range", "str", "int", "float", "list", "dict", "set", "tuple",
        "open", "type", "self", "super", "enumerate", "zip", "map", "filter", "sorted"
    )

    private val rustKeywords = setOf(
        "fn", "let", "mut", "pub", "struct", "enum", "impl", "trait", "for", "in",
        "while", "loop", "if", "else", "match", "return", "break", "continue", "use",
        "mod", "crate", "super", "self", "Self", "as", "where", "async", "await",
        "move", "ref", "type", "const", "static", "unsafe"
    )

    private val rustTypes = setOf(
        "Option", "Result", "Some", "None", "Ok", "Err", "Vec", "String", "i32", "u32",
        "usize", "bool", "char", "i64", "u64", "f64", "f32"
    )

    private val jsKeywords = setOf(
        "function", "const", "let", "var", "return", "if", "else", "for", "while",
        "do", "switch", "case", "break", "continue", "new", "class", "extends", "super",
        "this", "import", "export", "from", "default", "try", "catch", "finally",
        "throw", "async", "await", "yield", "typeof", "instanceof", "interface", "type", "enum"
    )

    private val sqlKeywords = setOf(
        "select", "from", "where", "insert", "into", "update", "set", "delete", "create",
        "upsert", "group", "by", "order", "asc", "desc", "limit", "join", "inner", "left",
        "right", "on", "as", "and", "or", "not", "in", "is", "null", "table", "database",
        "define", "schemafull", "assert", "relate", "content", "return"
    )

    private val bashKeywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "in",
        "case", "esac", "function", "echo", "exit", "export", "source", "cd", "mkdir",
        "rm", "cp", "mv", "ls", "grep", "sed", "awk", "curl", "wget", "sudo", "bun", "npm", "node"
    )

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "when", "if", "else", "for", "while", "do", "return", "break", "continue",
        "try", "catch", "finally", "throw", "is", "as", "in", "out", "by", "where",
        "init", "constructor", "companion", "data", "sealed", "enum", "annotation",
        "open", "final", "abstract", "override", "private", "protected", "internal",
        "public", "lateinit", "const", "suspend", "inline", "noinline", "crossinline",
        "reified", "operator", "infix", "tailrec", "external", "vararg", "typealias",
        "get", "set", "this", "super", "companion", "expect", "actual", "value"
    )

    private val kotlinTypes = setOf(
        "String", "Int", "Long", "Double", "Float", "Boolean", "Char", "Byte",
        "Short", "Any", "Unit", "Nothing", "List", "MutableList", "Set", "MutableSet",
        "Map", "MutableMap", "Array", "IntArray", "Pair", "Triple", "Sequence",
        "Flow", "CoroutineScope", "Job", "Deferred", "State", "MutableState"
    )

    private val phpKeywords = setOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch",
        "class", "clone", "const", "continue", "declare", "default", "do", "echo",
        "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
        "endswitch", "endwhile", "enum", "extends", "final", "finally", "fn", "for",
        "foreach", "function", "global", "goto", "if", "implements", "include",
        "include_once", "instanceof", "insteadof", "interface", "isset", "list",
        "match", "namespace", "new", "or", "print", "private", "protected", "public",
        "readonly", "require", "require_once", "return", "static", "switch", "throw",
        "trait", "try", "unset", "use", "var", "while", "xor", "yield"
    )

    private val phpTypes = setOf(
        "int", "float", "string", "bool", "void", "mixed", "array", "object",
        "callable", "iterable", "never", "self", "parent", "static"
    )

    /** Kotlin raw strings open with three quotes; spelled out so the regex below stays readable. */
    private const val RAW = "\"\"\""

    /**
     * True when the next non-whitespace character after [endIndexExclusive] is
     * an opening parenthesis, i.e. the identifier just matched is a call.
     *
     * Scans forward instead of allocating a substring of the remaining source
     * for every identifier — that turns highlighting into O(n^2).
     */
    private fun isFollowedByCallParen(code: String, endIndexExclusive: Int): Boolean {
        var i = endIndexExclusive
        while (i < code.length && code[i].isWhitespace()) i++
        return i < code.length && code[i] == '('
    }

    /** Highlights [code] according to [rawLang]; unknown languages render plain. */
    fun highlight(code: String, rawLang: String): AnnotatedString {
        val lang = canonicalKey(rawLang)
        return when (lang) {
            "cpp", "c" -> highlightCpp(code)
            "python" -> highlightPython(code)
            "rust" -> highlightRust(code)
            "javascript", "typescript" -> highlightJs(code)
            "kotlin" -> highlightKotlin(code)
            "php" -> highlightPhp(code)
            "sql" -> highlightSql(code)
            "json" -> highlightJson(code)
            "bash" -> highlightBash(code)
            "text" -> AnnotatedString(code)
            else -> highlightGeneric(code)
        }
    }

    private fun highlightCpp(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(//[^\n]*|/\*[\s\S]*?\*/)|""" +                          // 1: comment
            """("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|""" +              // 2: string / char
            """(^[ \t]*#[a-zA-Z_]\w*)|""" +                             // 3: preprocessor
            """(\b-?(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)[fFuUlLzZ]*\b)|""" + // 4: number
            """(\b[a-zA-Z_]\w*\b)|""" +                                  // 5: identifier
            """(>>|<<|::|->\*|->|\+\+|--|&&|\|\||<=|>=|==|!=|[+\-*/%=<>&|^~!?:])|""" + // 6: operator
            """([{}()\[\];,."])""",                                       // 7: punctuation
            RegexOption.MULTILINE
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) {
                append(code.substring(lastIdx, range.first))
            }
            val (comment, str, prep, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                prep.isNotEmpty() -> withStyle(SpanStyle(color = PreprocessorColor, fontWeight = FontWeight.Bold)) { append(prep) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    when {
                        ident in cppKeywords -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident in cppTypes -> withStyle(SpanStyle(color = ClassTypeColor)) { append(ident) }
                        ident == "true" || ident == "false" || ident == "nullptr" || ident == "NULL" -> {
                            withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        }
                        isFollowedByCallParen(code, range.last + 1) -> {
                            withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                        }
                        else -> withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) {
            append(code.substring(lastIdx))
        }
    }

    private fun highlightPython(code: String): AnnotatedString = buildAnnotatedString {
        // Triple quotes are written as \" so they survive the Kotlin raw string.
        val pattern = Regex(
            """(#[^\n]*)|""" +                                                  // 1: comment
            """(\"\"\"[\s\S]*?\"\"\"|'''[\s\S]*?'''|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|""" + // 2: string
            """(\b-?(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?)\b)|""" +                // 3: number
            """(\b[a-zA-Z_]\w*\b)|""" +                                         // 4: identifier
            """([+\-*/%=<>&|^~!:]+)|""" +                                       // 5: operator
            """([{}()\[\];,."])"""                                              // 6: punctuation
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    when {
                        ident in pythonKeywords -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident == "True" || ident == "False" || ident == "None" -> withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident in pythonBuiltins -> withStyle(SpanStyle(color = ClassTypeColor)) { append(ident) }
                        isFollowedByCallParen(code, range.last + 1) -> {
                            withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                        }
                        else -> withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightRust(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(//[^\n]*|/\*[\s\S]*?\*/)|""" +
            """("(?:\\.|[^"\\])*")|""" +
            """(\b-?(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?)\b)|""" +
            """(\b[a-zA-Z_]\w*!?\b)|""" +
            """(::|->|=>|[+\-*/%=<>&|^~!?:.]+)|""" +
            """([{}()\[\];,])"""
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    when {
                        ident.removeSuffix("!") in rustKeywords -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident == "true" || ident == "false" -> withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident in rustTypes -> withStyle(SpanStyle(color = ClassTypeColor)) { append(ident) }
                        ident.endsWith("!") -> withStyle(SpanStyle(color = FunctionColor, fontWeight = FontWeight.Bold)) { append(ident) }
                        isFollowedByCallParen(code, range.last + 1) -> {
                            withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                        }
                        else -> withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightJs(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(//[^\n]*|/\*[\s\S]*?\*/)|""" +
            """(`[\s\S]*?`|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|""" +
            """(\b-?(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?)\b)|""" +
            """(\b[a-zA-Z_]\w*\b)|""" +
            """(=>|[+\-*/%=<>&|^~!?:]+)|""" +
            """([{}()\[\];,."])"""
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    when {
                        ident in jsKeywords -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident == "true" || ident == "false" || ident == "null" || ident == "undefined" -> {
                            withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        }
                        isFollowedByCallParen(code, range.last + 1) -> {
                            withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                        }
                        else -> withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightKotlin(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(//[^\n]*|/\*[\s\S]*?\*/)|""" +                        // 1: comment
            """($RAW[\s\S]*?$RAW|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|""" + // 2: string / char
            """(@[A-Za-z_]\w*)|""" +                                     // 3: annotation
            """(\b-?(?:0x[0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?)[fFlLuU]*\b)|""" + // 4: number
            """(\b[a-zA-Z_]\w*\b)|""" +                                 // 5: identifier
            """(->|::|==|!=|<=|>=|&&|\|\||\+\+|--|[+\-*/%=<>&|^~!?:])|""" + // 6: operator
            """([{}()\[\];,.])"""                                      // 7: punctuation
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, annotation, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                annotation.isNotEmpty() -> withStyle(SpanStyle(color = PreprocessorColor, fontWeight = FontWeight.SemiBold)) { append(annotation) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    when {
                        ident in kotlinKeywords -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident in kotlinTypes -> withStyle(SpanStyle(color = ClassTypeColor)) { append(ident) }
                        ident == "true" || ident == "false" || ident == "null" ->
                            withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        isFollowedByCallParen(code, range.last + 1) ->
                            withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                        else -> withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightPhp(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)|""" +                 // 1: comment
            """('(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")|""" +             // 2: string
            """(<\?php|<\?=|\?>)|""" +                                  // 3: open/close tag
            """(\$[A-Za-z_]\w*)|""" +                                    // 4: variable
            """(\b-?\d+(?:\.\d+)?\b)|""" +                              // 5: number
            """(\b[a-zA-Z_]\w*\b)|""" +                                 // 6: identifier
            """(->|::|=>|==|!=|<=|>=|&&|\|\||[+\-*/%=<>&|^~!?:.])|""" +   // 7: operator
            """([{}()\[\];,])"""                                       // 8: punctuation
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, tag, variable, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                tag.isNotEmpty() -> withStyle(SpanStyle(color = PreprocessorColor, fontWeight = FontWeight.Bold)) { append(tag) }
                variable.isNotEmpty() -> withStyle(SpanStyle(color = ClassTypeColor)) { append(variable) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    when {
                        ident.lowercase() in phpKeywords -> withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        ident.lowercase() in phpTypes -> withStyle(SpanStyle(color = ClassTypeColor)) { append(ident) }
                        ident.lowercase() == "true" || ident.lowercase() == "false" || ident.lowercase() == "null" ->
                            withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(ident) }
                        isFollowedByCallParen(code, range.last + 1) ->
                            withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                        else -> withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightSql(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(--[^\n]*|/\*[\s\S]*?\*/)|""" +
            """('(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")|""" +
            """(\b-?\d+(?:\.\d+)?\b)|""" +
            """(\b[a-zA-Z_]\w*\b)|""" +
            """([+\-*/%=<>&|]+)|""" +
            """([{}()\[\];,."])"""
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    if (ident.lowercase() in sqlKeywords) {
                        withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold)) { append(ident) }
                    } else {
                        withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightJson(code: String): AnnotatedString = buildAnnotatedString {
        // The key uses a lookahead so the whitespace between key and colon is
        // preserved verbatim instead of being collapsed into a single space.
        val pattern = Regex(
            """("(?:\\.|[^"\\])*")(?=\s*:)|""" +                 // 1: key
            """("(?:\\.|[^"\\])*")|""" +                           // 2: string value
            """(\b-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)|""" +        // 3: number
            """(\b(?:true|false|null)\b)|""" +                     // 4: boolean
            """([{}()\[\],])|""" +                                  // 5: punctuation
            """(:)"""                                              // 6: operator
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (key, str, num, boolVal, punct, colon) = match.destructured

            when {
                key.isNotEmpty() -> withStyle(SpanStyle(color = FunctionColor, fontWeight = FontWeight.SemiBold)) { append(key) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                boolVal.isNotEmpty() -> withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.SemiBold)) { append(boolVal) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                colon.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(colon) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightBash(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(#[^\n]*)|""" +
            """("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|""" +
            """(\b-?\d+\b)|""" +
            """(\b[a-zA-Z_]\w*\b)|""" +
            """([|&;><=]+)|""" +
            """([{}()\[\];,."])"""
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    if (ident in bashKeywords) {
                        withStyle(SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold)) { append(ident) }
                    } else {
                        withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }

    private fun highlightGeneric(code: String): AnnotatedString = buildAnnotatedString {
        val pattern = Regex(
            """(//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)|""" +
            """("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|""" +
            """(\b-?\d+(?:\.\d+)?\b)|""" +
            """(\b[a-zA-Z_]\w*\b)|""" +
            """([+\-*/%=<>&|~!?:]+)|""" +
            """([{}()\[\];,."])"""
        )

        var lastIdx = 0
        for (match in pattern.findAll(code)) {
            val range = match.range
            if (range.first > lastIdx) append(code.substring(lastIdx, range.first))
            val (comment, str, num, ident, op, punct) = match.destructured

            when {
                comment.isNotEmpty() -> withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) { append(comment) }
                str.isNotEmpty() -> withStyle(SpanStyle(color = StringColor)) { append(str) }
                num.isNotEmpty() -> withStyle(SpanStyle(color = NumberColor)) { append(num) }
                ident.isNotEmpty() -> {
                    if (isFollowedByCallParen(code, range.last + 1)) {
                        withStyle(SpanStyle(color = FunctionColor)) { append(ident) }
                    } else {
                        withStyle(SpanStyle(color = DefaultTextColor)) { append(ident) }
                    }
                }
                op.isNotEmpty() -> withStyle(SpanStyle(color = OperatorColor)) { append(op) }
                punct.isNotEmpty() -> withStyle(SpanStyle(color = PunctuationColor)) { append(punct) }
                else -> append(match.value)
            }
            lastIdx = range.last + 1
        }
        if (lastIdx < code.length) append(code.substring(lastIdx))
    }
}
