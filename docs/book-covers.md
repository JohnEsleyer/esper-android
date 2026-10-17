# Main Esper (022-esper) — How Book Covers Are Implemented

> Describes the **main repo** (`022-esper`: React 19 SPA in `src/`, Rust Axum API
> in `server/`, SurrealDB). This is the producer of every cover string the
> Android client consumes. For the Android-side decode/render recipe see
> `022-esper/docs/client-svg-covers.md`; for the compressed cross-repo context
> see `docs/esper-main/overview.md`.
> Last verified: 2026-10-17.

Book covers in Esper are a single **optional string** (`notebook.cover_image` /
JSON `coverImage`) that can hold a vector SVG, a Data URI, or a CDN URL, plus a
required **preset key** (`cover_preset` / `coverPreset`) used for gradient
fallbacks and app-rendered chrome. The web app never bakes a spine, border, or
shadow into the artwork — clients overlay that themselves.

---

## 1. Data model & storage

SurrealDB, `SCHEMAFULL`, tables `notebook` and `shelf` (`server/src/schema.surql`):

| Field | Type | Default | Notes |
|---|---|---|---|
| `cover_preset` | string | `"celestial"` | `/`schema.surql:40`, shelf `:94`. Key into the preset/theme catalogs. |
| `cover_image` | `option<string>` | `NONE` | `/`schema.surql:41`, shelf `:95`. The 4-format string below. |
| `cover_image_file_id` | `option<string>` | `NONE` | `/`schema.surql:42`. **Notebook only.** ImageKit `fileId` for DAM cleanup. |

Rust mirrors of the same fields:
- `NotebookRow` — `server/src/models.rs:143-148` (`default_cover()` → `"celestial"` at `models.rs:281`).
- `ShelfRow` — `models.rs:236-239`; `SaveShelfPayload` uses JSON names
  `coverPreset` / `coverImage` (`models.rs:252-255`).
- `NotebookRow` selects are centralized in `NOTEBOOK_FIELDS`
  (`server/src/main.rs:144`) and serialized camelCase at `main.rs:131-133`.

Frontend canonical shape is produced by `normalizeDoc` (`src/constants/presets.js:238-240`):
`coverPreset || "celestial"`, `coverImage || null`, `coverImageFileId || null`.
Outbound saves mirror this in `src/services/api.js:85-87`.

Key point: **`coverPreset` and `coverImage` are independent.** A book can have a
preset *and* an image; when an image exists it wins for the artwork while the
preset theme still drives spines/accents (see §6).

---

## 2. The four `coverImage` states (precedence & origin)

Written by `022-esper/docs/client-svg-covers.md`, restated from the producer side:

| # | Format | Produced by | Example prefix |
|---|---|---|---|
| 1 | Base64 SVG Data URI | Seeded example books (`server/src/seed.rs`) | `data:image/svg+xml;base64,PHN2Zy…` |
| 2 | URL-encoded SVG Data URI | Pasted AI SVG normalized in the browser | `data:image/svg+xml;utf8,%3Csvg…` |
| 3 | Raw SVG string | Legacy/manual storage (importers may pass through) | `<svg xmlns=…viewBox="0 0 1000 1440"…` |
| 4 | Remote HTTPS CDN URL | ImageKit raster upload through `/api/upload` | `https://ik.imagekit.io/…/cover.webp` |

If `coverImage` is `null`/empty, the client draws a gradient from `coverPreset`.
The backend stores the string verbatim — it does not validate format; the
browser normalizes on input (§5.2) and ImageKit produces format 4.

---

## 3. Preset & theme catalogs

Two hand-maintained, parallel catalogs must stay in sync:

1. **`PREMADE_COVERS`** — `src/constants/presets.js:17-42`. 24 entries, each
   `{ id, title, gradient }` where `gradient` is a Tailwind `from-… via-… to-…`
   class used directly in cards/modals/fallbacks. Ids: `celestial`, `quantum`,
   `solar`, `midnight`, `nebula`, `crimson`, `cybergrid`, `event-horizon`,
   `topographic`, `aurora`, `abyss`, `monolith`, `prismatic`, `retrowave`,
   `supernova`, `matrix`, `hyperdrive`, `molten`, `lunar`, `glitch`, `acid`,
   `obsidian-gold`, `glacier`, `parchment`.
