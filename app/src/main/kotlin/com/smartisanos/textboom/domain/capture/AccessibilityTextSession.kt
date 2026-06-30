package com.cashewteam.novatext.android.domain.capture

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.cashewteam.novatext.android.service.NovaTextAccessibilityService
import com.cashewteam.novatext.android.util.NovaTextLogger
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
    val rawBlocks: List<CaptureTextBlockContract>,
    val paragraphs: List<CaptureTextBlockContract>,
    val nearestParagraphIndex: Int,
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
        val prunedCandidates = pruneChildrenCoveredByRichParents(candidates)
        val sortedCandidates = prunedCandidates
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
        val rawBlocks = sortedCandidates.drop(windowStart).take(MAX_CACHED_BLOCKS).map { it.toContract() }
        val nearest = if (nearestInWindow < 0) -1 else nearestInWindow - windowStart
        val merged = mergeParagraphs(rawBlocks)
        return AccessibilityTextWindow(
            rawBlocks = rawBlocks,
            paragraphs = merged.paragraphs,
            nearestParagraphIndex = if (nearest < 0) -1 else merged.rawToParagraphIndex[nearest],
            durationMs = (SystemClock.elapsedRealtime() - startedAt).toInt(),
        )
    }

    private fun mergeParagraphs(
        rawBlocks: List<CaptureTextBlockContract>,
    ): MergedParagraphs {
        if (rawBlocks.isEmpty()) {
            return MergedParagraphs(emptyList(), IntArray(0))
        }
        val groups = mutableListOf<MutableList<CaptureTextBlockContract>>()
        val rawToParagraphIndex = IntArray(rawBlocks.size)
        rawBlocks.forEachIndexed { index, block ->
            val lastGroup = groups.lastOrNull()
            if (lastGroup != null && shouldMerge(lastGroup, block)) {
                lastGroup += block
            } else {
                groups += mutableListOf(block)
            }
            rawToParagraphIndex[index] = groups.lastIndex
        }
        return MergedParagraphs(
            paragraphs = groups.map { mergeGroup(it) },
            rawToParagraphIndex = rawToParagraphIndex,
        )
    }

    private fun shouldMerge(
        group: List<CaptureTextBlockContract>,
        block: CaptureTextBlockContract,
    ): Boolean {
        val last = group.last()
        val gap = block.top - last.bottom
        val averageHeight = group.map { it.bottom - it.top }.average()
        val mergeGapLimit = max(MIN_PARAGRAPH_GAP_PX, averageHeight * PARAGRAPH_GAP_MULTIPLIER)
        if (gap > mergeGapLimit) {
            return false
        }
        val groupLeft = group.map { it.left }.average()
        val sameColumn = kotlin.math.abs(block.left - groupLeft) <= SAME_COLUMN_TOLERANCE_PX
        val horizontalOverlap = min(last.right, block.right) - max(last.left, block.left)
        return sameColumn || horizontalOverlap >= 0.0
    }

    private fun mergeGroup(
        group: List<CaptureTextBlockContract>,
    ): CaptureTextBlockContract {
        val text = buildString {
            group.forEachIndexed { index, block ->
                if (index > 0) {
                    val previous = group[index - 1]
                    val sameLine = kotlin.math.abs(block.top - previous.top) <= max(
                        SAME_LINE_TOP_DELTA_PX,
                        min(previous.bottom - previous.top, block.bottom - block.top) / 2.0,
                    )
                    append(if (sameLine) "" else "\n")
                }
                append(block.text)
            }
        }
        return CaptureTextBlockContract(
            text = text,
            left = group.minOf { it.left },
            top = group.minOf { it.top },
            right = group.maxOf { it.right },
            bottom = group.maxOf { it.bottom },
            confidence = group.maxOfOrNull { it.confidence } ?: 1.0,
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
            val richDescription = isRichContentDescriptionNode(node, text)
            if (!bounds.isEmpty && shouldIncludeNode(node, text, bounds, richDescription)) {
                candidates += AccessibilityTextCandidate(
                    text = text,
                    bounds = bounds,
                    distanceSquared = adjustedDistanceSquared(bounds, text, touchX, touchY),
                    richDescription = richDescription,
                )
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            traverse(child, touchX, touchY, candidates)
            child.recycle()
        }
    }

    private fun shouldIncludeNode(
        node: AccessibilityNodeInfo,
        text: String,
        bounds: Rect,
        richDescription: Boolean,
    ): Boolean {
        if (richDescription) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (!child.isVisibleToUser) continue
                val childText = extractCleanText(child) ?: continue
                val childBounds = Rect()
                child.getBoundsInScreen(childBounds)
                if (childBounds.isEmpty || childBounds == bounds) continue
                if (!bounds.contains(childBounds)) continue
                if (text.contains(childText)) {
                    return false
                }
            } finally {
                child.recycle()
            }
        }
        return true
    }

    private fun isRichContentDescriptionNode(
        node: AccessibilityNodeInfo,
        text: String,
    ): Boolean {
        val description = node.contentDescription?.toString()?.let(::normalizeLine) ?: return false
        if (description != text) return false
        if (!node.isClickable && !node.isFocusable && !node.isLongClickable) return false
        if (description.length < RICH_CONTENT_DESCRIPTION_MIN_LENGTH) return false
        val separators = description.count { it == ',' || it == '，' }
        return separators >= RICH_CONTENT_DESCRIPTION_MIN_SEPARATORS
    }

    private fun pruneChildrenCoveredByRichParents(
        candidates: List<AccessibilityTextCandidate>,
    ): List<AccessibilityTextCandidate> {
        val richParents = candidates.filter { it.richDescription }
        if (richParents.isEmpty()) return candidates
        return candidates.filter { candidate ->
            candidate.richDescription || richParents.none { parent ->
                parent.bounds != candidate.bounds && parent.bounds.contains(candidate.bounds)
            }
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

    private fun adjustedDistanceSquared(
        bounds: Rect,
        text: String,
        touchX: Double,
        touchY: Double,
    ): Double {
        val distance = distanceSquared(bounds, touchX, touchY)
        return if (isLowValueStatisticText(text)) {
            distance + STATISTIC_TEXT_PENALTY_PX * STATISTIC_TEXT_PENALTY_PX
        } else {
            distance
        }
    }

    private fun isLowValueStatisticText(text: String): Boolean {
        val compact = text.replace(" ", "")
        if (compact.length > MAX_STATISTIC_TEXT_LENGTH) return false
        return compact.all { it.isDigit() || it == '.' || it == '-' || it == '万' || it == '亿' }
    }

    private data class AccessibilityTextCandidate(
        val text: String,
        val bounds: Rect,
        val distanceSquared: Double,
        val richDescription: Boolean,
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

    private data class MergedParagraphs(
        val paragraphs: List<CaptureTextBlockContract>,
        val rawToParagraphIndex: IntArray,
    )

    private companion object {
        const val MAX_CACHED_BLOCKS = 200
        const val MIN_TEXT_LENGTH = 1
        const val MIN_PARAGRAPH_GAP_PX = 24.0
        const val PARAGRAPH_GAP_MULTIPLIER = 1.1
        const val SAME_COLUMN_TOLERANCE_PX = 72.0
        const val SAME_LINE_TOP_DELTA_PX = 12.0
        const val RICH_CONTENT_DESCRIPTION_MIN_LENGTH = 16
        const val RICH_CONTENT_DESCRIPTION_MIN_SEPARATORS = 2
        const val MAX_STATISTIC_TEXT_LENGTH = 8
        const val STATISTIC_TEXT_PENALTY_PX = 240.0
    }
}

private class ParagraphWindow(
    val paragraphs: List<CaptureTextBlockContract>,
    initialIndex: Int,
) {
    private var start = if (paragraphs.isEmpty()) 0 else initialIndex.coerceIn(paragraphs.indices)
    private var end = if (paragraphs.isEmpty()) -1 else start

    var revision: Int = 0
        private set

    val currentBlocks: List<CaptureTextBlockContract>
        get() = if (end >= start && paragraphs.isNotEmpty()) paragraphs.subList(start, end + 1) else emptyList()

    val hasPrevious: Boolean
        get() = start > 0

    val hasNext: Boolean
        get() = end >= 0 && end < paragraphs.lastIndex

    fun peek(direction: String): CaptureTextBlockContract? {
        return mergeAdjacent(collectAdjacent(direction))
    }

    fun load(direction: String): Boolean {
        val adjacent = collectAdjacent(direction)
        if (adjacent.isEmpty()) return false
        when (direction) {
            "before" -> start -= adjacent.size
            "after" -> end += adjacent.size
            else -> return false
        }
        revision += 1
        return true
    }

    private fun collectAdjacent(direction: String): List<CaptureTextBlockContract> {
        val result = mutableListOf<CaptureTextBlockContract>()
        while (result.size < MAX_SHORT_ADJACENT_PARAGRAPHS) {
            val index = when (direction) {
                "before" -> start - result.size - 1
                "after" -> end + result.size + 1
                else -> return emptyList()
            }
            val block = paragraphs.getOrNull(index) ?: break
            result += block
            if (readableLength(block.text) >= SHORT_PARAGRAPH_CHAR_THRESHOLD) break
        }
        return result
    }

    private fun mergeAdjacent(blocks: List<CaptureTextBlockContract>): CaptureTextBlockContract? {
        if (blocks.isEmpty()) return null
        val ordered = blocks.sortedWith(compareBy<CaptureTextBlockContract> { it.top }.thenBy { it.left })
        return CaptureTextBlockContract(
            text = ordered.joinToString("\n\n") { it.text },
            left = ordered.minOf { it.left },
            top = ordered.minOf { it.top },
            right = ordered.maxOf { it.right },
            bottom = ordered.maxOf { it.bottom },
            confidence = ordered.maxOfOrNull { it.confidence } ?: 1.0,
        )
    }

    private fun readableLength(text: String): Int {
        return text.count { !it.isWhitespace() }
    }

    private companion object {
        const val SHORT_PARAGRAPH_CHAR_THRESHOLD = 25
        const val MAX_SHORT_ADJACENT_PARAGRAPHS = 3
    }
}

object TextSessionCoordinator {
    private var paragraphWindow = ParagraphWindow(emptyList(), -1)
    private var rawBlocks: List<CaptureTextBlockContract> = emptyList()
    private var durationMs = 0
    private var source = "none"
    private var sessionId = UUID.randomUUID().toString()
    private var debugMessage = "channel=none; matched=0"

    @Volatile
    private var lastSnapshot: TextSessionSnapshot = emptySnapshot()

    @Synchronized
    fun runAccessibilityFirst(
        request: CaptureRequestContract,
        traceEnabled: Boolean = false,
        traceId: String = UUID.randomUUID().toString().take(8),
    ): TextSessionSnapshot {
        val service = NovaTextAccessibilityService.activeInstance
        if (service == null) {
            clearSession("channel=accessibility; matched=0; reason=service_unavailable")
        } else {
            val window = AccessibilityTextSessionRunner(service).captureWindow(request)
            rawBlocks = window.rawBlocks
            paragraphWindow = ParagraphWindow(window.paragraphs, window.nearestParagraphIndex)
            durationMs = window.durationMs
            source = if (window.paragraphs.isEmpty()) "none" else "accessibility"
            sessionId = UUID.randomUUID().toString()
            debugMessage = if (window.paragraphs.isEmpty()) {
                "channel=accessibility; matched=0; reason=no_accessible_text"
            } else {
                "channel=accessibility; cached=${window.rawBlocks.size}; paragraphs=${window.paragraphs.size}; initial=1"
            }
        }
        lastSnapshot = buildSnapshot()
        if (traceEnabled) {
            logTrace(traceId, request, lastSnapshot)
        } else {
            NovaTextLogger.d(
                "capture source=${lastSnapshot.captureResult.source} blocks=${paragraphWindow.paragraphs.size}"
            )
        }
        return lastSnapshot
    }

    @Synchronized
    fun replaceSession(
        paragraphs: List<CaptureTextBlockContract>,
        initialIndex: Int,
        source: String,
        durationMs: Int = 0,
        debugMessage: String = "channel=$source; matched=${paragraphs.size}",
    ): TextSessionSnapshot {
        rawBlocks = paragraphs
        paragraphWindow = ParagraphWindow(paragraphs, initialIndex)
        this.durationMs = durationMs
        this.source = if (paragraphs.isEmpty()) "none" else source
        sessionId = UUID.randomUUID().toString()
        this.debugMessage = debugMessage
        lastSnapshot = buildSnapshot()
        return lastSnapshot
    }

    @Synchronized
    fun loadAdjacent(direction: String): TextSessionSnapshot {
        paragraphWindow.load(direction)
        lastSnapshot = buildSnapshot()
        return lastSnapshot
    }

    @Synchronized
    fun peekAdjacentText(direction: String): String? {
        return paragraphWindow.peek(direction)?.text
    }

    @Synchronized
    fun clearSession(debugMessage: String = "channel=none; matched=0") {
        rawBlocks = emptyList()
        paragraphWindow = ParagraphWindow(emptyList(), -1)
        durationMs = 0
        source = "none"
        sessionId = UUID.randomUUID().toString()
        this.debugMessage = debugMessage
        lastSnapshot = buildSnapshot()
    }

    fun latestSnapshot(): TextSessionSnapshot = lastSnapshot

    private fun logTrace(
        traceId: String,
        request: CaptureRequestContract,
        snapshot: TextSessionSnapshot,
    ) {
        val allBounds = boundsOf(snapshot.captureResult.blocks)
        NovaTextLogger.d("trace[$traceId] phase=accessibility")
        NovaTextLogger.d("trace[$traceId] package=${request.packageName}")
        NovaTextLogger.d("trace[$traceId] touch=(${request.touchX.toInt()},${request.touchY.toInt()})")
        NovaTextLogger.d("trace[$traceId] durationMs=${snapshot.captureResult.durationMs}")
        NovaTextLogger.d("trace[$traceId] source=${snapshot.captureResult.source}")
        NovaTextLogger.d("trace[$traceId] rawBlockCount=${rawBlocks.size}")
        NovaTextLogger.d("trace[$traceId] paragraphCount=${paragraphWindow.paragraphs.size}")
        NovaTextLogger.d("trace[$traceId] selectedWindowCount=${snapshot.captureResult.blocks.size}")
        NovaTextLogger.d("trace[$traceId] selectedRevision=${snapshot.revision}")
        NovaTextLogger.d("trace[$traceId] debugMessage=${snapshot.captureResult.debugMessage}")
        val nearestIndex = paragraphWindow.paragraphs.indexOfFirst { current ->
            snapshot.captureResult.blocks.any { it === current }
        }
        NovaTextLogger.d("trace[$traceId] nearestIndex=$nearestIndex")
        rawBlocks.forEachIndexed { index, block ->
            NovaTextLogger.d(
                "trace[$traceId] raw[$index] text=${sanitizeForLog(block.text)} bounds=${formatBounds(block)}"
            )
        }
        paragraphWindow.paragraphs.forEachIndexed { index, block ->
            NovaTextLogger.d(
                "trace[$traceId] paragraph[$index] text=${sanitizeForLog(block.text)} bounds=${formatBounds(block)}"
            )
        }
        snapshot.captureResult.blocks.forEachIndexed { index, block ->
            NovaTextLogger.d(
                "trace[$traceId] selected[$index] text=${sanitizeForLog(block.text)} bounds=${formatBounds(block)}"
            )
        }
        NovaTextLogger.d("trace[$traceId] selectedText=${sanitizeForLog(snapshot.originalText)}")
        NovaTextLogger.d("trace[$traceId] selectedBounds=$allBounds")
    }

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

    private fun boundsOf(blocks: List<CaptureTextBlockContract>): String {
        if (blocks.isEmpty()) return "none"
        val left = blocks.minOf { it.left }
        val top = blocks.minOf { it.top }
        val right = blocks.maxOf { it.right }
        val bottom = blocks.maxOf { it.bottom }
        return "[$left,$top,$right,$bottom]"
    }

    private fun formatBounds(block: CaptureTextBlockContract): String {
        return "[${block.left},${block.top},${block.right},${block.bottom}]"
    }

    private fun sanitizeForLog(text: String): String {
        return text.replace("\n", "\\n")
    }
}
