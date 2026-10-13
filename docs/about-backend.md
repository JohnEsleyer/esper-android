# Esper Backend API & Android Integration Guide

This guide documents the Esper Rust/SurrealDB backend API and outlines how the Android client (`esper-android`) integrates with it, including Clerk authentication, zero-knowledge encryption, and network communication.

---

## 1. Architecture Overview

- **Server Tech Stack:** Rust (Axum, Tokio, Tower), SurrealDB 3.x over HTTP RPC, Argon2 password hashing.
- **Base URLs:**
  - **Local Development (Android Emulator):** `http://10.0.2.2:8080` (alias for host machine `127.0.0.1`)
  - **Local Development (Physical Device):** `http://<HOST_LAN_IP>:8080`
  - **Production:** `https://esper-api.johnesleyer.com`
- **Identity & Auth Provider:** Clerk (JWT validated via RS256 JWKS).
- **Access Control Model:**
  - `public`: Anyone can view. Only owner can edit.
  - `protected`: Requires verification of a notebook password. Returns a guest token.
  - `restricted`: Owner-only. Content is end-to-end encrypted client-side (zero-knowledge) using AES-256-GCM.
- **Member Tiers & Quotas:**
  - `visitor`: 50 notebooks, 10 shelves, 100 chapters/book, 2 MB visual payload, pruned after 30 days inactivity.
  - `member`: 150 notebooks, 30 shelves, 360 chapters/book, 4 MB visual payload, pruned after 365 days inactivity.
  - `admin`: 10,000 notebooks, 1,000 shelves, 1,000 chapters/book, 10 MB visual payload, never pruned.

---

## 2. Authentication Flow for Android

Esper delegates user identity to **Clerk**. The backend does not issue user credentials directly; it validates Clerk-issued session tokens using Clerk's JWKS endpoint (`CLERK_JWKS_URL`).

### 2.1 Android Sign-In Flow

```
[Android Client]                  [Clerk Identity Provider]                 [Esper Rust Backend]
       |                                      |                                      |
       |--- 1. Authenticate (OAuth/Email) --->|                                      |
       |<-- 2. Session Token (JWT RS256) -----|                                      |
       |                                                                             |
       |--- 3. HTTP Request + Header `Authorization: Bearer <JWT>` ----------------->|
       |                                                                             |--- 4. Verify signature via JWKS
       |                                                                             |--- 5. Extract `sub` (User ID)
       |<-- 6. API JSON Response ---------------------------------------------------|
```

### 2.2 Token Interceptor in Android (OkHttp / Retrofit)

All protected requests require an `Authorization: Bearer <token>` header:

```kotlin
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider()
        val requestBuilder = original.newBuilder()

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
```

### 2.3 Guest Access for `protected` Notebooks

When accessing a `protected` notebook:
1. `GET /api/notes/:id` returns `401 Unauthorized` with `{ "isProtected": true, "cryptoSalt": "...", "wrappedDek": "...", "title": "..." }`.
2. The Android client displays a password prompt.
3. The client calls `POST /api/notes/:id/verify-password` with `{ "password": "<input>" }`.
4. The server returns `{ "guestToken": "guest-token-<cleanId>" }`.
5. The client retries `GET /api/notes/:id` with the header:
   ```http
   x-guest-token: guest-token-<cleanId>
   ```

---

## 3. Endpoints Reference

### 3.1 System & Health

#### `GET /api/health`
- **Auth:** Public / Optional
- **Description:** Verifies service uptime, database connectivity, and returns plan limits.
- **Response (200 OK):**
  ```json
  {
    "service": "esper-rust",
    "engine": "surrealdb-http-client",
    "schema": "normalized-v2",
    "status": "healthy",
    "limits": {
      "max_docs": 100,
      "max_markdown_bytes": 524288,
      "max_visual_bytes": 2097152
    }
  }
  ```

---

### 3.2 User & Profile

#### `GET /api/me`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Returns profile metadata, current tier limits, and notebook/shelf usage stats.
- **Response (200 OK):**
  ```json
  {
    "sub": "user_2xyz...",
    "name": "Jane Doe",
    "avatar": "https://img.clerk.com/...",
    "member_type": "visitor",
    "last_active_at": 1711234567,
    "usage": {
      "notebook_count": 4,
      "shelf_count": 1
    },
    "limits": {
      "max_notebooks": 50,
      "max_shelves": 10,
      "max_pages_per_book": 100,
      "max_visual_bytes": 2097152,
      "inactivity_days": 30
    }
  }
  ```

