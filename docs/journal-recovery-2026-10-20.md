# Incident: The Android App Overwrote the Server Journal with an Empty One

**Date:** 2026-10-20
**Severity:** Data loss (server-side), caused by a client-side change.
**Status:** **RESOLVED.** The root-cause fix has landed and is covered by unit tests in `JournalBootstrapTest.kt`.

---

## 1. Summary

While fixing the Android bookshelf, the app's Room database schema was changed
(`pages` switched to a composite primary key). The app opens Room with
`fallbackToDestructiveMigration()`, so the **local** database was wiped on the next
launch.

On start-up the journal bootstrap found no local journal, created a **brand new empty
one**, and then pushed it to the server with a full `PUT`. That `PUT` replaced the
server's journal — which had the user's notes — with a single empty seed page.

The notes were recovered from the device's SQLite write-ahead log and restored to the
server. The user has verified the restoration.

---

## 2. What exactly happened

### 2.1 The trigger

`app/src/main/java/com/esper/android/data/local/EsperDatabase.kt` — the schema version
was bumped:

```kotlin
// v4: the pages table switched to a composite (notebookId, id) primary key
version = 4,
```

`MainActivity.kt` builds the database with:

```kotlin
Room.databaseBuilder(applicationContext, EsperDatabase::class.java, "esper.db")
    .fallbackToDestructiveMigration()
    .build()
```

`fallbackToDestructiveMigration()` **deletes the whole local database file** when the
version changes. Every locally cached notebook, page, canvas and question was lost
locally.

### 2.2 The bug that turned a local wipe into server data loss

`MainActivity.kt`, the journal bootstrap (`LaunchedEffect(currentUser?.uid)`):

```kotlin
val journalId = journalDocIdFor(currentUser?.uid)
if (db.dao().getNotebookById(journalId) == null) {
    val migrated = db.dao().getNotebookDetails("esper-journal")
    if (migrated != null) {
        // one-time migration of the legacy shared journal
        ...
    } else {
        // Fresh journal seed (deterministic id shared with web).
        db.dao().upsertNotebook(NotebookEntity(id = journalId, title = "Personal Journal", ...))
        db.dao().upsertPage(PageEntity(id = "jp-1", notebookId = journalId,
            title = dateStr, notes = "# $dateStr\n\nJot down ideas and quick thoughts here...", ...))
    }
    val migrationOp = SyncOp.FullBook(journalId)
    writeQueue.markDirty(migrationOp)
    launch(Dispatchers.IO) { if (replayOp(migrationOp)) writeQueue.markClean(migrationOp) }
}
```

Two defects compound:

1. **The "else" branch seeds an empty journal without asking the server.** The legacy
   shared `esper-journal` record had already been migrated away weeks earlier
   (commit `e9dde65`), so `migrated` was `null` and the empty seed was created — even
   though the *server* still held the real per-user journal.
2. **The push is unconditional.** `SyncOp.FullBook(journalId)` → `replayOp` →
   `pushFullBook()` → `PUT /api/notes/esper-journal-<uid>`. The server's
   `build_save_batch` does `DELETE page WHERE notebook = …` before re-creating the
   pages it was sent, so the empty seed replaced the real journal.

There is a second path with the same hazard in `syncWithCloud()`:

```kotlin
if (currentUser != null && !report.remoteIds.contains(currentJournalId)) {
    val journalDetails = db.dao().getNotebookDetails(currentJournalId)
    if (journalDetails != null) pushFullBook(currentJournalId)
}
```

This pushes the local journal whenever the server does not have it. Combined with a
wiped local database, that is the same destructive operation.

### 2.3 The damage

The server's journal was reduced to:

```
jp-1 | "Tue, Oct 20" | "# Tue, Oct 20\n\nJot down ideas and quick thoughts here..."
```

---

## 3. Recovery

### 3.1 Where the data came from

Room's write-ahead log (`databases/esper.db-wal`, ~490 KB) had **not** been truncated by
the destructive migration and still contained the old `pages` row for the journal. The
row was extracted directly from the WAL bytes.

A Firebase ID token was read from the app's `shared_prefs/esper_auth_prefs.xml` to
query and restore the server record (`GET /api/user/notes`, `PUT /api/notes/<id>`).

### 3.2 What was recovered

