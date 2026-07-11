package com.cashewteam.novatext.android.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

object DesktopShortcutPermission {
    const val GRANTED = 0
    const val DENIED = -1
    const val ASK = 1
    const val UNKNOWN = 2

    fun check(context: Context): Int = when (Build.MANUFACTURER.lowercase(Locale.ROOT)) {
        "xiaomi" -> checkMiui(context)
        "huawei" -> checkHuawei(context)
        "oppo" -> checkOppo(context)
        "vivo" -> checkVivo(context)
        else -> UNKNOWN
    }

    fun openSettings(context: Context) {
        val intent = when (Build.MANUFACTURER.lowercase(Locale.ROOT)) {
            "xiaomi" -> Intent("miui.intent.action.APP_PERM_EDITOR")
                .setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"))
                .putExtra("extra_pkgname", context.packageName)
            "huawei" -> Intent().setComponent(
                ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity"),
            )
            "oppo" -> Intent().setComponent(
                ComponentName("com.oppo.launcher", "com.oppo.launcher.shortcut.ShortcutSettingsActivity"),
            ).putExtra("packageName", context.packageName)
            "vivo" -> Intent().setComponent(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"),
            ).putExtra("packagename", context.packageName)
            else -> null
        }
        if (intent?.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun checkMiui(context: Context): Int = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = AppOpsManager::class.java
            .getDeclaredMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            .invoke(appOps, 10017, context.applicationInfo.uid, context.packageName) as Int
        when (mode) {
            0 -> GRANTED
            1 -> DENIED
            5 -> ASK
            else -> UNKNOWN
        }
    } catch (_: Exception) {
        UNKNOWN
    }

    private fun checkHuawei(context: Context): Int = try {
        val allowed = Class.forName("com.huawei.hsm.permission.PermissionManager")
            .getDeclaredMethod("canSendBroadcast", Context::class.java, Intent::class.java)
            .invoke(null, context, Intent(ACTION_INSTALL_SHORTCUT)) as Boolean
        if (allowed) GRANTED else DENIED
    } catch (_: Exception) {
        UNKNOWN
    }

    private fun checkOppo(context: Context): Int = try {
        context.contentResolver.query(OPPO_PERMISSION_URI, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(cursor.getColumnIndex("value")) ?: continue
                if (value.contains("${context.packageName}, 1")) return GRANTED
                if (value.contains("${context.packageName}, 0")) return DENIED
            }
        }
        UNKNOWN
    } catch (_: Exception) {
        UNKNOWN
    }

    private fun checkVivo(context: Context): Int = try {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        context.contentResolver.query(VIVO_PERMISSION_URI, null, null, null, null)?.use { cursor ->
            val titleIndex = cursor.getColumnIndex("title")
            val permissionIndex = cursor.getColumnIndex("shortcutPermission")
            while (cursor.moveToNext() && titleIndex >= 0 && permissionIndex >= 0) {
                if (cursor.getString(titleIndex) == appName) {
                    return when (cursor.getInt(permissionIndex)) {
                        16 -> GRANTED
                        1, 17 -> DENIED
                        18 -> ASK
                        else -> UNKNOWN
                    }
                }
            }
        }
        UNKNOWN
    } catch (_: Exception) {
        UNKNOWN
    }

    private const val ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT"
    private val OPPO_PERMISSION_URI = Uri.parse("content://settings/secure/launcher_shortcut_permission_settings")
    private val VIVO_PERMISSION_URI = Uri.parse("content://com.bbk.launcher2.settings/favorites")
}
