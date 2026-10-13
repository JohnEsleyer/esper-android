# ESPER SYSTEM SPECIFICATION (FOR ANDROID IMPLEMENTATION)

---

## 1. High-Level Architecture & Philosophy
Esper is an interactive note-taking and notebook environment structured around:
1. **Interactive Companion Visuals** (Sandboxed HTML/JS/WebGL simulations, synchronized YouTube video playback, and vector whiteboard diagrams).
2. **Hierarchical Page Notes** (Markdown-based document trees with chapters and sub-chapters).
3. **Structured Review/Questions** (Q&A flashcard-like companion tabs per book).
4. **Chapter-Scoped Discussions** (User comments linked to specific chapters).
5. **Zero-Knowledge Client-Side Encryption** (Restricted notebooks encrypted client-side using AES-GCM + PBKDF2 before hitting the network).
6. **Multi-Tier Multi-Tenancy** (`visitor`, `member`, `admin`) with automated inactive account pruning.

```
┌────────────────────────────────────────────────────────┐
│                   Esper Client                        │
│ (Web / Desktop / Future Android Kotlin App)           │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTP REST (JSON / Multipart)
                           │ Bearer Clerk JWT or Guest Token
                           ▼
┌────────────────────────────────────────────────────────┐
│               Esper Rust Backend (Axum)                │
│             Port 8080 (esper-server)                   │
├────────────────────────────────────────────────────────┤
│ • Auth & Token Verification (Clerk JWKS RS256)         │
│ • Quota & Plan Enforcement (TierLimits)                │
│ • Static Assets & Media Uploads (/uploads/...)         │
│ • Inactivity Reaper Background Task (6h loops)         │
└──────────────────────────┬─────────────────────────────┘
                           │ SurrealQL over HTTP (JSON)
                           ▼
┌────────────────────────────────────────────────────────┐
│                SurrealDB v3.x Engine                   │
│   Namespace: "esper", Database: "production"           │
│   Normalized relational tables (SCHEMAFULL)            │
└────────────────────────────────────────────────────────┘
```

---

## 2. Domain Data Model & Normalized Schema

All IDs in SurrealDB follow the record convention `table:id`. Client APIs strip prefixes on outbound reads and parse bare IDs inbound.

### Tables & Relationships
- **`user`**: `user:<sub_id>`
  - `name: string`, `avatar: option<string>`, `member_type: "visitor" | "member" | "admin"` (default `"visitor"`), `last_active_at: int`, `created_at: int`, `updated_at: int`.
- **`notebook`**: `notebook:<id>`
  - `title: string`, `description: string`, `owner: record<user>`, `owner_sub: option<string>`, `visibility: "public" | "restricted" | "protected"`, `password_hash: option<string>` (Argon2), `is_encrypted: bool`, `wrapped_dek: option<string>`, `crypto_salt: option<string>`, `tags: array<string>`, `cover_preset: string`, `cover_image: option<string>`, `is_pinned: bool`, `created_at: int`, `updated_at: int`.
- **`page`**: `page:<id>`
  - `notebook: record<notebook>`, `parent: option<record<page>>`, `title: string`, `notes: string` (Markdown), `position: int`.
- **`canvas`**: `canvas:<id>`
  - `notebook: record<notebook>`, `kind: "html" | "youtube" | "excalidraw"`, `title: string`, `payload: string` (JSON stringified), `pages: array<record<page>>` (chapters this visual spans across).
- **`question`**: `question:<id>`
  - `notebook: record<notebook>`, `title: string`, `answer: string` (Markdown), `position: int`, `created_at: int`.
- **`comment`**: `comment:<id>`
  - `notebook: record<notebook>`, `page: option<record<page>>`, `parent: option<record<comment>>`, `author: option<record<user>>`, `user_sub: option<string>`, `user_name: string`, `user_avatar: option<string>`, `content: string`, `created_at: int`.
- **`shelf`**: `shelf:<id>`
  - `owner: record<user>`, `title: string`, `description: string`, `color: string` (hex), `cover_preset: string`, `cover_image: option<string>`, `position: int`, `created_at: int`, `updated_at: int`.
- **`shelf_item`**: Relation joining `shelf` and `notebook` with `position: int` (Unique on `[shelf, notebook]`).

---

## 3. Membership Tiers & Hard Quotas

Limits are enforced on save/create endpoints:

| Feature / Limit | Visitor | Member | Admin |
|---|---|---|---|
| **Max Notebooks** | 50 | 150 | 10,000 |
| **Max Shelves** | 10 | 30 | 1,000 |
| **Max Pages/Book** | 100 | 360 | 1,000 |
| **Max Visual Code (Bytes)** | 2 MB (2,097,152 B) | 4 MB (4,194,304 B) | 10 MB (10,485,760 B) |
| **Inactivity Auto-Prune** | 30 days (1 month) | 365 days (1 year) | **Immune** |

### Automated Reaper Engine
Every 6 hours, the backend queries:
```sql
SELECT meta::id(id) AS id FROM user WHERE
  (member_type = 'visitor' AND last_active_at < (now - 30d)) OR
  (member_type = 'member' AND last_active_at < (now - 365d));
```
When an account expires, cascading deletes wipe all comments, pages, canvases, questions, assets, notebooks, shelves, and user records.

---

## 4. End-to-End Cryptography & Security

### 4.1. Access Controls
- **`public`**: Accessible by anyone (read-only). Plaintext on wire.
- **`protected`**: Password-gated. Password verified server-side via **Argon2id**. On success, client receives a guest token (`guest-token-<cleanId>`) to supply in header `x-guest-token`.
- **`restricted`**: Private to author. Encrypted client-side. Server only stores ciphertext.

### 4.2. Zero-Knowledge Client-Side Encryption
Used for restricted notebooks:
1. **Key Derivation (PBKDF2)**:
   - Passphrase = User ID (`user.id`).
   - Salt = `"esper_user_salt_2025"`.
   - Hash = SHA-256, 100,000 iterations.
   - Derived Algorithm = AES-GCM, 256-bit key length.
2. **Ciphertext Envelope Format**:
   - `enc:v1:<Base64(12-byte IV)>:<Base64(Ciphertext)>`
3. **Encrypted Fields**:
   - `page.title`, `page.notes`
   - `question.title`, `question.answer`
   - `notebook.title`, `notebook.description`
4. **Defensive Decryption**:
   - Strings not starting with `enc:v1:` pass through unchanged.
   - Decryption runs up to 3 bounded iterations to heal legacy double-encrypted data.
   - Client prevents encrypting already-prefixed `enc:v1:` strings.

---

## 5. REST API Specification

### Authentication
- `Authorization: Bearer <clerk_jwt_token>`
- Or `x-guest-token: guest-token-<docId>` for unlocked protected notes.

### Endpoints

#### User & Profile
- `GET /api/me`: Returns `{ sub, name, avatar, member_type, last_active_at, usage: { notebook_count, shelf_count }, limits: { ... } }`
- `POST /api/me`: Body `{ name, avatar }`. Upserts user and updates `last_active_at`.

#### Notebooks
- `GET /api/user/notes`: Returns user's notebooks (including pages, canvases, questions).
- `GET /api/notes/:id`: Fetch single notebook. If protected and missing `x-guest-token`, returns HTTP 401 with `{ isProtected: true, cryptoSalt, wrappedDek, title }`.
- `POST /api/notes/:id/verify-password`: Body `{ password }`. Returns `{ success: true, guestToken, cryptoSalt, wrappedDek }`.
- `POST /api/notes`: Create notebook. Body: `{ id, body: <SaveNotebookPayload> }`.
- `PUT /api/notes/:id`: Full upsert of notebook and its child items.
- `DELETE /api/notes/:id`: Cascading delete of notebook and all children.

#### Pages (Granular Operations)
- `POST /api/notes/:id/pages`: Body `{ id?, parent_id?, title, notes?, position? }`.
- `PATCH /api/notes/:id/pages/:page_id`: Body `{ title?, notes?, position? }`.
- `DELETE /api/notes/:id/pages/:page_id`: Recursively deletes chapter, subchapters, linked comments, and cleans canvas associations.

#### Shelves
- `GET /api/shelves`: Returns user's shelves with `notebookIds` and book counts.
- `GET /api/shelves/:id`: Assembles shelf with full child notebook models.
- `POST /api/shelves`: Body `{ id, body: <SaveShelfPayload> }`.
- `PUT /api/shelves/:id`: Upsert shelf metadata and re-link notebooks.
- `DELETE /api/shelves/:id`: Deletes shelf record (leaves notebooks intact).

#### Comments & Assets
- `GET /api/comments/:doc_id?page_id=<optional>`: Fetch up to 100 comments. Supports `ETag` and `If-None-Match` cache negotiation.
- `POST /api/comments/:doc_id`: Body `{ content, userName, userAvatar?, pageId?, parentId? }`.
- `POST /api/upload`: Multipart `file`. Returns `{ success: true, url: "<baseUrl>/uploads/<uuid>.<ext>" }`. Max 4 MB.

---