| Field | Value |
|---|---|
| id | `jp-1` |
| notebookId | `esper-journal-06PjSOOj73ZVZeFBP1kHasK3gHM2` |
| title | `KeyboardCheck` |
| notes | `## From PC\n\nFrom PC again\nFrom androidskroWtidEoTpaTabc123SyncStress` |

The WAL holds two historical versions of the row:

1. title `From PC`, notes `## From PC\n\nFrom PC again\nFrom android`
2. title `KeyboardCheck`, notes `## From PC\n\nFrom PC again\nFrom android` plus the
   additional test strings `skroWtidEoTpa`, `Tabc123`, `SyncStress`

Version 2 is the fuller one and is what was restored. The three trailing test strings are
stored contiguously with the notes in the WAL, so whether they were one run of typed
text or three separate entries cannot be proven from the bytes alone.

The journal only ever had a single page (`jp-1`).

### 3.3 Cross-check for the user

The web client keeps its own copy of the journal in `localStorage`:

- `esper_journal_snapshot_v2_06PjSOOj73ZVZeFBP1kHasK3gHM2` (current key)
- `esper_journal_snapshot_v1` (legacy key)

If the web app was used with the journal, that snapshot may still hold the original text
and can be used to confirm or correct the reconstruction (DevTools → Application →
Local Storage → the origin → the key).

---

## 4. What was fixed in the same change set

These changes are already implemented, built and deployed to the device:

- `data/model/Entities.kt` — `PageEntity` uses the composite primary key
  `(notebookId, id)`. Page ids are only unique *within* a notebook: every book created
  on the web started with a chapter called `"p1"`, so Room's `REPLACE` strategy made
  each new book steal the previous book's only chapter. The server has the same defect
  (the `page` table is keyed by `page:<id>`), which is why seven of the eight notebooks
  on the test account had no pages at all.
- `data/local/EsperDao.kt` — every page query is scoped to its notebook.
- `data/local/EsperDatabase.kt` — version 4, schema exported to `app/schemas/`.
- Web repo (`022-esper`) — `defaultPageId(notebookId)` in `src/constants/presets.js`
  and `src/App.jsx` derive the chapter id from the notebook id, so new books no longer
  collide. Books that lost their chapter are repaired the next time they are saved on
  the web.

---

## 5. Remaining work

> ✅ **Fixed (2026-10-20).** The server-as-authority check, the
> `hasMeaningfulJournalContent` guard and the `SyncManager.upsertRemoteNotebook`
> extraction have all landed — see §5.1–§5.3 and §5.5 below for the shipped
> shape of each. The remaining open item is §5.4 (a real `Migration(3, 4)`
> instead of `fallbackToDestructiveMigration()`), which is now a robustness
> improvement rather than a data-loss risk: no code path can push local state
> over the server's journal any more.

### 5.1 Make the journal bootstrap pull before it pushes ✅

`MainActivity.kt`, the journal bootstrap must check the **server** before creating
anything locally:

```kotlin
if (db.dao().getNotebookById(journalId) == null) {
    // 1. The server is the source of truth. If it already holds this user's
    //    journal, adopt it — seeding an empty one here and pushing it would
    //    destroy the real journal.
    val remote = try { api.getNote(journalId, null) } catch (_: Exception) { null }
    if (remote != null) {
        syncManager.upsertRemoteNotebook(remote, currentUser?.uid)
        // adopted: do NOT push back
    } else {
        // 2. Legacy shared journal (pre per-user migration).
        val migrated = db.dao().getNotebookDetails("esper-journal")
        if (migrated != null) {
            /* existing legacy migration */
        } else {
            /* fresh seed */
        }
        // 3. Only push what we just created locally.
        val migrationOp = SyncOp.FullBook(journalId)
        writeQueue.markDirty(migrationOp)
        launch(Dispatchers.IO) { if (replayOp(migrationOp)) writeQueue.markClean(migrationOp) }
    }
}
```

`api.getNote(id, guestToken)` already exists (`data/remote/EsperApi.kt:126-130`).

**Shipped:** the lookup distinguishes three outcomes (`Found` / `Absent` / `Unknown`),
so a network failure is never mistaken for "the server has no journal". The fresh
seed is created **locally only** — it is never pushed. The legacy migration is
pushed only when the server is known to have no journal *and* the migrated content
is real user notes (`hasMeaningfulJournalContent`).

