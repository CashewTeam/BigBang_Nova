package com.smartisanos.textboom;

import android.app.Application;

import com.smartisanos.textboom.data.CppJiebaTokenizer;
import com.smartisanos.textboom.util.ConfigUtils;
import com.smartisanos.textboom.util.LogUtils;

public class BoomApplication extends Application {
    private static final String TAG = "BoomApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        ConfigUtils.init(this);
        warmUpTokenizer();
    }

    private void warmUpTokenizer() {
        final Application app = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CppJiebaTokenizer.get(app).warmUp();
                    LogUtils.d(TAG, "cppjieba warm-up complete");
                } catch (RuntimeException e) {
                    LogUtils.e(TAG, "cppjieba warm-up failed");
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }, "cppjieba-warmup").start();
    }
}
