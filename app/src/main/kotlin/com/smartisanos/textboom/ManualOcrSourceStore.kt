package com.cashewteam.novatext.android

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ManualOcrSourceStore {
    data class Source(
        val token: String,
        val imageUri: Uri? = null,
        val cachedBitmap: Bitmap? = null,
        val touchX: Int,
        val touchY: Int,
        val callerPackage: String?,
        val fullscreen: Boolean,
        val offsetX: Int,
        val offsetY: Int,
        val sourceTag: String,
        val replayMode: String? = null,
        val selectionRect: Rect? = null,
        val ocrMode: String? = null,
    )

    private val sources = LinkedHashMap<String, Source>()
    private val revision = MutableStateFlow(0L)
    private var activeToken: String? = null

    fun newToken(): String = UUID.randomUUID().toString().take(12)

    @Synchronized
    fun put(source: Source) {
        clearOthersLocked(source.token)
        val previous = sources[source.token]
        val previousBitmap = previous?.cachedBitmap
        if (previousBitmap != null && previousBitmap !== source.cachedBitmap && !previousBitmap.isRecycled) {
            previousBitmap.recycle()
        }
        sources[source.token] = source
        activeToken = source.token
        revision.value += 1
    }

    @Synchronized
    fun get(token: String?): Source? {
        if (token.isNullOrBlank()) return null
        return sources[token]
    }

    @Synchronized
    fun remove(token: String?) {
        if (token.isNullOrBlank()) return
        recycleSourceLocked(sources.remove(token))
        if (activeToken == token) {
            activeToken = sources.keys.lastOrNull()
        }
        revision.value += 1
    }

    @Synchronized
    fun clear() {
        if (sources.isEmpty()) return
        sources.values.forEach(::recycleSourceLocked)
        sources.clear()
        activeToken = null
        revision.value += 1
    }

    @Synchronized
    fun activeToken(): String? = activeToken

    fun revisionFlow(): StateFlow<Long> = revision.asStateFlow()

    private fun clearOthersLocked(keepToken: String) {
        if (sources.size <= 1 && (sources.isEmpty() || sources.containsKey(keepToken))) {
            return
        }
        val iterator = sources.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == keepToken) continue
            recycleSourceLocked(entry.value)
            iterator.remove()
        }
    }

    private fun recycleSourceLocked(source: Source?) {
        val bitmap = source?.cachedBitmap ?: return
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    const val REPLAY_MODE_NEAREST_PARAGRAPH = "nearest_paragraph"
    const val REPLAY_MODE_SELECTION_RECT = "selection_rect"
}
