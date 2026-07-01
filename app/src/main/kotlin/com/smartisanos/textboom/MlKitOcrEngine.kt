package com.cashewteam.novatext.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.DisplayMetrics
import com.cashewteam.novatext.android.data.BigBangSettings
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MlKitOcrEngine {
    private const val MAX_BITMAP_EDGE = 2048
    private const val PKG_GALLERY = "com.android.gallery3d"
    private const val SCALE_SCREENSHOT = 2
    private const val BELOW_TOUCH_PENALTY_MULTIPLIER = 4.0
    private const val SAME_VISUAL_LINE_TOLERANCE_RATIO = 0.45

    data class PreparedBitmap(
        val bitmap: Bitmap,
        val touchX: Int,
        val touchY: Int,
    )

    data class NearestTextBlockMatch(
        val text: String,
        val bounds: Rect,
        val distanceSquared: Double,
        val blockCount: Int,
        val score: Double,
    )

    data class RawTextBlock(
        val text: String,
        val bounds: Rect,
    )

    @JvmStatic
    fun copyBitmap(bitmap: Bitmap): Bitmap {
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
    }

    @JvmStatic
    fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longestEdge = max(info.size.width, info.size.height)
            if (longestEdge > MAX_BITMAP_EDGE) {
                decoder.setTargetSampleSize(
                    ceil(longestEdge / MAX_BITMAP_EDGE.toDouble()).toInt().coerceAtLeast(1)
                )
            }
        }
    }

    @JvmStatic
    fun loadBitmap(
        context: Context,
        sourceToken: String?,
        fallbackUri: Uri? = null,
    ): Bitmap? {
        ManualOcrSourceStore.get(sourceToken)?.cachedBitmap
            ?.takeUnless { it.isRecycled }
            ?.let { return copyBitmap(it) }
        val sourceUri = fallbackUri ?: ManualOcrSourceStore.get(sourceToken)?.imageUri
        return sourceUri?.let { decodeBitmap(context, it) }
    }

    @JvmStatic
    fun recognize(bitmap: Bitmap, mode: String): Task<Text> {
        val recognizer = createRecognizer(mode)
        val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
        task.addOnCompleteListener {
            recognizer.close()
        }
        return task
    }

    @JvmStatic
    fun prepareBitmap(
        context: Context,
        screenshot: Bitmap,
        callerPackage: String?,
        fullscreen: Boolean,
        offsetX: Int,
        offsetY: Int,
        touchX: Int,
        touchY: Int,
    ): PreparedBitmap {
        val sourceWidth = screenshot.width
        val sourceHeight = screenshot.height
        if (callerPackage.isNullOrEmpty() && offsetX == 0 && offsetY == 0) {
            return PreparedBitmap(
                bitmap = screenshot,
                touchX = touchX.coerceIn(0, (sourceWidth - 1).coerceAtLeast(0)),
                touchY = touchY.coerceIn(0, (sourceHeight - 1).coerceAtLeast(0)),
            )
        }
        val displayMetrics = context.resources.displayMetrics
        val w = displayMetrics.widthPixels.coerceAtLeast(1)
        val h = displayMetrics.heightPixels.coerceAtLeast(1)
        val scaleX = sourceWidth / w.toFloat()
        val scaleY = sourceHeight / h.toFloat()
        fun sourceX(value: Int): Int = (value * scaleX).roundToInt()
        fun sourceY(value: Int): Int = (value * scaleY).roundToInt()
        val statusBarHeight = systemStatusBarHeight(context, displayMetrics)
        var top = sourceY(statusBarHeight)
        var bottom = 0
        var left = 0
        var right = 0
        if (offsetX == 0 && offsetY == 0) {
            if (callerPackage == PKG_GALLERY && !fullscreen) {
                top = sourceY(context.resources.getInteger(R.integer.gallery_top))
                bottom = sourceY(context.resources.getInteger(R.integer.gallery_bottom))
            }
        } else {
            val scaleFactor = offsetY / h.toFloat()
            val sideH = offsetY
            val sideW = (scaleFactor * w).toInt()
            if (callerPackage == PKG_GALLERY && !fullscreen) {
                val galleryTop = (context.resources.getInteger(R.integer.gallery_top) * (1 - scaleFactor)).toInt()
                val galleryBottom = (context.resources.getInteger(R.integer.gallery_bottom) * (1 - scaleFactor)).toInt()
                top = sourceY(sideH + galleryTop)
                bottom = sourceY(galleryBottom)
            } else {
                top = sourceY(sideH + ((1 - scaleFactor) * statusBarHeight).toInt())
            }
            if (offsetX == 0) {
                right = sourceX(sideW)
            } else {
                left = sourceX(sideW)
            }
        }
        if (sourceWidth <= left + right || sourceHeight <= top + bottom) {
            return PreparedBitmap(
                bitmap = screenshot,
                touchX = sourceX(touchX).coerceIn(0, (sourceWidth - 1).coerceAtLeast(0)),
                touchY = sourceY(touchY).coerceIn(0, (sourceHeight - 1).coerceAtLeast(0)),
            )
        }
        val scale = if (offsetX != 0 || offsetY != 0 || (callerPackage == PKG_GALLERY && !fullscreen)) {
            SCALE_SCREENSHOT
        } else {
            1
        }
        val aw = (sourceWidth - left - right) / scale
        val ah = (sourceHeight - top - bottom) / scale
        if (aw <= 0 || ah <= 0) {
            return PreparedBitmap(
                bitmap = screenshot,
                touchX = sourceX(touchX).coerceIn(0, (sourceWidth - 1).coerceAtLeast(0)),
                touchY = sourceY(touchY).coerceIn(0, (sourceHeight - 1).coerceAtLeast(0)),
            )
        }
        val bitmap = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(
            screenshot,
            Rect(left, top, sourceWidth - right, sourceHeight - bottom),
            Rect(0, 0, aw, ah),
            paint,
        )
        screenshot.recycle()
        val mappedTouchX = ((sourceX(touchX) - left).toFloat() / scale).toInt()
            .coerceIn(0, (aw - 1).coerceAtLeast(0))
        val mappedTouchY = ((sourceY(touchY) - top).toFloat() / scale).toInt()
            .coerceIn(0, (ah - 1).coerceAtLeast(0))
        return PreparedBitmap(bitmap = bitmap, touchX = mappedTouchX, touchY = mappedTouchY)
    }

    @JvmStatic
    fun findNearestTextBlock(
        result: Text,
        touchX: Int,
        touchY: Int,
    ): NearestTextBlockMatch? {
        return findNearestTextBlock(findParagraphs(result), touchX, touchY)
    }

    @JvmStatic
    fun findParagraphs(result: Text): List<NearestTextBlockMatch> {
        val finalParagraphs = mutableListOf<NearestTextBlockMatch>()
        val singleLineBlocks = mutableListOf<OcrRawBlock>()
        collectRawTextBlocks(result).forEach { block ->
            if (block.wasMultiline) {
                finalParagraphs += block.toMatch()
            } else {
                singleLineBlocks += block
            }
        }
        if (singleLineBlocks.isNotEmpty()) {
            finalParagraphs += buildVisualLines(singleLineBlocks).map { line ->
                NearestTextBlockMatch(
                    text = line.text,
                    bounds = Rect(line.bounds),
                    distanceSquared = 0.0,
                    blockCount = line.fragments.size,
                    score = 0.0,
                )
            }
        }
        if (finalParagraphs.isEmpty()) {
            return emptyList()
        }
        return finalParagraphs.sortedWith(compareBy<NearestTextBlockMatch> { it.bounds.top }.thenBy { it.bounds.left })
    }

    @JvmStatic
    fun collectRawBlocks(result: Text): List<RawTextBlock> {
        return collectRawTextBlocks(result)
            .map { RawTextBlock(it.text, Rect(it.bounds)) }
    }

    private fun collectRawTextBlocks(result: Text): List<OcrRawBlock> {
        return result.textBlocks
            .mapNotNull { block ->
                toRawBlock(
                    text = block.text,
                    bounds = block.boundingBox,
                    wasMultiline = containsLineBreak(block.text),
                )
            }
            .sortedWith(compareBy<OcrRawBlock> { it.bounds.top }.thenBy { it.bounds.left })
    }

    private fun buildVisualLines(fragments: List<OcrRawBlock>): List<OcrRawLine> {
        val lines = mutableListOf<OcrLineGroup>()
        fragments.forEach { fragment ->
            val target = lines
                .mapNotNull { line -> line.lineScore(fragment)?.let { score -> line to score } }
                .minByOrNull { it.second }
                ?.first
            if (target != null) {
                target.add(fragment)
            } else {
                lines += OcrLineGroup(fragment)
            }
        }
        return lines.mapNotNull { it.toRawLine() }
    }

    private fun toRawBlock(
        text: String,
        bounds: Rect?,
        wasMultiline: Boolean,
    ): OcrRawBlock? {
        val normalized = normalizeBlockText(text)
        val safeBounds = bounds ?: return null
        if (normalized.isEmpty() || safeBounds.width() <= 0 || safeBounds.height() <= 0) {
            return null
        }
        return OcrRawBlock(
            text = normalized,
            bounds = Rect(safeBounds),
            wasMultiline = wasMultiline,
        )
    }

    private fun buildLineText(
        fragments: List<OcrRawBlock>,
        fallbackText: String,
    ): String {
        if (fragments.isEmpty()) {
            return fallbackText.trim()
        }
        return buildString {
            fragments.forEachIndexed { index, fragment ->
                if (index > 0) {
                    append(inlineSeparator(fragments[index - 1].text, fragment.text))
                }
                append(fragment.text)
            }
        }.trim()
    }

    @JvmStatic
    fun buildParagraphText(result: Text): String {
        return findParagraphs(result)
            .joinToString(separator = "\n\n") { it.text.trim() }
            .trim()
    }

    @JvmStatic
    fun findNearestTextBlock(
        paragraphs: List<NearestTextBlockMatch>,
        touchX: Int,
        touchY: Int,
    ): NearestTextBlockMatch? {
        return paragraphs
            .map { paragraph ->
                val distanceSquared = distanceSquared(paragraph.bounds, touchX.toDouble(), touchY.toDouble())
                paragraph.copy(
                    distanceSquared = distanceSquared,
                    score = adjustedScore(paragraph.bounds, touchX, touchY),
                )
            }
            .minByOrNull { it.score }
    }

    private fun adjustedScore(
        bounds: Rect,
        touchX: Int,
        touchY: Int,
    ): Double {
        var score = distanceSquared(bounds, touchX.toDouble(), touchY.toDouble())
        if (bounds.top > touchY) {
            val belowDelta = (bounds.top - touchY).toDouble()
            score += belowDelta * belowDelta * BELOW_TOUCH_PENALTY_MULTIPLIER
        }
        return score
    }

    private data class OcrRawBlock(
        val text: String,
        val bounds: Rect,
        val wasMultiline: Boolean,
    ) {
        fun toMatch(): NearestTextBlockMatch {
            return NearestTextBlockMatch(
                text = text,
                bounds = Rect(bounds),
                distanceSquared = 0.0,
                blockCount = 1,
                score = 0.0,
            )
        }
    }

    private data class OcrRawLine(
        val text: String,
        val bounds: Rect,
        val fragments: List<OcrRawBlock>,
    ) {
        val averageLineHeight: Double = bounds.height().toDouble()
    }

    private fun isSameVisualLine(
        previous: Rect,
        current: Rect,
    ): Boolean {
        val previousCenterY = (previous.top + previous.bottom) / 2.0
        val currentCenterY = (current.top + current.bottom) / 2.0
        val tolerance = max(
            10.0,
            min(previous.height(), current.height()) * SAME_VISUAL_LINE_TOLERANCE_RATIO,
        )
        return kotlin.math.abs(previousCenterY - currentCenterY) <= tolerance
    }

    private class OcrLineGroup(first: OcrRawBlock) {
        private val fragments = mutableListOf(first)
        private val bounds = Rect(first.bounds)

        fun lineScore(block: OcrRawBlock): Double? {
            if (!isSameVisualLine(bounds, block.bounds)) {
                return null
            }
            val groupCenterY = (bounds.top + bounds.bottom) / 2.0
            val blockCenterY = (block.bounds.top + block.bounds.bottom) / 2.0
            return abs(groupCenterY - blockCenterY)
        }

        fun add(block: OcrRawBlock) {
            fragments += block
            bounds.union(block.bounds)
        }

        fun toRawLine(): OcrRawLine? {
            val sorted = fragments.sortedBy { it.bounds.left }
            val text = buildLineText(sorted, "")
            if (text.isEmpty()) {
                return null
            }
            return OcrRawLine(
                text = text,
                bounds = Rect(bounds),
                fragments = sorted,
            )
        }
    }

    private fun inlineSeparator(previous: String, current: String): String {
        val previousChar = previous.lastOrNull() ?: return ""
        val currentChar = current.firstOrNull() ?: return ""
        if (previousChar.isWhitespace() || currentChar.isWhitespace()) {
            return ""
        }
        return if (previousChar.isLetterOrDigit() && currentChar.isLetterOrDigit()) " " else ""
    }

    private fun containsLineBreak(text: String): Boolean {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
    }

    private fun normalizeBlockText(text: String): String {
        return text
            .replace(Regex("\\s*[\\r\\n]+\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun createRecognizer(mode: String): TextRecognizer {
        return when (mode) {
            BigBangSettings.OCR_MODE_JAPANESE ->
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            BigBangSettings.OCR_MODE_KOREAN ->
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            BigBangSettings.OCR_MODE_LATIN ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            else ->
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
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

    private fun systemStatusBarHeight(
        context: Context,
        displayMetrics: DisplayMetrics,
    ): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId != 0) {
            return context.resources.getDimensionPixelSize(resourceId)
        }
        return (24f * displayMetrics.density).roundToInt()
    }
}
