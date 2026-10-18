package com.esper.android.remote

import com.esper.android.data.remote.MeLimits
import com.esper.android.data.remote.MeResponse
import com.esper.android.data.remote.MeUsage
import com.esper.android.deriveChapterTitle
import com.esper.android.journalDocIdFor
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ApiContractTest {

    private val gson = Gson()

    @Test
    fun `admin profile parses with null inactivity days`() {
        val parsed = gson.fromJson(
            """{"sub":"u1","name":"A","avatar":null,"member_type":"admin",
                "usage":{"notebook_count":3,"shelf_count":1},
                "limits":{"max_notebooks":10000,"max_shelves":1000,
                "max_pages_per_book":1000,"max_visual_bytes":10485760,
                "inactivity_days":null}}""".trimIndent(),
            MeResponse::class.java
        )
        assertEquals("admin", parsed.memberType)
        assertEquals(3, parsed.usage?.notebook_count)
        assertEquals(10000, parsed.limits?.max_notebooks)
        assertNull(parsed.limits?.inactivity_days)
    }

    @Test
    fun `profile without usage and limits uses safe defaults`() {
        val parsed = gson.fromJson(
            """{"sub":"u2","name":null,"avatar":null,"member_type":"visitor"}""",
            MeResponse::class.java
        )
        assertEquals("visitor", parsed.memberType)
        // Null nested objects stay null; callers fall back to placeholders.
        assertNull(parsed.usage)
        assertNull(parsed.limits)
        // Standalone defaults (used when constructing locally) are visitor-tier.
        assertEquals(50, MeLimits().max_notebooks)
        assertEquals(30L, MeLimits().inactivity_days)
    }

    @Test
    fun `chapter title mirrors first markdown line`() {
        assertEquals("Hello", deriveChapterTitle("# Hello\n\nbody"))
        assertEquals("Hello", deriveChapterTitle("  Hello  "))
        assertEquals("Untitled Chapter", deriveChapterTitle(""))
        assertEquals("Untitled Chapter", deriveChapterTitle("   \n  "))
        // Strips quote/list markers, caps at 100 chars.
        assertEquals("Item", deriveChapterTitle("> Item"))
        assertEquals("Item", deriveChapterTitle("- Item"))
        assertTrue(deriveChapterTitle("# " + "x".repeat(200)).length <= 100)
    }

    @Test
    fun `journal doc id is scoped per signed-in uid`() {
        assertEquals("esper-journal-uid123", journalDocIdFor("uid123"))
    }

    @Test
    fun `journal doc id falls back to legacy shared id when signed out`() {
        assertEquals("esper-journal", journalDocIdFor(null))
    }
}
