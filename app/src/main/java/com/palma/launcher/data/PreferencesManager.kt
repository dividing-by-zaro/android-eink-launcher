package com.palma.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("palma_launcher", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_RENAMED_APPS = "renamed_apps"
        private const val KEY_WEATHER_TEMP = "weather_temp"
        private const val KEY_WEATHER_CODE = "weather_code"
        private const val KEY_WEATHER_TIMESTAMP = "weather_timestamp"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_FIRST_RUN = "first_run"
    }

    // --- Hidden apps ---

    fun getHiddenApps(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_APPS, emptySet())?.toSet() ?: emptySet()

    fun setHiddenApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, apps).apply()
    }

    fun addHiddenApp(packageName: String) {
        setHiddenApps(getHiddenApps() + packageName)
    }

    fun removeHiddenApp(packageName: String) {
        setHiddenApps(getHiddenApps() - packageName)
    }

    // --- Renamed apps ---

    fun getRenamedApps(): Map<String, String> {
        val json = prefs.getString(KEY_RENAMED_APPS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            for (key in obj.keys()) {
                map[key] = obj.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setRenamedApp(packageName: String, customName: String) {
        val map = getRenamedApps().toMutableMap()
        map[packageName] = customName
        prefs.edit().putString(KEY_RENAMED_APPS, JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun removeRenamedApp(packageName: String) {
        val map = getRenamedApps().toMutableMap()
        map.remove(packageName)
        prefs.edit().putString(KEY_RENAMED_APPS, JSONObject(map as Map<*, *>).toString()).apply()
    }

    // --- Weather ---

    fun getWeatherTemp(): Float = prefs.getFloat(KEY_WEATHER_TEMP, 0f)
    fun setWeatherTemp(temp: Float) { prefs.edit().putFloat(KEY_WEATHER_TEMP, temp).apply() }

    fun getWeatherCode(): Int = prefs.getInt(KEY_WEATHER_CODE, -1)
    fun setWeatherCode(code: Int) { prefs.edit().putInt(KEY_WEATHER_CODE, code).apply() }

    fun getWeatherTimestamp(): Long = prefs.getLong(KEY_WEATHER_TIMESTAMP, 0L)
    fun setWeatherTimestamp(ts: Long) { prefs.edit().putLong(KEY_WEATHER_TIMESTAMP, ts).apply() }

    fun getWeatherLat(): Float = prefs.getFloat(KEY_WEATHER_LAT, 36.17f)
    fun setWeatherLat(lat: Float) { prefs.edit().putFloat(KEY_WEATHER_LAT, lat).apply() }

    fun getWeatherLon(): Float = prefs.getFloat(KEY_WEATHER_LON, -115.14f)
    fun setWeatherLon(lon: Float) { prefs.edit().putFloat(KEY_WEATHER_LON, lon).apply() }

    // --- First run ---

    fun isFirstRun(): Boolean = prefs.getBoolean(KEY_FIRST_RUN, true)

    fun setFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
    }
}
