package com.cashewteam.novatext.android

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.domain.capture.CaptureTextBlockContract
import com.cashewteam.novatext.android.domain.capture.CaptureRequestContract
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator
import com.cashewteam.novatext.android.service.AccessibilityScreenshotCapture
import com.cashewteam.novatext.android.service.BoomActivityLauncher
import com.cashewteam.novatext.android.service.BoomOcrLauncher
import com.cashewteam.novatext.android.util.LogUtils
import com.cashewteam.novatext.android.util.NovaTextLogger
import kotlin.concurrent.thread
import java.util.UUID
import android.view.animation.LinearInterpolator

class OcrLaunchActivity : Activity() {
    private var loopAnimFrame: FrameLayout? = null
    private var loopRotateImage: ImageView? = null
    private var contentFrame: FrameLayout? = null
    private var loadingAnimator: ObjectAnimator? = null
    private var launchGateOpen = false
    private var launched = false
    private var cancelled = false
    private var touchX = 0f
    private var touchY = 0f
    private var pendingText: String? = null
    private var captureRequested = false
    private var captureOcrScreenshotRequested = false
    private var captureOcrScreenshotStarted = false
    private var pendingOcrSelectionLaunch = false
    private var ocrSelectionLaunched = false
    private var callerPackage: String? = null
    private var enableAdjacentSession = false
    private var traceEnabled = false
    private var traceId = UUID.randomUUID().toString().take(8)
    private var manualOcrSourceToken: String? = null
    private var silentManualOcrCaptureStarted = false
    private var replayOcrMode: String? = null
    private var replayMode: String? = null
    private var replayStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        captureOcrScreenshotRequested = intent.getBooleanExtra(BoomOcrLauncher.EXTRA_CAPTURE_OCR_SCREENSHOT, false)
        pendingOcrSelectionLaunch = !intent.getStringExtra(BoomOcrActivity.EXTRA_OCR_IMAGE_URI).isNullOrEmpty()
        callerPackage = intent.getStringExtra("caller_pkg")
        enableAdjacentSession = intent.getBooleanExtra(BoomActivity.EXTRA_ENABLE_ADJACENT_SESSION, false)
        manualOcrSourceToken = intent.getStringExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN)
        replayOcrMode = intent.getStringExtra(EXTRA_REPLAY_OCR_MODE)
        replayMode = intent.getStringExtra(EXTRA_REPLAY_MODE)
        traceEnabled = intent.getBooleanExtra(EXTRA_CAPTURE_TRACE_ENABLED, false)
        traceId = intent.getStringExtra(EXTRA_CAPTURE_TRACE_ID)?.takeIf { it.isNotBlank() }
            ?: traceId
        touchX = readTouchCoordinate("boom_startx", true)
        touchY = readTouchCoordinate("boom_starty", false)
        captureRequested = intent.getBooleanExtra(EXTRA_CAPTURE_ACCESSIBILITY, false)
        pendingText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        ensureLaunchUi()
        if (pendingOcrSelectionLaunch) {
            loopRotateImage?.visibility = View.INVISIBLE
            loopAnimFrame?.visibility = View.INVISIBLE
            return
        }
        if (isReplayRequested()) {
            window.decorView.post {
                val frame = loopAnimFrame ?: return@post
                if (launched || cancelled || pendingOcrSelectionLaunch) {
                    return@post
                }
                frame.translationX = touchX - frame.width / 2f
                frame.translationY = touchY - frame.height / 2f
                startLaunchAnimation()
            }
            return
        }
        if (captureRequested) {
            startSilentManualOcrCapture()
            startAccessibilityCapture()
            window.decorView.post {
                val frame = loopAnimFrame ?: return@post
                if (launched || cancelled || pendingOcrSelectionLaunch) {
                    return@post
                }
                frame.translationX = touchX - frame.width / 2f
                frame.translationY = touchY - frame.height / 2f
                startLaunchAnimation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isReplayRequested() && !replayStarted && !cancelled) {
            replayStarted = true
            startReplayOcr()
            return
        }
        if (captureOcrScreenshotRequested && !captureOcrScreenshotStarted && !cancelled) {
            captureOcrScreenshotStarted = true
            window.decorView.post {
                if (cancelled || isFinishing || isDestroyed) {
                    return@post
                }
                val started = AccessibilityScreenshotCapture.captureToOcr(
                    context = this,
                    onCaptured = { imageUri ->
                        val sourceToken = manualOcrSourceToken ?: ManualOcrSourceStore.newToken().also {
                            manualOcrSourceToken = it
                        }
                        ManualOcrSourceStore.put(
                            ManualOcrSourceStore.Source(
                                token = sourceToken,
                                imageUri = imageUri,
                                touchX = touchX.toInt(),
                                touchY = touchY.toInt(),
                                callerPackage = callerPackage,
                                fullscreen = intent.getBooleanExtra("boom_fullscreen", false),
                                offsetX = intent.getIntExtra("boom_offsetx", 0),
                                offsetY = intent.getIntExtra("boom_offsety", 0),
                                sourceTag = "ocr_capture",
                                replayMode = ManualOcrSourceStore.REPLAY_MODE_NEAREST_PARAGRAPH,
                            ),
                        )
                        startNearestParagraphOcr(imageUri)
                    },
                )
                if (!started) {
                    finish()
                }
            }
            return
        }
        if (pendingOcrSelectionLaunch && !ocrSelectionLaunched && !cancelled) {
            window.decorView.post {
                if (pendingOcrSelectionLaunch && !ocrSelectionLaunched && !cancelled && !isFinishing && !isDestroyed) {
                    launchOcrSelection()
                }
            }
        }
    }

    override fun onDestroy() {
        cancelled = true
        loadingAnimator?.cancel()
        loadingAnimator = null
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun readTouchCoordinate(extraName: String, horizontal: Boolean): Float {
        if (intent.hasExtra(extraName)) {
            return intent.getIntExtra(extraName, 0).toFloat()
        }
        val metrics: DisplayMetrics = resources.displayMetrics
        return if (horizontal) metrics.widthPixels / 2f else metrics.heightPixels / 2f
    }

    private fun startLaunchAnimation() {
        val frame = loopAnimFrame ?: return
        if (launched || cancelled || pendingOcrSelectionLaunch) {
            return
        }
        frame.alpha = 1f
        frame.scaleX = 1f
        frame.scaleY = 1f
        showLoadingIndicator()
        launchGateOpen = true
        maybeLaunchBigBang()
    }

    private fun startAccessibilityCapture() {
        thread(name = "bigbang-launch-capture") {
            val snapshot = TextSessionCoordinator.runAccessibilityFirst(
                CaptureRequestContract(
                    touchX = touchX.toDouble(),
                    touchY = touchY.toDouble(),
                    packageName = callerPackage ?: applicationContext.packageName,
                    allowOcrFallback = false,
                ),
                traceEnabled = traceEnabled,
                traceId = traceId,
            )
            val text = snapshot.originalText.trim()
            runOnUiThread {
                if (cancelled || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                if (text.isEmpty()) {
                    LogUtils.d("OcrLaunchActivity", "capture failed: no accessible text")
                    finish()
                    return@runOnUiThread
                }
                pendingText = text
                maybeLaunchBigBang()
            }
        }
    }

    private fun maybeLaunchBigBang() {
        if (launched || cancelled || isFinishing || isDestroyed || !launchGateOpen) {
            return
        }
        val text = pendingText ?: return
        hideLoadingIndicator()
        launched = true
        LogUtils.d("OcrLaunchActivity", "launch ocr")
        startActivity(
            Intent(this, OverlayActivity::class.java).apply {
                replaceExtras(this@OcrLaunchActivity.intent)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(EXTRA_SKIP_LEGACY_FADE_IN, true)
                putExtra(BoomActivity.EXTRA_ENABLE_ADJACENT_SESSION, enableAdjacentSession || captureRequested)
                if (!manualOcrSourceToken.isNullOrEmpty()) {
                    putExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN, manualOcrSourceToken)
                }
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
        )
        finish()
    }

    private fun launchOcrSelection() {
        pendingOcrSelectionLaunch = false
        ocrSelectionLaunched = true
        startActivity(
            Intent(this, BoomOcrActivity::class.java).apply {
                replaceExtras(this@OcrLaunchActivity.intent)
                if (!manualOcrSourceToken.isNullOrEmpty()) {
                    putExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN, manualOcrSourceToken)
                }
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        finish()
    }

    private fun startNearestParagraphOcr(imageUri: Uri) {
        startNearestParagraphOcr(imageUri, replayOcrMode ?: BigBangSettings.get(this).ocrRecognizerMode)
    }

    private fun startNearestParagraphOcr(
        imageUri: Uri,
        mode: String,
    ) {
        thread(name = "bigbang-ocr-nearest") {
            val settings = BigBangSettings.get(this)
            val fullscreen = intent.getBooleanExtra("boom_fullscreen", false)
            val offsetX = intent.getIntExtra("boom_offsetx", 0)
            val offsetY = intent.getIntExtra("boom_offsety", 0)
            val bitmap = try {
                MlKitOcrEngine.decodeBitmap(this, imageUri)
            } catch (exception: Exception) {
                LogUtils.e("Failed to decode OCR screenshot", exception)
                null
            }
            if (bitmap == null) {
                runOnUiThread {
                    if (!isFinishing) {
                        Toast.makeText(this, R.string.ocr_image_unavailable, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                return@thread
            }
            val rawWidth = bitmap.width
            val rawHeight = bitmap.height
            val prepared = MlKitOcrEngine.prepareBitmap(
                context = this,
                screenshot = bitmap,
                callerPackage = callerPackage,
                fullscreen = fullscreen,
                offsetX = offsetX,
                offsetY = offsetY,
                touchX = touchX.toInt(),
                touchY = touchY.toInt(),
            )
            MlKitOcrEngine.recognize(prepared.bitmap, mode)
                .addOnSuccessListener(this) { result ->
                    val paragraphs = MlKitOcrEngine.findParagraphs(result)
                    val nearestMatch = MlKitOcrEngine.findNearestTextBlock(paragraphs, prepared.touchX, prepared.touchY)
                    logOcrTrace(
                        settings = settings,
                        mode = mode,
                        callerPackage = callerPackage,
                        rawWidth = rawWidth,
                        rawHeight = rawHeight,
                        prepared = prepared,
                        result = result,
                        nearestMatch = nearestMatch,
                    )
                    prepared.bitmap.recycle()
                    if (isFinishing) {
                        return@addOnSuccessListener
                    }
                    if (nearestMatch == null) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }
                    TextSessionCoordinator.replaceSession(
                        paragraphs = paragraphs.map {
                            CaptureTextBlockContract(
                                text = it.text,
                                left = it.bounds.left.toDouble(),
                                top = it.bounds.top.toDouble(),
                                right = it.bounds.right.toDouble(),
                                bottom = it.bounds.bottom.toDouble(),
                                confidence = 1.0,
                            )
                        },
                        initialIndex = paragraphs.indexOfFirst { it.text == nearestMatch.text && it.bounds == nearestMatch.bounds }
                            .coerceAtLeast(0),
                        source = "ocr",
                        debugMessage = "channel=ocr; paragraphs=${paragraphs.size}; initial=1",
                    )
                    updateDirectOcrReplayContext(sourceToken = manualOcrSourceToken, imageUri = imageUri)
                    enableAdjacentSession = true
                    pendingText = nearestMatch.text
                    maybeLaunchBigBang()
                }
                .addOnFailureListener(this) { throwable ->
                    prepared.bitmap.recycle()
                    LogUtils.e("ML Kit OCR failed", throwable)
                    if (!isFinishing) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
        }
    }

    private fun startReplayOcr() {
        val source = ManualOcrSourceStore.get(manualOcrSourceToken)
        val mode = replayOcrMode
        if (source == null || mode.isNullOrBlank()) {
            finish()
            return
        }
        if (source.replayMode == ManualOcrSourceStore.REPLAY_MODE_SELECTION_RECT) {
            startSelectionRectReplay(source, mode)
        } else {
            startNearestParagraphOcr(source.imageUri, mode)
        }
    }

    private fun startSelectionRectReplay(
        source: ManualOcrSourceStore.Source,
        mode: String,
    ) {
        val selection = source.selectionRect ?: run {
            finish()
            return
        }
        thread(name = "bigbang-ocr-selection-replay") {
            val bitmap = try {
                MlKitOcrEngine.decodeBitmap(this, source.imageUri)
            } catch (exception: Exception) {
                LogUtils.e("Failed to decode OCR replay image", exception)
                null
            }
            if (bitmap == null) {
                runOnUiThread {
                    if (!isFinishing) {
                        Toast.makeText(this, R.string.ocr_image_unavailable, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                return@thread
            }
            val prepared = MlKitOcrEngine.prepareBitmap(
                context = this,
                screenshot = bitmap,
                callerPackage = source.callerPackage,
                fullscreen = source.fullscreen,
                offsetX = source.offsetX,
                offsetY = source.offsetY,
                touchX = source.touchX,
                touchY = source.touchY,
            )
            val safeSelection = Rect(
                selection.left.coerceIn(0, prepared.bitmap.width.coerceAtLeast(1) - 1),
                selection.top.coerceIn(0, prepared.bitmap.height.coerceAtLeast(1) - 1),
                selection.right.coerceIn(1, prepared.bitmap.width),
                selection.bottom.coerceIn(1, prepared.bitmap.height),
            )
            if (safeSelection.width() <= 0 || safeSelection.height() <= 0) {
                prepared.bitmap.recycle()
                runOnUiThread { finish() }
                return@thread
            }
            val cropped = Bitmap.createBitmap(
                prepared.bitmap,
                safeSelection.left,
                safeSelection.top,
                safeSelection.width(),
                safeSelection.height(),
            )
            MlKitOcrEngine.recognize(cropped, mode)
                .addOnSuccessListener(this) { result ->
                    prepared.bitmap.recycle()
                    cropped.recycle()
                    if (isFinishing) {
                        return@addOnSuccessListener
                    }
                    val text = MlKitOcrEngine.buildParagraphText(result)
                    if (text.isBlank()) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }
                    val bounds = result.textBlocks.firstOrNull()?.boundingBox ?: Rect(safeSelection)
                    TextSessionCoordinator.replaceSession(
                        paragraphs = listOf(
                            CaptureTextBlockContract(
                                text = text,
                                left = bounds.left.toDouble(),
                                top = bounds.top.toDouble(),
                                right = bounds.right.toDouble(),
                                bottom = bounds.bottom.toDouble(),
                                confidence = 1.0,
                            ),
                        ),
                        initialIndex = 0,
                        source = "ocr",
                        debugMessage = "channel=ocr; paragraphs=1; initial=1",
                    )
                    ManualOcrSourceStore.put(
                        source.copy(
                            touchX = source.touchX,
                            touchY = source.touchY,
                            sourceTag = "ocr_selection",
                            replayMode = ManualOcrSourceStore.REPLAY_MODE_SELECTION_RECT,
                            selectionRect = Rect(selection),
                            ocrMode = mode,
                        ),
                    )
                    enableAdjacentSession = true
                    pendingText = text
                    maybeLaunchBigBang()
                }
                .addOnFailureListener(this) { throwable ->
                    prepared.bitmap.recycle()
                    cropped.recycle()
                    LogUtils.e("ML Kit OCR replay failed", throwable)
                    if (!isFinishing) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
        }
    }

    private fun startSilentManualOcrCapture() {
        if (silentManualOcrCaptureStarted || !captureRequested) {
            return
        }
        silentManualOcrCaptureStarted = true
        manualOcrSourceToken = manualOcrSourceToken ?: ManualOcrSourceStore.newToken()
        val started = AccessibilityScreenshotCapture.captureToCache(
            context = this,
            onCaptured = { imageUri ->
                val sourceToken = manualOcrSourceToken ?: return@captureToCache
                ManualOcrSourceStore.put(
                    ManualOcrSourceStore.Source(
                        token = sourceToken,
                        imageUri = imageUri,
                        touchX = touchX.toInt(),
                        touchY = touchY.toInt(),
                        callerPackage = callerPackage,
                        fullscreen = true,
                        offsetX = 0,
                        offsetY = 0,
                        sourceTag = "accessibility_capture",
                    ),
                )
            },
        )
        if (!started) {
            LogUtils.d("OcrLaunchActivity", "silent OCR cache capture skipped")
        }
    }

    private fun updateDirectOcrReplayContext(
        sourceToken: String?,
        imageUri: Uri,
    ) {
        val token = sourceToken ?: return
        val source = ManualOcrSourceStore.get(token)
        ManualOcrSourceStore.put(
            (source ?: ManualOcrSourceStore.Source(
                token = token,
                imageUri = imageUri,
                touchX = touchX.toInt(),
                touchY = touchY.toInt(),
                callerPackage = callerPackage,
                fullscreen = intent.getBooleanExtra("boom_fullscreen", false),
                offsetX = intent.getIntExtra("boom_offsetx", 0),
                offsetY = intent.getIntExtra("boom_offsety", 0),
                sourceTag = "ocr_capture",
                ocrMode = replayOcrMode ?: BigBangSettings.get(this).ocrRecognizerMode,
            )).copy(
                imageUri = imageUri,
                sourceTag = "ocr_direct",
                replayMode = ManualOcrSourceStore.REPLAY_MODE_NEAREST_PARAGRAPH,
                selectionRect = null,
                ocrMode = replayOcrMode ?: BigBangSettings.get(this).ocrRecognizerMode,
            ),
        )
    }

    private fun isReplayRequested(): Boolean {
        return !replayOcrMode.isNullOrBlank() && !replayMode.isNullOrBlank()
    }

    private fun ensureLaunchUi() {
        if (contentFrame != null) {
            return
        }
        setContentView(R.layout.boom_ocr_launch_layout)
        loopAnimFrame = findViewById(R.id.anim_loop)
        loopRotateImage = findViewById(R.id.loop_rotate)
        contentFrame = findViewById(R.id.click_layout)
        contentFrame?.setOnClickListener {
            cancelled = true
            finish()
        }
        loopRotateImage?.visibility = View.INVISIBLE
        loopAnimFrame?.visibility = View.INVISIBLE
        if (pendingOcrSelectionLaunch || captureRequested || isReplayRequested()) {
            return
        }
        loopAnimFrame?.post {
            val frame = loopAnimFrame ?: return@post
            if (launched || cancelled || pendingOcrSelectionLaunch) {
                return@post
            }
            frame.translationX = touchX - frame.width / 2f
            frame.translationY = touchY - frame.height / 2f
            startLaunchAnimation()
        }
    }

    private fun showLoadingIndicator() {
        val frame = loopAnimFrame ?: return
        val image = loopRotateImage ?: return
        frame.visibility = View.VISIBLE
        image.visibility = View.VISIBLE
        if (loadingAnimator?.isRunning == true) {
            return
        }
        loadingAnimator = ObjectAnimator.ofFloat(image, "rotation", image.rotation, image.rotation + 360f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun hideLoadingIndicator() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        loopRotateImage?.rotation = 0f
        loopRotateImage?.visibility = View.GONE
        loopAnimFrame?.visibility = View.INVISIBLE
    }

    private fun logOcrTrace(
        settings: BigBangSettings,
        mode: String,
        callerPackage: String?,
        rawWidth: Int,
        rawHeight: Int,
        prepared: MlKitOcrEngine.PreparedBitmap,
        result: com.google.mlkit.vision.text.Text,
        nearestMatch: MlKitOcrEngine.NearestTextBlockMatch?,
    ) {
        if (!traceEnabled && !settings.isDebugCaptureTraceEnabled) {
            return
        }
        NovaTextLogger.d("trace[$traceId] phase=ocr")
        NovaTextLogger.d("trace[$traceId] package=${callerPackage ?: "unknown"}")
        NovaTextLogger.d("trace[$traceId] touchRaw=(${touchX.toInt()},${touchY.toInt()})")
        NovaTextLogger.d("trace[$traceId] touchMapped=(${prepared.touchX},${prepared.touchY})")
        NovaTextLogger.d("trace[$traceId] mode=$mode")
        NovaTextLogger.d("trace[$traceId] bitmapRaw=${rawWidth}x${rawHeight}")
        NovaTextLogger.d("trace[$traceId] bitmapPrepared=${prepared.bitmap.width}x${prepared.bitmap.height}")
        val rawBlocks = MlKitOcrEngine.collectRawBlocks(result)
        NovaTextLogger.d("trace[$traceId] rawBlockCount=${rawBlocks.size}")
        rawBlocks.forEachIndexed { index, block ->
            NovaTextLogger.d(
                "trace[$traceId] raw[$index] text=${sanitizeForLog(block.text)} bounds=${formatBounds(block.bounds)}"
            )
        }
        NovaTextLogger.d("trace[$traceId] paragraphCount=${MlKitOcrEngine.findParagraphs(result).size}")
        if (nearestMatch == null) {
            NovaTextLogger.d("trace[$traceId] selectedText=none")
            NovaTextLogger.d("trace[$traceId] selectedBounds=none")
            NovaTextLogger.d("trace[$traceId] selectedDistance=none")
            return
        }
        NovaTextLogger.d("trace[$traceId] selectedText=${sanitizeForLog(nearestMatch.text)}")
        NovaTextLogger.d("trace[$traceId] selectedBounds=${formatBounds(nearestMatch.bounds)}")
        NovaTextLogger.d("trace[$traceId] selectedDistance=${nearestMatch.distanceSquared}")
        NovaTextLogger.d("trace[$traceId] selectedBlockCount=${nearestMatch.blockCount}")
        NovaTextLogger.d("trace[$traceId] selectedScore=${nearestMatch.score}")
    }

    private fun formatBounds(bounds: android.graphics.Rect?): String {
        return if (bounds == null) {
            "none"
        } else {
            "[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]"
        }
    }

    private fun sanitizeForLog(text: String): String {
        return text.replace("\n", "\\n")
    }

    companion object {
        const val EXTRA_CAPTURE_ACCESSIBILITY = "extra_capture_accessibility"
        const val EXTRA_CAPTURE_TRACE_ID = "extra_capture_trace_id"
        const val EXTRA_CAPTURE_TRACE_ENABLED = "extra_capture_trace_enabled"
        const val EXTRA_SKIP_LEGACY_FADE_IN = "extra_skip_legacy_fade_in"
        const val EXTRA_REPLAY_OCR_MODE = "extra_replay_ocr_mode"
        const val EXTRA_REPLAY_MODE = "extra_replay_mode"
    }
}
