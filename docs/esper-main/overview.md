# esper-main (022-esper) — Compressed Context for LLM Agents

> Read this INSTEAD of `022-esper/codebase.txt`. It carries everything the
> Android client needs to know about the web + backend repo. If you change
> main-repo code that alters anything described here, update this file in the
> same change (see `docs/contributing.md`).
> Last verified: 2026-10-23. Covers post-encryption-removal state + SVG cover contract + per-user journal id + code block parity + in-block language picker + Turnstile gating.

## Identity
- Repo `022-esper`: React 19 SPA (`src/`, Cloudflare Pages) + Rust Axum API (`server/`, VPS via Skiff) + SurrealDB v3.x (`surrealkv`). API origin `https://esper-api.johnesleyer.com`, local `http://127.0.0.1:8080`.
- No client-side encryption anywhere:_notes stored **plaintext**. Security = TLS + Firebase Auth + server visibility gates + Argon2 share passwords.

## Data model (SurrealDB, NS `esper` / DB `production`)
- Source of truth: `server/src/schema.surql` (boot-applied, all `IF NOT EXISTS`); runnable copy `server/init_tables.surql` (`USE NS esper; … INFO FOR DB;`).
- Tables (all SCHEMAFULL, client-slug IDs): `user:<firebase-uid>` (name/avatar/member_type visitor|member|admin/last_active_at/open_tabs), `notebook:<slug>` (title/description/owner→user/visibility public|restricted|protected/password_hash/tags/cover_*/is_pinned/unix timestamps), `page` (notebook/parent?/title/notes/position), `canvas` (notebook/kind html|youtube|excalidraw/title/payload JSON-text/pages[]), `question` (notebook/title/answer/position), `comment` (notebook/page?/parent?/author?/user_* snapshot/content 1–2000), `shelf`+`shelf_item` (UNIQUE shelf,notebook), `asset` (DDL-only; uploads go to ImageKit CDN, URL on `notebook.cover_image`).
- Record links, not FK strings: `SELECT ->authored->notebook.* FROM user:…`. BM25 FTS: analyzer `text_search`, indexes `page_title_fts`/`page_notes_fts`, query `WHERE title @0@ '…'`.

## Book covers (`coverImage` contract — see `022-esper/docs/client-svg-covers.md`)
- `notebook.cover_image` (JSON `coverImage`) is an optional string in one of **4 formats**: **Base64 SVG Data URI** (`data:image/svg+xml;base64,...`, built-in seeded books from `server/src/seed.rs`), **URL-encoded SVG Data URI** (`data:image/svg+xml;utf8,%3Csvg...`, pasted AI covers from `src/services/coverPromptTemplate.js`), **raw SVG** (`<svg xmlns=...>`), or **ImageKit CDN URL** (`https://ik.imagekit.io/...`).
- `coverImage` null/empty → client renders a gradient from `coverPreset` (`celestial|quantum|solar|midnight|nebula|crimson`), not an image.
- Vector canvases are full-bleed **1:1.44** (`viewBox="0 0 1000 1440"`, no baked-in spine/border/lighting) — the client overlays its own 3D spine + vignette. Coil (with `SvgDecoder.Factory()`) cannot fetch `data:image/svg+xml;base64,...` string URIs directly; decode Data URIs to a `ByteArray` first. Full parser + Compose recipe in the guide above.

## Backend API (`server/src/main.rs`, Firebase RS256 JWKS, 30s drift leeway)
- `GET /api/health`; `GET /api/user/notes` (owner, authed); `GET /api/notes/:id` (public open; restricted owner-only else 403; protected needs owner or `x-guest-token: guest-token-<id>` else `401 {isProtected:true}`);
- `PUT /api/notes/:id` / `POST /api/notes` (full upsert, children replaced wholesale); `DELETE /api/notes/:id` (cascades); `POST/PATCH/DELETE /api/notes/:id/pages[/:pageId]` (granular; page DELETE cascades subpages+comments+canvas refs, returns `deletedPageIds`); shelves CRUD; `GET/POST /api/comments/:docId` (ETag-cached GET); `POST /api/upload` (≤25MB → ImageKit `/esper/covers`); `POST /api/me` + `GET /api/me` (profile, member_type, usage/limits).
- **Turnstile (2026-10-23):** `POST /api/notes/:id/verify-password` requires `password` **and** `cf-turnstile-response` (Siteverify action `unlock`); `POST /api/comments/:docId` requires action `comment`. Hostnames come from `TURNSTILE_HOSTNAMES` (prod: `esper.johnesleyer.com`); failures return `403 {"error":"forbidden"}` and the check is fail-closed. Web sends tokens (`src/services/turnstile.js`, `PasswordPromptModal.jsx`, comment widget in `WorkspaceView.jsx`); Android does not yet — see `esper-android/docs/turnstile-unlock.md`. Rotation: `022-esper/docs/turnstile-credentials.md`.
- JSON is camelCase (`coverPreset`, `isPinned`, `owner_sub`, `member_type`, `guestToken`). Missing tables surface as empty lists, never 500.
- SSE `GET /api/events`: `page_updated|page_created|page_deleted|note_updated` with docId — web refetches affected notebook unless a save is pending.
- Quotas server-enforced (visitor 50 books/100 ch/book/2MB visuals; member 150/360/4MB; admin unlimited); 6h Tokio task prunes inactive non-admin accounts (visitor 30d, member 365d).

## Weactive non-admin accounts (visitor 30d, member 365d).

