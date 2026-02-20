package com.seasalt.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class WeatherData(
    val temperature: Float,
    val weatherCode: Int,
) {
    val condition: String
        get() = mapWmoCode(weatherCode)

    val displayText: String
        get() = "${temperature.toInt()}°F $condition"

    companion object {
        fun mapWmoCode(code: Int): String = when (code) {
            0, 1 -> "Clear"
            2 -> "Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow"
            95, 96, 99 -> "Storms"
            else -> ""
        }
    }
}

class WeatherRepository(
    private val context: Context,
    private val prefsManager: PreferencesManager,
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private companion object {
        const val STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000L // 2 hours
        const val DEFAULT_LAT = 36.17f
        const val DEFAULT_LON = -115.14f
    }

    fun getCachedWeather(): WeatherData? {
        val temp = prefsManager.getWeatherTemp()
        val code = prefsManager.getWeatherCode()
        if (code == -1) return null
        return WeatherData(temp, code)
    }

    fun isStale(): Boolean {
        val timestamp = prefsManager.getWeatherTimestamp()
        if (timestamp == 0L) return true
        return System.currentTimeMillis() - timestamp > STALE_THRESHOLD_MS
    }

    suspend fun fetchWeather(): WeatherData? {
        val (lat, lon) = getLocation()
        return fetchFromApi(lat, lon)
    }

    private suspend fun getLocation(): Pair<Float, Float> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return getCachedOrDefaultLocation()
        }

        return try {
            val location = suspendCancellableCoroutine { cont ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(loc)
                        } else {
                            val cts = CancellationTokenSource()
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_LOW_POWER,
                                cts.token,
                            ).addOnSuccessListener { currentLoc ->
                                cont.resume(currentLoc)
                            }.addOnFailureListener {
                                cont.resume(null)
                            }
                            cont.invokeOnCancellation { cts.cancel() }
                        }
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            }

            if (location != null) {
                val lat = location.latitude.toFloat()
                val lon = location.longitude.toFloat()
                prefsManager.setWeatherLat(lat)
                prefsManager.setWeatherLon(lon)
                Pair(lat, lon)
            } else {
                getCachedOrDefaultLocation()
            }
        } catch (e: SecurityException) {
            getCachedOrDefaultLocation()
        }
    }

    private fun getCachedOrDefaultLocation(): Pair<Float, Float> {
        val lat = prefsManager.getWeatherLat()
        val lon = prefsManager.getWeatherLon()
        return if (lat != 0f || lon != 0f) Pair(lat, lon) else Pair(DEFAULT_LAT, DEFAULT_LON)
    }

    private suspend fun fetchFromApi(lat: Float, lon: Float): WeatherData? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code" +
                        "&temperature_unit=fahrenheit&timezone=auto"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                try {
                    if (connection.responseCode != 200) return@withContext getCachedWeather()

                    val body = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").toFloat()
                    val code = current.getInt("weather_code")

                    prefsManager.setWeatherTemp(temp)
                    prefsManager.setWeatherCode(code)
                    prefsManager.setWeatherTimestamp(System.currentTimeMillis())

                    WeatherData(temp, code)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                getCachedWeather()
            }
        }
    }
}