#### `POST /api/me`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Upserts the user's display name and avatar in the backend database.
- **Request Body:**
  ```json
  {
    "name": "Jane Doe",
    "avatar": "https://img.clerk.com/avatar.png"
  }
  ```
- **Response (200 OK):**
  ```json
  { "success": true }
  ```

---

### 3.3 Notebooks

#### `GET /api/user/notes`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Retrieves all notebooks owned by the authenticated user, along with their pages, canvases, and questions. Refreshes the user's `last_active_at` timestamp.
- **Response (200 OK):** Array of Notebook objects (see schema below).

#### `GET /api/notes/:id`
- **Auth:** Optional (`Bearer <token>` or `x-guest-token`)
- **Description:** Retrieves a single notebook by ID.
  - Returns `403 Forbidden` if visibility is `restricted` and the caller is not the owner.
  - Returns `401 Unauthorized` if visibility is `protected` without a valid `x-guest-token`.
- **Response (200 OK):** Single Notebook object:
  ```json
  {
    "id": "kepler-001",
    "title": "Kepler's Laws",
    "description": "Planetary motion simulations",
    "visibility": "public",
    "isEncrypted": false,
    "wrappedDek": null,
    "cryptoSalt": null,
    "tags": ["Astrodynamics"],
    "coverPreset": "celestial",
    "coverImage": null,
    "isPinned": true,
    "owner_sub": "user_2xyz...",
    "createdAt": 1700000000,
    "updatedAt": 1700005000,
    "pages": [
      {
        "id": "p1",
        "title": "Introduction",
        "parentId": null,
        "notes": "# Introduction\n\nNotes markdown...",
        "position": 0
      }
    ],
    "canvases": [
      {
        "id": "c1",
        "type": "html",
        "title": "Orbit Simulator",
        "assignedPages": ["p1"],
        "data": { "code": "<!DOCTYPE html>..." }
      }
    ],
    "questions": [
      {
        "id": "q1",
        "title": "State Kepler's 1st Law",
        "answer": "Planets move in ellipses...",
        "position": 0
      }
    ]
  }
  ```

#### `POST /api/notes`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Creates a new notebook with the given ID.
- **Request Body:**
  ```json
  {
    "id": "doc-1711234567890",
    "title": "Orbital Mechanics",
    "description": "Lecture Series",
    "visibility": "public",
    "password": null,
    "is_encrypted": false,
    "wrapped_dek": null,
    "crypto_salt": null,
    "tags": ["Physics"],
    "coverPreset": "celestial",
    "coverImage": null,
    "isPinned": false,
    "pages": [
      { "id": "p1", "parentId": null, "title": "Ch 1", "notes": "# Ch 1" }
    ],
    "canvases": [],
    "questions": []
  }
  ```
- **Response (200 OK):**
  ```json
  { "success": true, "id": "doc-1711234567890", "syncedAt": 1711234568000 }
  ```

#### `PUT /api/notes/:id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Full upsert / save of an existing notebook. Replaces all child pages, canvases, and questions atomically.
- **Request Body:** Same schema as `POST /api/notes`, without the top-level `id` wrapper.
- **Response (200 OK):**
  ```json
  { "success": true, "id": "doc-1711234567890", "syncedAt": 1711234568000 }
  ```

#### `DELETE /api/notes/:id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Cascade deletes the notebook, its pages, canvases, questions, comments, and assets.
- **Response (200 OK):**
  ```json
  { "success": true, "id": "doc-1711234567890" }
  ```

#### `POST /api/notes/:id/verify-password`
- **Auth:** Public
- **Description:** Validates a password against the Argon2 hash of a protected notebook.
- **Request Body:**
  ```json
  { "password": "secret-passphrase" }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "guestToken": "guest-token-<cleanId>",
    "cryptoSalt": "...",
    "wrappedDek": "..."
  }
  ```

---

### 3.4 Chapters & Pages

