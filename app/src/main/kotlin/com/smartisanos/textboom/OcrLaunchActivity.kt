package com.cashewteam.novatext.android

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.cashewteam.novatext.android.util.LogUtils

class OcrLaunchActivity : Activity() {
    private var loopAnimFrame: FrameLayout? = null
    private var loopRotateImage: ImageView? = null
    private var contentFrame: FrameLayout? = null
    private var touchAnimation: AnimatorSet? = null
    private var touchAnimating = false
    private var launched = false
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            finish()
            return
        }
        setContentView(R.layout.boom_ocr_launch_layout)
        loopAnimFrame = findViewById(R.id.anim_loop)
        loopRotateImage = findViewById(R.id.loop_rotate)
        contentFrame = findViewById(R.id.click_layout)
        touchX = readTouchCoordinate("boom_startx", true)
        touchY = readTouchCoordinate("boom_starty", false)

        contentFrame?.setOnClickListener { finish() }
        loopRotateImage?.visibility = View.INVISIBLE
        loopAnimFrame?.visibility = View.INVISIBLE
        loopAnimFrame?.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (touchAnimating || launched) {
                return@addOnLayoutChangeListener
            }
            loopAnimFrame?.post {
                loopAnimFrame?.translationX = touchX - (right - left) / 2f
                loopAnimFrame?.translationY = touchY - (bottom - top) / 2f
                startTouchBoomAnimation()
            }
        }
    }

    override fun onDestroy() {
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
        if (touchAnimating || launched) {
            return
        }
        frame.visibility = View.INVISIBLE
        frame.scaleX = TOUCH_SCALE_FROM
        frame.scaleY = TOUCH_SCALE_FROM
        frame.alpha = TOUCH_ALPHA_FROM

        val scaleAnimation = ValueAnimator.ofFloat(TOUCH_SCALE_FROM, TOUCH_SCALE_TO_1).apply {
            duration = SCALE_1_DURATION
            addUpdateListener {
                val value = it.animatedValue as Float
                frame.scaleX = value
                frame.scaleY = value
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    touchAnimating = true
                    frame.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator) = Unit
                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
            })
        }

        val alphaAnimation = ValueAnimator.ofFloat(TOUCH_ALPHA_FROM, TOUCH_ALPHA_TO).apply {
            duration = SCALE_1_DURATION
            addUpdateListener { frame.alpha = it.animatedValue as Float }
        }

        val scaleAnimation2 = ValueAnimator.ofFloat(TOUCH_SCALE_TO_1, TOUCH_SCALE_TO_2).apply {
            duration = SCALE_2_DURATION
            addUpdateListener {
                val value = it.animatedValue as Float
                frame.scaleX = value
                frame.scaleY = value
            }
        }

        touchAnimation = AnimatorSet().apply {
            interpolator = CubicInInterpolator()
            playSequentially(
                AnimatorSet().apply { playTogether(scaleAnimation, alphaAnimation) },
                scaleAnimation2,
            )
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    touchAnimating = false
                    launchOcrActivity()
                }

                override fun onAnimationCancel(animation: Animator) {
                    touchAnimating = false
                }

                override fun onAnimationRepeat(animation: Animator) = Unit
            })
            start()
        }
    }

    private fun launchOcrActivity() {
        if (launched) {
            return
        }
        launched = true
        LogUtils.d("OcrLaunchActivity", "launch ocr")
        startActivity(
            Intent(this, OverlayActivity::class.java).apply {
                replaceExtras(this@OcrLaunchActivity.intent)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
        )
        finish()
    }

    companion object {
        private const val TOUCH_SCALE_FROM = 2f
        private const val TOUCH_SCALE_TO_1 = 0.2f
        private const val TOUCH_SCALE_TO_2 = 1.15f
        private const val SCALE_1_DURATION = 400L
        private const val SCALE_2_DURATION = 200L
        private const val TOUCH_ALPHA_FROM = 0.4f
        private const val TOUCH_ALPHA_TO = 1f
    }
}
