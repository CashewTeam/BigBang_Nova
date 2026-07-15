package com.cashewteam.novatext.extra.vector;

import android.content.SharedPreferences;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.cashewteam.novatext.extra.ExtraSettings;
import com.cashewteam.novatext.extra.TriggerPolicy;

import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

public final class VectorTouchModule extends XposedModule {
    public VectorTouchModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
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
            hook(onInputEvent, InputEventHooker.class);
        } catch (Throwable throwable) {
            log("NovaTextExtra: unable to hook input", throwable);
        }
    }

    @XposedHooker
    public static final class InputEventHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static void before(XposedInterface.BeforeHookCallback callback) {
            Object[] args = callback.getArgs();
            if (args.length > 0 && args[0] instanceof MotionEvent) {
                VectorTouchBridge.onMotionEvent((MotionEvent) args[0], callback.getThisObject());
            }
        }
    }
}
