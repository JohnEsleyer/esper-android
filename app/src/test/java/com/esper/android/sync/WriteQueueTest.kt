package com.esper.android.sync

import com.esper.android.data.sync.OpStore
import com.esper.android.data.sync.SyncOp
import com.esper.android.data.sync.WriteQueue
import org.junit.Assert.*
import org.junit.Test

private class FakeStore : OpStore {
    var data: LinkedHashSet<String> = linkedSetOf()
    override fun load(): LinkedHashSet<String> = LinkedHashSet(data)
    override fun save(ops: Set<String>) { data = LinkedHashSet(ops) }
}

class WriteQueueTest {

    @Test
    fun `encode decode round-trips every op type`() {
        val ops = listOf(
            SyncOp.PatchPage("d1", "p1"),
            SyncOp.CreatePage("d1", "p2"),
            SyncOp.FullBook("d1"),
            SyncOp.DeletePage("d1", "p3"),
            SyncOp.DeleteBook("d2")
        )
        ops.forEach { assertEquals(it, SyncOp.decode(it.encode())) }
    }

    @Test
    fun `decode rejects malformed entries`() {
        assertNull(SyncOp.decode(""))
        assertNull(SyncOp.decode("bogus|d1|p1"))
        assertNull(SyncOp.decode("patch|onlydoc"))
    }

    @Test
    fun `full book supersedes queued page ops for the same book`() {
        val q = WriteQueue(FakeStore())
        q.markDirty(SyncOp.PatchPage("d1", "p1"))
        q.markDirty(SyncOp.CreatePage("d1", "p2"))
        q.markDirty(SyncOp.PatchPage("d2", "p9"))
        q.markDirty(SyncOp.FullBook("d1"))
        assertEquals(
            listOf(SyncOp.PatchPage("d2", "p9"), SyncOp.FullBook("d1")),
            q.pending()
        )
    }

    @Test
    fun `delete book clears everything queued for that book`() {
        val q = WriteQueue(FakeStore())
        q.markDirty(SyncOp.PatchPage("d1", "p1"))
        q.markDirty(SyncOp.FullBook("d1"))
        q.markDirty(SyncOp.PatchPage("d2", "p9"))
        q.markDirty(SyncOp.DeleteBook("d1"))
        assertEquals(
            listOf(SyncOp.PatchPage("d2", "p9"), SyncOp.DeleteBook("d1")),
            q.pending()
        )
    }

    @Test
    fun `delete page drops matching patch and create ops`() {
        val q = WriteQueue(FakeStore())
        q.markDirty(SyncOp.CreatePage("d1", "p1"))
        q.markDirty(SyncOp.PatchPage("d1", "p2"))
        q.markDirty(SyncOp.DeletePage("d1", "p1"))
        assertEquals(
            listOf(SyncOp.PatchPage("d1", "p2"), SyncOp.DeletePage("d1", "p1")),
            q.pending()
        )
    }

    @Test
    fun `markClean removes only the given op`() {
        val q = WriteQueue(FakeStore())
        q.markDirty(SyncOp.PatchPage("d1", "p1"))
        q.markDirty(SyncOp.PatchPage("d1", "p2"))
        q.markClean(SyncOp.PatchPage("d1", "p1"))
        assertEquals(listOf(SyncOp.PatchPage("d1", "p2")), q.pending())
        assertEquals(1, q.pendingCount())
    }

    @Test
    fun `re-marking an op moves it to the back`() {
        val q = WriteQueue(FakeStore())
        q.markDirty(SyncOp.PatchPage("d1", "p1"))
        q.markDirty(SyncOp.PatchPage("d1", "p2"))
        q.markDirty(SyncOp.PatchPage("d1", "p1"))
        assertEquals(
            listOf(SyncOp.PatchPage("d1", "p2"), SyncOp.PatchPage("d1", "p1")),
            q.pending()
        )
    }
}
