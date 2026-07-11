package com.cashewteam.novatext.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cashewteam.novatext.android.BuildConfig;
import com.cashewteam.novatext.android.data.BigBangSettings;

public class LogUtils {
    public static final boolean DEBUG = BuildConfig.DEBUG;
    public static final String TAG = "TextBoom";
    private static SharedPreferences sSettings;

    public static void init(Context context) {
        sSettings = context.getApplicationContext().getSharedPreferences(
                BigBangSettings.PREF_NAME, Context.MODE_PRIVATE);
    }

    private static boolean isDebugEnabled() {
        return DEBUG && sSettings != null && sSettings.getBoolean(BigBangSettings.KEY_DEBUG_MODE, false);
    }

    public static void d(String tag, String msg) {
        if (isDebugEnabled())
            Log.d(tag, msg);
    }

    public static void d(String msg) {
        if (isDebugEnabled())
            Log.d(TAG, msg);
    }

    public static void i(String tag, String msg) {
        if (isDebugEnabled())
            Log.i(tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void i(String msg) {
        if (isDebugEnabled())
            Log.i(TAG, msg);
    }

    public static void v(String tag, String msg) {
        if (isDebugEnabled())
            Log.v(tag, msg);
    }

    public static void v(String msg) {
        if (isDebugEnabled())
            Log.v(TAG, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }
}
