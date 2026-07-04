package com.cashewteam.novatext.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.cashewteam.novatext.android.data.BigBangSettings;

public final class LauncherIconManager {
    private static final String LEGACY_ALIAS =
            "com.cashewteam.novatext.android.LauncherLegacyAlias";
    private static final String ADAPTIVE_ALIAS =
            "com.cashewteam.novatext.android.LauncherAdaptiveAlias";

    private LauncherIconManager() {
    }

    public static void sync(Context context) {
        apply(context, BigBangSettings.get(context).isAdaptiveLauncherIconEnabled());
    }

    public static void setAdaptiveEnabled(Context context, boolean enabled) {
        BigBangSettings.get(context).setAdaptiveLauncherIconEnabled(enabled);
        apply(context, enabled);
    }

    private static void apply(Context context, boolean adaptiveEnabled) {
        PackageManager packageManager = context.getPackageManager();
        setComponentEnabled(packageManager, new ComponentName(context, LEGACY_ALIAS), !adaptiveEnabled);
        setComponentEnabled(packageManager, new ComponentName(context, ADAPTIVE_ALIAS), adaptiveEnabled);
    }

    private static void setComponentEnabled(
            PackageManager packageManager,
            ComponentName componentName,
            boolean enabled
    ) {
        packageManager.setComponentEnabledSetting(
                componentName,
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }
}
