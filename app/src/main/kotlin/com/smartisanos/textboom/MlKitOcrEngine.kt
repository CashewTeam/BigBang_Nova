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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MlKitOcrEngine {
    private const val MAX_BITMAP_EDGE = 2048
    private const val PKG_GALLERY = "com.android.gallery3d"
    private const val SCALE_SCREENSHOT = 2
    private const val MIN_GROUP_GAP_PX = 24
    private const val SAME_COLUMN_TOLERANCE_PX = 72
    private const val BELOW_TOUCH_PENALTY_MULTIPLIER = 4.0
    private const val PARAGRAPH_MERGE_LINE_GAP_MULTIPLIER = 1.15

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
        val rawBlocks = result.textBlocks
            .mapNotNull { block ->
                val text = block.text.trim()
                val bounds = block.boundingBox ?: return@mapNotNull null
                if (text.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
                    return@mapNotNull null
                }
                OcrRawBlock(text = text, bounds = Rect(bounds))
            }
            .sortedWith(compareBy<OcrRawBlock> { it.bounds.top }.thenBy { it.bounds.left })
        if (rawBlocks.isEmpty()) {
            return emptyList()
        }
        val groups = mutableListOf<OcrGroup>()
        rawBlocks.forEach { block ->
            val lastGroup = groups.lastOrNull()
            if (lastGroup != null && shouldMerge(lastGroup, block)) {
                lastGroup.add(block)
            } else {
                groups += OcrGroup(block)
            }
        }
        return groups.map { it.toMatch() }
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

    private fun shouldMerge(
        group: OcrGroup,
        block: OcrRawBlock,
    ): Boolean {
        val gap = block.bounds.top - group.bounds.bottom
        val mergeGapLimit = max(
            MIN_GROUP_GAP_PX.toDouble(),
            group.averageLineHeight * PARAGRAPH_MERGE_LINE_GAP_MULTIPLIER,
        )
        if (gap > mergeGapLimit) {
            return false
        }
        val sameColumn = kotlin.math.abs(block.bounds.left - group.anchorLeft) <= SAME_COLUMN_TOLERANCE_PX
        val horizontalOverlap = min(group.bounds.right, block.bounds.right) - max(group.bounds.left, block.bounds.left)
        return sameColumn || horizontalOverlap >= 0
    }

    private data class OcrRawBlock(
        val text: String,
        val bounds: Rect,
    ) {
        val lineCount: Int = text.count { it == '\n' } + 1
        val averageLineHeight: Double =
            bounds.height().toDouble() / lineCount.coerceAtLeast(1).toDouble()
    }

    private class OcrGroup(first: OcrRawBlock) {
        private val parts = mutableListOf(first)
        val bounds = Rect(first.bounds)
        var anchorLeft = first.bounds.left
            private set
        var averageLineHeight = first.averageLineHeight
            private set
        val blockCount: Int
            get() = parts.size

        fun add(block: OcrRawBlock) {
            parts += block
            bounds.union(block.bounds)
            anchorLeft = ((anchorLeft * (parts.size - 1)) + block.bounds.left) / parts.size
            averageLineHeight = parts.map { it.averageLineHeight }.average()
        }

        fun toMatch(): NearestTextBlockMatch {
            val text = buildString {
                parts.forEachIndexed { index, part ->
                    if (index > 0) {
                        val previous = parts[index - 1]
                        val sameLine = kotlin.math.abs(part.bounds.top - previous.bounds.top) <= max(
                            12,
                            min(previous.bounds.height(), part.bounds.height()) / 2,
                        )
                        append(if (sameLine) "" else "\n")
                    }
                    append(part.text)
                }
            }
            return NearestTextBlockMatch(
                text = text,
                bounds = Rect(bounds),
                distanceSquared = 0.0,
                blockCount = parts.size,
                score = 0.0,
            )
        }
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
