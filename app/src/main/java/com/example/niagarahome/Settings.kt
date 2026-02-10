package com.example.niagarahome

import android.content.Context
import android.content.SharedPreferences

object Settings {
    private const val PREFS_NAME = "niagara_settings"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Spacing ---
    var itemVerticalPaddingDp: Int
        get() = prefs.getInt("item_v_padding", DEF_ITEM_V_PADDING)
        set(v) = prefs.edit().putInt("item_v_padding", v).apply()

    var itemHorizontalPaddingDp: Int
        get() = prefs.getInt("item_h_padding", DEF_ITEM_H_PADDING)
        set(v) = prefs.edit().putInt("item_h_padding", v).apply()

    var iconTextMarginDp: Int
        get() = prefs.getInt("icon_text_margin", DEF_ICON_TEXT_MARGIN)
        set(v) = prefs.edit().putInt("icon_text_margin", v).apply()

    var alphabetStripWidthDp: Int
        get() = prefs.getInt("strip_width", DEF_STRIP_WIDTH)
        set(v) = prefs.edit().putInt("strip_width", v).apply()

    var listTopPaddingDp: Int
        get() = prefs.getInt("list_top_padding", DEF_LIST_TOP_PADDING)
        set(v) = prefs.edit().putInt("list_top_padding", v).apply()

    var listBottomPaddingDp: Int
        get() = prefs.getInt("list_bottom_padding", DEF_LIST_BOTTOM_PADDING)
        set(v) = prefs.edit().putInt("list_bottom_padding", v).apply()

    // --- Animation ---
    var pressScale: Float
        get() = prefs.getFloat("press_scale", DEF_PRESS_SCALE)
        set(v) = prefs.edit().putFloat("press_scale", v).apply()

    var enterAnimSlideDp: Int
        get() = prefs.getInt("enter_anim_slide", DEF_ENTER_ANIM_SLIDE)
        set(v) = prefs.edit().putInt("enter_anim_slide", v).apply()

    var enterAnimDurationMs: Int
        get() = prefs.getInt("enter_anim_duration", DEF_ENTER_ANIM_DURATION)
        set(v) = prefs.edit().putInt("enter_anim_duration", v).apply()

    var scrollSpeed: Float
        get() = prefs.getFloat("scroll_speed", DEF_SCROLL_SPEED)
        set(v) = prefs.edit().putFloat("scroll_speed", v).apply()

    // --- Alphabet Strip ---
    var pillOpacityPercent: Int
        get() = prefs.getInt("pill_opacity", DEF_PILL_OPACITY)
        set(v) = prefs.edit().putInt("pill_opacity", v).apply()

    var pillCornerRadiusDp: Int
        get() = prefs.getInt("pill_corner_radius", DEF_PILL_CORNER_RADIUS)
        set(v) = prefs.edit().putInt("pill_corner_radius", v).apply()

    var highlightScale: Float
        get() = prefs.getFloat("highlight_scale", DEF_HIGHLIGHT_SCALE)
        set(v) = prefs.edit().putFloat("highlight_scale", v).apply()

    var stripVerticalPaddingDp: Int
        get() = prefs.getInt("strip_v_padding", DEF_STRIP_V_PADDING)
        set(v) = prefs.edit().putInt("strip_v_padding", v).apply()

    // --- Hidden Apps ---
    val hiddenApps: Set<String>
        get() = prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()

    fun addHiddenApp(packageName: String) {
        val current = hiddenApps.toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet("hidden_apps", current).apply()
    }

    fun removeHiddenApp(packageName: String) {
        val current = hiddenApps.toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet("hidden_apps", current).apply()
    }

    fun clearHiddenApps() {
        prefs.edit().remove("hidden_apps").apply()
    }

    // --- Gesture ---
    var pullDownThresholdDp: Int
        get() = prefs.getInt("pull_down_threshold", DEF_PULL_DOWN_THRESHOLD)
        set(v) = prefs.edit().putInt("pull_down_threshold", v).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    // Defaults
    const val DEF_ITEM_V_PADDING = 12
    const val DEF_ITEM_H_PADDING = 20
    const val DEF_ICON_TEXT_MARGIN = 16
    const val DEF_STRIP_WIDTH = 32
    const val DEF_LIST_TOP_PADDING = 8
    const val DEF_LIST_BOTTOM_PADDING = 48
    const val DEF_PRESS_SCALE = 0.97f
    const val DEF_ENTER_ANIM_SLIDE = 40
    const val DEF_ENTER_ANIM_DURATION = 250
    const val DEF_SCROLL_SPEED = 4.0f
    const val DEF_PILL_OPACITY = 10
    const val DEF_PILL_CORNER_RADIUS = 12
    const val DEF_HIGHLIGHT_SCALE = 1.4f
    const val DEF_STRIP_V_PADDING = 24
    const val DEF_PULL_DOWN_THRESHOLD = 100
}
