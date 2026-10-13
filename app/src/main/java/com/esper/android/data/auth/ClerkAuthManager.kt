package com.esper.android.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri

object ClerkAuthManager {
    const val PUBLISHABLE_KEY = "pk_test_ZXhjaXRpbmctc2Vhc25haWwtNTM0NC5jbGVyay5hY2NvdW50cy5kZXYk"
    const val CLERK_FRONTEND_API = "https://exciting-seasnail-5344.clerk.accounts.dev"
    const val REDIRECT_SCHEME = "esper://oauth-callback"

    /**
     * Builds the Clerk Hosted Sign-In URL with Google OAuth preselected.
     */
    fun getGoogleOAuthUrl(): String {
        return "$CLERK_FRONTEND_API/sign-in?strategy=oauth_google&redirect_url=${Uri.encode(REDIRECT_SCHEME)}"
    }

    /**
     * Launches the Clerk browser authentication session.
     */
    fun launchClerkHostedAuth(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getGoogleOAuthUrl()))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
