package com.smartisanos.textboom.domain.capture

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.smartisanos.textboom.service.NovaTextAccessibilityService
import com.smartisanos.textboom.util.NovaTextLogger
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class TextSessionSnapshot(
    val captureResult: CaptureResultContract,
    val rawLines: List<String>,
    val originalText: String,
    val source: String,
    val sessionId: String,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val revision: Int,
)

data class CaptureRequestContract(
    val touchX: Double,
    val touchY: Double,
    val packageName: String,
    val allowOcrFallback: Boolean,
)

private data class AccessibilityTextWindow(
    val blocks: List<CaptureTextBlockContract>,
    val nearestIndex: Int,
    val durationMs: Int,
)

private class AccessibilityTextSessionRunner(
    private val service: NovaTextAccessibilityService,
) {
    fun captureWindow(request: CaptureRequestContract): AccessibilityTextWindow {
        val startedAt = SystemClock.elapsedRealtime()
        val root = service.rootInActiveWindow
        val candidates = mutableListOf<AccessibilityTextCandidate>()
        if (root != null) {
            traverse(root, request.touchX, request.touchY, candidates)
            root.recycle()
        }
        val sortedCandidates = candidates
            .distinctBy { "${it.text}\u0000${it.bounds.flattenToString()}" }
            .sortedWith(
                compareBy<AccessibilityTextCandidate> { it.bounds.top }
                    .thenBy { it.bounds.left }
                    .thenBy { it.bounds.bottom }
            )
        val nearestInWindow =
            sortedCandidates.indices.minByOrNull { sortedCandidates[it].distanceSquared } ?: -1
        val windowStart = if (sortedCandidates.size <= MAX_CACHED_BLOCKS) {
            0
        } else {
            (nearestInWindow - MAX_CACHED_BLOCKS / 2)
                .coerceIn(0, sortedCandidates.size - MAX_CACHED_BLOCKS)
        }
        val blocks = sortedCandidates.drop(windowStart).take(MAX_CACHED_BLOCKS)
        val nearest = if (nearestInWindow < 0) -1 else nearestInWindow - windowStart
        return AccessibilityTextWindow(
            blocks = blocks.map { it.toContract() },
            nearestIndex = nearest,
            durationMs = (SystemClock.elapsedRealtime() - startedAt).toInt(),
        )
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        touchX: Double,
        touchY: Double,
        candidates: MutableList<AccessibilityTextCandidate>,
    ) {
        val text = extractCleanText(node)
        if (text != null && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                candidates += AccessibilityTextCandidate(
                    text = text,
                    bounds = bounds,
                    distanceSquared = distanceSquared(bounds, touchX, touchY),
                )
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            traverse(child, touchX, touchY, candidates)
            child.recycle()
        }
    }

    private fun extractCleanText(node: AccessibilityNodeInfo): String? {
        val rawText = node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: return null
        val normalized = normalizeLine(rawText)
        if (normalized.isBlank()) return null
        if (normalized.length < MIN_TEXT_LENGTH && normalized.all { !it.isLetterOrDigit() }) {
            return null
        }
        return normalized
    }

    private fun normalizeLine(value: String): String {
        val normalized = buildString(value.length) {
            value.forEach { char ->
                append(
                    when {
                        char == '\u3000' -> ' '
                        char.code in 0xFF01..0xFF5E -> (char.code - 0xFEE0).toChar()
                        char == '\t' || char == '\r' -> ' '
                        else -> char
                    }
                )
            }
        }
        return normalized.replace(Regex("[ ]+"), " ").trim()
    }

    private fun distanceSquared(
        bounds: Rect,
        touchX: Double,
        touchY: Double,
    ): Double {
        val nearestX = max(bounds.left.toDouble(), min(touchX, bounds.right.toDouble()))
        val nearestY = max(bounds.top.toDouble(), min(touchY, bounds.bottom.toDouble()))
        val dx = touchX - nearestX
        val dy = touchY - nearestY
        return dx * dx + dy * dy
    }

    private data class AccessibilityTextCandidate(
        val text: String,
        val bounds: Rect,
        val distanceSquared: Double,
    ) {
        fun toContract(): CaptureTextBlockContract {
            return CaptureTextBlockContract(
                text = text,
                left = bounds.left.toDouble(),
                top = bounds.top.toDouble(),
                right = bounds.right.toDouble(),
                bottom = bounds.bottom.toDouble(),
                confidence = 1.0,
            )
        }
    }

    private companion object {
        const val MAX_CACHED_BLOCKS = 200
        const val MIN_TEXT_LENGTH = 1
    }
}

