package com.cashewteam.novatext.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
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

object MlKitOcrEngine {
    private const val MAX_BITMAP_EDGE = 2048
    private const val PKG_GALLERY = "com.android.gallery3d"
    private const val SCALE_SCREENSHOT = 2

    data class PreparedBitmap(
        val bitmap: Bitmap,
        val touchX: Int,
        val touchY: Int,
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
        if (callerPackage.isNullOrEmpty() && offsetX == 0 && offsetY == 0) {
            return PreparedBitmap(
                bitmap = screenshot,
                touchX = touchX.coerceIn(0, screenshot.width),
                touchY = touchY.coerceIn(0, screenshot.height),
            )
        }
        val w = context.resources.getInteger(R.integer.screen_width)
        val h = context.resources.getInteger(R.integer.screen_height)
        val statusBarHeight = context.resources.getInteger(R.integer.status_bar_height)
        var top = statusBarHeight
        var bottom = 0
        var left = 0
        var right = 0
        if (offsetX == 0 && offsetY == 0) {
            if (callerPackage == PKG_GALLERY && !fullscreen) {
                top = context.resources.getInteger(R.integer.gallery_top)
                bottom = context.resources.getInteger(R.integer.gallery_bottom)
            }
        } else {
            val scaleFactor = offsetY / h.toFloat()
            val sideH = offsetY
            val sideW = (scaleFactor * w).toInt()
            if (callerPackage == PKG_GALLERY && !fullscreen) {
                val galleryTop = (context.resources.getInteger(R.integer.gallery_top) * (1 - scaleFactor)).toInt()
                val galleryBottom = (context.resources.getInteger(R.integer.gallery_bottom) * (1 - scaleFactor)).toInt()
                top = sideH + galleryTop
                bottom = galleryBottom
            } else {
                top = sideH + ((1 - scaleFactor) * statusBarHeight).toInt()
            }
            if (offsetX == 0) {
                right = sideW
            } else {
                left = sideW
            }
        }
        val sourceWidth = screenshot.width
        val sourceHeight = screenshot.height
        if (sourceWidth <= left + right || sourceHeight <= top + bottom) {
            return PreparedBitmap(
                bitmap = screenshot,
                touchX = touchX.coerceIn(0, screenshot.width),
                touchY = touchY.coerceIn(0, screenshot.height),
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
                touchX = touchX.coerceIn(0, screenshot.width),
                touchY = touchY.coerceIn(0, screenshot.height),
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
        val mappedTouchX = ((touchX - left).toFloat() / scale).toInt().coerceIn(0, aw)
        val mappedTouchY = ((touchY - top).toFloat() / scale).toInt().coerceIn(0, ah)
        return PreparedBitmap(bitmap = bitmap, touchX = mappedTouchX, touchY = mappedTouchY)
    }

    @JvmStatic
    fun findNearestTextBlock(
        result: Text,
        touchX: Int,
        touchY: Int,
    ): String {
        return result.textBlocks
            .asSequence()
            .mapNotNull { block ->
                val text = block.text.trim()
                val bounds = block.boundingBox ?: return@mapNotNull null
                if (text.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
                    return@mapNotNull null
                }
                Triple(text, bounds, distanceSquared(bounds, touchX.toDouble(), touchY.toDouble()))
            }
            .minByOrNull { it.third }
            ?.first
            .orEmpty()
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
}
