package com.esper.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModeTest {

    @Test
    fun `isReadOnly is true when in offline mode regardless of network`() {
        val isNetworkOnline = true
        val isOfflineMode = true
        val forceOnline = false

        val isReadOnly = isOfflineMode || (!isNetworkOnline && !forceOnline)
        assertTrue(isReadOnly)
    }

    @Test
    fun `isReadOnly is true when network is disconnected without force online`() {
        val isNetworkOnline = false
        val isOfflineMode = false
        val forceOnline = false

        val isReadOnly = isOfflineMode || (!isNetworkOnline && !forceOnline)
        assertTrue(isReadOnly)
    }

    @Test
    fun `force online overrides disconnected network`() {
        val isNetworkOnline = false
        val isOfflineMode = false
        val forceOnline = true

        val isReadOnly = isOfflineMode || (!isNetworkOnline && !forceOnline)
        assertFalse(isReadOnly)
    }

    @Test
    fun `both online and not in offline mode gives full read-write access`() {
        val isNetworkOnline = true
        val isOfflineMode = false
        val forceOnline = false

        val isReadOnly = isOfflineMode || (!isNetworkOnline && !forceOnline)
        assertFalse(isReadOnly)
    }
}
