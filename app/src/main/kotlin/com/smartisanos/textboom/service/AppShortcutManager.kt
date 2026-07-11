package com.cashewteam.novatext.android.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cashewteam.novatext.android.OcrLaunchActivity
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.data.BigBangSettings

object AppShortcutManager {
    @JvmStatic
    fun sync(context: Context) {
        val launcherActivity = ComponentName(
            context,
            if (BigBangSettings.get(context).isAdaptiveLauncherIconEnabled) {
                ADAPTIVE_LAUNCHER_ALIAS
            } else {
                LEGACY_LAUNCHER_ALIAS
            },
        )
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            listOf(
                ShortcutInfoCompat.Builder(context, OCR_SHORTCUT_ID)
                    .setShortLabel(context.getString(R.string.app_shortcut_ocr))
                    .setLongLabel(context.getString(R.string.app_shortcut_ocr_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_ocr))
                    .setActivity(launcherActivity)
                    .setIntent(BoomOcrLauncher.selectionCaptureIntent(context, 0).setAction(ACTION_OCR))
                    .build(),
                ShortcutInfoCompat.Builder(context, CLIPBOARD_SHORTCUT_ID)
                    .setShortLabel(context.getString(R.string.app_shortcut_clipboard))
                    .setLongLabel(context.getString(R.string.app_shortcut_clipboard_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_clipboard))
                    .setActivity(launcherActivity)
                    .setIntent(
                        Intent(context, OcrLaunchActivity::class.java)
                            .setAction(ACTION_CLIPBOARD)
                            .putExtra(OcrLaunchActivity.EXTRA_PROCESS_CLIPBOARD_TEXT, true)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                            ),
                    )
                    .build(),
            ),
        )
    }

    private const val OCR_SHORTCUT_ID = "app_shortcut_ocr"
    private const val CLIPBOARD_SHORTCUT_ID = "app_shortcut_clipboard_v3"
    private const val LEGACY_LAUNCHER_ALIAS = "com.cashewteam.novatext.android.LauncherLegacyAlias"
    private const val ADAPTIVE_LAUNCHER_ALIAS = "com.cashewteam.novatext.android.LauncherAdaptiveAlias"
    private const val ACTION_OCR = "com.cashewteam.novatext.android.action.APP_SHORTCUT_OCR"
    private const val ACTION_CLIPBOARD = "com.cashewteam.novatext.android.action.APP_SHORTCUT_CLIPBOARD"
}
