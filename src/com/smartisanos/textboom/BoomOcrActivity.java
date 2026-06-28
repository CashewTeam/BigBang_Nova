package com.cashewteam.novatext.android;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.cashewteam.novatext.android.data.BigBangSettings;
import com.cashewteam.novatext.android.service.BoomActivityLauncher;
import com.cashewteam.novatext.android.util.LogUtils;
import com.google.mlkit.vision.text.Text;

public class BoomOcrActivity extends Activity {

    public static final String EXTRA_OCR_IMAGE_URI = "ocr_image_uri";

    private static final String TAG = "BoomOcrActivity";
    private static final String PKG_GALLERY = "com.android.gallery3d";
    public static final int SCALE_SCREENSHOT = 2;

    private static BoomOcrActivity sSelf;

    static boolean sBoomCancel = false;

    private Toast mToastStop;
    private Handler mHandler;
    private FrameLayout mContentFrame;
    private Bitmap mPreparedBitmap;
    private String mOcrText;
    private float mTouchX;
    private float mTouchY;
    private boolean mFullscreen = false;
    private String mPackage;
    private int[] mOffset;
    private boolean mOcrStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        sSelf = this;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE || sBoomCancel) {
            finish();
            return;
        }
        mHandler = new Handler(getMainLooper());
        setContentView(R.layout.boom_ocr_layout);
        mContentFrame = findViewById(R.id.click_layout);
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
        mOffset = new int[] {
                getIntent().getIntExtra("boom_offsetx", 0),
                getIntent().getIntExtra("boom_offsety", 0)
        };

        prepareOcr();
        if (!isFinishing()) {
            startOcr();
        }
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        sBoomCancel = false;
        mOcrStarted = false;
        if (mToastStop != null) {
            mToastStop.cancel();
            mToastStop = null;
        }
        if (mPreparedBitmap != null && !mPreparedBitmap.isRecycled()) {
            mPreparedBitmap.recycle();
            mPreparedBitmap = null;
        }
        if (sSelf == this) {
            sSelf = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        stopOcr();
    }

    public static BoomOcrActivity getInstance() {
        return sSelf;
    }

    public void post(Runnable run) {
        if (mHandler != null && run != null) {
            mHandler.post(run);
        }
    }

    public void cancelOcr() {
        LogUtils.e(TAG, "cancel ocr");
        stopOcr();
    }

    private float readTouchCoordinate(String extraName, boolean horizontal) {
        if (getIntent().hasExtra(extraName)) {
            return getIntent().getIntExtra(extraName, 0);
        }
        return horizontal
                ? getResources().getDisplayMetrics().widthPixels / 2f
                : getResources().getDisplayMetrics().heightPixels / 2f;
    }

    private void startOcr() {
        if (mPreparedBitmap == null) {
            showImageUnavailableAndFinish();
            return;
        }
        mOcrStarted = true;
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
        String text = result == null ? "" : result.getText();
        mOcrText = text == null ? "" : text.trim();
        if (mOcrText.isEmpty()) {
            Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
            stopOcr();
            return;
        }
        BoomActivityLauncher.openText(this, mOcrText, (int) mTouchX, (int) mTouchY, false, false);
        mOcrStarted = false;
        finish();
    }

    private void stopOcr() {
        LogUtils.e(TAG, "stop ocr");
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
        return TextUtils.isEmpty(value) ? null : Uri.parse(value);
    }

    private boolean shouldAdjustScreenshot() {
        return !TextUtils.isEmpty(mPackage) || mOffset[0] != 0 || mOffset[1] != 0;
    }

    private void showImageUnavailableAndFinish() {
        Toast.makeText(this, R.string.ocr_image_unavailable, Toast.LENGTH_SHORT).show();
        stopOcr();
    }

    private Bitmap adjustScreenshotFor(Bitmap screenshot) {
        int w = getResources().getInteger(R.integer.screen_width);
        int h = getResources().getInteger(R.integer.screen_height);
        int statusBarHeight = getResources().getInteger(R.integer.status_bar_height);
        int top = statusBarHeight;
        int bottom = 0;
        int left = 0;
        int right = 0;
        if (mOffset[0] == 0 && mOffset[1] == 0) {
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
            if (mOffset[0] == 0) {
                right = sidew;
            } else {
                left = sidew;
            }
        }
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
        Bitmap bitmap = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        paint.setAntiAlias(true);
        canvas.drawBitmap(
                screenshot,
                new Rect(left, top, sourceWidth - right, sourceHeight - bottom),
                new Rect(0, 0, aw, ah),
                paint
        );
        screenshot.recycle();
        return bitmap;
    }
}