## 6. Client Features & Domain Logics

### 6.1. Visual Canvas Companion
Canvases can be assigned to multiple chapters simultaneously via `canvas.assignedPages = ["p1", "p2", ...]`:
1. **HTML Simulation (`type: "html"`)**:
   - Sandboxed iframe (`sandbox="allow-scripts allow-same-origin"`).
   - Live code editor with run hotkey (`Ctrl/Cmd+Enter`).
2. **YouTube Player (`type: "youtube"`)**:
   - Embed URL transformed to: `https://www.youtube.com/embed/{id}?enablejsapi=1&origin={origin}&start={s}&end={e}`.
   - Interactive timestamps in Markdown (e.g. `[01:45]` or `[02:15:30]`) trigger video seeking via `postMessage`:
     `{ "event": "command", "func": "seekTo", "args": [seconds, true] }`.
3. **Vector Whiteboard (`type: "excalidraw"`)**:
   - Vector drawing canvas storing elements: `{ tool, points, x, y, w, h, color, width, text }`.

### 6.2. Markdown Title Derivation
- When title input is empty or defaults to chapter names, title is dynamically extracted from the first level-1 header:
  `^#\s+(.+)$`
- Renaming via chapter tree updates or prepends `# <NewTitle>` in the Markdown buffer.

### 6.3. Hierarchical Numbering Engine
Pages compute dot-notation numbering:
- Root page: `1.`, `2.`, `3.`
- Sub-page: `1.1`, `1.2`, `2.1`

### 6.4. Offline Export (ZIP Format)
Export archive structure:
```
esper-offline-backup-YYYY-MM-DD.zip
├── manifest.json
├── shelves.json
├── notebooks.json
└── books/
    └── 1_Book_Title/
        ├── 1_Chapter_One.md
        ├── 2_Chapter_Two.md
        └── visuals/
            └── 1_Simulation.html
```

---

## 7. Android Implementation Blueprint (Kotlin / Jetpack Compose)

When designing the native Android app:

```
                  ┌───────────────────────────────┐
                  │    Android App Components     │
                  └───────────────┬───────────────┘
                                  │
    ┌─────────────────────────────┼─────────────────────────────┐
    ▼                             ▼                             ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ Network & Auth   │    │ Local Storage    │    │ UI & Editors     │
├──────────────────┤    ├──────────────────┤    ├──────────────────┤
│ • Retrofit/Ktor  │    │ • Room DB        │    │ • Jetpack Compose│
│ • OkHttp Inter-  │    │   (Offline-first)│    │ • Android WebView│
│   ceptor         │    │ • Encrypted-     │    │   (Simulations)  │
│ • Clerk Android  │    │   SharedPreferences│  │ • Markdown Edit/ │
│   SDK / Auth0    │    │ • BiometricKey-  │    │   RichText       │
│                  │    │   Store (Dek/Umk)│    │ • YouTube IFrame │
└──────────────────┘    └──────────────────┘    └──────────────────┘
```

1. **Networking Layer**:
   - Target API: Base URL defined via build flavors (`http://10.0.2.2:8080` emulator, `https://esper-api.johnesleyer.com` production).
   - Inject `Authorization: Bearer <clerkToken>` dynamically.
   - For protected books, store `guestToken` per notebook in a transient session map.

2. **Security & Cryptography**:
   - Use `javax.crypto.Cipher` with `AES/GCM/NoPadding` (IV: 12 bytes, Tag: 128 bits).
   - Use `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` with 100,000 iterations to match web key derivation.
   - Match ciphertext envelope format: `enc:v1:<Base64(IV)>:<Base64(Data)>`.

3. **Rendering Visuals & Canvas**:
   - **HTML Simulations**: Render via `android.webkit.WebView` with `settings.javaScriptEnabled = true`, `settings.domStorageEnabled = true`. Load data via `loadDataWithBaseURL("https://localhost/", code, "text/html", "UTF-8", null)`.
   - **YouTube Sync**: Use Android YouTube Player API or embedded WebView with IFrame API enabled.
   - **Markdown Editor**: Use native Jetpack Compose Markdown engines with custom regex timestamp detectors (`\[(\d{1,2}:\d{2}(?::\d{2})?)\]`) that route click events to the video player.

4. **Offline Sync Pattern**:
   - Model Room database mirrors the SurrealDB schema (`NotebookEntity`, `PageEntity`, `CanvasEntity`, `QuestionEntity`, `ShelfEntity`).
   - Mark dirty state with `isSynced` boolean flags; debounce synchronization to backend by 600ms on background coroutines (`Dispatchers.IO`).
