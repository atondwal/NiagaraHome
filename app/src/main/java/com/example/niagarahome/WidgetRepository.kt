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
    }

    val appWidgetHost = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            ctx: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = NHWidgetHostView(ctx)
    }
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedWidgetIds(): List<Int> {
        val raw = prefs.getString(KEY_WIDGET_IDS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun addWidgetId(id: Int) {
        val ids = getSavedWidgetIds().toMutableList()
        if (id !in ids) ids.add(id)
        prefs.edit().putString(KEY_WIDGET_IDS, ids.joinToString(",")).apply()
    }

    fun removeWidgetId(id: Int) {
        val ids = getSavedWidgetIds().toMutableList()
        ids.remove(id)
        prefs.edit().putString(KEY_WIDGET_IDS, ids.joinToString(",")).apply()
        appWidgetHost.deleteAppWidgetId(id)
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
