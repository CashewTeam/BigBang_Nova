package com.cashewteam.novatext.extra.vector;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.cashewteam.novatext.extra.ExtraSettings;
import com.cashewteam.novatext.extra.TriggerPolicy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class VectorTouchModule extends XposedModule {
    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!param.isFirstPackage() || TriggerPolicy.isExcludedPackage(param.getPackageName())) {
            return;
        }
        try {
            SharedPreferences preferences = getRemotePreferences(ExtraSettings.GROUP);
            VectorTouchBridge.attach(preferences);
            Class<?> receiver = Class.forName("android.view.ViewRootImpl$WindowInputEventReceiver");
            Method onInputEvent = Arrays.stream(receiver.getDeclaredMethods())
                    .filter(method -> method.getName().equals("onInputEvent"))
                    .filter(method -> method.getParameterCount() > 0)
                    .filter(method -> InputEvent.class.isAssignableFrom(method.getParameterTypes()[0]))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("WindowInputEventReceiver.onInputEvent"));
            hook(onInputEvent)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        if (!args.isEmpty() && args.get(0) instanceof MotionEvent) {
                            VectorTouchBridge.onMotionEvent((MotionEvent) args.get(0), chain.getThisObject());
                        }
                        return chain.proceed();
                    });
        } catch (Throwable throwable) {
            log(Log.ERROR, "NovaTextExtra", "unable to hook input", throwable);
        }
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        try {
            SharedPreferences preferences = getRemotePreferences(ExtraSettings.GROUP);
            NovaTextAccessibilityBridge.attachSystemServer(preferences);
        } catch (Throwable throwable) {
            log(Log.ERROR, "NovaTextExtra", "unable to configure Nova Text accessibility", throwable);
        }
    }
}