## Web sync semantics (relevant to Android parity)
- Keystroke path = surgical PATCH (never full PUT while typing — server PUT deletes+re-UPSERTs children, so stale full writes wipe concurrent edits). Full PUT only for structural/metadata changes.
- Initial load resolves notes+profile+shelves together before dismissing skeleton; banner shows only when `member_type === 'visitor'` explicitly.
- Journal = per-user book id `esper-journal-<uid>` (`journalDocIdFor(uid)` in `src/App.jsx`), restricted visibility. Legacy shared id `esper-journal` is hidden on web (id-prefix filter) and adopted at most once into the per-user id. Android parity: `journalDocIdFor()` in `MainActivity.kt` + one-time Room migration.

## Web frontend map (`src/`)
- `App.jsx` (state, sync, tabs/shelves/tags), `components/WorkspaceView.jsx`, `components/MarkdownEditor.jsx` (Lexical + Prism highlight + `/code` slash command + per-block copy/delete + language picker), `components/CodeEditor.jsx`, drawers/modals in `components/*/`, `services/api.js` (fetch/save/patch, guest-token via `sessionStorage`), `services/firebase.js` (Google popup sign-in), `services/bookImporter.js` (ZIP/`.esper` import), `services/zipExport.js`, `constants/presets.js` (API base resolution, `normalizeDoc`, ID helpers).
- Editor themes `editor-dark/editor-light` in `src/index.css`; code blocks `.editor-code-block` carry `data-highlight-language`, and the language badge is rendered by the editor overlay (not by a CSS pseudo element).
- Code-block language tags: the stock `@lexical/markdown` transformer only captures `\w+`, so ```` ```C++ ```` used to parse as the language `C`. Esper replaces it with `src/components/nodes/CodeBlockTransformer.js` (accepts `+`/`#`) and resolves every alias/casing via `src/services/prismSetup.js` (`C++`→`cpp`, `py`→`python`, `C#`→`csharp`, `php8`→`php`, …). Prism is **not** imported directly by app code — the single instance bundled inside `@lexical/code` is reused, otherwise the highlighter and the alias table would end up on two disconnected copies.
- Web-only grammars (hand-written in `prismSetup.js`, **not** ported to the Kotlin highlighter yet): `bash`, `json`, `yaml`, `go`, `csharp`, `kotlin`, `php`, `ruby`, `lua`, `docker`, `diff`, `graphql`. A block using one of them highlights on web and falls back to plain text on Android.

## Code blocks (web ⇄ Android parity)
- Web: fenced blocks are parsed by `CodeBlockTransformer.js`, highlighted by Prism, and get copy + trash buttons plus a **language picker rendered inside the block** (top-left corner, `CodeBlockActionsOverlay` in `components/MarkdownEditor.jsx`). The picker writes straight to that block's `CodeNode.setLanguage(...)`, so Prism re-tokenises it immediately and the change is persisted into the markdown fence (` ```python `). In read-only mode the picker degrades to a static badge and the trash button is hidden. `/code` (or `/code <lang>`) expands into a block instantly, and every new block is followed by an escape paragraph.
- Android: `ui/lexical/CodeSyntaxHighlighter.kt` is a dependency-free Kotlin port of the same grammars, using the exact `editor-dark` token colours so both clients look identical. `ui/lexical/LexicalParser.kt` captures the language tag verbatim and records the fence line range so the block can be deleted or re-tagged in the raw markdown. `/code`, the escape-paragraph rule, copy/trash and the language picker live in `ui/lexical/ComposeLexicalEditor.kt`.
- Contract: both clients read the same markdown, so a block written on one renders highlighted on the other. Findings and the resolution log: `esper-android/docs/code-blocks-findings.md`.

## Deploy / local dev
- Backend: Skiff recipe `skiff.json` (musl static build → `/opt/skiff/apps/esper`, `setup.sh` applies schema, systemd + Caddy). Reinstall = fresh SurrealDB + same recipe.
- Web: `bun run dev[:local|:prod]`, `bun run build`, `pages:deploy/upload` (Cloudflare Pages, `wrangler.jsonc`). Docs prompts: `docs/ai-book-bundle.md`, `docs/ai-book-cover.md`.

## Android bookshelf (filtering + covers)
- The bookshelf grid is **not** a flat list of every notebook. `ui/bookshelf/BookshelfScreen.kt` narrows `sortedNotebooks` by **tag AND search AND shelf** (all ANDed), then paginates **6 books per page** with a pager in the bottom nav.
- "All Notebooks" is not a mode — it is only `selectedShelfId == null`. The bottom-nav **Library** button clears the *tag* filter but **not** the shelf filter, and the **"Clear filter"** button clears the shelf but **not** the tag filter. A shelf therefore shows the intersection, and "Library" does not reliably return to the full library.
- Shelf membership is stored as a CSV (`ShelfEntity.notebookIdsCsv`, built in `SyncManager`) and tested with `String.contains`, which is a substring match, not a membership test. `shelves.find { … }` returning `null` (stale shelf id) makes the whole shelf render empty.
- Covers are SVG **data URIs** for the seeded books (1000×1440, with a `feGaussianBlur` filter) and remote WebP for uploads; `CoverImageHelper.resolveCoverModel` pre-decodes data URIs to a `ByteArray` (cached in a thread-safe LRU) and Coil renders them with `SvgDecoder`.
- Screen changes are **instant** — no transition animation between the bookshelf, a book, the tabs or the settings.
- Findings and fix list: `esper-android/docs/bookshelf-findings.md`.

## Gotchas for Android agents
- Never full-PUT a notebook you only patched — you will wipe the other client's concurrent pages.
- `owner_sub: "*"` rows are world-readable legacy/journal rows.
- `SURREAL_NS/SURREAL_DB` and Firebase project 