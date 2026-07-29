package com.cashewteam.novatext.extra.vector

import android.content.Context
import android.content.ContentResolver
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.cashewteam.novatext.extra.ExtraSettings

object NovaTextAccessibilityBridge {
    const val SERVICE_COMPONENT =
        "com.cashewteam.novatext.android/com.cashewteam.novatext.android.service.NovaTextAccessibilityService"
    private const val SERVICE_SHORT_COMPONENT =
        "com.cashewteam.novatext.android/.service.NovaTextAccessibilityService"

    private var attachedPreferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @JvmStatic
    fun attachSystemServer(preferences: SharedPreferences) {
        if (attachedPreferences === preferences) return
        attachedPreferences?.let { current -> preferenceListener?.let(current::unregisterOnSharedPreferenceChangeListener) }
        attachedPreferences = preferences
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == ExtraSettings.KEY_AUTO_ENABLE_NOVA_TEXT_ACCESSIBILITY) enableIfRequested(changed)
        }
        preferences.registerOnSharedPreferenceChangeListener(requireNotNull(preferenceListener))
        enableIfRequested(preferences)
    }

    @JvmStatic
    fun enabledServicesWithNovaText(current: String?): String {
        val services = LinkedHashSet(current.orEmpty().split(':').filter(String::isNotBlank))
        if (services.none(::isNovaTextService)) services.add(SERVICE_COMPONENT)
        return services.joinToString(":")
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty().split(':').any(::isNovaTextService)

    private fun enableIfRequested(preferences: SharedPreferences) {
        if (!preferences.getBoolean(ExtraSettings.KEY_AUTO_ENABLE_NOVA_TEXT_ACCESSIBILITY, false)) return
        runCatching {
            val context = systemContext() ?: error("system context unavailable")
            val resolver = context.contentResolver
            val enabled = getSecureStringForCurrentUser(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val updated = enabledServicesWithNovaText(enabled)
            putSecureStringForCurrentUser(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated,
            )
            putSecureStringForCurrentUser(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1",
            )
            Log.i(TAG, "Nova Text accessibility service enabled")
        }.onFailure { Log.e(TAG, "Unable to enable Nova Text accessibility service", it) }
    }

    private fun systemContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentActivityThread")
            .invoke(null)
            ?: return null
        activityThread.javaClass.getDeclaredMethod("getSystemContext")
            .apply { isAccessible = true }
            .invoke(activityThread) as? Context
    }.getOrNull()

    private fun getSecureStringForCurrentUser(resolver: ContentResolver, key: String): String? =
        Settings.Secure::class.java.getDeclaredMethod(
            "getStringForUser",
            ContentResolver::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        ).invoke(null, resolver, key, currentUserId()) as? String

    private fun putSecureStringForCurrentUser(resolver: ContentResolver, key: String, value: String) {
        Settings.Secure::class.java.getDeclaredMethod(
            "putStringForUser",
            ContentResolver::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        ).invoke(null, resolver, key, value, currentUserId())
    }

    private fun currentUserId(): Int = Class.forName("android.app.ActivityManager")
        .getDeclaredMethod("getCurrentUser")
        .invoke(null) as Int

    private fun isNovaTextService(component: String): Boolean =
        component.equals(SERVICE_COMPONENT, ignoreCase = true) ||
            component.equals(SERVICE_SHORT_COMPONENT, ignoreCase = true)

    private const val TAG = "NovaExtraA11y"
}
