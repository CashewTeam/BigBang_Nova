package com.smartisanos.textboom;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.smartisanos.textboom.data.CppJiebaTokenizer;
import com.smartisanos.textboom.util.LogUtils;

public class BoomActivity extends Activity {

    public final static boolean DBG = true;
    public static final String EXTRA_DEBUG_PREVIEW_TEXT = "extra_debug_preview_text";
    private final static String TAG = "BoomActivity";
    private final static String SELECTED_STATE = "selected_state";

    private BoomChipPage mBoomChipPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View contentView = getLayoutInflater().inflate(R.layout.boom_activity_layout, null);
        View boom_page = contentView.findViewById(R.id.boom_page);
        boom_page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        BoomAnimator.makeFadeIn(boom_page, BoomAnimator.BOOM_DURATION);
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBoomChipPage.handleClick()) {
                    finish();
                }
            }
        });
        mBoomChipPage = new BoomChipPage(this, contentView);

        setContentView(contentView);
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        lp.width = dm.widthPixels;
        lp.height = dm.heightPixels;
        window.setAttributes(lp);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        final String previewText = getIntent().getStringExtra(EXTRA_DEBUG_PREVIEW_TEXT);
        final String inputText = previewText != null
                ? previewText
                : getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (inputText == null || inputText.length() == 0) {
            finish();
            return;
        }
        segmentLocally(inputText);
    }

    private void segmentLocally(final String text) {
        if (DBG) {
            Log.d(TAG, "text=" + text);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int[] result = CppJiebaTokenizer.get(BoomActivity.this).segment(text);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                handleSegmentResult(text, result);
                            }
                        }
                    });
                } catch (final RuntimeException e) {
                    LogUtils.e(TAG, "local segmentation failed");
                    LogUtils.e(e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }
                    );
                }
            }
        }).start();
    }

    private void handleSegmentResult(String text, int[] result) {
        if (result == null || result.length == 0) {
            Log.e(TAG, "Segmentation fails for text=" + text);
            finish();
            return;
        }
        int touchIndex = getIntent().getIntExtra("boom_index", -1);
        int touchedX = getIntent().getIntExtra("boom_startx", -1);
        int touchedY = getIntent().getIntExtra("boom_starty", -1);
        if (!mBoomChipPage.initWords(result, text, touchIndex, touchedX, touchedY)) {
            StringBuilder log = new StringBuilder();
            for (int i = 0; i < result.length; ++i) {
                log.append(result[i] + ", ");
            }
            Log.w(TAG, "No words left after segment, input=" + text + ", output=" + log);
            boolean image = (getIntent().getStringExtra("boom_image") != null);
            if (image) {
                Toast.makeText(BoomActivity.this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mBoomChipPage != null && mBoomChipPage.mBoomActionHandler.hasSelection()) {
            outState.putSerializable(SELECTED_STATE,
                    mBoomChipPage.mBoomActionHandler.mSelectedId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (mBoomChipPage != null) {
            mBoomChipPage.mSavedData = savedInstanceState.getSerializable(SELECTED_STATE);
        }
    }

}
