package com.seasalt.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager as PM
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.seasalt.launcher.data.AppEntry
import com.seasalt.launcher.data.BookRepository
import com.seasalt.launcher.data.CoverExtractor
import com.seasalt.launcher.data.PreferencesManager
import com.seasalt.launcher.data.RecentBook
import com.seasalt.launcher.data.WeatherData
import com.seasalt.launcher.data.WeatherRepository
import com.seasalt.launcher.ui.ContextMenuAction
import com.seasalt.launcher.ui.HiddenAppInfo
import com.seasalt.launcher.ui.HiddenAppsDialog
import com.seasalt.launcher.ui.HomeScreen
import com.seasalt.launcher.ui.RenameDialog
import com.seasalt.launcher.ui.theme.SeasaltTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LauncherActivity : ComponentActivity() {

    lateinit var prefsManager: PreferencesManager
        private set

    lateinit var weatherRepository: WeatherRepository
        private set

    val apps = mutableStateListOf<AppEntry>()
    val weatherState = mutableStateOf<WeatherData?>(null)
    val recentBooksState = mutableStateOf<List<RecentBook>>(emptyList())

    // Dialog state
    private val showRenameDialog = mutableStateOf(false)
    private val renameTargetApp = mutableStateOf<AppEntry?>(null)
    private val showHiddenAppsDialog = mutableStateOf(false)
    private val hiddenAppsList = mutableStateListOf<HiddenAppInfo>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        refreshWeather()
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) refreshRecentBooks()
    }

    companion object {
        private val DEFAULT_VISIBLE_PACKAGES = setOf(
            "com.overdrive.mobile.android.libby",
            "com.thestorygraph.thestorygraph",
            "com.android.documentsui",
            "com.android.vending",
            "com.android.settings",
            "com.nutomic.syncthingandroid",
            "com.github.catfriend1.syncthingandroid",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PreferencesManager(this)
        weatherRepository = WeatherRepository(this, prefsManager)

        // First-run: whitelist — hide everything except default visible apps
        if (prefsManager.isFirstRun()) {
            val allPackages = discoverAllPackages()
            val hidden = (allPackages - DEFAULT_VISIBLE_PACKAGES) + packageName
            prefsManager.setHiddenApps(hidden)
            prefsManager.setFirstRunComplete()
        }

        // Load cached weather immediately
        weatherState.value = weatherRepository.getCachedWeather()

        // Request permissions
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)

        setContent {
            SeasaltTheme {
                BackHandler { /* consume back press on home screen */ }
                HomeScreen(
                    apps = apps,
                    weatherData = weatherState.value,
                    recentBooks = recentBooksState.value,
                    onAppClick = { app -> launchApp(app) },
                    onContextMenuAction = { app, action -> handleContextMenu(app, action) },
                    onAllBooksClick = { openLibrary() },
                    onBookClick = { book -> openBook(book) },
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
        refreshAppList()
        refreshRecentBooks()
        if (weatherRepository.isStale()) {
            refreshWeather()
        }
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

    // --- Library ---

    private fun refreshRecentBooks() {
        lifecycleScope.launch {
            val books = withContext(Dispatchers.IO) {
                val raw = BookRepository.getRecentBooks(this@LauncherActivity, limit = 3)
                raw.map { book ->
                    val cover = CoverExtractor.getOrExtractCover(
                        this@LauncherActivity,
                        book.filePath,
                        book.fileType,
                    )
                    book.copy(coverBitmap = cover)
                }
            }
            recentBooksState.value = books
        }
    }

    private fun openLibrary() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName("com.onyx", "com.onyx.common.library.ui.LibraryActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Library unavailable — silently ignore
        } catch (_: Exception) {
            // Unexpected error — silently ignore
        }
    }

    private fun openBook(book: RecentBook) {
        val file = File(book.filePath)
        if (!file.exists()) return

        val mimeType = when (book.fileType.lowercase()) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "mobi" -> "application/x-mobipocket-ebook"
            else -> "application/octet-stream"
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                setPackage("com.onyx.kreader")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // NeoReader can't handle it — try without package restriction
            try {
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(fallback)
            } catch (_: Exception) { }
        } catch (_: Exception) { }
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

    // --- App discovery ---

    private fun discoverAllPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        for (info in packageManager.queryIntentActivities(launcherIntent, 0)) {
            packages.add(info.activityInfo.packageName)
        }
        for (appInfo in packageManager.getInstalledApplications(0)) {
            val pkg = appInfo.packageName
            if (pkg in packages) continue
            if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                packages.add(pkg)
                continue
            }
            try {
                val pkgInfo = packageManager.getPackageInfo(pkg, PM.GET_ACTIVITIES)
                if (pkgInfo.activities?.any { it.exported } == true) {
                    packages.add(pkg)
                }
            } catch (_: Exception) { }
        }
        return packages
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
