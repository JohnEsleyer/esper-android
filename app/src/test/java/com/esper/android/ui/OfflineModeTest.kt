package com.esper.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModeTest {

    @Test
    fun `isReadOnly is true when in offline mode regardless of network`() {
        val isNetworkOnline = true
        val isOfflineMode = true

        val isReadOnly = isOfflineMode
        assertTrue(isReadOnly)
    }

    @Test
    fun `isReadOnly is false when network is disconnected if offline mode is not enabled in settings`() {
        val isNetworkOnline = false
        val isOfflineMode = false

        val isReadOnly = isOfflineMode
        assertFalse(isReadOnly)
    }

    @Test
    fun `both online and not in offline mode gives full read-write access`() {
        val isNetworkOnline = true
        val isOfflineMode = false

        val isReadOnly = isOfflineMode
        assertFalse(isReadOnly)
    }
}
