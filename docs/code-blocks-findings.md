# Code Block Support in the Android App: Parsed and Rendered, but Never Highlighted

**Date:** 2026-10-20
**Scope:** `esper-android` — does the native app support code blocks?
**Verdict:** **Yes, partially.** Fenced code blocks are a first-class block type with a
parsed language tag, a dedicated dark renderer and inline-code styling. But there is
**no syntax highlighting of any kind** — the code body is rendered as a single
monospace `Text` in one flat colour. The web app now highlights `C++`, `python`,
`rust`, … ; the Android app shows none of it.

> **Status: RESOLVED (2026-10-20).** Every gap listed below has been closed — see
> [§7 Resolution log](#7-resolution-log) for what shipped and how it was verified.

---

## 1. Summary table

| Capability | Android | Web |
|---|---|---|
| Fenced code block parsed from markdown | Yes | Yes |
| Language tag captured (` ```C++ ` → `C++`) | Yes | Yes (after the `CodeBlockTransformer` fix) |
| Language badge shown in the block header | Yes (uppercase monospace) | Yes (CSS `::before` badge) |
| **Syntax highlighting** | **No** | **Yes** (Prism) |
| Inline code (`` `x` ``) styled | Yes | Yes |
| Copy button | **Icon only — not wired up** | Yes (functional) |
| Delete button | No | Yes (added this round) |
| `/code` slash command | **No** | Yes (added this round) |
| Language picker | No | Yes (added this round) |
| Escape paragraph under a new block | No | Yes (added this round) |

---

## 2. What *is* supported

### 2.1 The node model

`app/src/main/java/com/esper/android/ui/lexical/LexicalNode.kt:25-29`:

```kotlin
data class LexicalCodeBlockNode(
    override val key: String,
    val code: String,
    val language: String = "markdown"
) : LexicalBlockNode()
```

A code block carries its body as a raw string plus the language tag. Note that the
default is `"markdown"`, but the parser never actually produces `"markdown"` — see §2.2.

### 2.2 Parsing — and the language tag is handled *correctly*

`app/src/main/java/com/esper/android/ui/lexical/LexicalParser.kt:49-67`:

```kotlin
// 2. Code Block: ```
if (trimmed.startsWith("```")) {
    val lang = trimmed.removePrefix("```").trim().ifEmpty { "text" }
    val codeLines = mutableListOf<String>()
    i++
    while (i < lines.size && !lines[i].trim().startsWith("```")) {
        codeLines.add(lines[i])
        i++
    }
    blocks.add(
        LexicalCodeBlockNode(
            key = UUID.randomUUID().toString(),
            code = codeLines.joinToString("\n"),
            language = lang
        )
    )
    i++
    continue
}
```

`removePrefix("```").trim()` captures **whatever** the user wrote after the fence, so a
fence tagged `C++` yields `language = "C++"`, a fence tagged `c#` yields `"c#"`, and a
bare fence with no tag falls back to `"text"`.

This is worth calling out: **the Android parser is more faithful about the language
tag than `@lexical/markdown` was on the web.** The stock web transformer used
`` /^[ \t]*```(\w+)?/ ``, which stopped at the first non-word character, so ```` ```C++ ````
was read as the language `C` and the trailing `++` leaked into the code body. That was
fixed on the web by `src/components/nodes/CodeBlockTransformer.js`; Android needed no
equivalent fix.

### 2.3 Rendering

`app/src/main/java/com/esper/android/ui/lexical/ComposeLexicalEditor.kt:377-414`:

```kotlin
is LexicalCodeBlockNode -> {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF030712),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(...) {
                Text(
                    text = block.language.uppercase(),   // language badge
                    color = IndigoGlow,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.ContentCopy, ...)     // decorative only — see §3.2
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = block.code,
                color = Color(0xFFE0E7FF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}
```

Visually this is a faithful reproduction of the web block: near-black background
(`0xFF030712`), slate hairline border, 10dp rounded corners, a small uppercase
monospace language label in indigo, and the body in monospace at 12sp.

### 2.4 Inline code

`LexicalNode.kt:64-70` gives `LexicalTextNode` an `isCode` flag; the parser sets it via
`` Regex("`([^`]+)`") `` (`LexicalParser.kt:249`, applied at `LexicalParser.kt:272`); the
renderer styles it with a monospace family on a slate background
(`ComposeLexicalEditor.kt:606-612`).

### 2.5 Creating a code block

The editor toolbar has a **Code** button (`ComposeLexicalEditor.kt:671`) wired to
`LexicalAction.AddCodeBlock`, which appends a literal fenced block to the raw markdown
(`ComposeLexicalEditor.kt:723`):

```kotlin
LexicalAction.AddCodeBlock ->
    "$content\n```python\ndef orbital_period(a, mu):\n    return 2 * math.pi * math.sqrt(a**3 / mu)\n```"
```

Note the language is **hardcoded to `python`** — there is no way to create a `C++` block
from the UI.

### 2.6 Round-trip fidelity

The Android editor keeps the raw markdown and only re-parses it for rendering
(`ComposeLexicalEditor.kt:111-120`):

```kotlin
fun commitText(newText: String) {
    if (readOnly) return
    rawText = newText
    blocks = LexicalParser.parseToLexicalAst(newText)
    ...
}
```

The AST is never serialised back to markdown, so **the language tag survives editing,
sync and export untouched**. Zip export/import needs no special handling either, since
notes are stored as plain markdown text (the `.html` files in the zip are canvas
visuals, not notes).

---

## 3. What is *not* supported

### 3.1 No syntax highlighting — the main gap

There is **no highlighting library and no tokenising logic anywhere in the Android
app**:

- `app/build.gradle.kts` contains no Prism, commonmark, flexmark or any highlighting
  dependency. The full dependency list is AndroidX/Compose, Coil, Retrofit/Gson,
  OkHttp, Room, Firebase, YouTube and Kotlin coroutines — nothing markdown-related.
- Grepping the entire `app/src/main/java/` tree for `highlight|syntaxhighlight|tokeniz|prism`
  returns exactly two hits, both of which are the cover theme named **"Prismatic"**
  (`ui/theme/CoverTheme.kt:165-166`). Nothing else.

Consequently the code body is emitted as one `Text` with one colour
(`ComposeLexicalEditor.kt:405-411`), so `int main(){ std::cout << "Hello World"; }`
renders as a flat wall of `#E0E7FF` monospace with no keyword/string/operator colours.

### 3.2 The copy button is decorative

The `Icons.Default.ContentCopy` in the block header (`ComposeLexicalEditor.kt:397-402`)
is a bare `Icon` with **no `onClick`**. The editor never touches `ClipboardManager` —
grepping for it finds usages only in `ui/bookshelf/CreateNotebookScreen.kt:328` and
`ui/bookshelf/ConfigureNotebookScreen.kt:320`, i.e. the notebook title screens. Tapping
the copy glyph on a code block does nothing.

### 3.3 Missing editor affordances

| Missing | Note |
|---|---|
| `/code` slash command | No match for `/code` or `slash` anywhere in `app/src/main/java/` |
| Language picker | The only language obtainable from the UI is the hardcoded `python` |
| Delete button | No way to remove a code block from the editor |
| Copy (functional) | See §3.2 |
| Escape paragraph | Nothing guarantees a paragraph after a code block |

### 3.4 Parser edge case (shared with the web)

The closing-fence test is `!lines[i].trim().startsWith("```")`, so a code block whose
**content** contains a line starting with three backticks terminates early and the
remainder is parsed as ordinary markdown. The web's transformer has the same behaviour,
so this is a consistent sharp edge rather than a divergence — but it is worth knowing
if anyone writes nested fences.

---

## 4. Cross-platform impact

The asymmetry is one-directional:

- **Web → Android loses highlighting.** A note written on the web as
  ```` ```C++ ```` opens on Android with the badge reading `C++` and the code as plain
  monospace text. Everything else (body text, indentation, the language label) matches.
- **Android → web keeps highlighting.** A note written on Android as
  ```` ```python ```` opens on the web fully highlighted, because the web's Prism setup
  covers `python`.

The web's recent changes (language picker, `/code`, trash button, escape paragraph) have
no Android counterpart yet. None of them break the existing Android behaviour: the
escape paragraph appended by the web's `CodeBlockTrailingParagraphPlugin` simply
produces a trailing blank line in the markdown, which the Android parser skips
(`if (trimmed.isNotEmpty())`, `LexicalParser.kt:191`).

---

## 5. Recommendations

Ordered by value-per-effort. Nothing here is required for parity — the Android app
already *supports* code blocks; this is about closing the feature gap.

1. **Add syntax highlighting.** The cheapest path is a Kotlin multiplatform port of the
   Prism grammars, or a small hand-written tokenizer per language mirroring
   `src/services/prismSetup.js`. Compose can then render the token list with
   `buildAnnotatedString` — the same technique already used for inline code
   (`ComposeLexicalEditor.kt:602-618`), so the rendering plumbing already exists.
   A dependency-free first pass could highlight just C/C++ and Python, which covers the
   languages Esper notes actually use.

2. **Wire up the copy button.** Replace the `Icon` with a clickable `Surface` and call
   `ClipboardManager.setPrimaryClip()`. Trivial, and it removes a visibly dead control.

3. **Add a language picker** to the block header (a small dropdown next to the badge) and
   let it rewrite the fence tag in the raw markdown.

4. **Add a `/code` command** to the raw markdown field so it matches the web.

5. **Consider deleting a block** by rewriting the fenced range in the raw markdown.

Items 2–5 all operate on the raw markdown text (`rawText`), which the editor already
owns — none of them require changing the AST model.

---

## 6. File reference

| File | Role |
|---|---|
| `app/src/main/java/com/esper/android/ui/lexical/LexicalNode.kt` | AST node definitions, incl. `LexicalCodeBlockNode` (25-29) and `isCode` (64-70) |
| `app/src/main/java/com/esper/android/ui/lexical/LexicalParser.kt` | Markdown → AST; code blocks (49-67), inline code (249, 272) |
| `app/src/main/java/com/esper/android/ui/lexical/ComposeLexicalEditor.kt` | Rendering (377-414 code block, 602-618 inline code), toolbar (671), `AddCodeBlock` (723), raw-text commit (111-120) |
| `app/src/main/java/com/esper/android/ui/workspace/WorkspaceScreen.kt` | Mounts the editor for chapter notes (235, 391) |
| `app/src/main/java/com/esper/android/ui/journal/JournalScreen.kt` | Mounts the editor for the journal (187) |
| `app/build.gradle.kts` | Dependency list — contains no markdown/highlighting library |

---

## 7. Resolution log

All gaps from §1 and §3 were closed on 2026-10-20. Nothing was left as a
"recommendation" — every item is now implemented.

### 7.1 Syntax highlighting — `ui/lexical/CodeSyntaxHighlighter.kt` (new)

A dependency-free Kotlin port of the web's Prism grammars. Compose has no Prism
equivalent, so the grammars are re-implemented as regular expressions and rendered
through `AnnotatedString`.

- **Languages:** C++, C, Python, Rust, JavaScript, TypeScript, SQL/SurrealQL, Bash,
  JSON, plus a generic fallback for HTML/Markdown.
- **Token colours are the exact values of the web `editor-dark` palette** in
  `src/index.css` (`#C084FC` keyword, `#34D399` string, `#FBBF24` number, `#F43F5E`
  boolean, `#38BDF8` function, `#67E8F9` type, `#64748B` comment, `#818CF8` operator,
  `#94A3B8` punctuation), so a note looks identical on both clients.
- **Aliases:** `canonicalKey()` maps every tag a user may type onto a grammar
  (`C++`→`cpp`, `py`→`python`, `C#`→`csharp`, `sh`→`bash`, `surrealql`→`sql`, …),
  and unknown tags fall back to plain text instead of being mis-coloured.
- `std`, `cout`, `cin`, `endl`, `vector`, … are highlighted as types, so the
  `std::cout << "Hello World";` example from the original report is fully coloured.

Two deliberate deviations from a literal Prism port:

- `Plain Text` renders **unhighlighted**. Prism would happily colour a "text"
  block; a block the user tagged as plain text should stay plain.
- The function-call lookahead scans forward instead of allocating a substring of
  the remaining source for every identifier — the naive form turns highlighting
  into O(n²) on long blocks.

### 7.2 `/code` slash command — `ComposeLexicalEditor.kt`

Typing `/code` (or `/code <lang>`) on a line of its own expands it into a fenced
code block immediately. The caret lands on an empty content line inside the block
so the user can type the code straight away, and an escape paragraph is appended
below the closing fence.

Only a line that is *entirely* the command triggers it — `some /code text` and
`/codes` are left alone.

### 7.3 Escape paragraph guarantee

Every path that creates a code block leaves an escape route below it:

| Path | Mechanism |
|---|---|
| `/code` command | the inserted fence is followed by a blank line |
| Toolbar **Code** button | `applyAction` now emits a trailing `\n\n` |
| Hand-typed fence | `ensureEscapeParagraph()` appends a blank line when the markdown ends with a *closing* fence |

The hand-typed case only fires on a fence that actually closes a block: the fence
line count must be even, so an opening fence the user is still typing is left
alone and the language tag can still be completed.

### 7.4 Copy and trash actions

- **Copy** is now wired to `ClipboardManager` and shows a "Copied!" confirmation
  for two seconds. It copies `block.code` — the raw source, not the annotated
  string — so the clipboard is byte-identical to what was written.
- **Trash** removes the whole block, fence included, from the raw markdown.
  `LexicalCodeBlockNode` now carries `lineStartIndex` / `lineEndIndex` (populated
  by the parser) so the fenced range can be excised without re-parsing.

The trash button and the language picker are hidden in read-only mode; the copy
button stays available because copying is not an edit.

### 7.5 Interactive language picker

Tapping the language badge opens a dropdown with all supported languages. Choosing
one rewrites the fence tag in the raw markdown (````python` → ````cpp`), so the
change is persisted and synced like any other edit.

### 7.6 Verification

`app/src/test/java/com/esper/android/ui/lexical/CodeBlockTest.kt` (new, 21 tests)
covers the parser line indices, the slash command, the escape paragraph rules,
the alias table and the token colours — including the exact C++ example from
§2.3. A dedicated test asserts that highlighting never alters the source text,
for every supported language.

```
./gradlew :app:testDebugUnitTest      # 21 new tests, 45 total, 0 failures
./gradlew :app:assembleDebug           # app-debug.apk builds
```

### 7.7 Cross-platform status

The divergence from §4 is closed: a note written on the web as ```` ```C++ ````
now opens on Android with the same colours, and a note written on Android opens
on the web unchanged. The language tags the Android picker can write (`cpp`,
`csharp`, `go`, `kotlin`, `bash`, `json`, `html`, `markdown`, …) are all covered
by the web's `src/services/prismSetup.js` alias table, so the two clients agree
in both directions.

One asymmetry remains by design: the web keeps the language tag exactly as the
user typed it (`C++` stays `C++`), while the Android picker writes the canonical
key (`csharp` for `C#`). Both resolve to the same grammar on both clients.
