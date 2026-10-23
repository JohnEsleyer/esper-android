# Turnstile on protected-notebook unlock — Android action item

> Status: **open** — the web client is done; the Android client needs a change
> before it can open password-protected notebooks again.
> Backend change landed: 2026-10-23. Related: `022-esper/docs/turnstile-credentials.md`.

## What changed

`POST /api/notes/{id}/verify-password` now requires a Cloudflare Turnstile token
in addition to the password:

```json
{
  "password": "…",
  "cf-turnstile-response": "<turnstile token>"
}
```

The backend (`server/src/main.rs::verify_password_handler` →
`server/src/turnstile.rs`) calls Siteverify and requires all of:

- `success === true`
- `action === "unlock"`
- `hostname` is in `TURNSTILE_HOSTNAMES` (production: `esper.johnesleyer.com`)

Any failure returns `403 {"error":"forbidden"}`. The check is **fail-closed**:
if the server has no `TURNSTILE_SECRET` / `TURNSTILE_HOSTNAMES`, unlock is
disabled for everyone.

The comment endpoint (`POST /api/comments/{docId}`, action `comment`) is gated
the same way, but the Android client does not post comments today.

## Symptom in the app

`EsperApi.verifyPassword()` sends only `password`, so the request gets `403`.
`GuestTokenStore` never receives a `guestToken`, and the protected notebook
cannot be opened. Wrong passwords also surface as a generic failure, so the two
cases look identical to the user.

## Options

Pick one; the backend change is already deployed independently of Android.

### A. Exempt Firebase-authenticated requests (no Android change)

The Android OkHttp interceptor already attaches `Authorization: Bearer <Firebase
ID token>` when a user is signed in (`EsperApi.kt`, auth interceptor). The
unlock route runs behind `optional_auth_middleware`, so the backend can skip
Turnstile when that token verified.

Trade-off: a signed-in account is no longer bot-checked on unlock. Anonymous
brute-force attempts stay gated; an attacker would need a valid Firebase
account, which raises the cost substantially.

Axum 0.7 does not support `Option<Extension<T>>`, so add a tiny extractor:

```rust
use axum::{extract::FromRequestParts, http::request::Parts};
use std::convert::Infallible;

/// `Some(sub)` when `optional_auth_middleware` verified a Firebase token.
struct OptionalUser(Option<String>);

impl<S: Send + Sync> FromRequestParts<S> for OptionalUser {
    type Rejection = Infallible;

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        Ok(Self(parts.extensions.get::<String>().cloned()))
    }
}
```

Then in `verify_password_handler`, add `user: OptionalUser` before the `Json`
extractor and gate only anonymous callers:

```rust
if user.0.is_none()
    && !state
        .turnstile
        .verify(
            payload.turnstile_token.as_deref().unwrap_or_default(),
            client_ip(&headers).as_deref(),
            "unlock",
        )
        .await
{
    return Err((StatusCode::FORBIDDEN, "forbidden"));
}
```

Test: signed-in Android unlock → `200`; anonymous `curl` without a token → `403`.

### B. Add Turnstile to the Android app (no backend change)

Render the widget in a `WebView` and send the resulting token with the password.

1. Host a small page on an allowed hostname (the widget already trusts
   `esper.johnesleyer.com`), e.g. `https://esper.johnesleyer.com/turnstile.html`:

   ```html
   <!doctype html>
   <meta charset="utf-8" />
   <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" async defer></script>
   <div id="w"></div>
   <script>
     const action = new URLSearchParams(location.search).get("action") || "unlock";
     const boot = () => {
       if (!window.turnstile) return setTimeout(boot, 100);
       turnstile.render("#w", {
         sitekey: "0x4AAAAAAFh9eu_LjlUap8Tx",
         action,
         callback: (token) => window.EsperTurnstile.onToken(token),
         "error-callback": () => window.EsperTurnstile.onError("challenge_failed"),
       });
     };
     boot();
   </script>
   ```

   The page contains only the **public sitekey**. Never ship the Turnstile
   secret in the APK.

2. In Kotlin, load that page in a `WebView` with JS enabled and bridge the
   callback. One `WebView`/token per unlock attempt; tokens are single-use.

   ```kotlin
   @SuppressLint("SetJavaScriptEnabled")
   fun requestUnlockToken(context: Context, onResult: (Result<String>) -> Unit) {
       val webView = WebView(context).apply {
           settings.javaScriptEnabled = true
           addJavascriptInterface(object {
               @JavascriptInterface fun onToken(token: String) = onResult(Result.success(token))
               @JavascriptInterface fun onError(code: String) = onResult(Result.failure(IllegalStateException(code)))
           }, "EsperTurnstile")
           loadUrl("https://esper.johnesleyer.com/turnstile.html?action=unlock")
       }
       // Keep a strong reference until the callback fires, then destroy the WebView.
   }
   ```

3. Send the token with the existing call — the API signature already accepts a
   map, so no Retrofit change is needed:

   ```kotlin
   api.verifyPassword(
       id = docId,
       body = mapOf(
           "password" to password,
           "cf-turnstile-response" to token,
       ),
   )
   ```

Caveats:

- The widget hostname must match exactly (`esper.johnesleyer.com`); `file://`,
  `data:` and arbitrary WebView origins will fail Siteverify.
- A token expires after 300 s and is single-use. If the password is wrong,
  create a fresh token before the retry.
- Keep the WebView alive until the callback runs; destroy it afterwards.

### C. Defer

Leave Android as-is. Protected notebooks return `403` until the app ships a fix.
This is safe for the backend but a visible regression for Android users.

## Acceptance checklist

- [ ] Signed-in Android unlock of a protected notebook succeeds.
- [ ] Wrong password still fails (and the Turnstile token is not reused).
- [ ] Anonymous request without `cf-turnstile-response` gets `403`.
- [ ] Token generated for action `comment` is rejected by the unlock endpoint.
- [ ] No Turnstile secret is present in the APK, logs, or crash reports.

## References

- Web implementation: `src/components/modals/PasswordPromptModal.jsx`,
  `src/services/api.js`, `src/services/turnstile.js`.
- Backend: `server/src/turnstile.rs`, `server/src/main.rs`
  (`verify_password_handler`), `server/src/config.rs`.
- Credential storage and rotation: `022-esper/docs/turnstile-credentials.md`.
