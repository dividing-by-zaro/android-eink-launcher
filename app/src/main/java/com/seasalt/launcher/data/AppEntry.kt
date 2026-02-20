package com.seasalt.launcher.data

data class AppEntry(
    val packageName: String,
    val systemName: String,
    val customName: String? = null,
    val isHidden: Boolean = false,
    val activityName: String = "",
) : Comparable<AppEntry> {

    val displayName: String
        get() = customName ?: systemName

    val sortKey: String
        get() = displayName.lowercase()

    override fun compareTo(other: AppEntry): Int =
        sortKey.compareTo(other.sortKey)
}
