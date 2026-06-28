package com.cashewteam.novatext.android

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
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
import com.cashewteam.novatext.android.domain.capture.CaptureRequestContract
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator
import com.cashewteam.novatext.android.service.AccessibilityScreenshotCapture
import com.cashewteam.novatext.android.service.BoomActivityLauncher
import com.cashewteam.novatext.android.service.BoomOcrLauncher
import com.cashewteam.novatext.android.util.LogUtils
import kotlin.concurrent.thread

class OcrLaunchActivity : Activity() {
    private var loopAnimFrame: FrameLayout? = null
    private var loopRotateImage: ImageView? = null
    private var contentFrame: FrameLayout? = null
    private var touchAnimation: AnimatorSet? = null
    private var touchAnimating = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        captureOcrScreenshotRequested = intent.getBooleanExtra(BoomOcrLauncher.EXTRA_CAPTURE_OCR_SCREENSHOT, false)
        pendingOcrSelectionLaunch = !intent.getStringExtra(BoomOcrActivity.EXTRA_OCR_IMAGE_URI).isNullOrEmpty()
        if (captureOcrScreenshotRequested) {
            touchX = readTouchCoordinate("boom_startx", true)
            touchY = readTouchCoordinate("boom_starty", false)
            return
        }
        setContentView(R.layout.boom_ocr_launch_layout)
        loopAnimFrame = findViewById(R.id.anim_loop)
        loopRotateImage = findViewById(R.id.loop_rotate)
        contentFrame = findViewById(R.id.click_layout)
        touchX = readTouchCoordinate("boom_startx", true)
        touchY = readTouchCoordinate("boom_starty", false)
        if (pendingOcrSelectionLaunch) {
            loopRotateImage?.visibility = View.INVISIBLE
            loopAnimFrame?.visibility = View.INVISIBLE
            return
        }
        captureRequested = intent.getBooleanExtra(EXTRA_CAPTURE_ACCESSIBILITY, false)
        pendingText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }

        contentFrame?.setOnClickListener {
            cancelled = true
            finish()
        }
        loopRotateImage?.visibility = View.INVISIBLE
        loopAnimFrame?.visibility = View.INVISIBLE
        loopAnimFrame?.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (touchAnimating || launched || cancelled) {
                return@addOnLayoutChangeListener
            }
            loopAnimFrame?.post {
                loopAnimFrame?.translationX = touchX - (right - left) / 2f
                loopAnimFrame?.translationY = touchY - (bottom - top) / 2f
                startTouchBoomAnimation()
            }
        }
        if (captureRequested) {
            startAccessibilityCapture()
        }
    }

    override fun onResume() {
        super.onResume()
        if (captureOcrScreenshotRequested && !captureOcrScreenshotStarted && !cancelled) {
            captureOcrScreenshotStarted = true
            window.decorView.post {
                if (cancelled || isFinishing || isDestroyed) {
                    return@post
                }
                val started = AccessibilityScreenshotCapture.captureToOcr(
                    context = this,
                    onCaptured = { imageUri -> startNearestParagraphOcr(imageUri) },
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
        touchAnimation?.cancel()
        touchAnimation = null
        touchAnimating = false
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

    private fun startTouchBoomAnimation() {
        val frame = loopAnimFrame ?: return
        if (touchAnimating || launched || cancelled) {
            return
        }
        frame.visibility = View.INVISIBLE
        frame.scaleX = TOUCH_SCALE_FROM
        frame.scaleY = TOUCH_SCALE_FROM
        frame.alpha = TOUCH_ALPHA_FROM
        loopRotateImage?.visibility = View.GONE

        val scaleAnimation = ValueAnimator.ofFloat(TOUCH_SCALE_FROM, TOUCH_SCALE_TO).apply {
            duration = TOUCH_EXPAND_DURATION
            addUpdateListener {
                val value = it.animatedValue as Float
                frame.scaleX = value
                frame.scaleY = value
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    touchAnimating = true
                    frame.visibility = View.VISIBLE
                    frame.post {
                        launchGateOpen = true
                        maybeLaunchBigBang()
                    }
                }

                override fun onAnimationEnd(animation: Animator) = Unit
                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
            })
        }

        val alphaAnimation = ValueAnimator.ofFloat(TOUCH_ALPHA_FROM, TOUCH_ALPHA_TO).apply {
            duration = TOUCH_EXPAND_DURATION
            addUpdateListener { frame.alpha = it.animatedValue as Float }
        }

        touchAnimation = AnimatorSet().apply {
            interpolator = CubicInInterpolator()
            playTogether(scaleAnimation, alphaAnimation)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    touchAnimating = false
                    frame.visibility = View.INVISIBLE
                    maybeLaunchBigBang()
                }

                override fun onAnimationCancel(animation: Animator) {
                    touchAnimating = false
                }

                override fun onAnimationRepeat(animation: Animator) = Unit
            })
            start()
        }
    }

    private fun startAccessibilityCapture() {
        thread(name = "bigbang-launch-capture") {
            val snapshot = TextSessionCoordinator.runAccessibilityFirst(
                CaptureRequestContract(
                    touchX = touchX.toDouble(),
                    touchY = touchY.toDouble(),
                    packageName = applicationContext.packageName,
                    allowOcrFallback = false,
                ),
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
        launched = true
        LogUtils.d("OcrLaunchActivity", "launch ocr")
        startActivity(
            Intent(this, OverlayActivity::class.java).apply {
                replaceExtras(this@OcrLaunchActivity.intent)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(EXTRA_SKIP_LEGACY_FADE_IN, true)
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
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        finish()
    }

    private fun startNearestParagraphOcr(imageUri: Uri) {
        thread(name = "bigbang-ocr-nearest") {
            val settings = BigBangSettings.get(this)
            val callerPackage = intent.getStringExtra("caller_pkg")
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
            MlKitOcrEngine.recognize(prepared.bitmap, settings.ocrRecognizerMode)
                .addOnSuccessListener(this) { result ->
                    val nearestText = MlKitOcrEngine.findNearestTextBlock(result, prepared.touchX, prepared.touchY)
                    prepared.bitmap.recycle()
                    if (isFinishing) {
                        return@addOnSuccessListener
                    }
                    if (nearestText.isEmpty()) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }
                    BoomActivityLauncher.openText(
                        context = this,
                        text = nearestText,
                        touchX = touchX.toInt(),
                        touchY = touchY.toInt(),
                        isPreview = false,
                        animateLaunch = false,
                    )
                    finish()
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

    companion object {
        const val EXTRA_CAPTURE_ACCESSIBILITY = "extra_capture_accessibility"
        const val EXTRA_SKIP_LEGACY_FADE_IN = "extra_skip_legacy_fade_in"

        private const val TOUCH_SCALE_FROM = 0.28f
        private const val TOUCH_SCALE_TO = 2.6f
        private const val TOUCH_EXPAND_DURATION = 180L
        private const val TOUCH_ALPHA_FROM = 0.9f
        private const val TOUCH_ALPHA_TO = 0f
    }
}
