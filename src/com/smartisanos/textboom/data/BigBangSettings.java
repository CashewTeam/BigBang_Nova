package com.cashewteam.novatext.android.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class BigBangSettings {
    public static final String PREF_NAME = "bigbang_settings";

    public static final String KEY_WEB_SEARCH_TYPE = "web_search_type";
    public static final String KEY_DICT_SEARCH_TYPE = "dict_search_type";
    public static final String KEY_WIKI_SEARCH_TYPE = "wiki_search_type";
    public static final String KEY_BIG_BANG_ENABLED = "big_bang_enabled";
    public static final String KEY_OCR_ENABLED = "ocr_enabled";
    public static final String KEY_TRIGGER_AREA = "trigger_area";
    public static final String KEY_DEBUG_PRESET_TEXT = "debug_preset_text";
    public static final String KEY_DEBUG_PREVIEW_TEXT = "debug_preview_text";
    public static final String KEY_DEBUG_SKIP_ACCESSIBILITY = "debug_skip_accessibility";
    public static final String KEY_USE_SHIZUKU_SCREENSHOT = "use_shizuku_screenshot";
    public static final String KEY_DEBUG_MODE = "debug_mode";
    public static final String KEY_BACKGROUND_POPUP_GUIDE_OS = "background_popup_guide_os";
    public static final String KEY_OCR_RECOGNIZER_MODE = "ocr_recognizer_mode";
    public static final String KEY_OCR_WHITELIST_PACKAGES = "ocr_whitelist_packages";
    public static final String KEY_OCR_SELECTION_CAPTURE_DELAY_MS = "ocr_selection_capture_delay_ms";
    public static final String KEY_FLOATING_BALL_SIZE_PERCENT = "floating_ball_size_percent";
    public static final String KEY_FLOATING_BALL_ACTIVE_ALPHA_PERCENT = "floating_ball_active_alpha_percent";
    public static final String KEY_FLOATING_BALL_IDLE_ALPHA_PERCENT = "floating_ball_idle_alpha_percent";
    public static final String KEY_FLOATING_BALL_HEIGHT_LOCKED = "floating_ball_height_locked";
    public static final String KEY_FLOATING_BALL_SIDE_LOCKED = "floating_ball_side_locked";
    public static final String KEY_FLOATING_BALL_ONE_HAND_MODE = "floating_ball_one_hand_mode";
    public static final String KEY_FLOATING_BALL_ONE_HAND_ANGLE_DEGREES = "floating_ball_one_hand_angle_degrees";
    public static final String KEY_FLOATING_BALL_HIDDEN = "floating_ball_hidden";
    public static final String KEY_FLOATING_BALL_LANDSCAPE_SAFE_AREA = "floating_ball_landscape_safe_area";
    public static final String KEY_TOUCH_EVENT_ENABLE = "touch_event_enable";
    public static final String KEY_TOUCH_EVENT_PRS_PERCENT = "touch_event_prs_percent";
    public static final String KEY_TOUCH_EVENT_SIZE_PERCENT = "touch_event_size_percent";
    public static final String KEY_ADAPTIVE_LAUNCHER_ICON = "adaptive_launcher_icon";
    public static final String KEY_CLASSIC_OVERLAY_STYLE = "classic_overlay_style";
    public static final String KEY_GAP_ROW_HEIGHT_PERCENT = "gap_row_height_percent";
    public static final String KEY_CUSTOM_SEARCH_PROVIDERS = "custom_search_providers";
    public static final String KEY_NEXT_CUSTOM_SEARCH_TYPE = "next_custom_search_type";

    public static final int TYPE_BAIDU = 0x000;
    public static final int TYPE_GOOGLE = 0x001;
    public static final int TYPE_BING = 0x002;
    public static final int TYPE_SHENMA = 0x003;
    public static final int TYPE_WIKI = 0x010;
    public static final int TYPE_BAIKE = 0x011;
    public static final int TYPE_WIKIPEDIA = 0x012;
    public static final int TYPE_MOEGIRL = 0x013;
    public static final int TYPE_YOUDAO = 0x100;
    public static final int TYPE_KINGSOFT = 0x101;
    public static final int TYPE_BINGDICT = 0x102;
    public static final int TYPE_HIDICT = 0x103;
    public static final int TYPE_BAIDU_TRANSLATE = 0x104;
    public static final int TYPE_BING_TRANSLATE = 0x105;
    public static final int TYPE_GOOGLE_TRANSLATE = 0x106;
    public static final int FIRST_CUSTOM_SEARCH_TYPE = 0x1000;

    public static final int TRIGGER_AREA_SMALLEST = 0;
    public static final int TRIGGER_AREA_SMALL = 1;
    public static final int TRIGGER_AREA_MIDDLE = 2;
    public static final int TRIGGER_AREA_LARGE = 3;
    public static final int TRIGGER_AREA_LARGEST = 4;

    public static final String OCR_MODE_CHINESE = "chinese";
    public static final String OCR_MODE_JAPANESE = "japanese";
    public static final String OCR_MODE_KOREAN = "korean";
    public static final String OCR_MODE_LATIN = "latin";

    private static final String DEFAULT_PRESET_TEXT =
            "BigBang Nova lets you preview word chips before wiring the full capture flow.";

    private static final int DEFAULT_WEB_SEARCH_TYPE = TYPE_BING;
    private static final int DEFAULT_DICT_SEARCH_TYPE = TYPE_BINGDICT;
    private static final int DEFAULT_WIKI_SEARCH_TYPE = TYPE_WIKI;
    private static final String DEFAULT_OCR_RECOGNIZER_MODE = OCR_MODE_CHINESE;
    private static final int DEFAULT_OCR_SELECTION_CAPTURE_DELAY_MS = 250;
    private static final int DEFAULT_FLOATING_BALL_SIZE_PERCENT = 75;
    private static final int DEFAULT_FLOATING_BALL_ACTIVE_ALPHA_PERCENT = 80;
    private static final int DEFAULT_FLOATING_BALL_IDLE_ALPHA_PERCENT = 20;
    private static final int DEFAULT_FLOATING_BALL_ONE_HAND_ANGLE_DEGREES = 18;
    private static final int DEFAULT_TOUCH_EVENT_PRS_PERCENT = 50;
    private static final int DEFAULT_TOUCH_EVENT_SIZE_PERCENT = 50;
    private static final int DEFAULT_GAP_ROW_HEIGHT_PERCENT = 15;

    private final SharedPreferences preferences;

    private BigBangSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static BigBangSettings get(Context context) {
        return new BigBangSettings(context);
    }

    public int getWebSearchType() {
        return preferences.getInt(KEY_WEB_SEARCH_TYPE, DEFAULT_WEB_SEARCH_TYPE);
    }

    public void setWebSearchType(int value) {
        preferences.edit().putInt(KEY_WEB_SEARCH_TYPE, value).apply();
    }

    public int getDictSearchType() {
        return preferences.getInt(KEY_DICT_SEARCH_TYPE, DEFAULT_DICT_SEARCH_TYPE);
    }

    public void setDictSearchType(int value) {
        preferences.edit().putInt(KEY_DICT_SEARCH_TYPE, value).apply();
    }

    public int getWikiSearchType() {
        return preferences.getInt(KEY_WIKI_SEARCH_TYPE, DEFAULT_WIKI_SEARCH_TYPE);
    }

    public void setWikiSearchType(int value) {
        preferences.edit().putInt(KEY_WIKI_SEARCH_TYPE, value).apply();
    }

    public boolean isBigBangEnabled() {
        return preferences.getBoolean(KEY_BIG_BANG_ENABLED, true);
    }

    public void setBigBangEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_BIG_BANG_ENABLED, enabled).apply();
    }

    public boolean isOcrEnabled() {
        return preferences.getBoolean(KEY_OCR_ENABLED, false);
    }

    public void setOcrEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_OCR_ENABLED, enabled).apply();
    }

    public int getTriggerArea() {
        return preferences.getInt(KEY_TRIGGER_AREA, TRIGGER_AREA_MIDDLE);
    }

    public void setTriggerArea(int value) {
        preferences.edit().putInt(KEY_TRIGGER_AREA, value).apply();
    }

    public String getDebugPresetText() {
        return preferences.getString(KEY_DEBUG_PRESET_TEXT, DEFAULT_PRESET_TEXT);
    }

    public void setDebugPresetText(String text) {
        preferences.edit().putString(KEY_DEBUG_PRESET_TEXT, text).apply();
    }

    public String getDebugPreviewText() {
        return preferences.getString(KEY_DEBUG_PREVIEW_TEXT, getDebugPresetText());
    }

    public void setDebugPreviewText(String text) {
        preferences.edit().putString(KEY_DEBUG_PREVIEW_TEXT, text).apply();
    }

    public boolean isDebugSkipAccessibilityEnabled() {
        return isDebugModeEnabled() && preferences.getBoolean(KEY_DEBUG_SKIP_ACCESSIBILITY, false);
    }

    public void setDebugSkipAccessibilityEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEBUG_SKIP_ACCESSIBILITY, enabled).apply();
    }

    public boolean getDebugSkipAccessibilitySetting() {
        return preferences.getBoolean(KEY_DEBUG_SKIP_ACCESSIBILITY, false);
    }

    public boolean isUseShizukuScreenshotEnabled() {
        return preferences.getBoolean(KEY_USE_SHIZUKU_SCREENSHOT, false);
    }

    public void setUseShizukuScreenshotEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_USE_SHIZUKU_SCREENSHOT, enabled).apply();
    }

    public boolean isDebugModeEnabled() {
        return preferences.getBoolean(KEY_DEBUG_MODE, false);
    }

    public void setDebugModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply();
    }

    public String getBackgroundPopupGuideOs() {
        return preferences.getString(KEY_BACKGROUND_POPUP_GUIDE_OS, "");
    }

    public void setBackgroundPopupGuideOs(String os) {
        preferences.edit().putString(KEY_BACKGROUND_POPUP_GUIDE_OS, os).apply();
    }

    public Set<String> getOcrWhitelistPackages() {
        Set<String> stored = preferences.getStringSet(
                KEY_OCR_WHITELIST_PACKAGES,
                defaultOcrWhitelistPackages()
        );
        return new HashSet<>(stored);
    }

    public void setOcrWhitelistPackages(Set<String> packages) {
        preferences.edit()
                .putStringSet(KEY_OCR_WHITELIST_PACKAGES, new HashSet<>(packages))
                .apply();
    }

    public String getOcrRecognizerMode() {
        return normalizeOcrRecognizerMode(
                preferences.getString(KEY_OCR_RECOGNIZER_MODE, DEFAULT_OCR_RECOGNIZER_MODE)
        );
    }

    public void setOcrRecognizerMode(String value) {
        preferences.edit()
                .putString(KEY_OCR_RECOGNIZER_MODE, normalizeOcrRecognizerMode(value))
                .apply();
    }

    public int getOcrSelectionCaptureDelayMs() {
        return clampOcrSelectionCaptureDelay(preferences.getInt(
                KEY_OCR_SELECTION_CAPTURE_DELAY_MS,
                DEFAULT_OCR_SELECTION_CAPTURE_DELAY_MS
        ));
    }

    public void setOcrSelectionCaptureDelayMs(int value) {
        preferences.edit()
                .putInt(KEY_OCR_SELECTION_CAPTURE_DELAY_MS, clampOcrSelectionCaptureDelay(value))
                .apply();
    }

    public int getFloatingBallSizePercent() {
        return clampPercent(preferences.getInt(
                KEY_FLOATING_BALL_SIZE_PERCENT,
                DEFAULT_FLOATING_BALL_SIZE_PERCENT
        ));
    }

    public void setFloatingBallSizePercent(int value) {
        preferences.edit().putInt(KEY_FLOATING_BALL_SIZE_PERCENT, clampPercent(value)).apply();
    }

    public int getFloatingBallActiveAlphaPercent() {
        return clampPercent(preferences.getInt(
                KEY_FLOATING_BALL_ACTIVE_ALPHA_PERCENT,
                DEFAULT_FLOATING_BALL_ACTIVE_ALPHA_PERCENT
        ));
    }

    public void setFloatingBallActiveAlphaPercent(int value) {
        preferences.edit().putInt(KEY_FLOATING_BALL_ACTIVE_ALPHA_PERCENT, clampPercent(value)).apply();
    }

    public int getFloatingBallIdleAlphaPercent() {
        return clampPercent(preferences.getInt(
                KEY_FLOATING_BALL_IDLE_ALPHA_PERCENT,
                DEFAULT_FLOATING_BALL_IDLE_ALPHA_PERCENT
        ));
    }

    public void setFloatingBallIdleAlphaPercent(int value) {
        preferences.edit().putInt(KEY_FLOATING_BALL_IDLE_ALPHA_PERCENT, clampPercent(value)).apply();
    }

    public boolean isFloatingBallHeightLocked() {
        return preferences.getBoolean(KEY_FLOATING_BALL_HEIGHT_LOCKED, false);
    }

    public void setFloatingBallHeightLocked(boolean enabled) {
        preferences.edit().putBoolean(KEY_FLOATING_BALL_HEIGHT_LOCKED, enabled).apply();
    }

    public boolean isFloatingBallSideLocked() {
        return preferences.getBoolean(KEY_FLOATING_BALL_SIDE_LOCKED, false);
    }

    public void setFloatingBallSideLocked(boolean enabled) {
        preferences.edit().putBoolean(KEY_FLOATING_BALL_SIDE_LOCKED, enabled).apply();
    }

    public boolean isFloatingBallOneHandModeEnabled() {
        return preferences.getBoolean(KEY_FLOATING_BALL_ONE_HAND_MODE, false);
    }

    public void setFloatingBallOneHandModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_FLOATING_BALL_ONE_HAND_MODE, enabled).apply();
    }

    public int getFloatingBallOneHandAngleDegrees() {
        return clampAngleDegrees(preferences.getInt(
                KEY_FLOATING_BALL_ONE_HAND_ANGLE_DEGREES,
                DEFAULT_FLOATING_BALL_ONE_HAND_ANGLE_DEGREES
        ));
    }

    public void setFloatingBallOneHandAngleDegrees(int value) {
        preferences.edit()
                .putInt(KEY_FLOATING_BALL_ONE_HAND_ANGLE_DEGREES, clampAngleDegrees(value))
                .apply();
    }

    public boolean isFloatingBallHidden() {
        return preferences.getBoolean(KEY_FLOATING_BALL_HIDDEN, false);
    }

    public void setFloatingBallHidden(boolean enabled) {
        preferences.edit().putBoolean(KEY_FLOATING_BALL_HIDDEN, enabled).apply();
    }

    public boolean isFloatingBallLandscapeSafeAreaEnabled() {
        return preferences.getBoolean(KEY_FLOATING_BALL_LANDSCAPE_SAFE_AREA, true);
    }

    public void setFloatingBallLandscapeSafeAreaEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_FLOATING_BALL_LANDSCAPE_SAFE_AREA, enabled).apply();
    }

    public boolean isTouchEventEnable() {
        return preferences.getBoolean(KEY_TOUCH_EVENT_ENABLE, false);
    }

    public void setTouchEventEnable(boolean enabled) {
        preferences.edit().putBoolean(KEY_TOUCH_EVENT_ENABLE, enabled).apply();
    }

    public int getTouchEventPrsPercent() {
        return clampPercent(preferences.getInt(
                KEY_TOUCH_EVENT_PRS_PERCENT,
                DEFAULT_TOUCH_EVENT_PRS_PERCENT
        ));
    }

    public void setTouchEventPrsPercent(int value) {
        preferences.edit().putInt(KEY_TOUCH_EVENT_PRS_PERCENT, clampPercent(value)).apply();
    }

    public int getTouchEventSizePercent() {
        return clampPercent(preferences.getInt(
                KEY_TOUCH_EVENT_SIZE_PERCENT,
                DEFAULT_TOUCH_EVENT_SIZE_PERCENT
        ));
    }

    public void setTouchEventSizePercent(int value) {
        preferences.edit().putInt(KEY_TOUCH_EVENT_SIZE_PERCENT, clampPercent(value)).apply();
    }

    public boolean isAdaptiveLauncherIconEnabled() {
        return preferences.getBoolean(KEY_ADAPTIVE_LAUNCHER_ICON, false);
    }

    public void setAdaptiveLauncherIconEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ADAPTIVE_LAUNCHER_ICON, enabled).apply();
    }

    public boolean isClassicOverlayStyleEnabled() {
        return preferences.getBoolean(KEY_CLASSIC_OVERLAY_STYLE, false);
    }

    public void setClassicOverlayStyleEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_CLASSIC_OVERLAY_STYLE, enabled).apply();
    }

    public int getGapRowHeightPercent() {
        return clampPercent(preferences.getInt(
                KEY_GAP_ROW_HEIGHT_PERCENT,
                DEFAULT_GAP_ROW_HEIGHT_PERCENT
        ));
    }

    public void setGapRowHeightPercent(int value) {
        preferences.edit().putInt(KEY_GAP_ROW_HEIGHT_PERCENT, clampPercent(value)).apply();
    }

    public String getCustomSearchProvidersJson() {
        return preferences.getString(KEY_CUSTOM_SEARCH_PROVIDERS, "[]");
    }

    public void setCustomSearchProvidersJson(String value) {
        preferences.edit().putString(KEY_CUSTOM_SEARCH_PROVIDERS, value).apply();
    }

    public int allocateCustomSearchType() {
        int value = preferences.getInt(KEY_NEXT_CUSTOM_SEARCH_TYPE, FIRST_CUSTOM_SEARCH_TYPE);
        preferences.edit().putInt(KEY_NEXT_CUSTOM_SEARCH_TYPE, value + 1).apply();
        return value;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int clampAngleDegrees(int value) {
        return Math.max(5, Math.min(45, value));
    }

    private static int clampOcrSelectionCaptureDelay(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    private static Set<String> defaultOcrWhitelistPackages() {
        HashSet<String> packages = new HashSet<>();
        packages.add("com.tencent.mm");
        packages.add("com.tencent.mobileqq");
        return packages;
    }

    private static String normalizeOcrRecognizerMode(String value) {
        if (OCR_MODE_JAPANESE.equals(value)) {
            return OCR_MODE_JAPANESE;
        }
        if (OCR_MODE_KOREAN.equals(value)) {
            return OCR_MODE_KOREAN;
        }
        if (OCR_MODE_LATIN.equals(value)) {
            return OCR_MODE_LATIN;
        }
        return OCR_MODE_CHINESE;
    }
}
