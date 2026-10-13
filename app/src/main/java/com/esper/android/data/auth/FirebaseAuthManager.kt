package com.esper.android.data.auth

import android.content.Context
import com.google.firebase.auth.FirebaseUser

/**
 * Firebase Auth session helpers — replaces the old Clerk hosted-auth flow.
 * Google Sign-In itself lives in MainActivity via Play Services + Firebase.
 */
object FirebaseAuthManager {
    const val PREFS = "esper_auth_prefs"
    const val KEY_TOKEN = "firebase_jwt_token"

    fun getCachedToken(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    fun cacheToken(context: Context, user: FirebaseUser?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (user != null) {
            user.getIdToken(false).addOnSuccessListener { result ->
                prefs.edit().putString(KEY_TOKEN, result.token).apply()
            }
        } else {
            prefs.edit().remove(KEY_TOKEN).apply()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_TOKEN).apply()
    }
}