2. **`PRESET_THEMES`** — `src/services/coverTheme.js:2-195`. Per-preset
   `{ accent, border, activeBorder, bgGradient, spineColor, tagBg }` (exact
   hexes/rgba, not Tailwind). Drives spines, tab accents, active borders.

A third, smaller catalog `COVER_PALETTES` (`presets.js:44-69`) exposes just
`{ accent, bgGlow, border }` via `getCoverPalette()` (`presets.js:71`).

The canonical short list `celestial | quantum | solar | midnight | nebula |
crimson` is the one documented for AI book bundles (`docs/ai-book-bundle.md:76`)
and requests from importers.

Note: the docs/contract talks about 6 presets, but the app exposes **24**; clients
must treat unknown preset ids as a fallback to `celestial` rather than erroring
(`getCoverPresetTheme`/`getCoverPalette` both default).

---

## 4. Generation path A — Seeded example books (base64 SVG)

`server/src/seed.rs` ships five fully-designed vector covers inline and encodes
each as a Data URI:

- `svg_to_data_uri(svg)` — `seed.rs:4-7` — standard `base64` of the UTF-8 SVG
  bytes prefixed with `data:image/svg+xml;base64,`.
- All five use `viewBox="0 0 1000 1440"` (aspect **1:1.44**), a dark radial
  background, an `.12` opacity background lattice, a category badge, title block,
  central scientific illustration, an equation line, and a footer divider/seal.
- `get_example_books()` — `seed.rs:397` — returns `SaveNotebookPayload`s:

| Book | `cover_preset` | Cover fn | Seed line |
|---|---|---|---|
| Quantum Mechanics | `quantum` | `svg_quantum_cover()` | `seed.rs:406-407` |
| Linear Algebra & Dimensional Topology | `celestial` | `svg_linalg_cover()` | `seed.rs:592-593` |
| Orbital Mechanics & Trajectory Design | `solar` | `svg_orbit_cover()` | `seed.rs:770-771` |
| Fourier & Signal Processing | `midnight` | `svg_signals_cover()` | `seed.rs:932-933` |
| Thermodynamics | `crimson` | `svg_thermo_cover()` | `seed.rs:1090-1091` |

This path is why format 1 exists: the artwork travels **inside the JSON payload**
with zero CDN dependency, but is large (the base64 is stored and served verbatim).

---

## 5. Generation path B — AI / pasted vector covers

### 5.1 The prompt template
`src/services/coverPromptTemplate.js:6-63` exports `LLM_COVER_PROMPT_TEMPLATE`,
the copy-paste prompt surfaced by the "Generate with AI" button in
`NewBookModal` (`:130-138`) and `ConfigModal` (`:58-64`). It pins exactly the
constraints clients rely on:

- `viewBox="0 0 1000 1440"` + `xmlns`, scale-independent.
- Dark base (`#020617`/`#090d16`/`#050508`) with luminous accents
  (`#818cf8`, `#34d399`, `#fbbf24`, `#38bdf8`, `#f43f5e`).
- Four stacked layers: bg gradient → `.1`–`.2` geometry → topic illustration →
  typography (badge / volume / title / subtitle / author+seal).
- **Explicitly forbids baked-in spine, spine shadow, page edges, sheen, gloss,
  border frame, or device mockup** — the client renders those.
- Returns raw `<svg>…</svg>` only, no markdown fences.

`docs/ai-book-cover.md` is a standalone copy of the same spec for users to paste
without the app.

### 5.2 Normalization on input
`sanitizeCoverImageInput(input)` — `coverPromptTemplate.js:70-77`:
- Trims; if the value starts with `<svg` and ends with `</svg>`, returns
  `data:image/svg+xml;utf8,${encodeURIComponent(trimmed)}` (format 2).
- Any other non-empty string passes through unchanged (so a raw URL, a
  `data:…` URI, or a non-SVG string is kept as the user typed it).

Both `NewBookModal` and `ConfigModal` call it from `handleCoverTextChange` and
**clear `coverImageFileId`** whenever cover text changes (`NewBookModal.jsx:30-36`,
`ConfigModal.jsx:66-72`), because a pasted URL/Data URI is not an ImageKit file.

---

## 6. Generation path C — Uploaded raster images (ImageKit)

The only path that produces an ImageKit CDN URL and a `fileId`.

