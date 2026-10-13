# Esper Android — Error Log

Known problems encountered during builds/deploys, with fixes. Newest first.

---

## 2026-10-13 — `Duplicate resources` for `default_web_client_id`

**Status:** RESOLVED (proactively removed manual entry)

**Cause:** The Google Services Gradle plugin auto-generates `default_web_client_id`
from `app/google-services.json` (from the `oauth_client` entry with `client_type: 3`).
Having a duplicate manual string in `strings.xml` causes a resource merge collision.

**Fix:** Removed `default_web_client_id` from `app/src/main/res/values/strings.xml`,
leaving only `app_name`. `MainActivity.kt` resolves the plugin-generated ID seamlessly.

---

## 2026-10-13 — `Unresolved reference 'dp'` in `MainActivity.kt:412`

**Status:** FIXED

**Cause:** The Firebase rewrite of `MainActivity.kt` used `8.dp` in the
settings dialog but dropped `import androidx.compose.ui.unit.dp`.

**Fix:** Re-added the import. Re-ran `bun run deploy`.
