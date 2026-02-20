package com.palma.launcher

import android.Manifest
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.palma.launcher.data.AppEntry
import com.palma.launcher.data.PreferencesManager
import com.palma.launcher.data.WeatherData
import com.palma.launcher.data.WeatherRepository
import com.palma.launcher.ui.ContextMenuAction
import com.palma.launcher.ui.HiddenAppInfo
import com.palma.launcher.ui.HiddenAppsDialog
import com.palma.launcher.ui.HomeScreen
import com.palma.launcher.ui.RenameDialog
import com.palma.launcher.ui.theme.PalmaLauncherTheme
import com.palma.launcher.widget.WidgetHostManager
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {

    lateinit var prefsManager: PreferencesManager
        private set

    lateinit var weatherRepository: WeatherRepository
        private set

    lateinit var widgetHostManager: WidgetHostManager
        private set

    val apps = mutableStateListOf<AppEntry>()
    val weatherState = mutableStateOf<WeatherData?>(null)
    val widgetViewState = mutableStateOf<AppWidgetHostView?>(null)

    // Dialog state
    private val showRenameDialog = mutableStateOf(false)
    private val renameTargetApp = mutableStateOf<AppEntry?>(null)
    private val showHiddenAppsDialog = mutableStateOf(false)
    private val hiddenAppsList = mutableStateListOf<HiddenAppInfo>()

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        refreshWeather()
    }

    private lateinit var widgetPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>
    private lateinit var widgetConfigureLauncher: ActivityResultLauncher<Intent>

    companion object {
        private val DEFAULT_HIDDEN_PACKAGES = setOf(
            "com.android.htmlviewer",
            "com.android.printspooler",
            "com.android.bips",
            "com.android.calllogbackup",
            "com.android.providers.calendar",
            "com.android.providers.contacts",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PreferencesManager(this)
        weatherRepository = WeatherRepository(this, prefsManager)
        widgetHostManager = WidgetHostManager(this, prefsManager)

        // First-run: set default hidden apps
        if (prefsManager.isFirstRun()) {
            val defaultHidden = DEFAULT_HIDDEN_PACKAGES + packageName
            prefsManager.setHiddenApps(defaultHidden)
            prefsManager.setFirstRunComplete()
        }

        // Load cached weather immediately
        weatherState.value = weatherRepository.getCachedWeather()

        // Register widget activity result launchers
        widgetPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val widgetId = result.data?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    finishWidgetSetup(widgetId)
                }
            } else {
                if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    widgetHostManager.appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                }
            }
        }

        widgetBindLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finishWidgetSetup(pendingWidgetId)
            } else {
                if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    widgetHostManager.appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                }
            }
        }

        widgetConfigureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                widgetHostManager.saveWidgetId(pendingWidgetId)
                loadWidget(pendingWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

        // Request location permission
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Restore widget if previously configured
        if (widgetHostManager.isConfigured) {
            loadWidget(widgetHostManager.savedWidgetId)
        }

        setContent {
            PalmaLauncherTheme {
                BackHandler { /* consume back press on home screen */ }
                HomeScreen(
                    apps = apps,
                    weatherData = weatherState.value,
                    widgetView = widgetViewState.value,
                    onAppClick = { app -> launchApp(app) },
                    onContextMenuAction = { app, action -> handleContextMenu(app, action) },
                    onWidgetConfigureClick = { startWidgetPicker() },
                )

                // Rename dialog
                if (showRenameDialog.value && renameTargetApp.value != null) {
                    RenameDialog(
                        currentName = renameTargetApp.value!!.displayName,
                        onConfirm = { newName ->
                            renameApp(renameTargetApp.value!!, newName)
                            showRenameDialog.value = false
                            renameTargetApp.value = null
                        },
                        onDismiss = {
                            showRenameDialog.value = false
                            renameTargetApp.value = null
                        },
                    )
                }

                // Hidden apps dialog
                if (showHiddenAppsDialog.value) {
                    HiddenAppsDialog(
                        hiddenApps = hiddenAppsList,
                        onUnhide = { packageName ->
                            unhideApp(packageName)
                        },
                        onDismiss = {
                            showHiddenAppsDialog.value = false
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        widgetHostManager.startListening()
        refreshAppList()
        if (weatherRepository.isStale()) {
            refreshWeather()
        }
    }

    override fun onStop() {
        super.onStop()
        widgetHostManager.stopListening()
    }

    private fun handleContextMenu(app: AppEntry, action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.RENAME -> {
                renameTargetApp.value = app
                showRenameDialog.value = true
            }
            ContextMenuAction.RESET_NAME -> resetAppName(app)
            ContextMenuAction.HIDE -> hideApp(app)
            ContextMenuAction.SHOW_HIDDEN -> showHiddenApps()
            ContextMenuAction.APP_INFO -> openAppInfo(app)
        }
    }

    // --- Hide / Unhide ---

    private fun hideApp(app: AppEntry) {
        prefsManager.addHiddenApp(app.packageName)
        refreshAppList()
    }

    private fun unhideApp(packageName: String) {
        prefsManager.removeHiddenApp(packageName)
        refreshAppList()
        refreshHiddenAppsList()
    }

    private fun showHiddenApps() {
        refreshHiddenAppsList()
        showHiddenAppsDialog.value = true
    }

    private fun refreshHiddenAppsList() {
        val hiddenPackages = prefsManager.getHiddenApps()
        val renamedApps = prefsManager.getRenamedApps()

        val intent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)

        val hiddenInfos = resolveInfos
            .filter { it.activityInfo.packageName in hiddenPackages }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val systemName = info.loadLabel(packageManager).toString()
                val displayName = renamedApps[pkg] ?: systemName
                HiddenAppInfo(pkg, displayName)
            }
            .sortedBy { it.displayName.lowercase() }

        hiddenAppsList.clear()
        hiddenAppsList.addAll(hiddenInfos)
    }

    // --- Rename ---

    private fun renameApp(app: AppEntry, newName: String) {
        prefsManager.setRenamedApp(app.packageName, newName)
        refreshAppList()
    }

    private fun resetAppName(app: AppEntry) {
        prefsManager.removeRenamedApp(app.packageName)
        refreshAppList()
    }

    // --- Widget ---

    private fun startWidgetPicker() {
        pendingWidgetId = widgetHostManager.allocateWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        widgetPickerLauncher.launch(pickIntent)
    }

    private fun finishWidgetSetup(appWidgetId: Int) {
        val providerInfo = widgetHostManager.appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (providerInfo == null) {
            widgetHostManager.saveWidgetId(appWidgetId)
            loadWidget(appWidgetId)
            return
        }

        if (!widgetHostManager.bindWidget(appWidgetId, providerInfo)) {
            pendingWidgetId = appWidgetId
            val bindIntent = widgetHostManager.getBindIntent(appWidgetId, providerInfo)
            widgetBindLauncher.launch(bindIntent)
            return
        }

        if (providerInfo.configure != null) {
            pendingWidgetId = appWidgetId
            val configureIntent = Intent().apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            widgetConfigureLauncher.launch(configureIntent)
            return
        }

        widgetHostManager.saveWidgetId(appWidgetId)
        loadWidget(appWidgetId)
    }

    private fun loadWidget(appWidgetId: Int) {
        val view = widgetHostManager.createWidgetView(appWidgetId)
        widgetViewState.value = view
    }

    // --- Weather ---

    private fun refreshWeather() {
        lifecycleScope.launch {
            val data = weatherRepository.fetchWeather()
            if (data != null) {
                weatherState.value = data
            }
        }
    }

    // --- App launch ---

    private fun launchApp(app: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot launch ${app.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(app: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    // --- App list ---

    private fun refreshAppList() {
        val intent = Intent(
            Intent.ACTION_MAIN, null
        ).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        val hiddenApps = prefsManager.getHiddenApps()
        val renamedApps = prefsManager.getRenamedApps()

        val entries = resolveInfos.map { info ->
            val pkg = info.activityInfo.packageName
            AppEntry(
                packageName = pkg,
                systemName = info.loadLabel(packageManager).toString(),
                customName = renamedApps[pkg],
                isHidden = pkg in hiddenApps,
                activityName = info.activityInfo.name,
            )
        }.filter { !it.isHidden }.sorted()

        apps.clear()
        apps.addAll(entries)
    }
}
