package com.esper.android.ui

import com.esper.android.ui.theme.CoverImageHelper
import com.esper.android.ui.theme.CoverThemeEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class CoverImageHelperTest {

    @Before
    fun setUp() {
        CoverImageHelper.clearCache()
    }

    @Test
    fun `null and blank return null`() {
        assertNull(CoverImageHelper.resolveCoverModel(null))
        assertNull(CoverImageHelper.resolveCoverModel(""))
        assertNull(CoverImageHelper.resolveCoverModel("   "))
    }

    @Test
    fun `remote https url returns string`() {
        val url = "https://ik.imagekit.io/esper/covers/sample.webp"
        assertEquals(url, CoverImageHelper.resolveCoverModel(url))
    }

    @Test
    fun `raw svg returns byte array`() {
        val rawSvg = "<svg viewBox=\"0 0 1000 1440\"><circle r=\"10\"/></svg>"
        val model = CoverImageHelper.resolveCoverModel(rawSvg)
        assertTrue(model is ByteArray)
        assertEquals(rawSvg, String(model as ByteArray, Charsets.UTF_8))
    }

    @Test
    fun `base64 data uri decodes to svg byte array`() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>Hi</text></svg>"
        val encoded = Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))
        val dataUri = "data:image/svg+xml;base64,$encoded"

        val model = CoverImageHelper.resolveCoverModel(dataUri)
        assertTrue(model is ByteArray)
        assertEquals(svg, String(model as ByteArray, Charsets.UTF_8))
    }

    @Test
    fun `percent-encoded data uri decodes to svg byte array`() {
        val dataUri = "data:image/svg+xml;utf8,%3Csvg%20viewBox=%220%200%201000%201440%22%3E%3C/svg%3E"
        val model = CoverImageHelper.resolveCoverModel(dataUri)
        assertTrue(model is ByteArray)
        assertEquals("<svg viewBox=\"0 0 1000 1440\"></svg>", String(model as ByteArray, Charsets.UTF_8))
    }

    @Test
    fun `raw base64 string decodes to svg byte array`() {
        val svg = "<svg viewBox=\"0 0 1000 1440\"></svg>"
        val b64 = Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))
        assertTrue(b64.startsWith("PHN2"))

        val model = CoverImageHelper.resolveCoverModel(b64)
        assertTrue(model is ByteArray)
        assertEquals(svg, String(model as ByteArray, Charsets.UTF_8))
    }

    @Test
    fun `theme engine resolves preset and falls back correctly`() {
        val quantum = CoverThemeEngine.getTheme("quantum")
        assertEquals(CoverThemeEngine.PRESETS.first { it.id == "quantum" }.theme, quantum)

        val fallback = CoverThemeEngine.getTheme("unknown_preset", null)
        assertEquals(CoverThemeEngine.PRESETS[0].theme, fallback)
    }

    // ---------------------------------------------------------------- caching
    // The LRU model cache keeps base64 decoding off the UI thread while the
    // grid scrolls: a recycled item must get the very same model instance.

    @Test
    fun `raw svg model is cached and reused`() {
        val rawSvg = "<svg viewBox=\"0 0 1000 1440\"><circle r=\"10\"/></svg>"
        val first = CoverImageHelper.resolveCoverModel(rawSvg)
        val second = CoverImageHelper.resolveCoverModel(rawSvg)

        assertSame("cached model must be reused", first, second)
    }

    @Test
    fun `base64 data uri model is cached and reused`() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>Hi</text></svg>"
        val dataUri = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))

        val first = CoverImageHelper.resolveCoverModel(dataUri)
        val second = CoverImageHelper.resolveCoverModel(dataUri)

        assertSame("cached model must be reused", first, second)
        assertEquals(svg, String(first as ByteArray, Charsets.UTF_8))
    }

    @Test
    fun `remote url model is cached and reused`() {
        val url = "https://ik.imagekit.io/esper/covers/sample.webp"
        val first = CoverImageHelper.resolveCoverModel(url)
        val second = CoverImageHelper.resolveCoverModel(url)

        assertSame("cached model must be reused", first, second)
        assertEquals(url, first)
    }

    @Test
    fun `clearing the cache forces a fresh decode`() {
        val rawSvg = "<svg viewBox=\"0 0 1000 1440\"></svg>"
        val first = CoverImageHelper.resolveCoverModel(rawSvg)
        CoverImageHelper.clearCache()
        val second = CoverImageHelper.resolveCoverModel(rawSvg)

        assertNotSame("cache must be cleared", first, second)
        assertEquals(rawSvg, String(second as ByteArray, Charsets.UTF_8))
    }

    @Test
    fun `base64 whitespace is stripped in a single pass`() {
        // A payload wrapped across lines (as some exports produce) must still
        // decode to the original SVG.
        val svg = "<svg viewBox=\"0 0 1000 1440\"><text>Hi</text></svg>"
        val encoded = Base64.getEncoder().encodeToString(svg.toByteArray(Charsets.UTF_8))
        val wrapped = encoded.chunked(16).joinToString("\n")

        val model = CoverImageHelper.resolveCoverModel("data:image/svg+xml;base64,$wrapped")
        assertEquals(svg, String(model as ByteArray, Charsets.UTF_8))
    }
}
