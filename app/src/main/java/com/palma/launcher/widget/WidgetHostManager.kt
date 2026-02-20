package com.palma.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import com.palma.launcher.data.PreferencesManager

class WidgetHostManager(
    private val context: Context,
    private val prefsManager: PreferencesManager,
) {
    companion object {
        const val HOST_ID = 1024
    }

    val appWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    val savedWidgetId: Int
        get() = prefsManager.getWidgetId()

    val isConfigured: Boolean
        get() = savedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
    }

    fun bindWidget(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Boolean {
        return appWidgetManager.bindAppWidgetIdIfAllowed(
            appWidgetId,
            providerInfo.provider,
        )
    }

    fun getBindIntent(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
        }
    }

    fun createWidgetView(appWidgetId: Int): AppWidgetHostView? {
        return try {
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
            appWidgetHost.createView(context, appWidgetId, providerInfo)
        } catch (e: Exception) {
            null
        }
    }

    fun saveWidgetId(appWidgetId: Int) {
        prefsManager.setWidgetId(appWidgetId)
    }

    fun clearWidget() {
        val currentId = savedWidgetId
        if (currentId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                appWidgetHost.deleteAppWidgetId(currentId)
            } catch (_: Exception) {}
        }
        prefsManager.setWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    fun startListening() {
        appWidgetHost.startListening()
    }

    fun stopListening() {
        appWidgetHost.stopListening()
    }
}
