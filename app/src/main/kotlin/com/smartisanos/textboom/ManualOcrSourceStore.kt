package com.cashewteam.novatext.android

import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object ManualOcrSourceStore {
    data class Source(
        val token: String,
        val imageUri: Uri,
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

    fun newToken(): String = UUID.randomUUID().toString().take(12)

    @Synchronized
    fun put(source: Source) {
        sources[source.token] = source
        revision.value += 1
    }

    @Synchronized
    fun get(token: String?): Source? {
        if (token.isNullOrBlank()) return null
        return sources[token]
    }

    fun revisionFlow(): StateFlow<Long> = revision.asStateFlow()

    const val REPLAY_MODE_NEAREST_PARAGRAPH = "nearest_paragraph"
    const val REPLAY_MODE_SELECTION_RECT = "selection_rect"
}