#### `POST /api/notes/:id/pages`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Appends a new chapter/subchapter to an existing notebook without resending the entire book.
- **Request Body:**
  ```json
  {
    "id": "p-1711234567890",
    "parentId": "p1",
    "title": "Subchapter 1.1",
    "notes": "# Subchapter 1.1 notes",
    "position": 1
  }
  ```
- **Response (200 OK):**
  ```json
  { "success": true, "id": "p-1711234567890" }
  ```

#### `PATCH /api/notes/:id/pages/:page_id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Granular update for notes, title, or position of a single page.
- **Request Body:**
  ```json
  {
    "title": "Updated Title",
    "notes": "Updated Markdown content...",
    "position": 0
  }
  ```
- **Response (200 OK):**
  ```json
  { "success": true, "pageId": "p1" }
  ```

#### `DELETE /api/notes/:id/pages/:page_id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Recursively deletes a page, all its child descendant subpages, associated comments, and clears canvas page assignments.
- **Response (200 OK):**
  ```json
  { "success": true, "deletedPageIds": ["p1", "p1_1", "p1_2"] }
  ```

---

### 3.5 Shelves (Playlists)

#### `GET /api/shelves`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Returns all shelves owned by the authenticated user with notebook IDs and book counts.
- **Response (200 OK):**
  ```json
  [
    {
      "id": "s-1711234567",
      "title": "Physics & Math",
      "description": "Semester 1",
      "color": "#3b82f6",
      "coverPreset": "celestial",
      "coverImage": null,
      "position": 0,
      "bookCount": 2,
      "notebookIds": ["doc-1", "doc-2"],
      "notebooks": []
    }
  ]
  ```

#### `GET /api/shelves/:id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Returns a specific shelf along with fully resolved child notebook objects.

#### `POST /api/shelves`
- **Auth:** Required (`Bearer <token>`)
- **Request Body:**
  ```json
  {
    "id": "s-1711234567",
    "title": "Astrodynamics",
    "description": "Core papers",
    "color": "#8b5cf6",
    "coverPreset": "midnight",
    "coverImage": null,
    "position": 0,
    "notebookIds": ["doc-1"]
  }
  ```

#### `PUT /api/shelves/:id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Updates shelf title, color, preset, position, and re-links `shelf_item` records.

#### `DELETE /api/shelves/:id`
- **Auth:** Required (`Bearer <token>`)
- **Description:** Deletes the shelf and its `shelf_item` links. Does *not* delete the contained notebooks.

---

### 3.6 Chapter Discussion & Comments

#### `GET /api/comments/:doc_id?page_id=:page_id`
- **Auth:** Public / Optional
- **Headers:** Supports `If-None-Match` for HTTP 304 Not Modified caching via ETag.
- **Description:** Retrieves up to 100 comments for a notebook or scoped to a specific chapter (`page_id`).
- **Response (200 OK):**
  ```json
  [
    {
      "id": "uuid-v4",
      "user_name": "Jane Doe",
      "user_avatar": "https://img.clerk.com/...",
      "content": "Great explanation on the ellipse focus!",
      "page": "page:p2",
      "parent": null,
      "author": "user:user_2xyz",
      "created_at": 1711234900
    }
  ]
  ```

#### `POST /api/comments/:doc_id`
- **Auth:** Required (`Bearer <token>`)
- **Request Body:**
  ```json
  {
    "content": "Clarification on equation 2...",
    "userName": "Jane Doe",
    "userAvatar": "https://img.clerk.com/avatar.png",
    "pageId": "p2",
    "parentId": null
  }
  ```
- **Response (200 OK):**
  ```json
  { "success": true, "id": "uuid-v4" }
  ```

---

### 3.7 File Uploads

