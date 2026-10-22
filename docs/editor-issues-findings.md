# Editor issues: Gemini paste, undo/redo, chapter delete

**Date:** 2026-10-23
**Scope:** `esper-android` — `ui/lexical` (paste + history), `ui/workspace`
(chapter list). Builds on `lexical-renderer-findings.md`.

Three problems reported on the device after the live editor shipped. The first one
turned out to be two bugs stacked on top of each other; the middle one is a missing
feature; the last one is a visibility bug, not a missing action.

---

## 1. Pasting Gemini output does not render

### What the user sees

A Gemini answer pasted into a note stays as a wall of plain text: paragraphs run
together, `1.` items lose their list styling, and code is ordinary prose. Nothing
crashes, and the note saves.

### Evidence

Reproduced on the A14 with the real clipboard payload. The payload is 3465
characters and, from the paste log, starts:

```
Welcome to Rust! Rust is a system programming language ... ownership.
To get you up to speed efficiently, we'll break down ...
1. Variables and Mutability (let and mut)
By default, variables in Rust are immutable (read-only). ...
fn main() {
    let x = 5; // Immutable
    // x = 6;  // Error! Cannot mutate immutable variable

    let mut y = 10; // Mutable
```

Two facts about Gemini's clipboard format explain the result:

1. **Every paragraph is one long line, joined to the next by a single `\n`.** There
   are no blank lines anywhere in the prose, so the parser correctly (but uselessly)
   reads the whole answer as one paragraph with soft line breaks.
2. **Code arrives without fences**, as a `fn main() {` line followed by 4-space
   indented lines; bullets arrive as `" * text"` (leading space).

The paste *detector* fired (`change old=29 new=3485 … paste=3465`) and
`handleMarkdownPaste` ran (`block=LexicalParagraphNode … docAfter=4463`), so this was
not a detection failure. Two separate defects:

* **Stale-field clobbering.** Right after the paste committed, `BasicTextField`
  delivered one more `onValueChange` with the *unchanged* block text
  (`change old=29 new=29`). The `else` branch of `onValueChange` called
  `onTextCommitted(newText)` unconditionally, which wrote the old 29-character
  paragraph back over the first line of the freshly pasted document. The pasted
  block was left without its leading line and everything below shifted up into the
  same paragraph.
* **No normalisation of pasted text.** Even with the clobbering fixed, pasted
  single-newline prose is one paragraph, and unfenced indented code is prose too.

### Fix

* `LiveTextField` now ignores no-op changes (`newText == oldText`) and latches a
  `pasteHandled` flag so the field cannot commit again after handing over to the
  editor.
* `LexicalSource.normalizePastedMarkdown` rebuilds block structure from the
  flattened shape:
  * a prose line that ends in sentence punctuation (or is full width) starts a new
    paragraph — blank line inserted;
  * a short prose line without punctuation is *joined* to the next line, so
    ChatGPT-style hard wrapping does not become one paragraph per visual line;
  * lines ending in `{`/`:` followed by indented lines become a fenced code block,
    with a small language sniff (Rust, Python, JS/TS, Java);
  * `" * item"` becomes `* item`, `1.`/`-`/`+` lines become their own list items,
    headings/tables/quotes/rules get a blank line before them;
  * existing fences and their contents are copied verbatim, and a line ending in
    two spaces keeps its hard break.

Applied to pasted text only (`insertMarkdownAtBlock`, `appendMarkdown`), never to
the document the user is typing in.

**Fixture:** the full 3465-character Gemini answer is in
`LexicalSourceTest.a gemini answer is rebuilt into paragraphs lists and code`, so
the regression is pinned to the real payload rather than a hand-written one.

---

## 2. No undo / redo

Selecting text and pressing **Paste** replaced the whole document, with no way back
except the IME's per-field undo (which cannot undo anything that spanned blocks).

### Fix

The editor keeps a document-level history of markdown snapshots:

* every `commitText` records the previous document, coalesced to one entry per
  700 ms burst so typing a word is one undo step;
* undo/redo move between snapshots, clear the inline editor (so the field cannot
  replay stale text over the restored document), and save through the same debounce
  as typing;
* two new toolbar buttons (`Undo`, `Redo`) with disabled states, and Ctrl+Z /
  Ctrl+Shift+Z when a hardware keyboard is attached.

History is capped at 100 entries and is cleared when `initialMarkdown` changes.

---

## 3. Chapters have no delete button

`ChapterDrawerContent` *does* render a delete icon, but only when the notebook has
more than one chapter (`if (pages.size > 1)`). In a notebook with a single chapter —
including a brand-new one — the row has no delete affordance at all, and the icon is
a 14 dp glyph inside a 24 dp button at 60 % opacity, which reads as decoration.

### Fix

* The delete button is always shown (the data layer already supports deleting the
  last page), with a 32 dp hit target and a full-opacity icon.
* The button is suppressed only in read-only mode, where the whole action group is
  hidden today.
* The confirmation dialog stays as-is (`ConfirmDeleteDialog`).

---

## Resolution log

| Report | Cause | Change |
|---|---|---|
| Gemini paste does not render | Stale `onValueChange` re-committed the old block text over the paste; pasted text kept Gemini's one-line-per-paragraph, unfenced-code shape | No-op guard + paste latch in `LiveTextField`; `normalizePastedMarkdown` rebuilds paragraphs, lists and fences before insertion |
| No undo/redo | History was never modelled; only the IME had undo, scoped to one field | Snapshot history in `ComposeLexicalEditor`, coalesced, with toolbar buttons and Ctrl+Z/Ctrl+Shift+Z |
| Chapters have no delete button | Delete icon hidden when `pages.size <= 1`, otherwise 14 dp at 60 % opacity | Always visible, 32 dp target, full-opacity icon; read-only still hides it |

**Verification:** `./gradlew --offline :app:testDebugUnitTest` → **192 tests, 0
failures** (new: `MarkdownNormalizeTest`, `LexicalHistoryTest`); `:app:assembleDebug`
clean.

The failure itself was reproduced on the A14 (`LexicalPlaygroundActivity`, real
clipboard payload, log shown above). The fixed build could not be installed: the
device dropped off ADB mid-session (`No connected devices!`), so the final device
pass on paste, undo/redo and the chapter row is outstanding. The three code paths
themselves are covered by the new tests, and the Gemini payload is a checked-in
fixture (`app/src/test/resources/gemini-rust.md`).