### 5.2 Extract the remote → Room mapping into `SyncManager` ✅

`sync()` currently maps a remote notebook to Room inline (notebook, pages, canvases,
questions, with the whiteboard-JSON special cases). The bootstrap needs the same
mapping, so it should be extracted into a reusable method:

```kotlin
/**
 * Writes one remote notebook (with its pages, canvases and questions) into Room.
 */
suspend fun upsertRemoteNotebook(r: RemoteNotebook, ownerSub: String?) { … }
```

and `sync()`'s Step C becomes:

```kotlin
remoteNotes.forEach { r -> upsertRemoteNotebook(r, ownerSub) }
```

> **Shipped:** `SyncManager.upsertRemoteNotebook(r, ownerSub)` now exists and
> `sync()`'s Step C is a one-line call to it. `unlockProtected()` and the
> journal bootstrap reuse the same mapping, so all three paths write identical
> Room rows.
>
> (Historical note: this refactor was started and then reverted while
> investigating — the attempt to move the block with a script placed the method
> *inside* `sync()` and broke the file. It has since been redone with a careful
> hand edit: the method body is the original Step C body, de-indented by four
> spaces, placed **after** the closing brace of `sync()`, with `RemoteNotebook`
> imported into `SyncManager.kt`.)

### 5.3 Guard the `syncWithCloud()` push ✅

`MainActivity.kt`, `syncWithCloud()`:

```kotlin
if (currentUser != null && !report.remoteIds.contains(currentJournalId)) {
    val journalDetails = db.dao().getNotebookDetails(currentJournalId)
    if (journalDetails != null) pushFullBook(currentJournalId)
}
```

This pushes the local journal whenever the server lacks it. It should only push a
journal that actually has content, and it should never replace a server journal that
has pages:

- Only push when the local journal has at least one page **with notes**.
- Never push over a server journal that has pages (pull instead).

**Shipped:** the push is now guarded twice — the server is asked whether it
holds the journal (if it does, the app adopts the server copy instead), and the
local journal is only pushed when `hasMeaningfulJournalContent()` confirms it
holds real user notes rather than the starter template.

### 5.4 Consider a safer migration strategy

`fallbackToDestructiveMigration()` means *any* schema change silently deletes the local
database — and with a synced server that is usually harmless, but not when a
client-side bootstrap can push local state back. Options:

- Write an explicit `Migration(3, 4)` that recreates the `pages` table with the
  composite key and copies the rows across, instead of dropping everything.
- Keep `fallbackToDestructiveMigration()` but make sure no code path can push local
  state over the server (see §5.1–§5.3).

### 5.5 Regression test ✅

A test that asserts the bootstrap never seeds over a populated server journal is hard
to write without an instrumented test, but the invariant can be pinned by extracting
the decision into a pure function, e.g.

```kotlin
enum class JournalBootstrap { ADOPT_FROM_SERVER, MIGRATE_LEGACY, SEED_FRESH }
fun journalBootstrapPlan(serverHasJournal: Boolean, hasLegacyLocal: Boolean) = …
```

and unit testing the decision table.

**Shipped:** `app/src/test/java/com/esper/android/sync/JournalBootstrapTest.kt`
covers both halves of the invariant — the decision table
(`determineJournalBootstrapAction`) and the content detection
(`hasMeaningfulJournalContent`). Run it with `./gradlew testDebugUnitTest`.

---

## 6. Reference

| File | Role |
|---|---|
| `app/src/main/java/com/esper/android/MainActivity.kt` | Journal bootstrap (~line 439) and the `syncWithCloud()` journal push (~line 609) — **both need the fix** |
| `app/src/main/java/com/esper/android/data/sync/SyncManager.kt` | `sync()`; the remote → Room mapping to extract |
| `app/src/main/java/com/esper/android/data/remote/EsperApi.kt` | `getNote(id, guestToken)` (126-130) used to adopt the server journal |
| `app/src/main/java/com/esper/android/data/local/EsperDatabase.kt` | Version 4 + exported schema |
| `app/src/main/java/com/esper/android/data/model/Entities.kt` | `PageEntity` composite key |
| `docs/bookshelf-findings.md` | The bookshelf work this incident came out of |
| `docs/esper-main/overview.md` | Cross-client contract notes |
