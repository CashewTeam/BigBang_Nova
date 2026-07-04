package com.cashewteam.novatext.android;

import android.app.Application;
import android.os.Process;

import com.cashewteam.novatext.android.data.CppJiebaTokenizer;
import com.cashewteam.novatext.android.data.JiebaWarmUpTracker;
import com.cashewteam.novatext.android.util.ConfigUtils;
import com.cashewteam.novatext.android.util.LogUtils;
import com.cashewteam.novatext.android.LauncherIconManager;

public class BoomApplication extends Application {
    private static final String TAG = "BoomApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        ConfigUtils.init(this);
        LauncherIconManager.sync(this);
        warmUpTokenizer();
    }

    private void warmUpTokenizer() {
        final Application app = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                JiebaWarmUpTracker.markRunning();
                try {
                    CppJiebaTokenizer.get(app).warmUp();
                    JiebaWarmUpTracker.markReady();
                    LogUtils.d(TAG, "cppjieba warm-up complete");
                } catch (RuntimeException e) {
                    JiebaWarmUpTracker.markFailed();
                    LogUtils.e(TAG, "cppjieba warm-up failed");
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }, "cppjieba-warmup").start();
    }
}
