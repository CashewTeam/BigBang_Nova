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

import com.smartisanos.textboom.network.OkHttpClientManager;
import com.smartisanos.textboom.util.Constant;
import com.smartisanos.textboom.util.LogUtils;
import com.smartisanos.textboom.util.Utils;
import com.smartisanos.textboom.data.BigBangSettings;
import com.squareup.okhttp.Request;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

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
        if (previewText != null) {
            segmentPreview(previewText);
            return;
        }

        if (Utils.isNetworkAvailable(this)) {
            segmentOnline();
        } else {
            finish();
            Toast.makeText(getApplicationContext(), R.string.network_disconnected, Toast.LENGTH_SHORT).show();
        }
    }

    private void segmentOnline() {
        final String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            if (DBG) {
                Log.d(TAG, "text=" + text);
            }

            boolean ocr = (getIntent().getStringExtra("boom_image") != null);
            Map<String, String> params = new HashMap<>(2);
            params.put("words", text);
            params.put("filter", ocr ? "1" : "0");
            OkHttpClientManager.getInstance().post(Constant.SEGMENT_URL, params,
                    new OkHttpClientManager.ResultCallback() {
                @Override
                public void onError(Request request, Exception e) {
                    e.printStackTrace();
                    finish();
                }

                @Override
                public void onResponse(String response) {
                    if (response != null) {
                        try {
                            JSONObject json = new JSONObject(response);
                            int code = json.getInt("code");
                            if (code != 0) {
                                String msg = json.getString("msg");
                                LogUtils.d(TAG, msg);
                                Toast.makeText(BoomActivity.this, msg, Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                            JSONArray array = json.getJSONArray("list");
                            if (array != null) {
                                int[] result = new int[array.length()];
                                for (int i = 0; i < array.length(); i++) {
                                    result[i] = array.getInt(i);
                                }
                                handleSegmentResult(text, result);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    private void handleSegmentResult(String text, int[] result) {
        if (result == null) {
            Log.e(TAG, "Segmentation fails for text=" + text);
            result = new int[2];
            result[0] = 0;
            result[1] = text.length() - 1;
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

    private void segmentPreview(String text) {
        int[] result = buildPreviewSegments(text);
        handleSegmentResult(text, result);
    }

    private int[] buildPreviewSegments(String text) {
        java.util.ArrayList<Integer> words = new java.util.ArrayList<Integer>();
        java.util.ArrayList<Integer> punctuations = new java.util.ArrayList<Integer>();

        int index = 0;
        while (index < text.length()) {
            int start = index;
            int codePoint = text.codePointAt(index);
            if (Character.isWhitespace(codePoint)) {
                index += Character.charCount(codePoint);
                continue;
            }
            boolean asciiWord = isAsciiWord(codePoint);
            boolean chinese = isChineseWord(codePoint);
            if (asciiWord) {
                index += Character.charCount(codePoint);
                while (index < text.length()) {
                    int next = text.codePointAt(index);
                    if (!isAsciiWord(next)) {
                        break;
                    }
                    index += Character.charCount(next);
                }
                words.add(start);
                words.add(index - 1);
            } else if (chinese) {
                index += Character.charCount(codePoint);
                words.add(start);
                words.add(index - 1);
            } else {
                index += Character.charCount(codePoint);
                punctuations.add(start);
                punctuations.add(index - 1);
            }
        }

        int[] result = new int[words.size() + punctuations.size() + 1];
        int offset = 0;
        for (Integer value : words) {
            result[offset++] = value;
        }
        result[offset++] = -1;
        for (Integer value : punctuations) {
            result[offset++] = value;
        }
        return result;
    }

    private boolean isAsciiWord(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == '_';
    }

    private boolean isChineseWord(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
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