private class ParagraphWindow(
    val blocks: List<CaptureTextBlockContract>,
    initialIndex: Int,
) {
    private var start = if (blocks.isEmpty()) 0 else initialIndex.coerceIn(blocks.indices)
    private var end = if (blocks.isEmpty()) -1 else start

    var revision: Int = 0
        private set

    val currentBlocks: List<CaptureTextBlockContract>
        get() = if (end >= start && blocks.isNotEmpty()) blocks.subList(start, end + 1) else emptyList()

    val hasPrevious: Boolean
        get() = start > 0

    val hasNext: Boolean
        get() = end >= 0 && end < blocks.lastIndex

    fun load(direction: String): Boolean {
        val changed = when (direction) {
            "before" -> if (hasPrevious) {
                start -= 1
                true
            } else {
                false
            }
            "after" -> if (hasNext) {
                end += 1
                true
            } else {
                false
            }
            else -> false
        }
        if (changed) revision += 1
        return changed
    }
}

object TextSessionCoordinator {
    private var paragraphWindow = ParagraphWindow(emptyList(), -1)
    private var durationMs = 0
    private var source = "none"
    private var sessionId = UUID.randomUUID().toString()
    private var debugMessage = "channel=none; matched=0"

    @Volatile
    private var lastSnapshot: TextSessionSnapshot = emptySnapshot()

    @Synchronized
    fun runAccessibilityFirst(request: CaptureRequestContract): TextSessionSnapshot {
        val service = NovaTextAccessibilityService.activeInstance
        if (service == null) {
            paragraphWindow = ParagraphWindow(emptyList(), -1)
            durationMs = 0
            source = "none"
            sessionId = UUID.randomUUID().toString()
            debugMessage = "channel=accessibility; matched=0; reason=service_unavailable"
        } else {
            val window = AccessibilityTextSessionRunner(service).captureWindow(request)
            paragraphWindow = ParagraphWindow(window.blocks, window.nearestIndex)
            durationMs = window.durationMs
            source = if (window.blocks.isEmpty()) "none" else "accessibility"
            sessionId = UUID.randomUUID().toString()
            debugMessage = if (window.blocks.isEmpty()) {
                "channel=accessibility; matched=0; reason=no_accessible_text"
            } else {
                "channel=accessibility; cached=${window.blocks.size}; initial=1"
            }
        }
        lastSnapshot = buildSnapshot()
        NovaTextLogger.d(
            "capture source=${lastSnapshot.captureResult.source} blocks=${paragraphWindow.blocks.size}"
        )
        return lastSnapshot
    }

    @Synchronized
    fun loadAdjacent(direction: String): TextSessionSnapshot {
        paragraphWindow.load(direction)
        lastSnapshot = buildSnapshot()
        return lastSnapshot
    }

    fun latestSnapshot(): TextSessionSnapshot = lastSnapshot

    private fun buildSnapshot(): TextSessionSnapshot {
        val blocks = paragraphWindow.currentBlocks
        val rawLines = blocks.map { it.text }
        return TextSessionSnapshot(
            captureResult = CaptureResultContract(
                source = source,
                blocks = blocks,
                durationMs = durationMs,
                debugMessage = debugMessage,
            ),
            rawLines = rawLines,
            originalText = rawLines.joinToString("\n"),
            source = source,
            sessionId = sessionId,
            hasPrevious = paragraphWindow.hasPrevious,
            hasNext = paragraphWindow.hasNext,
            revision = paragraphWindow.revision,
        )
    }

    private fun emptySnapshot(): TextSessionSnapshot {
        return TextSessionSnapshot(
            captureResult = CaptureResultContract(
                source = "none",
                blocks = emptyList(),
                durationMs = 0,
                debugMessage = "channel=none; matched=0",
            ),
            rawLines = emptyList(),
            originalText = "",
            source = "none",
            sessionId = sessionId,
            hasPrevious = false,
            hasNext = false,
            revision = 0,
        )
    }
}
