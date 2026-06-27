package com.smartisanos.textboom.data;

import android.content.Context;

import com.smartisanos.textboom.BuildConfig;
import com.smartisanos.textboom.util.Utils;

import java.io.File;
import java.util.ArrayList;

public final class CppJiebaTokenizer {
    private static final String DICT_DIR = "dict";
    private static final String[] REQUIRED_DICT_FILES = {
            "jieba.dict.utf8",
            "hmm_model.utf8",
            "user.dict.utf8"
    };

    private static volatile CppJiebaTokenizer sInstance;

    static {
        System.loadLibrary("textboom_jieba");
    }

    private final Context appContext;
    private boolean initialized;

    private CppJiebaTokenizer(Context context) {
        appContext = context.getApplicationContext();
    }

    public static CppJiebaTokenizer get(Context context) {
        if (sInstance == null) {
            synchronized (CppJiebaTokenizer.class) {
                if (sInstance == null) {
                    sInstance = new CppJiebaTokenizer(context);
                }
            }
        }
        return sInstance;
    }

    public synchronized int[] segment(String text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        ensureInitialized();
        int[] tokenSpans = nativeCut(text);
        if (tokenSpans == null || tokenSpans.length == 0) {
            return null;
        }
        return buildSegments(text, tokenSpans);
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        File dictDir = ensureDictDirectory();
        if (!nativeInit(dictDir.getAbsolutePath())) {
            throw new IllegalStateException("cppjieba init failed");
        }
        initialized = true;
        if (BuildConfig.DEBUG) {
            selfCheck();
        }
    }

    private File ensureDictDirectory() {
        File dictDir = new File(appContext.getFilesDir(), DICT_DIR);
        if (hasRequiredFiles(dictDir)) {
            return dictDir;
        }
        // ponytail: copy-on-miss is enough until dict versioning actually matters.
        Utils.copyFileOrDir(appContext.getAssets(), DICT_DIR,
                appContext.getFilesDir().getAbsolutePath() + "/");
        if (!hasRequiredFiles(dictDir)) {
            throw new IllegalStateException("cppjieba dict files missing");
        }
        return dictDir;
    }

    private boolean hasRequiredFiles(File dictDir) {
        if (!dictDir.isDirectory()) {
            return false;
        }
        for (String fileName : REQUIRED_DICT_FILES) {
            if (!new File(dictDir, fileName).isFile()) {
                return false;
            }
        }
        return true;
    }

    private void selfCheck() {
        int[] tokenSpans = nativeCut("我来到北京清华大学");
        if (tokenSpans == null || tokenSpans.length < 2 || (tokenSpans.length % 2) != 0) {
            throw new IllegalStateException("cppjieba self-check failed");
        }
    }

    private int[] buildSegments(String text, int[] tokenSpans) {
        ArrayList<Integer> words = new ArrayList<Integer>();
        ArrayList<Integer> punctuations = new ArrayList<Integer>();
        int cursor = 0;
        for (int i = 0; i < tokenSpans.length; i += 2) {
            int start = tokenSpans[i];
            int endInclusive = tokenSpans[i + 1];
            if (start < cursor || endInclusive < start || endInclusive >= text.length()) {
                continue;
            }
            appendGapPunctuation(text, cursor, start, punctuations);
            if (hasWordCodePoint(text, start, endInclusive + 1)) {
                words.add(start);
                words.add(endInclusive);
            } else {
                appendPunctuation(text, start, endInclusive + 1, punctuations);
            }
            cursor = endInclusive + 1;
        }
        appendGapPunctuation(text, cursor, text.length(), punctuations);
        if (words.isEmpty() && punctuations.isEmpty()) {
            return null;
        }
        int[] result = new int[words.size() + punctuations.size() + 1];
        int index = 0;
        for (Integer value : words) {
            result[index++] = value;
        }
        result[index++] = -1;
        for (Integer value : punctuations) {
            result[index++] = value;
        }
        return result;
    }

    private void appendGapPunctuation(String text, int start, int end, ArrayList<Integer> out) {
        if (start < end) {
            appendPunctuation(text, start, end, out);
        }
    }

    private void appendPunctuation(String text, int start, int end, ArrayList<Integer> out) {
        int index = start;
        while (index < end) {
            int codePoint = text.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                out.add(index);
                out.add(next - 1);
            }
            index = next;
        }
    }

    private boolean hasWordCodePoint(String text, int start, int end) {
        int index = start;
        while (index < end) {
            int codePoint = text.codePointAt(index);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static native boolean nativeInit(String dictDir);
    private static native int[] nativeCut(String text);
}
