package com.seasalt.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager as PM
import android.net.Uri
import androidx.core.content.ContextCompat
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.seasalt.launcher.data.AppEntry
import com.seasalt.launcher.data.BookRepository
import com.seasalt.launcher.data.CoverExtractor
import com.seasalt.launcher.data.DiscoveredApp
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    /** Cached package scan, shared by the app list, the hidden list and first-run seeding. */
    private var discoveryJob: Deferred<List<DiscoveredApp>>? = null
    private var appListDirty = true

    /**
     * The installed-app set almost never changes between resumes, so rescanning
     * every time was pure waste. Invalidate only when the system says so —
     * PACKAGE_CHANGED also covers the enable/disable that Onyx firmware performs.
     */
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appListDirty = true
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                refreshAppList()
            }
        }
    }

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

        // First-run whitelist seeding happens in refreshAppList(), which reuses the
        // same package scan instead of running a second one on the main thread.

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            packageFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageChangeReceiver)
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
        lifecycleScope.launch {
            val discovered = discoveredApps().ifEmpty { return@launch }
            val hiddenPackages = prefsManager.getHiddenApps()
            val renamedApps = prefsManager.getRenamedApps()

            val entries = discovered
                .filter { it.packageName in hiddenPackages }
                .map { HiddenAppInfo(it.packageName, renamedApps[it.packageName] ?: it.systemName) }
                .sortedBy { it.displayName.lowercase() }

            hiddenAppsList.clear()
            hiddenAppsList.addAll(entries)
        }
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
                val raw = BookRepository.getRecentBooks(this@LauncherActivity, limit = 2)
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
            val intent = Intent("com.onyx.action.LIBRARY").apply {
                component = ComponentName("com.onyx", "com.onyx.common.library.ui.LibraryActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open library: ${e.message}", Toast.LENGTH_SHORT).show()
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

    /**
     * Three-tier discovery of every launchable package:
     *   1. CATEGORY_LAUNCHER activities
     *   2. packages with a standard launch intent
     *   3. packages exposing an exported activity (e.g. Boox NeoReader)
     *
     * Costs a binder round trip per installed package, so it must never run on
     * the main thread — go through [discoveredApps] rather than calling this.
     */
    private fun discoverInstalledApps(): List<DiscoveredApp> {
        val byPackage = LinkedHashMap<String, DiscoveredApp>()

        val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        for (info in packageManager.queryIntentActivities(launcherIntent, 0)) {
            val pkg = info.activityInfo.packageName
            byPackage[pkg] = DiscoveredApp(
                packageName = pkg,
                systemName = info.loadLabel(packageManager).toString(),
                activityName = info.activityInfo.name,
            )
        }

        for (appInfo in packageManager.getInstalledApplications(0)) {
            val pkg = appInfo.packageName
            if (pkg in byPackage) continue

            if (packageManager.getLaunchIntentForPackage(pkg) != null) {
                byPackage[pkg] = DiscoveredApp(
                    packageName = pkg,
                    systemName = appInfo.loadLabel(packageManager).toString(),
                )
                continue
            }

            try {
                val pkgInfo = packageManager.getPackageInfo(pkg, PM.GET_ACTIVITIES)
                val activity = pkgInfo.activities?.firstOrNull { it.exported }
                if (activity != null) {
                    byPackage[pkg] = DiscoveredApp(
                        packageName = pkg,
                        systemName = appInfo.loadLabel(packageManager).toString(),
                        activityName = activity.name,
                    )
                }
            } catch (_: Exception) { }
        }

        return byPackage.values.toList()
    }

    /**
     * The cached discovery result, recomputed on IO only when [appListDirty].
     * Backed by a Deferred so concurrent callers share one scan instead of racing.
     *
     * Returns an empty list if the scan failed; callers leave the UI untouched in
     * that case rather than blanking it, and the cache is invalidated so the next
     * resume retries instead of serving the failure forever.
     */
    private suspend fun discoveredApps(): List<DiscoveredApp> {
        val job = discoveryJob?.takeIf { !appListDirty }
            ?: lifecycleScope.async(Dispatchers.IO) { discoverInstalledApps() }
                .also {
                    discoveryJob = it
                    appListDirty = false
                }

        return try {
            job.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            discoveryJob = null
            appListDirty = true
            emptyList()
        }
    }

    // --- App list ---

    /**
     * Rebuilds the visible list from cached discovery. Cheap enough to call on
     * every resume and after every hide/rename — the expensive scan only reruns
     * when a package actually changed.
     */
    private fun refreshAppList() {
        lifecycleScope.launch {
            // An empty scan means failure, not an empty device — never seed the
            // first-run whitelist or blank the list from it.
            val discovered = discoveredApps().ifEmpty { return@launch }

            // First run: hide everything outside the default whitelist. Seeded here
            // rather than in onCreate so it reuses the same scan.
            if (prefsManager.isFirstRun()) {
                val allPackages = discovered.map { it.packageName }.toSet()
                prefsManager.setHiddenApps((allPackages - DEFAULT_VISIBLE_PACKAGES) + packageName)
                prefsManager.setFirstRunComplete()
            }

            val hiddenApps = prefsManager.getHiddenApps()
            val renamedApps = prefsManager.getRenamedApps()

            val entries = discovered
                .filter { it.packageName !in hiddenApps }
                .map { app ->
                    AppEntry(
                        packageName = app.packageName,
                        systemName = app.systemName,
                        customName = renamedApps[app.packageName],
                        activityName = app.activityName,
                    )
                }
                .sorted()

            apps.clear()
            apps.addAll(entries)
        }
    }
}
