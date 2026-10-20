# Android Bookshelf Findings: Books Hidden Outside Their Shelf, and Laggy / Glitching Covers

**Date:** 2026-10-20
**Scope:** `esper-android` — (1) books missing from the *All Notebooks* view while visible inside a shelf, (2) laggy scrolling and glitching covers in the bookshelf grid.
**Verdict:** Two independent problems, both confirmed by reading the code.

> **Status: RESOLVED (2026-10-20).** Every defect and performance issue documented
> below has been fixed — see [§4 Resolution log](#4-resolution-log).

1. **The shelf filter is a substring match on a CSV**, the tag filter and the shelf
   filter are applied **independently and stacked**, the **Library** button clears only
   one of them, and the pager is **never reset** when the filter changes. The result is
   that "All Notebooks" is rarely what the user thinks it is, and a shelf can silently
   render the wrong slice of its books — or nothing at all.
2. **The cover artwork is decoded twice and its "loaded" flag is scoped to the item, not
   the image.** Every time a card scrolls out of view and back, Compose disposes and
   recreates its composition, the flag resets, and the **book title is drawn over the
   cover** for a frame or two before Coil's `onSuccess` fires. That is the glitch.

---

## 1. Books missing from "All Notebooks"

Everything below happens in `app/src/main/java/com/esper/android/ui/bookshelf/BookshelfScreen.kt`.

### 1.1 The filter chain

`BookshelfScreen.kt:93-100`:

```kotlin
val filtered = remember(sortedNotebooks, selectedTagFilter, searchQuery, selectedShelfId, shelves) {
    sortedNotebooks.filter { book ->
        val tagMatches = selectedTagFilter == "All" || book.tagsCsv.contains(selectedTagFilter, ignoreCase = true)
        val searchMatches = searchQuery.isBlank() || book.title.contains(searchQuery, ignoreCase = true)
        val shelfMatches = selectedShelfId == null || shelves.find { it.id == selectedShelfId }?.notebookIdsCsv?.contains(book.id) == true
        tagMatches && searchMatches && shelfMatches
    }
}
```

`"All Notebooks"` is not a special mode — it is simply `selectedShelfId == null`. There
is no separate "all books" query: the grid is always `sortedNotebooks` narrowed by
**tag AND search AND shelf**, and then paginated.

### 1.2 Defect A — the shelf filter is a substring match on a CSV

`BookshelfScreen.kt:97`:

```kotlin
shelves.find { it.id == selectedShelfId }?.notebookIdsCsv?.contains(book.id) == true
```

`notebookIdsCsv` is a comma-joined list built in
`data/sync/SyncManager.kt:146`:

```kotlin
notebookIdsCsv = s.notebookIds?.joinToString(",") ?: "",
```

so the shelf holds `"book-a,book-b,book-c"` and the check is a raw `String.contains`.
That is not a membership test:

- **False positives** — the id `book` matches the shelf `"book-a"`, so a book appears in
  a shelf it was never added to.
- The seeded example books use ids of the form `sample-1-<hash>`
  (`server/src/seed.rs:488`), so any two ids that share a prefix can collide.

The same pattern is used for the tag filter (`BookshelfScreen.kt:95`), where
`tagsCsv.contains("Art")` also matches `"Art History"`.

Membership must be tested against the split list, not the joined string.

### 1.3 Defect B — a stale shelf id empties the whole shelf

`BookshelfScreen.kt:97` also has an operator-precedence trap. Read as written it is:

```
selectedShelfId == null  ||  (shelves.find { … }?.notebookIdsCsv?.contains(book.id) == true)
```

If `selectedShelfId` points at a shelf that is **not in the `shelves` list**, the
`find` returns `null`, `?.contains(...)` short-circuits to `null`, and `null == true` is
`false`. The result is that **every book is filtered out** and the shelf renders
"No notebooks found" — with no indication that the shelf id is simply unknown.

The same happens when the shelf exists but its `notebookIdsCsv` is empty.

### 1.4 Defect C — the tag filter and the shelf filter stack, and neither "Library" nor "Clear filter" resets both

Two independent filters are ANDed together, but the two escape hatches only clear one
each:

| Action | Clears | Does **not** clear |
|---|---|---|
| Bottom nav **Library** (`BookshelfScreen.kt:370`) → `onSelectTagFilter("All")` | the tag filter | **the shelf filter** |
| **"Clear filter"** button (`BookshelfScreen.kt:481-487`) → `selectedShelfId = null` | the shelf filter | **the tag filter** |
| Drawer → **All Notebooks** (`BookshelfScreen.kt:202`) | the shelf filter | the tag filter |

So the "Library" tab — the most natural way to say "show me everything" — leaves the
shelf filter in place. The grid keeps showing one shelf while the user believes they
returned to the whole library. To see the remaining books the user has to walk through
each shelf individually, which is precisely the reported *"some books can only be seen in
their respective shelf"*.

The mirror image is the reverse: selecting a shelf does **not** clear an active tag
filter, so a shelf can render empty (or nearly so) while the tag chip is still lit.

The heading does show the active shelf title (`BookshelfScreen.kt:441`,
`shelfTitle = currentShelf?.title ?: "All Notebooks"`) and a "Clear filter" button, so
the state is discoverable — but nothing in the bottom navigation communicates it.

### 1.5 Defect D — the pager is not reset when the filter changes

`BookshelfScreen.kt:83-113`:

```kotlin
val pageSize = 6
var currentPage by remember { mutableIntStateOf(1) }

val totalPages = remember(filtered) { maxOf(1, ceil(filtered.size.toDouble() / pageSize).toInt()) }
LaunchedEffect(totalPages) { if (currentPage > totalPages) currentPage = totalPages }
val pagedBooks = remember(filtered, currentPage) { filtered.drop((currentPage - 1) * pageSize).take(pageSize) }
```

`currentPage` is reset in exactly one place — the search box (`BookshelfScreen.kt:327`).
It is **not** reset when the shelf changes, when the tag filter changes, or when the
Library button is tapped.

The `LaunchedEffect(totalPages)` only clamps *downwards*, and only when `totalPages`
actually changes. Two concrete failures:

- **Wrong slice.** "All Notebooks" with 12 books on page 2 (books 7-12). Selecting a
  shelf with 8 books keeps `totalPages == 2`, so the effect never fires and the grid
  keeps showing `drop(6)` — books 7-8 of that shelf. The header still reports
  "8 books available" (`BookshelfScreen.kt:475`) while only 2 are on screen.
- **Empty page.** If the shelf ends up with fewer books than the current page offset,
  `pagedBooks` is empty and the screen shows the "No notebooks found" empty state
  (`BookshelfScreen.kt:492-505`) even though the shelf has books.

The pager itself reports `Page X of Y (N books)`
(`ui/components/CustomBottomNavBar.kt:126`) with `totalBooksCount = filtered.size` — the
*filtered* count, while only `pageSize` books are actually rendered. A library of 30
books shows "6 books" on one screen behind a "Page 1 of 5 (30 books)" bar, which is easy
to read as "the other books are missing".

### 1.6 Summary of issue 1

| # | Defect | Effect |
|---|---|---|
| A | Shelf membership tested with `String.contains` on a CSV | Wrong books in a shelf |
| B | Unknown shelf id ⇒ `null == true` ⇒ nothing matches | Whole shelf renders empty |
| C | Tag filter and shelf filter stack; each escape hatch clears only one | "All Notebooks" is not reachable by the obvious route |
| D | `currentPage` never reset on filter change | Wrong slice, or a spurious empty page |

---

## 2. Laggy scrolling and glitching covers

### 2.1 The glitch — the "loaded" flag is scoped to the item, not the image

`ui/components/BookCard.kt:38-41`:

```kotlin
val coverModel = remember(notebook.coverImage) {
    CoverImageHelper.resolveCoverModel(notebook.coverImage)
}
var isImageLoaded by remember(coverModel) { mutableStateOf(false) }
```

and the flag drives **two** pieces of the card:

```kotlin
AsyncImage(
    model = coverModel,
    onSuccess = { isImageLoaded = true },
    onError = { isImageLoaded = false },
    …
)
if (isImageLoaded) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))   // vignette
}
…
if (coverModel == null || !isImageLoaded) {
    Text(text = notebook.title, …)          // title drawn ON TOP of the artwork
}
```

`isImageLoaded` lives in the **card's composition scope**. A `LazyVerticalGrid`
(`BookshelfScreen.kt:507-522`) disposes the composition of any item that scrolls out of
view and recreates it when the item comes back. On recreation `isImageLoaded` starts at
`false` again, so:

1. the book title is drawn **over the cover artwork**,
2. the vignette overlay disappears,
3. Coil serves the image from its memory cache and fires `onSuccess`,
4. the title vanishes and the vignette returns.

That is a one-or-two-frame flash of the title over the cover, every single time a card
scrolls out of view and back — exactly the "covers glitch sometimes" report. The same
pattern exists in `ui/workspace/TabsScreen.kt:186-213`, so the tab list glitches too.

The state is also wrong in the other direction: `onError` sets `isImageLoaded = false`,
so a cover that fails to load permanently shows the title over a broken image.

### 2.2 The lag — the cover is decoded again on every scroll-back

`BookCard.kt:38-39` rebuilds the model inside a `remember` keyed on the cover string.
When the item's composition is destroyed and recreated (see above), that `remember` is
re-evaluated too, so `CoverImageHelper.resolveCoverModel` runs again — and for a data
URI cover that means a **base64 decode on the UI thread, during composition**
(`ui/theme/CoverTheme.kt:357-371`):

```kotlin
val clean = payload.replace("\n", "").replace("\r", "").replace(" ", "").trim()
decodeBase64(clean)
```

That is three intermediate string copies plus a decode, on the main thread, for every
cover that scrolls back into view.

The covers themselves are not cheap to draw either. `docs/client-svg-covers.md` (in the
main repo) specifies that every vector cover is a 1000×1440 SVG containing a `<filter>`
with `<feGaussianBlur>`, radial gradients and text. Coil renders them through
`SvgDecoder` (`EsperApplication.kt:13-15`):

```kotlin
.components {
    add(DataUriFetcher.Factory())
    add(SvgDecoder.Factory())
}
```

A blurred SVG is one of the most expensive things to rasterise, and it is rasterised at
device resolution inside a container that is also `clip`ped to a rounded shape
(`BookCard.kt:54-58`), which forces an offscreen layer for every visible card.

### 2.3 The lag — the pull-to-refresh wrapper re-lays-out the grid on every frame

`ui/components/PullToRefresh.kt:95-106` wraps the whole screen:

```kotlin
Box(
    modifier = modifier.fillMaxSize().nestedScroll(nestedScrollConnection)
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = with(density) { (animatedOffset * 0.65f).toDp() })
    ) {
        content()          // <- the LazyVerticalGrid
    }
```

`animatedOffset` is an `animateFloatAsState` (`PullToRefresh.kt:48-52`) driven by
`pullDistance`, and `pullDistance` is mutated from the nested-scroll connection on
**every scroll frame** in which the grid cannot consume the whole gesture
(`PullToRefresh.kt:71-83`):

```kotlin
override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    if (source == NestedScrollSource.UserInput && available.y > 0f) {
        val newDistance = pullDistance + (available.y * friction)
        pullDistance = newDistance.coerceAtMost(thresholdPx * 1.6f)
        return Offset(0f, available.y)
    }
    return Offset.Zero
}
```

Because `animatedOffset` is read inside the content container's modifier chain, every
change to it **recomposes and re-places the entire grid** — all visible cards included —
for each animation frame of the pull. `Modifier.offset` is a placement modifier, so each
of those frames also re-lays-out the lazy grid's items.

The offset is applied unconditionally, even when it is zero (`PullToRefresh.kt:103`), so
the placement pass is never skipped.

`pullDistance` is only reset on a fling (`PullToRefresh.kt:85-91`), so an interrupted
gesture can leave the list sitting at a non-zero offset.

### 2.4 Secondary — the theme is recomputed on every card recomposition

`BookCard.kt:35`:

```kotlin
val theme = CoverThemeEngine.getTheme(notebook.coverPreset, notebook.coverImage)
```

`getTheme` (`ui/theme/CoverTheme.kt:298-310`) does a linear scan over ~15 presets with
`equals(ignoreCase = true)` and, for covers whose preset is not in the list, hashes the
**entire** cover string:

```kotlin
val hash = kotlin.math.abs(coverImageUrl.hashCode())
```

It is not wrapped in a `remember`, so it runs on every recomposition of every card —
which, per §2.3, happens on every frame of a pull gesture.

### 2.5 Secondary — `DataUriFetcher` is unreachable *for book covers*

`CoverImageHelper.resolveCoverModel` (`ui/theme/CoverTheme.kt:326-347`) converts every
data URI into a `ByteArray` before it reaches Coil:

```kotlin
if (trimmed.startsWith("data:", ignoreCase = true)) {
    return decodeDataUriToBytes(trimmed)
}
```

Coil therefore never sees a `data:` **Uri** for a book cover, so the custom
`DataUriFetcher` (`ui/theme/CoverTheme.kt:424-452`), which only matches `Uri` models
with a `data` scheme, is never invoked for covers — even though it is registered in
the image loader (`EsperApplication.kt:13-15`).

It is *not* entirely dead: the markdown image path passes the raw URL straight to Coil
(`ui/lexical/ComposeLexicalEditor.kt:440-441`, `model = block.imageUrl`), so a data URI
embedded in a note does reach `DataUriFetcher`. The point stands for covers, and the
duplication — the same base64 is decoded by `resolveCoverModel` *and* again by
`DataUriFetcher` on the markdown path — is worth knowing about.

### 2.6 Summary of issue 2

| # | Defect | Effect |
|---|---|---|
| A | `isImageLoaded` is item-scoped, not image-scoped | Title flashes over the cover on every scroll-back |
| B | Cover model (base64 decode) rebuilt on every scroll-back | Main-thread work during scroll |
| C | Blurred 1000×1440 SVG rasterised inside a clipped layer | Expensive per-frame draw |
| D | `Modifier.offset` on the grid container, driven by a per-frame animated state | Full grid re-layout + recomposition per pull frame |
| E | `CoverThemeEngine.getTheme` not memoised | Redundant work per card recomposition |
| F | `DataUriFetcher` unreachable | Dead code / false confidence |

---

## 3. Recommendations

### Issue 1

| # | Change | Where |
|---|---|---|
| 1 | Test shelf membership against the split list: `notebookIdsCsv.split(",").contains(book.id)` — better, keep the id list as a `List<String>` in the entity instead of a CSV | `BookshelfScreen.kt:97`, `SyncManager.kt:146`, `Entities.kt:82-91` |
| 2 | Make an unknown shelf id fall back to "no filter" instead of "match nothing" | `BookshelfScreen.kt:97` |
| 3 | Make the filters mutually exclusive: selecting a shelf clears the tag filter, and the Library button clears **both** | `BookshelfScreen.kt:370`, `481-487` |
| 4 | Reset `currentPage = 1` whenever `filtered` changes (a `LaunchedEffect(filtered)`), not only when `totalPages` shrinks | `BookshelfScreen.kt:106-108` |
| 5 | Show the shelf name and the tag in the pager so the state is unambiguous | `CustomBottomNavBar.kt:126` |
| 6 | Match tags against the split list as well | `BookshelfScreen.kt:95` |

### Issue 2

| # | Change | Where |
|---|---|---|
| 7 | Key the loaded flag on the **notebook id**, not on `coverModel`, and derive it from Coil's state (`AsyncImagePainter.state`) instead of a local flag, so recycling cannot reset it | `BookCard.kt:38-41` |
| 8 | Do not draw the title over loaded artwork — cross-fade it instead | `BookCard.kt:157-170` |
| 9 | Memoise the cover model and the theme with `remember(notebook.id)` / `remember(notebook.coverPreset, notebook.coverImage)` | `BookCard.kt:35-39` |
| 10 | Replace `Modifier.offset` with `Modifier.graphicsLayer { translationY = … }` so the offset does not trigger a layout pass | `PullToRefresh.kt:100-106` |
| 11 | Reset `pullDistance` when the gesture ends, not only on fling | `PullToRefresh.kt:85-91` |
| 12 | Prefer a pre-rasterised cover (WebP/PNG) over a blurred SVG for the grid, or drop the `feGaussianBlur` from the seeded covers | `server/src/seed.rs` |
| 13 | Remove the unreachable-for-covers `DataUriFetcher`, or route cover data URIs through it instead of pre-decoding to `ByteArray` (which would also remove the duplicate base64 decode) | `EsperApplication.kt:13-15`, `CoverTheme.kt:326-355`, `424-452` |

---

## 4. File reference

| File | Role |
|---|---|
| `ui/bookshelf/BookshelfScreen.kt` | Filter chain (93-100), pagination (83-113), drawer shelf list (195-265), Library button (370), shelf heading (437-489), grid (507-522) |
| `ui/components/BookCard.kt` | Cover model + loaded flag (38-41), `AsyncImage` (61-67), vignette (69-76), title overlay (157-170) |
| `ui/components/CustomBottomNavBar.kt` | Pager (60-146), nav items incl. Library (150-222) |
| `ui/components/PullToRefresh.kt` | Nested-scroll connection (60-93), offset wrapper (95-106) |
| `ui/theme/CoverTheme.kt` | `CoverThemeEngine.getTheme` (298-310), `CoverImageHelper.resolveCoverModel` (326-355), `decodeDataUriToBytes` (357-371), `DataUriFetcher` (424-452) |
| `EsperApplication.kt` | Coil `ImageLoader` setup — `DataUriFetcher` + `SvgDecoder` (10-17) |
| `data/sync/SyncManager.kt` | Shelf upsert with `notebookIdsCsv` (137-150) |
| `data/local/EsperDao.kt` | `observeAllNotebooks` (19-24), `observeAllShelves` (115-116) |
| `data/model/Entities.kt` | `ShelfEntity.notebookIdsCsv` (82-91) |
| `ui/workspace/TabsScreen.kt` | Same `isImageLoaded` glitch pattern (186-213) |
| `docs/client-svg-covers.md` (main repo) | Cover formats; SVGs are 1000×1440 with a Gaussian blur filter |

---

## 4. Resolution log

Everything documented above was fixed on 2026-10-20.

### 4.0 The actual cause of "books missing from All Notebooks"

On-device verification (Room database + screenshots) showed the shelf filters were
**not** the reason books went missing. The real cause was the **manual pagination**:

```kotlin
val pageSize = 6                       // only six books rendered at a time
val pagedBooks = filtered.drop((currentPage - 1) * pageSize).take(pageSize)
```

The library held eight books, so the grid showed six of them and hid the rest behind a
thin pager strip at the very bottom of the screen. The books that happened to sort last
— the one without an artwork — were on page 2, while in its own shelf (a single book)
the same book fitted on page 1 and was visible. That is exactly the reported symptom:
*"all notebooks only displays the books with image covers… that book can only be seen in
its bookshelf"*.

`LazyVerticalGrid` is already a virtualised list, so the pagination was never needed.
It has been **removed**: the grid now renders the whole filtered library and the user
scrolls through it, matching the web client. The pager row was dropped from
`CustomBottomNavBar`, which now only carries the navigation items.

### 4.1 Filtering (defects A–D)
The rules now live in one testable place, `ui/bookshelf/BookshelfFiltering.kt`, and
`BookshelfScreen.kt` calls into it:

| Defect | Fix |
|---|---|
| A — substring membership | `BookshelfFiltering.shelfBookIds()` splits the CSV into an exact `Set<String>`; membership is `set.contains(id)`, never `csv.contains(id)`. Tags are matched the same way, with `equals(ignoreCase = true)` per tag. |
| B — stale shelf id | `shelfFilterActive` is `selectedShelfId != null && currentShelf != null`. An unknown or deleted shelf id therefore degrades to "no shelf filter" instead of `null == true` hiding every book. |
| C — stacked filters | Selecting a shelf clears the tag filter; selecting a tag chip clears the shelf filter. The bottom-nav **Library** button and the header **Clear filter** button now reset *both* filters plus the search box and the page. |
| D — stale pager | `LaunchedEffect(selectedTagFilter, selectedShelfId, searchQuery) { currentPage = 1 }` resets the pager on any filter change, and the clamp in `LaunchedEffect(totalPages)` is kept as a safety net. |

The "Clear filter" button also appears when a tag or a search is active, not only when a
shelf is selected, and the tag chips only highlight when no shelf filter is on — so the
active filter is always visible in the UI.

### 4.2 Cover glitch and scroll lag

| Fix | Detail |
|---|---|
| Glitch (A) | The item-scoped `isImageLoaded` flag is gone from `BookCard.kt` and `TabsScreen.kt`. The centred fallback title renders only when `coverModel == null` (matching the web contract), so a recycled grid item never flashes the title over the artwork. The vignette is drawn unconditionally when artwork exists. |
| Decode lag (B) | `CoverImageHelper.resolveCoverModel` now caches resolved models in a thread-safe LRU (`LinkedHashMap`, 120 entries) keyed by the cover string. A base64 SVG data URI is decoded exactly once instead of on every scroll-back. `clearCache()` is exposed for bulk refreshes. |
| Base64 cost | `decodeBase64` strips whitespace in a single `filterNot { it.isWhitespace() }` pass instead of three chained `replace()` calls. |
| Theme cost (E) | `CoverThemeEngine.getTheme(...)` is wrapped in `remember(notebook.coverPreset, notebook.coverImage)` in `BookCard.kt`. |
| Layout thrash (D) | `PullToRefreshLayout` uses `Modifier.graphicsLayer { translationY = … }` instead of `Modifier.offset`. The pull offset is now a render-thread translation and no longer re-measures or re-places the grid on every touch frame. |

### 4.3 Screen navigation

Screen changes are **instant** — there is no slide, fade or any other transition
between the bookshelf, a book, the tabs or the settings. `MainActivity.kt` renders the
active screen directly from the screen stack, so opening a book swaps the content
immediately.

The navigation *behaviour* was still corrected while the transitions were removed:

- `popScreen()` dismisses the question editor first. The editor is not part of the
  screen stack, so Back used to pop the workspace underneath it and leave the editor
  on screen.
- `popScreen()` clears `activeNotebookId` when the destination is not the workspace, so
  returning to the bookshelf never leaves a stale book selected.
- `BackHandler` is enabled when the question editor is open as well as when the stack
  has more than one entry.

### 4.4 Immediate note rendering

`WorkspaceScreen.kt` dropped the artificial 300 ms `isEditorReady` delay (and its
skeleton). Chapter text now renders as soon as the page arrives instead of flashing a
skeleton first. The attachment pane keeps its own short readiness delay, which is still
needed for the WebView/canvas to mount.

### 4.5 Page identity — books that had lost their only chapter

While verifying on the device, the Room database revealed a second, more serious bug:
**seven of the eight notebooks had no pages at all**, so they opened to an empty
workspace.

The cause is a shared id: `PageEntity` used the page id alone as its primary key, but
the web creates every new book with a chapter called **`"p1"`**
(`src/App.jsx` → `pages: [{ id: "p1", … }]`). Room's `REPLACE` strategy therefore
overwrote the previous book's row on every sync, and only the last book kept its
chapter.

The same collision exists on the server: the `page` table is keyed by `page:<id>`
(`build_save_batch` in `server/src/main.rs`), so a `UPSERT page:p1` also re-points the
record at a different notebook.

Fixed on both sides:

- **Android** — `PageEntity` now uses the composite key `(notebookId, id)` and every
  page query is scoped to its notebook. The database version was bumped to 4; the app
  opens Room with `fallbackToDestructiveMigration`, so the local copy is rebuilt and
  re-synced from the server.
- **Web** — `defaultPageId(notebookId)` (`src/constants/presets.js`) derives the
  chapter id from the notebook id, so it is globally unique and deterministic. It is
  used both when creating a book and when `normalizeDoc` synthesises a default chapter
  for a notebook that has none.

A book that lost its chapter is repaired the next time it is saved on the web (any
full save re-creates the chapter under its new, unique id).

`app/schemas/` is now exported by Room so the database contract is reviewable, and
`app/src/test/java/com/esper/android/data/local/PageIdentityTest.kt` asserts the
composite key against that exported schema.

### 4.6 Verification

`app/src/test/java/com/esper/android/ui/BookshelfFilterTest.kt` (new, 12 tests) covers
the substring collision, the stale shelf id, exact tag matching and the filter
combinations against the real `BookshelfFiltering` helpers.

`app/src/test/java/com/esper/android/ui/CoverImageHelperTest.kt` was extended with 5
tests covering the LRU cache (same instance returned, cache cleared on demand) and
whitespace-wrapped base64 payloads.

`app/src/test/java/com/esper/android/data/local/PageIdentityTest.kt` (new, 4 tests)
asserts the composite page key and the notebook-scoped page queries.

```
./gradlew testDebugUnitTest     # 66 tests, 0 failures
./gradlew assembleDebug          # app-debug.apk builds
```

Verified on the device (192.168.2.60, debug APK):

| Check | Result |
|---|---|
| "All Notebooks" | 8 books, all reachable by scrolling — no pager, no hidden page |
| Gradient-cover book ("Random Journal 1") | Visible in the library **and** in its shelf |
| Selecting the "Random Journals" shelf | Heading becomes the shelf name, "1 books available", "Clear filter" appears |
| Bottom-nav **Library** | Returns to "All Notebooks" with all 8 books (it used to keep the shelf filter) |
| Opening a book | Instant, no transition, no crash |
| Room database after the fix | Every notebook keeps its own chapter |

### 4.6 File reference (after the fix)

| File | Change |
|---|---|
| `ui/bookshelf/BookshelfFiltering.kt` | **New** — pure, tested filtering rules |
| `ui/bookshelf/BookshelfScreen.kt` | Uses the shared helpers; mutually exclusive filters; **pagination removed** (the grid now renders the whole library) |
| `ui/components/CustomBottomNavBar.kt` | Pager row removed — navigation only |
| `ui/components/BookCard.kt` | No `isImageLoaded`; memoised theme; title only without artwork |
| `ui/workspace/TabsScreen.kt` | Same cover glitch fix |
| `ui/components/PullToRefresh.kt` | `graphicsLayer` instead of `offset` |
| `ui/theme/CoverTheme.kt` | Thread-safe LRU model cache; single-pass base64 cleanup |
| `ui/workspace/WorkspaceScreen.kt` | Removed the artificial editor delay; empty state for chapter-less notebooks |
| `MainActivity.kt` | Instant screen switching; fixed Back handling for the question editor and stale book selection |
| `data/model/Entities.kt` | `PageEntity` uses the composite key `(notebookId, id)` |
| `data/local/EsperDao.kt` | Page queries scoped to the notebook |
| `data/local/EsperDatabase.kt` | Version 3 → 4, schema exported |
| `app/build.gradle.kts` | Room schema export location |
| `app/schemas/…/4.json` | **New** — exported Room schema |
| `app/src/test/.../BookshelfFilterTest.kt` | **New** — 12 filter regression tests |
| `app/src/test/.../PageIdentityTest.kt` | **New** — 4 page-key regression tests |
| `app/src/test/.../CoverImageHelperTest.kt` | Extended with 5 cache tests |

### 4.7 Related fix in the web repo (`022-esper`)

The colliding `"p1"` chapter id originated in the web client, so the fix was applied
there as well:

- `src/constants/presets.js` — new `defaultPageId(notebookId)` helper; `normalizeDoc`
  uses it for the synthesised default chapter.
- `src/App.jsx` — a new book gets its chapter under a globally unique id.
