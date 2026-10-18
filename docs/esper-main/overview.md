# esper-main (022-esper) — Compressed Context for LLM Agents

> Read this INSTEAD of `022-esper/codebase.txt`. It carries everything the
> Android client needs to know about the web + backend repo. If you change
> main-repo code that alters anything described here, update this file in the
> same change (see `docs/contributing.md`).
> Last verified: 2026-10-18. Covers post-encryption-removal state + SVG cover contract + per-user journal id.

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
- JSON is camelCase (`coverPreset`, `isPinned`, `owner_sub`, `member_type`, `guestToken`). Missing tables surface as empty lists, never 500.
- SSE `GET /api/events`: `page_updated|page_created|page_deleted|note_updated` with docId — web refetches affected notebook unless a save is pending.
- Quotas server-enforced (visitor 50 books/100 ch/book/2MB visuals; member 150/360/4MB; admin unlimited); 6h Tokio task prunes inactive non-admin accounts (visitor 30d, member 365d).

## Weactive non-admin accounts (visitor 30d, member 365d).

## Web sync semantics (relevant to Android parity)
- Keystroke path = surgical PATCH (never full PUT while typing — server PUT deletes+re-UPSERTs children, so stale full writes wipe concurrent edits). Full PUT only for structural/metadata changes.
- Initial load resolves notes+profile+shelves together before dismissing skeleton; banner shows only when `member_type === 'visitor'` explicitly.
- Journal = per-user book id `esper-journal-<uid>` (`journalDocIdFor(uid)` in `src/App.jsx`), restricted visibility. Legacy shared id `esper-journal` is hidden on web (id-prefix filter) and adopted at most once into the per-user id. Android parity: `journalDocIdFor()` in `MainActivity.kt` + one-time Room migration.

## Web frontend map (`src/`)
- `App.jsx` (state, sync, tabs/shelves/tags), `components/WorkspaceView.jsx`, `components/MarkdownEditor.jsx` (Lexical + Prism highlight + per-block copy buttons), `components/CodeEditor.jsx`, drawers/modals in `components/*/`, `services/api.js` (fetch/save/patch, guest-token via `sessionStorage`), `services/firebase.js` (Google popup sign-in), `services/bookImporter.js` (ZIP/`.esper` import), `services/zipExport.js`, `constants/presets.js` (API base resolution, `normalizeDoc`, ID helpers).
- Editor themes `editor-dark/editor-light` in `src/index.css`; code blocks `.editor-code-block` with `data-highlight-language` badge.

## Deploy / local dev
- Backend: Skiff recipe `skiff.json` (musl static build → `/opt/skiff/apps/esper`, `setup.sh` applies schema, systemd + Caddy). Reinstall = fresh SurrealDB + same recipe.
- Web: `bun run dev[:local|:prod]`, `bun run build`, `pages:deploy/upload` (Cloudflare Pages, `wrangler.jsonc`). Docs prompts: `docs/ai-book-bundle.md`, `docs/ai-book-cover.md`.

## Gotchas for Android agents
- Never full-PUT a notebook you only patched — you will wipe the other client's concurrent pages.
- `owner_sub: "*"` rows are world-readable legacy/journal rows.
- `SURREAL_NS/SURREAL_DB` and Firebase project 