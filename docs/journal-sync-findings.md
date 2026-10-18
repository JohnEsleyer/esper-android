# Journal Sync Findings: Android Edits Never Appear on Web

**Date:** 2026-10-18
**Scenario:** Type in the Android journal → wait → refresh web → journal unchanged.
**Verdict:** Android journal edits *do* reach the server. They fail to show up on web because the two platforms write/read **two different server records**: Android uses the hardcoded shared id `esper-journal`, web reads/writes the per-user id `esper-journal-<uid>`.

---

## 1. Root cause: journal doc-id split-brain

### Web reads and writes a per-user record

`src/App.jsx:62-63`:

```ts
const journalDocIdFor = (uid) => (uid ? `${JOURNAL_DOC_ID}-${uid}` : JOURNAL_DOC_ID);
// => "esper-journal-<firebase-uid>"
```

The Web client fully qualifies the user for you: `The per-user journal is loaded only from `docList.find((d) => d.id === journalDocId)` (`src/App.jsx:688`), under the goal `esper-journal-<uid>` is owned by the user, making ownership (write) and read (read) safe for multi-user support.

### Android writes the shared record

`esper-android/app/src/main/java/com/esper/android/MainActivity.kt` seeds and edits a notebook with the literal id **`esper-journal`**:

| Line(s) | What happens |
|---|---|
| 430–453 | Seeds notebook `id = "esper-journal"` (once, if Room is empty) |
| 685 | `SyncOp.PatchPage("esper-journal", pageId)` on every keystroke save |
| 708 | `SyncOp.CreatePage("esper-journal", newPageId)` on new entry |
| 721 | `SyncOp.DeletePage("esper-journal", pageId)` on delete |
| 573–578 | `pushFullBook("esper-journal")` during sync when missing remotely |

Nothing on Android ever touches `esper-journal-<uid>`.

### Server result

- `PUT /api/notes/esper-journal` and `PATCH /api/notes/esper-journal/pages/jp-*` succeed because `check_ownership` (`server/src/main.rs:913-923`) passes: the notebook is either unowned (`owner = '*'`) or owned by the same signed-in user, and `build_save_batch` (`server/src/main.rs:852`) stamps `owner = user:<uid>` at save time.
- So the server ends up with **two separate journal notebooks** owned by the same account:
  - `esper-journal` — written by Android (and adopted once by web, see §3)
  - `esper-journal-<uid>` — written and read by web

Both are only visible via the owner-scoped listing `GET /api/user/notes` (`server/src/main.rs:601-603`, `WHERE owner = user:<uid>`), so the data is not lost — it is just filed in a drawer web never opens.

---

## 2. Why web actively ignores the Android record

Web intentionally hides the legacy id everywhere:

- **Bookshelf hides all `esper-journal*` ids** — `src/App.jsx:704-709`:
  ```ts
  return docList.filter((d) => !d.id.startsWith(JOURNAL_DOC_ID));
  ```
- **Journal view ignores the legacy record** — `src/App.jsx:688`: the memo keys off `d.id === journalDocId` (per-user id) only.

Live event plumbing is actually fine: when Android patches a page, the Rust server broadcasts `{ type: "page_updated", docId: "esper-journal" }` (`server/src/main.rs:1100`), web's SSE handler (`src/App.jsx:641-676`) even refetches `/api/notes/esper-journal` and puts it in `docList` — it just never reaches the journal memo because of the id filter. The pipe works; the mailbox address is wrong.

---

## 3. The one-time legacy "adoption" is a one-time trap

`src/App.jsx:687-702` — web has a backward-compat path: if a browser session has **no per-user server journal AND no localStorage snapshot**, it adopts the legacy `esper-journal` doc (and the old `v1` snapshot), picks whichever `updatedAt` is newer, and **renames it internally to `esper-journal-<uid>`**.

Consequences:

1. Every web session pulls the latest `esper-journal-<uid>` from the server + the `v2_<uid>` snapshot — the legacy doc is consulted **only when both are missing**.
2. After adoption, the snapshot/localStorage key exists, so **subsequent Android edits to `esper-journal` are invisible forever**, even after an adopt.
3. Adoption never deletes the legacy server record — the split brain persists server-side.

This is exactly the observed reproduction: web refresh resolves `docList`, finds the per-user journal (or its snapshot) and renders that; Android's newer copy sits in the legacy record.

---

## 4. Android side: the reverse-sync mirror bug

- **Web → Android:** web journal edits live in `esper-journal-<uid>`. Android's sync (`SyncManager.kt:42-125`) pulls it like any notebook, and because every Android "journal exemption" tests *exact equality* with `esper-journal` (`SyncManager.kt:49`, `MainActivity.kt:582-583`, `BookshelfScreen.kt:89`), the per-user web journal shows up as an **ordinary duplicate "Personal Journal" book** on the Android bookshelf, while the journal *screen* reads only `esper-journal` (`MainActivity.kt:673`).
- **Android → web:** mirrored by §1–§3.

So each platform currently sees the *other's* journal as a plain notebook and its *own* journal in the journal screen — never the same record.

---

## 5. Latent server hazard found during the investigation

`server/src/main.rs:1068-1103` — `patch_page_handler` never returns 404:

- Ownership check passes even when the target notebook does not exist (empty `OwnerCheck` list).
- `UPDATE page SET ...` that matches **zero rows** still returns `200 {"success": true}` (SurrealDB `UPDATE` on a missing record is not an error).
- The handler always broadcasts `page_updated` and returns success.

Impact on the Android client (relevant to `replayOp`, `MainActivity.kt`): the 404-healing added in `replayOp` (`PatchPage → createPage → pushFullBook`) can never trigger for a missing page/notebook via `PATCH`, because the server answers 200 while writing nothing — the op is marked clean and the edit is dropped silently. This is masked today only because `pushFullBook` seeds the page ids (`jp-*`) server-side first, making subsequent page ids match. If the journal record is ever absent server-side (fresh server, record cleaned up, tier quota purge, etc.), Android keystrokes will "save successfully" into the void. Consider:

- Server: make `patch_page_handler` return 404 (or a `{"updated": false}` flag) when the notebook/page row is missing, so client-side healing fires; and/or
- Android: after `PATCH`, verify with `GET /api/notes/:id` (or diff) before `markClean`, or use full-book `PUT` for journal saves (web's journal already uses atomic full-PUT for exactly this reason — see `src/App.jsx` `flushJournalSave` comment, ~line 731).

---

## 6. What is NOT the cause

- Debounce windows (Android 300 ms / web 600 ms) — only a crash-in-flight risk, not a persistent divergence.
- Offline write queue / `replayOp` — ops replay on reconnect and land on the server fine (wrong record, but they land).
- SSE — server broadcasts Android journal writes correctly; web even refetches them.
- Auth/ownership — ownership checks pass for the shared id for the account that owns it.
- Guest/offline mode on Android — user reported the issue while signed in and typing normally.

---

## 7. Recommended fix (Android-side, matches web's contract)

Make Android use the same per-user id web uses. Single-source it:

```kotlin
fun journalDocIdFor(uid: String?): String = uid?.let { "esper-journal-$it" } ?: "esper-journal"
```

Then:

1. **Seed / migrate (MainActivity ~line 430):** seed under `journalDocIdFor(currentUser?.uid)`; if the legacy local record `esper-journal` exists for the same user, migrate it once (upsert under the new id in Room), since the journal screen and ops all reference the resolved id.
2. **Journal callbacks (~lines 673–723):** replace every literal `"esper-journal"` with the resolved id.
3. **syncWithCloud push (~lines 573–578):** push `journalDocIdFor(uid)` when missing from `report.remoteIds`.
4. **Exemptions — use prefix, not equality.** Replace every `id == "esper-journal"` / `id != "esper-journal"` with `id.startsWith("esper-journal")` / `!id.startsWith(...)` in:
   - `MainActivity.kt:421, 582-583` (tags, tab reconcile)
   - `SyncManager.kt:49` (purge)
   - `BookshelfScreen.kt:89, 387` (bookshelf grid + quick tags)
5. **Optional server hardening:** return 404 from `patch_page_handler` on zero-affected-row/missing-notebook so client 404-healing (§5) actually works.

Web needs **no change** — its contract (`journalDocIdFor`, prefix-based hiding, one-time adoption) is already the canonical one; Android simply never conformed to it. After the fix, each account's journal is a single `esper-journal-<uid>` record that both clients create/patch/pull, and SSE (2.5 s self-echo suppression on web; Android re-syncs on any event) keeps them converged.

---

*Key references: `src/App.jsx:54,62-63,641-676,687-709` · `server/src/main.rs:593-687,852,913-923,1068-1103` · `esper-android/app/src/main/java/com/esper/android/MainActivity.kt:430-453,573-583,673-723` · `esper-android/app/src/main/java/com/esper/android/data/sync/SyncManager.kt:42-60`*

---

## RESOLVED (2026-10-18): Android writes never reached the server — Retrofit wildcard crash

After the per-user journal id fix was installed, further device forensics showed Android pulls
worked but **no write (PUT/PATCH/POST) was ever emitted**, and queued ops sat in
`esper_sync.xml` for hours. Instrumenting `replayOp` revealed the actual cause:

```
java.lang.IllegalArgumentException: Parameter type must not include a type variable or
wildcard: java.util.Map<java.lang.String, ?> (parameter #2) — for method EsperApiService.updateNote
```

**Cause:** `@Body body: Map<String, Any?>` compiles to a wildcard JVM signature
(`Map<String, ?>`), which Retrofit's `RequestFactory` rejects — lazily, on the first
invocation of each affected method. Because `replayOp` swallowed all exceptions with
`catch (_: Exception) { false }`, every write failed invisibly while reads kept working.

**Fix:** `EsperApi.kt` now declares

```kotlin
typealias PayloadMap = Map<String, @JvmSuppressWildcards Any?>
```

and all `@Body Map<String, Any?>` parameters use `PayloadMap` (updateNote, createNote,
createPage, patchPage, createShelf, updateShelf). Verified on device: after reinstall the
three stuck queued ops (migration FullBook + both pending journal patches) flushed 200,
and a live keystroke produced a debounced PATCH → 200 within ~300 ms. `replayOp` now also
logs failures (`Log.w("EsperSync", "replayOp FAILED …")`) so silent write-loss can't hide.
