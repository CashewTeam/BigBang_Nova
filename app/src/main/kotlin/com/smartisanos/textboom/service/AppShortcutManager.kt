package com.cashewteam.novatext.android.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cashewteam.novatext.android.OcrLaunchActivity
import com.cashewteam.novatext.android.R

object AppShortcutManager {
    @JvmStatic
    fun sync(context: Context) {
        ShortcutManagerCompat.removeDynamicShortcuts(
            context,
            listOf(OCR_SHORTCUT_ID, CLIPBOARD_SHORTCUT_ID, PREVIOUS_CLIPBOARD_SHORTCUT_ID),
        )
        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            listOf(
                ShortcutInfoCompat.Builder(context, OCR_SHORTCUT_ID)
                    .setShortLabel(context.getString(R.string.app_shortcut_ocr))
                    .setLongLabel(context.getString(R.string.app_shortcut_ocr_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_ocr))
                    .setActivity(ComponentName(context, OcrLaunchActivity::class.java))
                    .setIntent(BoomOcrLauncher.selectionCaptureIntent(context, 0).setAction(ACTION_OCR))
                    .build(),
                ShortcutInfoCompat.Builder(context, CLIPBOARD_SHORTCUT_ID)
                    .setShortLabel(context.getString(R.string.app_shortcut_clipboard))
                    .setLongLabel(context.getString(R.string.app_shortcut_clipboard_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_clipboard))
                    .setActivity(ComponentName(context, OcrLaunchActivity::class.java))
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
    private const val PREVIOUS_CLIPBOARD_SHORTCUT_ID = "app_shortcut_clipboard_v2"
    private const val ACTION_OCR = "com.cashewteam.novatext.android.action.APP_SHORTCUT_OCR"
    private const val ACTION_CLIPBOARD = "com.cashewteam.novatext.android.action.APP_SHORTCUT_CLIPBOARD"
}
