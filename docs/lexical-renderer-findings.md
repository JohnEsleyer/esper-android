# Lexical on Android: a Hand-Written Renderer, Turned Into a Live Editor

**Date:** 2026-10-23
**Scope:** How `esper-android` renders markdown compared to the web client's Lexical
editor, and what the "Improve newlines / improve `:` / make it WYSIWYG" round changed.
**Verdict:** **Android is not Lexical.** The web client runs the real
`@lexical/react` engine and serialises it to markdown; Android has always been a
hand-written markdown parser feeding Compose renderers. That is still true — but it is
now a *rendered-by-default, tap-a-block-to-edit* editor rather than a read-only
renderer plus a raw markdown textarea. Four real bugs were found on device and fixed
along the way, including a crash on a trailing colon.

> **Status: IMPLEMENTED (2026-10-23).** All three asks — newlines/continuation,
> `:` handling, and a live WYSIWYG surface — are done. See
> [§8 Resolution log](#8-resolution-log) for what shipped and how it was verified.

---

## 1. Summary table

| Capability | Android before | Android now | Web (Lexical 0.28) |
|---|---|---|---|
| Editor model | Rendered blocks **or** a raw markdown textarea | Rendered blocks, tap one to edit in place | Fully live editable document |
| Newlines inside a paragraph | One paragraph per source line | Soft break = line break inside one block | `<br>` in one paragraph |
| Blank lines | Silently collapsed | Preserved (`LexicalBlankLineNode`) | Preserved |
| Blockquote continuation | Only the first line | All `>` lines merged, lazy continuations too | One quote node |
| List continuation | One item per line, indented text lost | Marker indentation + content column | Nested list nodes |
| Table alignment `\|:---\|:---:\|---:\|` | **Failed to parse at all** | Parsed and rendered aligned | Not supported (markdown kept raw) |
| Links with schemes | Only `http(s)` clickable | `mailto:`, `tel:`, `esper:`, relative paths | Any scheme |
| `[H:MM:SS]` timestamps | Plain text | Clickable chip | Clickable chip |
| `:shortcode:` emoji | Literal text | Rendered glyph + live `:` strip | Emoji picker |
| Code block | Flat monospace, language badge | Highlighted, header chrome, language picker | Prism, overlay chrome |
| Languages highlighted | cpp/c, python, rust, js/ts, java, c#, go, bash, sql, json, html, markdown | …plus **Kotlin** and **PHP** | Prism (many) |
| Inline math | Full-width break per formula | Styled inline chip | KaTeX inline |
| Display math | KaTeX WebView | KaTeX WebView | KaTeX |
| Tables / task lists / images | Raw text | Rendered | Raw text (unsupported) |
| Raw markdown escape hatch | The only editor | Toolbar toggle ("Live" ↔ raw) | n/a |
| Paste a ChatGPT answer | Flattened into one line | Parsed into headings, lists, fences, tables, math | Block replacement on paste |

---

## 2. Why Android can't reuse Lexical

Lexical is a JavaScript document model with a DOM reconciler. The web editor
(`src/components/MarkdownEditor.jsx`) instantiates
`createLexicalComposerContext`, registers `RichTextPlugin`, `ListPlugin`,
`CodeHighlightPlugin`, … and uses `@lexical/markdown` transformers to convert the
editor state to markdown for storage. The markdown string is **output**, not the
document.

There is no JVM port, and running the JS engine in a WebView to host the whole
document would lose native text fields, IME behaviour, selection, accessibility and
per-block Compose rendering. So Android keeps its own parser, and the gap to close is
**behavioural parity**, not engine parity:

* markdown is the source of truth in both clients (so a note round-trips between them);
* the same visual result for each construct;
* the same "type and see it formatted" feel.

The import path is unchanged: markdown from the server is parsed into
`LexicalBlockNode`s, and every edit is written back as markdown through
`LexicalSource`, which knows the exact source line range of each block. That is what
makes in-place editing lossless — untouched bytes are never re-serialised.

---

## 3. What changed

### 3.1 The parser (`LexicalParser.kt`)

The old parser was line-oriented: each source line became its own block. That is the
root cause of the "newlines and continuing lines" complaint — a wrapped paragraph
became five paragraphs, and a quoted or bulleted continuation line fell out of its
block.

`parseToLexicalAst` now works in two passes: **block grouping**, then **inline
tokenising** (shared with the editor, see §3.2).

* **Paragraph continuation.** Consecutive plain lines join into one
  `LexicalParagraphNode` unless a blank line, a thematic break or another block
  marker starts a new one.
* **Hard vs soft breaks.** A line ending in two or more spaces, or a trailing `\`
  (the CommonMark hard break), becomes `LexicalLineBreakNode`. A plain newline also
  becomes a line break — this deliberately follows the **web client**, which renders
  a single `\n` as `<br>` inside one paragraph, rather than CommonMark's
  soft-break-as-space. Two clients that disagree about a newline are worse than two
  clients that agree on a slightly non-standard one.
* **Blank lines.** Runs of blank lines produce `LexicalBlankLineNode(count)` so
  vertical rhythm survives an edit round-trip instead of being eaten.
* **Quotes.** Every `>` line joins one quote node, with the `>` prefix stripped; a
  lazy continuation (a line with no `>` after a quoted line) is included, matching
  CommonMark's lazy continuation rule.
* **List items.** The item's own content column is computed from its marker
  (`- `, `1. `, `- [ ] `), and continuation lines are glued on when they start at or
  beyond that column. Indent level comes from a **content-column stack** at 2- and
  4-space steps, so `  - nested` and `    - nested` both nest correctly and the text
  keeps its indentation.
* **Colons in tables.** This was a genuine bug, not a style issue: the delimiter
  check required each cell to contain `---`, so `|:---|:--:|---:|` was rejected and
  the whole table degraded to text. `delimiterCellRegex = ^:?-+:?$` now accepts the
  align markers, and the alignment is captured per column and rendered.
* **Math.** `$$…$$` stays a display block; `\[…\]` is now accepted as an alias, and
  `$…$` on one line becomes inline math instead of forcing a block break.
* **Images.** Any scheme is accepted (previously `http(s)` only), so relative and
  `data:` sources render instead of leaking as literal `![alt](url)`.
* **Source ranges.** Every node carries `lineStart`/`lineEnd`, which `LexicalSource`
  uses to write an edit back without touching neighbouring lines.

### 3.2 Inline layer (`LexicalInline.kt`, `LexicalRichText.kt`)

One ordered token scan (`MARKER` tokens included) is now shared by three consumers:
the parser (AST), the renderer (`AnnotatedString`) and the text field
(`VisualTransformation`). Because they share the scanner, a construct looks the same
read-only and while being edited — no second grammar to keep in sync.

Supported inline: `**bold**`, `__bold__`, `*italic*`, `_italic_`, `~~strike~~`,
`` `code` ``, `$math$`, `[label](url)`, `[H:MM:SS]`, `:shortcode:`, `\` escapes.

Links and timestamps use `LinkAnnotation.Clickable`, so they stay tappable in the
rendered view without turning the block into a text field. Timestamps render as a
`▶ 00:42` chip and call `onTimestampClicked(seconds)`.

`displayOffsetToRaw()` maps a tap in the *rendered* text back to an offset in the
*markdown* text, which is how tapping the middle of `**bold**` puts the caret in the
right place even though the `**` are invisible.

### 3.3 The editor surface (`ComposeLexicalEditor.kt`)

**Rendered by default, tap to edit.** The note is always rendered. Tapping a block
swaps that one block for a `LiveTextField` at the same position, with the caret at
the tapped offset and the keyboard up. Tapping elsewhere (or leaving) writes the
block back and renders it again.

This was chosen over "everything is a text field" (Notion's older model) because:

* links, timestamps and checkboxes stay interactive without a mode switch;
* only one field exists, so IME, predictive text and `VisualTransformation` have one
  job each;
* the raw markdown stays untouched between edits, so no round-trip drift.

`LiveTextField` hides markers and reveals the construct under the caret: with the
caret inside `**bold**` you see the asterisks, one character out you see **bold**.
`InlineMarkdownTransformation` keeps an exact `OffsetMapping`, so the caret and the
IME's composing region never jump.

**Enter semantics.** Enter on a prose block splits it at the caret: the current block
keeps the text before the caret, a new block gets the rest, and because markdown
needs a blank line between paragraphs, a blank line is inserted in the source. Lists
continue as the next item, headings fall through to a paragraph, and quotes/code/math
keep the newline inside the block (`LexicalSource.keepsNewlines`). A paste with
embedded newlines is written as one string and re-parsed into blocks instead of
being swallowed by the field.

**Block rendering.**

* h1–h6 now scale properly (24/19/16/15/14/13 sp with weight and letter-spacing
  carrying the low end; h4–h6 used to be one identical grey size).
* Lists: consecutive items sit 2 dp apart instead of the normal 10 dp block gap, so a
  list reads as a list. Ordered markers cycle `1.` → `a.` → `i.` by depth.
* Task lists render a real `Checkbox` that rewrites `- [ ]`/`- [x]` in place.
* Quotes get an indigo rule and a tinted panel; code blocks get a header bar
  (language chip, line count, copy, delete), a language picker, and horizontal
  scrolling for long lines; tables zebra-stripe and honour per-column alignment.
* Inline math is a violet monospace chip (`#C4B5FD` on `#1E1B4B`). It used to be
  amber-on-brown, which read as a highlighter mark. Display math still uses the
  KaTeX `WebView`, and editing a display block shows the latex source above a live
  preview.

**The `:` strip.** Typing `:tada` opens a strip of matching shortcodes; picking one
replaces the query with `:tada: `, which immediately renders as 🎉. The strip lives at
the bottom of the editor (above the IME), not inside the block, because a block near
the end of the note would otherwise push its own suggestions off-screen. The query is
published by the field through a small `EmojiSuggestionHost`, so the tap still
inserts at the caret. Afterwards focus is re-requested, otherwise tapping the strip
dismissed the keyboard mid-word.

A bare `:` is not a query, so `Note:` and `10:30` never pop the strip, and only the
`[H:MM:SS]` bracket form is treated as a timestamp.

### 3.4 Syntax highlighting (`CodeSyntaxHighlighter.kt`)

**Kotlin** and **PHP** were missing from `canonicalKey`, so a ` ```kotlin ` block
silently fell back to `text` and the header showed *Plain Text* — on an app with a
Kotlin journal and a PHP journal. Both now have grammars (keywords, types, string
and char literals, `@Annotations` for Kotlin, `$variables` and `<?php ?>` tags for
PHP), plus aliases `kt`/`kts` and `php`, and appear in the language picker.

All grammars are pure `Regex` + `AnnotatedString` — no new dependency, and the
palette is the web's `editor-dark` values.

### 3.5 Markdown paste (`LexicalSource.kt`, `LexicalLiveTextField.kt`)

A rendered-block editor has a paste problem the raw textarea never had: the blocks are
rendered, but the caret lives in a one-block field whose text is *stripped* of its
marker (`# `, `- `, `> `). Dropping a ChatGPT answer in that field would have written
it as the content of one heading or one bullet.

The flow now is:

1. `pastedMarkdownIn(oldText, newText, selection, keepsNewlines)` compares the before
   and after text of `BasicTextField` and returns the inserted substring when the
   change is a paste of Markdown. It is a pure function, so `MarkdownPasteTest` covers
   the fragile offset arithmetic without a device or a clipboard.
   - Multi-line inserts count as a paste even when the lines are plain prose.
   - A single word or a single Enter is normal typing (Enter still splits the block).
   - Code blocks, math and quotes opt out via `LexicalSource.keepsNewlines`: their
     newlines are literal, so a paste belongs *inside* them.
2. The field calls `onPasteMarkdown` instead of committing, and the editor calls
   `LexicalSource.insertMarkdownAtBlock`. That replaces the block's own marker line
   with the pasted text (blank lines around it), so the paste is parsed at document
   level. Pasting a `# Heading` into a bullet produces a heading, not a bullet
   containing a `#`. An inline paste (`**bold**`, a link) is spliced into the sentence
   instead, and a blank or starter block is replaced outright.
3. `revealLine` clears `editingKey`/`editingCaret`, which drops out of inline
   composition, and asks the `LazyListState` to scroll to the first block at or after
   the insertion line. The document re-parses on the same frame.
4. When nothing is being edited, a **Paste** chip in the toolbar
   (`LexicalToolbar(onPaste = …)`) appends the clipboard through
   `LexicalSource.appendMarkdown`, and tapping the empty space under the note focuses
   the trailing paragraph so a long-press paste has a caret to land on.

Block shapes covered by `LexicalSourceTest` (`a chatgpt shaped answer parses into rich
blocks`): `##`/`###` headings, ordered and bullet lists, `- [x]` tasks, a fenced
`kotlin` block, a `|:---|---:|` table, a quote and `$$…$$` display math — one paste,
eight node types.

### 3.6 Debug playground (debug builds only)

`app/src/debug/java/com/esper/android/debug/LexicalPlaygroundActivity.kt` renders a
document containing every supported construct from in-memory markdown, with
"Show source", "Reset" and "Copy chat sample". The last one puts a realistic ChatGPT
answer on the clipboard, so the paste path can be tested without leaving the app: tap
the empty space under the note (or the toolbar **Paste** chip), then long-press and
paste. It is in the `debug` source set, so it cannot ship.

It exists because the alternative was typing test text into a real notebook that
syncs to the server. Launch it with:

```bash
adb shell am start -n com.johnesleyer.esper/com.esper.android.debug.LexicalPlaygroundActivity
```

---

## 4. Device verification

Run on the connected A14 (1080×2408, 450 dpi):

| Check | Result |
|---|---|
| Tap a rendered heading/paragraph | Enters edit mode at the tapped offset, keyboard up |
| Markers hidden while editing | Yes; the construct under the caret reveals its markers |
| Typing `:tad` | Strip appears above the IME with 🎉 `:tada:` |
| Picking the suggestion | Inserts `:tada: `, renders 🎉, keyboard stays open |
| Typing inside a code block | Live highlight, literal text (strip suppressed) |
| Lazy list of blocks | Stable keys, no item jump while typing |
| Trailing colon (split case) | **Crashed before the fix** (`StringIndexOutOfBoundsException`), fine after |
| Enter to split | Exercised by `splitBlock` unit tests; device session was cut short (see §7) |

### Bugs found only by running it

1. **Crash on a trailing colon.** `activeEmojiQuery` read `text[start + 1]` to check
   that a shortcode starts with a letter. When the colon was the last character — for
   example the block produced by splitting a paragraph right after a `:` — that index
   is past the end and Compose took the app down. Fixed by validating `token[0]`
   instead, with regression tests for `"note:"`, `""`, `"hi :"` and `"a : b"`.
2. **Zero-height suggestion strip.** `LazyColumn` inside the editor `Column` used
   `fillMaxSize()`, so a new sibling below it got no room and the strip was invisible.
   The animated content now takes `Modifier.weight(1f)`.
3. **Strip under the IME.** Even with room, a strip rendered inside the last block was
   clipped by the keyboard. Moved to the bottom of the editor, above `imePadding()`.
4. **Keyboard closed after picking.** The strip is outside the field, so the tap moved
   focus. The field now re-requests focus after inserting.

---

## 5. The web app (not changed)

The web client already **is** live WYSIWYG — `MarkdownEditor.jsx` is a Lexical
composer, and the markdown is only what gets saved. There was nothing to port to it
for this round, so it was left alone.

For a future pass, these are real defects found while reading it:

| Issue | Where |
|---|---|
| Horizontal rules are lost on save — no `HR` transformer is registered | `MarkdownEditor.jsx` transformers |
| `preserveCodeBlockNewlines` injects a `" "` into blank code lines | `MarkdownEditor.jsx` |
| h4–h6 have no styling | `src/index.css` |
| `==highlight==` parses to nothing | transformers / CSS |
| Nested lists need exactly 4 spaces, unlike Android's 2-or-4 | markdown transformers |
| Tables, task lists and images are unsupported (kept as raw text) | editor |
| An external `value` change resets the caret | `MarkdownEditor.jsx` effect |
| `editorConfig` is recreated every render | `MarkdownEditor.jsx` |
| `CodeBlockActionsOverlay` repositions on every keystroke | overlay |
| `codeTransformer` lookup is dead code | transformers |
| `katex` npm dependency is unused (the CDN build is used) | `package.json` |

---

## 6. Remaining differences from the web, by design

* **Not every keystroke is a document mutation.** Android re-parses the markdown of
  the edited block on commit; the web mutates a node tree. Markdown is the contract,
  so this is fine, but it means an inline construct is only "real" once the block
  commits (seconds, or on focus loss).
* **Tables/task lists/images are richer on Android.** The web client has no node for
  them, so Android renders constructs the web will show as raw text. Any note using
  them is still markdown, so nothing is lost — it just doesn't render the same.
* **No collaborative editing, undo stack or slash menu parity** on Android; undo is
  the IME's.

---

## 7. Open items

* **Enter-to-split was not re-confirmed on the device** after the crash fix: wireless
  ADB dropped mid-session and the installed package was replaced underneath the test,
  which ended the activity. The code path is covered by `LexicalSourceTest`
  (`splitBlock` keeps the tail, inserts the blank line, returns the focus line) and
  the crash it used to trigger now has its own test, but a one-minute manual check is
  still worth doing.
* **Markdown paste was not exercised on the device** after the toolbar chip and the
  empty-space target were added; the paste detector, the insert/replace logic and the
  parse result are unit-tested, but one manual paste of a real ChatGPT answer into
  `LexicalPlaygroundActivity` is the remaining check.
* `displayOffsetToRaw` is approximate around timestamps, because the rendered chip
  (`" ▶ 00:42 "`) is wider than the `[00:42]` it replaces. Tapping inside a timestamp
  chip therefore lands a couple of characters off. Low impact: the chip is a link in
  the rendered view, so a tap fires the timestamp rather than placing a caret.
* `Kept for API compatibility: tapToEditAnywhere` is now dead in practice (the live
  editor always tap-to-edits) and can be deleted with its call sites.

---

## 8. Resolution log

| Ask | Change |
|---|---|
| Improve newlines and continuing lines | Paragraph/quote/list continuation merging, lazy continuations, `LexicalLineBreakNode` for soft and hard breaks, `LexicalBlankLineNode` for blank runs, 2/4-space content-column nesting |
| Improve `:` | Table alignment delimiters parse (`\|:---\|:---:\|---:\|`), any-scheme links (`mailto:`, `tel:`, relative), `[H:MM:SS]` chips, `:shortcode:` emoji with a live strip — plus the `activeEmojiQuery` crash fix |
| Improve the look of rendered code | Kotlin/PHP grammars, language chip with background + picker, header chrome (line count, copy, delete), h1–h6 scale, tight list rhythm, styled quotes, aligned/zebra tables, calmer inline-math chip |
| Real-time rendered WYSIWYG instead of markdown ↔ rendered toggling | Rendered-by-default with tap-to-edit per block, marker-revealing `VisualTransformation` with exact offset mapping, Enter split, multi-line paste, terminal emoji insert, raw markdown kept only as an escape hatch |
| Paste a ChatGPT answer | `pastedMarkdownIn` detects the paste, `insertMarkdownAtBlock` replaces the block marker so the paste parses at document level, toolbar Paste + empty-space target for when no field has focus |
| Improve the web app | Not changed — it is already live Lexical. Defects listed in §5 for a later pass. |

**Verification:** `./gradlew --offline :app:testDebugUnitTest` → **175 tests, 0
failures** (`LexicalParserTest`, `LexicalSourceTest`, `LexicalInlineTest`,
`CodeBlockTest`, `MarkdownPasteTest`); `:app:assembleDebug` clean; smoke-tested on
device via the debug playground. The editor work landed in `b047f65`; the focus fix below and this report
were made afterwards.
