package com.palma.launcher

import android.Manifest
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager as PM
import android.net.Uri
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
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

        // Hide the system status bar (must be after setContent so DecorView exists)
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

        // Standard CATEGORY_LAUNCHER apps
        val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)

        val hiddenByPackage = mutableMapOf<String, HiddenAppInfo>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg !in hiddenPackages) continue
            val systemName = info.loadLabel(packageManager).toString()
            hiddenByPackage[pkg] = HiddenAppInfo(pkg, renamedApps[pkg] ?: systemName)
        }

        // Also check installed packages for launchable apps without CATEGORY_LAUNCHER
        val installedPackages = packageManager.getInstalledApplications(0)
        for (appInfo in installedPackages) {
            val pkg = appInfo.packageName
            if (pkg !in hiddenPackages || pkg in hiddenByPackage) continue
            if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                val systemName = appInfo.loadLabel(packageManager).toString()
                hiddenByPackage[pkg] = HiddenAppInfo(pkg, renamedApps[pkg] ?: systemName)
                continue
            }
            // Fallback: check for exported activity
            try {
                val pkgInfo = packageManager.getPackageInfo(pkg, PM.GET_ACTIVITIES)
                if (pkgInfo.activities?.any { it.exported } == true) {
                    val systemName = appInfo.loadLabel(packageManager).toString()
                    hiddenByPackage[pkg] = HiddenAppInfo(pkg, renamedApps[pkg] ?: systemName)
                }
            } catch (_: Exception) { }
        }

        hiddenAppsList.clear()
        hiddenAppsList.addAll(hiddenByPackage.values.sortedBy { it.displayName.lowercase() })
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
            // Widget not bound — picker failed or returned invalid state
            widgetHostManager.appWidgetHost.deleteAppWidgetId(appWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            return
        }

        // Widget is already bound (ACTION_APPWIDGET_PICK binds it).
        // Check if the provider requires a configure activity.
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
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
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
        } else if (app.activityName.isNotEmpty()) {
            // Fallback: launch by explicit component (for apps like NeoReader
            // that have no standard launch intent)
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(explicit)
            } catch (_: Exception) {
                Toast.makeText(this, "Cannot launch ${app.displayName}", Toast.LENGTH_SHORT).show()
            }
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
        val hiddenApps = prefsManager.getHiddenApps()
        val renamedApps = prefsManager.getRenamedApps()

        // Standard CATEGORY_LAUNCHER apps
        val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)

        val entriesByPackage = mutableMapOf<String, AppEntry>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            entriesByPackage[pkg] = AppEntry(
                packageName = pkg,
                systemName = info.loadLabel(packageManager).toString(),
                customName = renamedApps[pkg],
                isHidden = pkg in hiddenApps,
                activityName = info.activityInfo.name,
            )
        }

        // Also check all installed packages for launchable apps that lack
        // CATEGORY_LAUNCHER (e.g. Boox NeoReader and other system apps).
        // These apps have no standard launch intent, so we find their first
        // exported activity and launch by explicit component name.
        val installedPackages = packageManager.getInstalledApplications(0)
        for (appInfo in installedPackages) {
            val pkg = appInfo.packageName
            if (pkg in entriesByPackage) continue

            // First try the standard getLaunchIntentForPackage
            if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                entriesByPackage[pkg] = AppEntry(
                    packageName = pkg,
                    systemName = appInfo.loadLabel(packageManager).toString(),
                    customName = renamedApps[pkg],
                    isHidden = pkg in hiddenApps,
                    activityName = "",
                )
                continue
            }

            // Fallback: find first exported activity with an intent filter
            try {
                val pkgInfo = packageManager.getPackageInfo(pkg, PM.GET_ACTIVITIES)
                val activity = pkgInfo.activities?.firstOrNull { it.exported }
                if (activity != null) {
                    entriesByPackage[pkg] = AppEntry(
                        packageName = pkg,
                        systemName = appInfo.loadLabel(packageManager).toString(),
                        customName = renamedApps[pkg],
                        isHidden = pkg in hiddenApps,
                        activityName = activity.name,
                    )
                }
            } catch (_: Exception) { }
        }

        val entries = entriesByPackage.values.filter { !it.isHidden }.sorted()
        apps.clear()
        apps.addAll(entries)
    }
}