1. **Client compression** — `optimizeCoverImage(file, {maxDimension:900,
   quality:0.82})` in `src/services/imageOptimizer.js:15-86`:
   - Returns non-images and `image/svg+xml` untouched (`:17`).
   - Draws to a canvas bounded to 900px, high-quality smoothing, exports WebP
     with JPEG fallback (`:59-68`), keeps the original if output is larger.
2. **Gate** — `ConfigModal` rejects files > `MAX_IMAGE_BYTES` (4 MB,
   `presets.js:14`) before upload (`ConfigModal.jsx:77-79`).
3. **Upload call** — `uploadCoverImage(file, token)` — `api.js:275-285` — POST
   multipart `file` to `${API_BASE_URL}/api/upload` with Bearer auth.
4. **Server relays to ImageKit** — `upload_handler` in `server/src/main.rs:1495-1555`:
   - Requires `IMAGEKIT_PRIVATE_KEY` (`:1496`).
   - Any extension accepted; filename replaced with `{uuid}.{ext}` (`:1512`).
   - Streams to `https://upload.imagekit.io/api/v1/files/upload` with basic auth
     `private_key:""`, `folder=/esper/covers`, `useUniqueFileName=true`
     (`:1514-1528`).
   - Enforces `max_upload_bytes` = **25 MB** → `413` (`:1509-1511`).
   - Returns legacy shape `{ success, url, thumbnailUrl, fileId }` (`:1547-1552`).
5. **Persist** — the modal stores `res.url` into `coverImage` and `res.fileId`
   into `coverImageFileId`, then it is saved with the notebook
   (`NewBookModal.jsx:47-51`, `ConfigModal.jsx:87-91`).

The web `<img src>` then loads straight from ImageKit; Android loads the same URL
via Coil (no Data-URI decode needed for this format).

---

## 7. Theming engine — `src/services/coverTheme.js`

- `getBookCoverTheme(doc)` — `:239-301`:
  - Base theme = `PRESET_THEMES[doc.coverPreset]` else `celestial` (`:242-243`).
  - If **no** `coverImage`, returns the base preset theme (`:245-247`).
  - If there **is** a `coverImage`, synchronously derives a vibrant theme by
    hashing the cover string and picking one of six base hues
    `[234,158,45,210,275,345]` (`:254-271`), then caches it.
  - Then (web only) loads the image with `crossOrigin="anonymous"`, samples a
    single pixel via a 1×1 canvas, and if non-black (`r+g+b > 30`) replaces the
    cached theme with the sampled color (`:274-298`). CORS/broken images are
    swallowed (`:295`) and the hash theme stands.
- `withAlpha(color, alpha=0.55)` — `:203-233` — applies alpha to hex/rgb/hsl;
  used for the translucent spine.

So **chrome color follows the image when present**, otherwise the preset.

---

## 8. Web rendering pipeline (the layer contract)

All listing surfaces render the same layered stack. Reference implementation —
book grid card `src/components/DashboardView.jsx:349-403`:

1. **Container** — `aspect-[1/1.44]`, `rounded-xl`, `border-white/10`,
   `overflow-hidden`, and background = preset Tailwind `gradient` *unless* an
   image exists (`:354-356`).
2. **Artwork** — `<img src={coverImage} object-cover>` absolutely fills the box
   when present (`:358-364`).
3. **Ambient overlay** — `<div class="absolute inset-0 bg-black/25">` (`:365`).
4. **App-rendered spine** — left edge, `w-1.5` (=6px), full height,
   `backgroundColor: withAlpha(getBookCoverTheme(doc).spineColor, 0.55)`, above
   the image and `pointer-events-none` (`:366-370`).
5. **Badges** — chapter count (top-left), visibility lock/key/globe + pin
   (top-right), title only when no image (center), tag + actions (bottom)
   (`:371-435`).

Other surfaces reuse this pattern:
- Continue-reading hero — `DashboardView.jsx:197-220` (uses `aspect-[1/1.4]`,
  spine + no dark overlay).
- Tabs drawer list & tab strip — `src/components/drawers/TabsDrawer.jsx:145-249`
  (spine `w-1.5`, tab left border via `withAlpha(theme.spineColor, 0.55)`).
- Cosmic tabs bar accent dot — `src/components/CosmicTabsBar.jsx:118-141`.
- Live preview cards — `NewBookModal.jsx:191-218`, `ConfigModal.jsx` cover section.

