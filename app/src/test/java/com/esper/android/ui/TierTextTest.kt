package com.esper.android.ui

import com.esper.android.ui.bookshelf.retentionText
import com.esper.android.ui.bookshelf.tierBadgeText
import org.junit.Assert.assertEquals
import org.junit.Test

class TierTextTest {

    @Test
    fun `offline badge regardless of tier`() {
        assertEquals("OFFLINE LOCAL", tierBadgeText("admin", signedIn = false))
        assertEquals("OFFLINE LOCAL", tierBadgeText(null, signedIn = false))
    }

    @Test
    fun `badge reflects resolved tier and syncing state`() {
        assertEquals("ADMIN PLAN", tierBadgeText("admin", signedIn = true))
        assertEquals("MEMBER PLAN", tierBadgeText("member", signedIn = true))
        assertEquals("VISITOR PLAN", tierBadgeText("visitor", signedIn = true))
        assertEquals("SYNCING…", tierBadgeText(null, signedIn = true))
    }

    @Test
    fun `admin retention is never pruned`() {
        assertEquals(
            "• Inactivity retention: never pruned (admin)",
            retentionText("admin", null)
        )
    }

    @Test
    fun `known tiers show day counts`() {
        assertEquals(
            "• Inactivity retention: 30 days cloud backup",
            retentionText("visitor", 30)
        )
        assertEquals(
            "• Inactivity retention: 365 days cloud backup",
            retentionText("member", 365)
        )
    }

    @Test
    fun `unknown state shows placeholder`() {
        assertEquals("• Inactivity retention: …", retentionText(null, null))
    }
}
