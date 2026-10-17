package com.esper.android.data.remote

import android.content.SharedPreferences

/**
 * Persists share-link guest tokens (`guest-token-<id>`, issued by
 * POST /api/notes/:id/verify-password) so protected notebooks stay
 * unlocked across restarts. Tokens are deterministic per book and
 * carry no identity — revocation happens server-side via password change.
 */
class GuestTokenStore(
    private val prefs: SharedPreferences,
    private val key: String = "guest_tokens_v1",
) {
    fun get(docId: String): String? =
        prefs.getStringSet(key, emptySet()).orEmpty()
            .firstOrNull { it.startsWith("$docId=") }
            ?.substringAfter("=")

    fun put(docId: String, token: String) {
        val next = prefs.getStringSet(key, emptySet()).orEmpty()
            .filterNot { it.startsWith("$docId=") }
            .toMutableSet()
        next.add("$docId=$token")
        prefs.edit().putStringSet(key, next).apply()
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }
}
