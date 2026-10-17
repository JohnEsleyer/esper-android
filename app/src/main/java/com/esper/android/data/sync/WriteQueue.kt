package com.esper.android.data.sync

import android.content.SharedPreferences

/**
 * Offline write queue. Every mutating network call registers its op here
 * BEFORE firing; successful calls remove it. Anything left queued (offline
 * failure, process death) is replayed from local Room content on the next
 * successful sync or reconnect — so offline edits are never silently lost.
 *
 * Full-notebook PUTs replace children wholesale server-side, therefore a
 * queued [FullBook] supersedes any queued page ops for the same notebook.
 */
sealed class SyncOp {
    data class PatchPage(val docId: String, val pageId: String) : SyncOp()
    data class CreatePage(val docId: String, val pageId: String) : SyncOp()
    data class FullBook(val docId: String) : SyncOp()
    data class DeletePage(val docId: String, val pageId: String) : SyncOp()
    data class DeleteBook(val docId: String) : SyncOp()

    fun encode(): String = when (this) {
        is PatchPage -> "patch|$docId|$pageId"
        is CreatePage -> "create|$docId|$pageId"
        is FullBook -> "full|$docId"
        is DeletePage -> "delpage|$docId|$pageId"
        is DeleteBook -> "delbook|$docId"
    }

    companion object {
        fun decode(raw: String): SyncOp? {
            val parts = raw.split("|")
            if (parts.size < 2) return null
            return when (parts[0]) {
                "patch" -> if (parts.size == 3) PatchPage(parts[1], parts[2]) else null
                "create" -> if (parts.size == 3) CreatePage(parts[1], parts[2]) else null
                "full" -> FullBook(parts[1])
                "delpage" -> if (parts.size == 3) DeletePage(parts[1], parts[2]) else null
                "delbook" -> DeleteBook(parts[1])
                else -> null
            }
        }
    }
}

/** Minimal persistence port so the queue is unit-testable without Android. */
interface OpStore {
    fun load(): LinkedHashSet<String>
    fun save(ops: Set<String>)
}

class PrefsOpStore(
    private val prefs: SharedPreferences,
    private val key: String = "pending_sync_ops_v1",
) : OpStore {
    override fun load(): LinkedHashSet<String> =
        LinkedHashSet(prefs.getStringSet(key, emptySet()).orEmpty())

    override fun save(ops: Set<String>) {
        prefs.edit().putStringSet(key, ops.toSet()).apply()
    }
}

class WriteQueue(private val store: OpStore) {

    /** Snapshot of pending ops in insertion order. */
    fun pending(): List<SyncOp> = synchronized(this) {
        store.load().mapNotNull { SyncOp.decode(it) }
    }

    fun markDirty(op: SyncOp) = synchronized(this) {
        val ops = store.load()
        fun docOf(raw: String): String? = raw.split("|").getOrNull(1)
        if (op is SyncOp.FullBook) {
            // A full PUT supersedes queued page ops for the same book.
            ops.removeAll { raw ->
                val kind = raw.substringBefore("|")
                (kind == "patch" || kind == "create") && docOf(raw) == op.docId
            }
        }
        if (op is SyncOp.DeleteBook) {
            // Deleting supersedes everything queued for the book.
            ops.removeAll { docOf(it) == op.docId }
        }
        if (op is SyncOp.DeletePage) {
            ops.removeAll { it == "patch|${op.docId}|${op.pageId}" || it == "create|${op.docId}|${op.pageId}" }
        }
        ops.remove(op.encode())
        ops.add(op.encode())
        store.save(ops)
    }

    fun markClean(op: SyncOp) = synchronized(this) {
        val ops = store.load()
        if (ops.remove(op.encode())) store.save(ops)
    }

    fun pendingCount(): Int = synchronized(this) { store.load().size }
}