#### `POST /api/upload`
- **Auth:** Required (`Bearer <token>`)
- **Content-Type:** `multipart/form-data`
- **Field Name:** `file`
- **Constraints:** Maximum 4 MB (`max_upload_bytes`). Image formats (`.webp`, `.jpg`, `.png`).
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "url": "https://esper-api.johnesleyer.com/uploads/3fa85f64-5717-4562-b3fc-2c963f66afa6.webp"
  }
  ```

---

## 4. End-to-End Encryption (Zero-Knowledge)

When `visibility == "restricted"`, the notebook fields (`title`, `description`, `page.title`, `page.notes`, `question.title`, `question.answer`) are encrypted client-side using **AES-256-GCM**. The backend only stores ciphertext strings.

### 4.1 Encrypted Payload Format
All encrypted strings follow this format:
```
enc:v1:<Base64(12-byte IV)>:<Base64(Ciphertext + GCM Tag)>
```

### 4.2 Key Derivation
- Key Derivation Function: **PBKDF2** with `SHA-256`.
- Iterations: `100,000`.
- Salt: `"esper_user_salt_2025"`.
- Passphrase: User's Clerk User ID (`user.id`, e.g., `user_2xyz...`).

### 4.3 Kotlin Implementation (Android)

Using standard Java/Android Cryptography Architecture (`javax.crypto`):

```kotlin
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EsperCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT = "esper_user_salt_2025"

    fun deriveKey(userId: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(userId.toCharArray(), SALT.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encryptText(plaintext: String, key: SecretKeySpec): String {
        if (plaintext.startsWith("enc:v1:")) return plaintext

        val iv = ByteArray(IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val dataB64 = Base64.getEncoder().encodeToString(cipherBytes)

        return "enc:v1:$ivB64:$dataB64"
    }

    fun decryptText(encryptedString: String, key: SecretKeySpec): String {
        if (!encryptedString.startsWith("enc:v1:")) return encryptedString

        return try {
            val parts = encryptedString.split(":")
            val iv = Base64.getDecoder().decode(parts[2])
            val cipherBytes = Base64.getDecoder().decode(parts[3])

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(cipherBytes)

            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "[Decryption Error]"
        }
    }
}
```

---

## 5. Android Architecture & Implementation Checklist

### 5.1 Dependencies (`build.gradle.kts`)
- **Networking:** `com.squareup.retrofit2:retrofit:2.11.0`, `com.squareup.okhttp3:logging-interceptor:4.12.0`
- **JSON:** `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1` or `com.squareup.retrofit2:converter-gson`
- **Markdown Rendering:** `io.noties.markwon:core:4.6.2`, `io.noties.markwon:syntax-highlight`
- **Visual Canvases:** Android `WebView` with hardware acceleration enabled for rendering HTML5, WebGL, Canvas simulations, and YouTube embeds (`<iframe>`).
- **Image Compression:** `Bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, stream)` prior to upload (`POST /api/upload`).

### 5.2 Retrofit Interface Example

```kotlin
interface EsperApiService {
    @GET("api/health")
    suspend fun getHealth(): HealthResponse

    @GET("api/me")
    suspend fun getProfile(): UserProfileResponse

    @POST("api/me")
    suspend fun updateProfile(@Body body: MePayload): SimpleResponse

    @GET("api/user/notes")
    suspend fun getUserNotes(): List<NotebookDto>

    @GET("api/notes/{id}")
    suspend fun getNote(
        @Path("id") id: String,
        @Header("x-guest-token") guestToken: String? = null
    ): NotebookDto

    @POST("api/notes")
    suspend fun createNote(@Body body: PostNotebookPayload): SyncResponse

    @PUT("api/notes/{id}")
    suspend fun saveNote(@Path("id") id: String, @Body body: SaveNotebookPayload): SyncResponse

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): SimpleResponse

    @PATCH("api/notes/{docId}/pages/{pageId}")
    suspend fun patchPage(
        @Path("docId") docId: String,
        @Path("pageId") pageId: String,
        @Body body: PatchPagePayload
    ): SimpleResponse

    @Multipart
    @POST("api/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): UploadResponse
}
```

### 5.3 Offline Storage & Synchronization Pattern
1. **Local Cache:** Store fetched notebooks in Room database or SQLDelight.
2. **Optimistic Updates:** Apply changes locally with an `is_dirty` flag.
3. **Debounced Sync:** Use Kotlin Coroutines (`Flow.debounce(600)`) to push changes to `PUT /api/notes/:id` or `PATCH /api/notes/:id/pages/:pageId`.
4. **Offline Export / Import:** Support reading and writing `.zip` backups generated using the same directory schema as `exportUserDataToZip()`:
   - `manifest.json`
   - `shelves.json`
   - `notebooks.json`
   - `books/<n>_<title>/<chapter>.md`
   - `books/<n>_<title>/visuals/<visual>.html`
