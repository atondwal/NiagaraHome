package com.example.niagarahome

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class WidgetRepository(private val context: Context) {

    companion object {
        private const val HOST_ID = 1024
        private const val PREFS_NAME = "widget_prefs"
        private const val KEY_WIDGET_IDS = "widget_ids"
        private const val KEY_PENDING_WIDGET_ID = "pending_widget_id"
        private const val KEY_PENDING_SCREEN = "pending_screen"
        private const val KEY_MIGRATED_PER_SCREEN = "migrated_per_screen"
    }

    val appWidgetHost = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            ctx: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = NHWidgetHostView(ctx)
    }
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedWidgetIds(screen: String): List<Int> {
        val raw = prefs.getString("${KEY_WIDGET_IDS}_$screen", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun addWidgetId(id: Int, screen: String) {
        val ids = getSavedWidgetIds(screen).toMutableList()
        if (id !in ids) ids.add(id)
        prefs.edit().putString("${KEY_WIDGET_IDS}_$screen", ids.joinToString(",")).apply()
    }

    fun removeWidgetId(id: Int, screen: String) {
        val ids = getSavedWidgetIds(screen).toMutableList()
        ids.remove(id)
        prefs.edit().putString("${KEY_WIDGET_IDS}_$screen", ids.joinToString(",")).apply()
        appWidgetHost.deleteAppWidgetId(id)
    }

    fun saveWidgetOrder(ids: List<Int>, screen: String) {
        prefs.edit().putString("${KEY_WIDGET_IDS}_$screen", ids.joinToString(",")).apply()
    }

    fun migratePerScreen(currentScreen: String) {
        if (prefs.getBoolean(KEY_MIGRATED_PER_SCREEN, false)) return
        val legacy = prefs.getString(KEY_WIDGET_IDS, "") ?: ""
        if (legacy.isNotBlank()) {
            prefs.edit()
                .putString("${KEY_WIDGET_IDS}_$currentScreen", legacy)
                .remove(KEY_WIDGET_IDS)
                .putBoolean(KEY_MIGRATED_PER_SCREEN, true)
                .apply()
        } else {
            prefs.edit().putBoolean(KEY_MIGRATED_PER_SCREEN, true).apply()
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun createView(widgetId: Int): NHWidgetHostView {
        val info = appWidgetManager.getAppWidgetInfo(widgetId)
        return appWidgetHost.createView(context, widgetId, info) as NHWidgetHostView
    }

    fun getProviderInfo(widgetId: Int): AppWidgetProviderInfo? {
        return appWidgetManager.getAppWidgetInfo(widgetId)
    }

    var pendingWidgetId: Int
        get() = prefs.getInt(KEY_PENDING_WIDGET_ID, -1)
        set(value) = prefs.edit().putInt(KEY_PENDING_WIDGET_ID, value).apply()

    var pendingScreen: String?
        get() = prefs.getString(KEY_PENDING_SCREEN, null)
        set(value) = prefs.edit().putString(KEY_PENDING_SCREEN, value).apply()

    fun startListening() {
        appWidgetHost.startListening()
    }

    fun stopListening() {
        appWidgetHost.stopListening()
    }

    fun getWidgetHeightDp(widgetId: Int): Int {
        return prefs.getInt("widget_height_$widgetId", 0)
    }

    fun setWidgetHeightDp(widgetId: Int, heightDp: Int) {
        prefs.edit().putInt("widget_height_$widgetId", heightDp).apply()
    }

    fun removeWidgetHeight(widgetId: Int) {
        prefs.edit().remove("widget_height_$widgetId").apply()
    }

    fun getWidgetWidthDp(widgetId: Int): Int {
        return prefs.getInt("widget_width_$widgetId", 0)
    }

    fun setWidgetWidthDp(widgetId: Int, widthDp: Int) {
        prefs.edit().putInt("widget_width_$widgetId", widthDp).apply()
    }

    fun removeWidgetWidth(widgetId: Int) {
        prefs.edit().remove("widget_width_$widgetId").apply()
    }
}
