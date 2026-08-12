package com.seasalt.launcher.data

/**
 * A launchable package as found on the device, before any of the user's hide or
 * rename preferences are applied.
 *
 * Discovery is expensive — it costs a binder round trip per installed package —
 * so it is cached and only recomputed when packages actually change. Preferences
 * are layered on top of the cached result to build [AppEntry] values.
 */
data class DiscoveredApp(
    val packageName: String,
    val systemName: String,
    val activityName: String = "",
)