Preset selection UI lives in `NewBookModal.jsx:140-153` and
`ConfigModal.jsx:273-283` (clicking a preset also clears `coverImage` +
`coverImageFileId`).

Artwork is **always full-bleed**; spine/overlay/badges are app-drawn.

---

## 9. Backend DAM lifecycle (cover file cleanup)

ImageKit files are content-addressed (UUID names) and immutable, so the server
garbage-collects by `fileId`:

- **On notebook upsert** — `main.rs:967-985`: reads the previous
  `cover_image_file_id`; if the incoming `cover_image_file_id` differs, calls
  `delete_imagekit_file(private_key, old_fid)` fire-and-forget.
- **On notebook delete** — `main.rs:1035-1051`: deletes the notebook's cover
  file after the row is removed.
- `delete_imagekit_file` — `main.rs:1442-1463` — `DELETE
  https://api.imagekit.io/v1/files/{fileId}` with basic auth; no-ops on empty key.
- Direct delete endpoint — `DELETE /api/v1/files/:file_id` (and legacy
  `/api/files/:file_id`) → `delete_file_handler` `main.rs:1466-1489`; web wrapper
  `deleteFileFromStorage` `api.js:287-297` (404 treated as success).

Implication: swapping an uploaded cover (or clearing it and saving) releases the
old ImageKit object server-side. A client that only changes `coverImage` without
carrying the matching `coverImageFileId` will orphan/overwrite incorrectly —
**always round-trip `coverImageFileId` with `coverImage`.**

---

## 10. Import / export

- AI book bundles (`docs/ai-book-bundle.md`) accept `coverPreset` only
  (`:54`, `:76`); no `coverImage` is specified, so imported books fall back to a
  preset gradient.
- `src/services/bookImporter.js:217-218` maps `meta.coverPreset || "celestial"`
  and `meta.coverImage || null`; the `.esper`/JSON single-file path does the same
  (`:250-251`).
- `ImportBookModal.jsx:14` resolves the preview preset from the parsed book and
  defaults to `PREMADE_COVERS[0]`.

---

## 11. Source-of-truth file map

| Concern | Location |
|---|---|
| DB schema fields | `server/src/schema.surql:40-42`, `:94-95` |
| Rust rows / defaults | `server/src/models.rs:143-148`, `:236-239`, `:281` |
| JSON (de)serialization | `server/src/main.rs:131-133`, `:144` |
| Upload relay + ImageKit delete | `server/src/main.rs:1429-1555` |
| DAM cleanup on save/delete | `server/src/main.rs:967-985`, `:1035-1051` |
| Seeded base64 SVG books | `server/src/seed.rs` |
| Preset catalog (`id/title/gradient`) | `src/constants/presets.js:17-42` |
| Palette + `normalizeDoc` | `src/constants/presets.js:44-73`, `:231-248` |
| Theme engine (`PRESET_THEMES`, `withAlpha`, sampling) | `src/services/coverTheme.js` |
| AI prompt + `sanitizeCoverImageInput` | `src/services/coverPromptTemplate.js` |
| Raster compression | `src/services/imageOptimizer.js` |
| Upload/delete API wrappers | `src/services/api.js:275-297` |
| Render surfaces | `src/components/DashboardView.jsx`, `drawers/TabsDrawer.jsx`, `CosmicTabsBar.jsx`, `modals/NewBookModal.jsx`, `modals/ConfigModal.jsx` |
| Client (Android) decode/render recipe | `022-esper/docs/client-svg-covers.md` |

---

## 12. Contract cheat-sheet for Android parity

- Store/pass `coverImage` **and** `coverImageFileId` together; never invent either.
- Four formats, precedence as §2; unknown `coverPreset` → `celestial`.
- Artwork `viewBox="0 0 1000 1440"` (1:1.44), full-bleed, no baked chrome.
- Client draws: a dark overlay (web uses `bg-black/25`; Android recipe uses
  `alpha 0.22`), left spine `6.dp` at `spineColor @ 0.55`, chapter/tag/visibility
  badges.
- Gradient fallback uses preset colors; theme color follows the image when a
  cover exists (web samples a pixel; Android may approximate via preset/hash).
- Coil cannot fetch `data:image/svg+xml;base64,…` string URIs — decode to
  `ByteArray` first (see `client-svg-covers.md`).
