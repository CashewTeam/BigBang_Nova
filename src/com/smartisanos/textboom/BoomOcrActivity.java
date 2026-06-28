package com.cashewteam.novatext.android;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.cashewteam.novatext.android.data.BigBangSettings;
import com.cashewteam.novatext.android.service.BoomActivityLauncher;
import com.cashewteam.novatext.android.util.LogUtils;
import com.google.mlkit.vision.text.Text;

/**
 * Created by jayce on 16-10-11.
 */
public class BoomOcrActivity extends Activity {

    public static final String EXTRA_OCR_IMAGE_URI = "ocr_image_uri";

    private final static String TAG = "BoomOcrActivity";

    private Toast mToastStop;

    private final Runnable mOcrRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sBoomCancel) {
                startOcr();
            }
        }
    };

    private static BoomOcrActivity sSelf;

    private FrameLayout mLoopAnimFrame;
    private ImageView mLoopRotateImage;
    private FrameLayout mContentFrame;

    private AnimatorSet mLoopAnimation;
    private AnimatorSet mTouchAnimation;
    private AnimatorSet mCircleAnimation;
    private boolean mAnimating = false;
    private boolean mTouchAnimating = false;
    private boolean mLoopAnimating = false;
    private boolean mCircleAnimating = false;
    private boolean mOcrResult = false;

    private static final float LOOP_SCALE_FROM = 1.15f;
    private static final float LOOP_SCALE_TO = 1.1f;
    private static final long SCALE_DURATION = 600;

    private String mOcrText = null;
    private float mTouchX;
    private float mTouchY;
    private boolean mFullscreen = false;

    static boolean sBoomCancel = false;

    private Handler mHandler;
    private String mPackage;
    private int[] mOffset;
    private Bitmap mPreparedBitmap;
    private boolean mOcrStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        sSelf = this;
        Configuration cf = getResources().getConfiguration();
        if (cf.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            finish();
            return;
        }
        if (sBoomCancel) {
            finish();
            return;
        }
        mHandler = new Handler(getMainLooper());
        setContentView(R.layout.boom_ocr_layout);
        mLoopAnimFrame = findViewById(R.id.anim_loop);
        mLoopAnimFrame.setVisibility(View.INVISIBLE);
        mLoopRotateImage = findViewById(R.id.loop_rotate);
        mContentFrame = findViewById(R.id.click_layout);
        mContentFrame.requestFocus();
        mContentFrame.setClickable(true);
        mContentFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopOcr();
            }
        });

        mTouchX = readTouchCoordinate("boom_startx", true);
        mTouchY = readTouchCoordinate("boom_starty", false);
        mFullscreen = getIntent().getBooleanExtra("boom_fullscreen", false);
        mPackage = getIntent().getStringExtra("caller_pkg");
        int offx = getIntent().getIntExtra("boom_offsetx", 0);
        int offy = getIntent().getIntExtra("boom_offsety", 0);
        mOffset = new int[] {offx, offy};
        LogUtils.d(TAG, "touchX:" + mTouchX + ", touchY:" + mTouchY + ", fullscreen:" + mFullscreen);

        prepareOcr();
        if (isFinishing()) {
            return;
        }

        mLoopAnimFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (!mAnimating) {
                    final int l = left;
                    final int t = top;
                    final int r = right;
                    final int b = bottom;
                    mLoopAnimFrame.post(new Runnable() {
                        @Override
                        public void run() {
                            mLoopAnimFrame.setTranslationX(mTouchX - (r - l) / 2f);
                            mLoopAnimFrame.setTranslationY(mTouchY - (b - t) / 2f);
                            startTouchBoomAnimation();
                        }
                    });
                }
            }
        });

        mContentFrame.removeCallbacks(mOcrRunnable);
        mContentFrame.postDelayed(mOcrRunnable, OCR_DELAY);
    }

    public static final long OCR_DELAY = 300;

    private float readTouchCoordinate(String extraName, boolean horizontal) {
        if (getIntent().hasExtra(extraName)) {
            return getIntent().getIntExtra(extraName, 0);
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return horizontal ? metrics.widthPixels / 2f : metrics.heightPixels / 2f;
    }

    private void startOcr() {
        if (mPreparedBitmap == null) {
            showImageUnavailableAndFinish();
            return;
        }
        mOcrStarted = true;
        mOcrResult = false;
        String mode = BigBangSettings.get(this).getOcrRecognizerMode();
        MlKitOcrEngine.recognize(mPreparedBitmap, mode)
                .addOnSuccessListener(this, this::handleOcrSuccess)
                .addOnFailureListener(this, throwable -> {
                    LogUtils.e("ML Kit OCR failed", throwable);
                    if (!isFinishing()) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
                    }
                    stopOcr();
                });
    }

    private void handleOcrSuccess(Text result) {
        if (isFinishing()) {
            return;
        }
        mOcrResult = true;
        String text = result == null ? "" : result.getText();
        mOcrText = text == null ? "" : text.trim();
        if (mOcrText.length() > 0) {
            stopTouchAnimation();
            stopLoopAnimation();
            startCircleAnimation();
            return;
        }
        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
        stopOcr();
    }

    private void startLoopAnimation() {
        if (sBoomCancel && !mOcrStarted || mOcrResult) {
            return;
        }
        ValueAnimator scaleIn = new ValueAnimator().ofFloat(LOOP_SCALE_FROM, LOOP_SCALE_TO);
        scaleIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();
                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleIn.setDuration(SCALE_DURATION);
        scaleIn.setInterpolator(new SineInoutInterpolator());

        ValueAnimator scaleOut = new ValueAnimator().ofFloat(LOOP_SCALE_TO, LOOP_SCALE_FROM);
        scaleOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();
                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleOut.setDuration(SCALE_DURATION);
        scaleOut.setInterpolator(new SineInoutInterpolator());

        AnimatorSet scaleAnimation = new AnimatorSet();
        scaleAnimation.playSequentially(scaleIn, scaleOut);
        scaleAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mLoopAnimating && !mOcrResult) {
                    animation.start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        ValueAnimator rotateAnimation = new ValueAnimator().ofInt(0, 360);
        mLoopAnimFrame.setPivotX(mLoopAnimFrame.getWidth() / 2f);
        mLoopAnimFrame.setPivotY(mLoopAnimFrame.getHeight() / 2f);
        rotateAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int animatorValue = (Integer) animation.getAnimatedValue();

                mLoopAnimFrame.setRotation(animatorValue);
            }
        });
        rotateAnimation.setDuration(SCALE_DURATION * 2);
        rotateAnimation.setRepeatMode(ValueAnimator.RESTART);
        rotateAnimation.setRepeatCount(ValueAnimator.INFINITE);
        rotateAnimation.setInterpolator(new LinearInterpolator());

        mLoopAnimation = new AnimatorSet();
        mLoopAnimation.playTogether(scaleAnimation, rotateAnimation);
        mLoopAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mLoopAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mLoopAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mLoopAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        mLoopAnimation.start();
    }

    private void stopLoopAnimation() {
        if (null != mLoopAnimation) {
            mLoopAnimation.cancel();
            mLoopAnimation = null;
        }
        mLoopAnimating = false;
    }

    private void stopCircleAnimation() {
        if (null != mCircleAnimation) {
            mCircleAnimation.cancel();
            mCircleAnimation = null;
        }
        mCircleAnimating = false;
    }

    private void stopAllAnimation() {
        stopTouchAnimation();
        stopLoopAnimation();
        stopCircleAnimation();
    }

    public void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        sBoomCancel = false;
        super.onDestroy();
        stopAllAnimation();
        if (null != mContentFrame) {
            mContentFrame.removeCallbacks(mOcrRunnable);
        }

        if (null != mToastStop) {
            mToastStop.cancel();
            mToastStop = null;
        }
        if (mPreparedBitmap != null && !mPreparedBitmap.isRecycled()) {
            mPreparedBitmap.recycle();
            mPreparedBitmap = null;
        }
        mAnimating = false;
        mOcrStarted = false;
        if (null != sSelf && sSelf == this) {
            sSelf = null;
        }
    }

    public static BoomOcrActivity getInstance() {
        return sSelf;
    }

    public static final float TOUCH_SCALE_FROM = 2f;
    public static final float TOUCH_SCALE_TO_1 = 0.2f;
    public static final float TOUCH_SCALE_TO_2 = 1.15f;
    public static final long TOUCH_DELAY = 0;

    public static final long SCALE_1_DURATION = 400;
    public static final long SCALE_2_DURATION = 200;

    public static final float TOUCH_ALPHA_FROM = 0.4f;
    public static final float TOUCH_ALPHA_TO = 1f;

    private void startTouchBoomAnimation() {
        LogUtils.d(TAG, "startTouchBoomAnimation");
        if (sBoomCancel) {
            return;
        }
        mAnimating = true;
        mLoopAnimFrame.setVisibility(View.INVISIBLE);
        mLoopRotateImage.setVisibility(View.INVISIBLE);
        mLoopAnimFrame.setScaleX(TOUCH_SCALE_FROM);
        mLoopAnimFrame.setScaleY(TOUCH_SCALE_FROM);
        mLoopAnimFrame.setAlpha(TOUCH_ALPHA_FROM);

        AnimatorSet setAnim = new AnimatorSet();
        ValueAnimator scaleAnimation = new ValueAnimator().ofFloat(TOUCH_SCALE_FROM, TOUCH_SCALE_TO_1);
        scaleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });
        scaleAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                LogUtils.d(TAG, "touch scale start");
                mTouchAnimating = true;
                mLoopAnimFrame.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        scaleAnimation.setDuration(SCALE_1_DURATION);

        ValueAnimator alphaAnimation = new ValueAnimator().ofFloat(TOUCH_ALPHA_FROM, TOUCH_ALPHA_TO);
        alphaAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setAlpha(animatorValue);
            }
        });

        alphaAnimation.setDuration(SCALE_1_DURATION);
        setAnim.playTogether(scaleAnimation, alphaAnimation);

        ValueAnimator scaleAnimation2 = new ValueAnimator().ofFloat(TOUCH_SCALE_TO_1, TOUCH_SCALE_TO_2);
        scaleAnimation2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleAnimation2.setDuration(SCALE_2_DURATION);

        mTouchAnimation = new AnimatorSet();
        mTouchAnimation.playSequentially(setAnim, scaleAnimation2);
        mTouchAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                LogUtils.d(TAG, "touch scale end");
                if (!mTouchAnimating) {
                    return;
                }
                mLoopRotateImage.setVisibility(View.VISIBLE);
                mLoopAnimFrame.setAlpha(1f);
                if (!mOcrResult) {
                    startLoopAnimation();
                }
                mTouchAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                LogUtils.d(TAG, "touch scale cancel");
                mTouchAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        mTouchAnimation.setInterpolator(new CubicInInterpolator());
        mTouchAnimation.setStartDelay(TOUCH_DELAY);
        mTouchAnimation.start();
    }

    private void stopTouchAnimation() {
        if (null != mTouchAnimation) {
            mTouchAnimation.cancel();
            mTouchAnimation = null;
        }
        mTouchAnimating = false;
    }

    private static final float CIRCLE_END_SCALE = 4f;
    private static final long CIRCLE_END_DURATION = 100;

    private void startCircleAnimation() {
        mLoopRotateImage.setVisibility(View.INVISIBLE);
        float currentScale = mLoopAnimFrame.getScaleX();

        mCircleAnimation = new AnimatorSet();

        ValueAnimator scaleAnimation = new ValueAnimator().ofFloat(currentScale, CIRCLE_END_SCALE);
        scaleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setScaleX(animatorValue);
                mLoopAnimFrame.setScaleY(animatorValue);
            }
        });

        scaleAnimation.setDuration(CIRCLE_END_DURATION);

        ValueAnimator alphaAnimation = new ValueAnimator().ofFloat(1f, 0f);
        alphaAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatorValue = (Float) animation.getAnimatedValue();

                mLoopAnimFrame.setAlpha(animatorValue);
            }
        });

        alphaAnimation.setDuration(CIRCLE_END_DURATION);
        mCircleAnimation.playTogether(scaleAnimation, alphaAnimation);
        mCircleAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mCircleAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCircleAnimating) {
                    return;
                }
                mCircleAnimating = false;
                startBoomActivity();
                finish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCircleAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        mCircleAnimation.start();
    }

    private void startBoomActivity() {
        LogUtils.e(TAG, "startBoomActivity");
        BoomActivityLauncher.openText(
                this,
                mOcrText,
                (int) mTouchX,
                (int) mTouchY,
                false
        );
        mOcrStarted = false;
    }

    public void post(Runnable run) {
        if (null != mHandler && null != run) {
            mHandler.post(run);
        }
    }

    public void cancelOcr() {
        LogUtils.e(TAG, "cancelOcr from touch");
        stopOcr();
    }

    private void stopOcr() {
        LogUtils.e(TAG, "stop ocr");
        stopAllAnimation();
        if (null != mContentFrame) {
            mContentFrame.removeCallbacks(mOcrRunnable);
        }

        if (null != mToastStop) {
            mToastStop.cancel();
            mToastStop = null;
        }
        mAnimating = false;
        mOcrStarted = false;
        finish();
    }

    private void prepareOcr() {
        LogUtils.e(TAG, "prepare ocr image");
        Uri imageUri = readImageUri();
        if (imageUri == null) {
            showImageUnavailableAndFinish();
            return;
        }
        try {
            Bitmap bitmap = MlKitOcrEngine.decodeBitmap(this, imageUri);
            if (bitmap == null) {
                showImageUnavailableAndFinish();
                return;
            }
            mPreparedBitmap = shouldAdjustScreenshot() ? adjustScreenshotFor(bitmap) : bitmap;
        } catch (Exception exception) {
            LogUtils.e("Failed to decode OCR image", exception);
            showImageUnavailableAndFinish();
        }
    }

    private Uri readImageUri() {
        String value = getIntent().getStringExtra(EXTRA_OCR_IMAGE_URI);
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        return Uri.parse(value);
    }

    private boolean shouldAdjustScreenshot() {
        return !TextUtils.isEmpty(mPackage) || mOffset[0] != 0 || mOffset[1] != 0;
    }

    private void showImageUnavailableAndFinish() {
        Toast.makeText(this, R.string.ocr_image_unavailable, Toast.LENGTH_SHORT).show();
        stopOcr();
    }

    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        stopOcr();
    }

    public static final int SCALE_SCREENSHOT = 2;
    private static final String PKG_GALLERY = "com.android.gallery3d";

    private Bitmap adjustScreenshotFor(Bitmap screenshot) {
        int w = getResources().getInteger(R.integer.screen_width);
        int h = getResources().getInteger(R.integer.screen_height);
        int statusBarHeight = getResources().getInteger(R.integer.status_bar_height);
        int top = statusBarHeight;
        int bottom = 0;
        int left = 0;
        int right = 0;
        if (0 == mOffset[0] && 0 == mOffset[1]) {
            if (PKG_GALLERY.equals(mPackage) && !mFullscreen) {
                top = getResources().getInteger(R.integer.gallery_top);
                bottom = getResources().getInteger(R.integer.gallery_bottom);
            }
        } else {
            float scaleFactor = mOffset[1] / (float) h;
            int sideh = mOffset[1];
            int sidew = (int) (scaleFactor * w);
            if (PKG_GALLERY.equals(mPackage) && !mFullscreen) {
                int gtop = (int) (getResources().getInteger(R.integer.gallery_top) * (1 - scaleFactor));
                int gbottom = (int) (getResources().getInteger(R.integer.gallery_bottom) * (1 - scaleFactor));
                top = sideh + gtop;
                bottom = gbottom;
            } else {
                top = sideh + (int) ((1 - scaleFactor) * statusBarHeight);
            }
            if (0 == mOffset[0]) {
                right = sidew;
            } else {
                left = sidew;
            }
        }
        LogUtils.d(TAG, "top:" + top + ", bottom:" + bottom + ", left:" + left + ", right:" + right);
        int sourceWidth = screenshot.getWidth();
        int sourceHeight = screenshot.getHeight();
        if (sourceWidth <= left + right || sourceHeight <= top + bottom) {
            return screenshot;
        }
        int aw = (sourceWidth - left - right) / SCALE_SCREENSHOT;
        int ah = (sourceHeight - top - bottom) / SCALE_SCREENSHOT;
        if (aw <= 0 || ah <= 0) {
            return screenshot;
        }
        Bitmap bm = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        Paint p = new Paint();
        p.setFilterBitmap(true);
        p.setAntiAlias(true);
        canvas.drawBitmap(
                screenshot,
                new Rect(left, top, sourceWidth - right, sourceHeight - bottom),
                new Rect(0, 0, aw, ah),
                p
        );
        screenshot.recycle();
        return bm;
    }
}
