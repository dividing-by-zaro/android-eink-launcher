package com.palma.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.palma.launcher.data.WeatherData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryInfo(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
)

@Composable
fun rememberCurrentTime(): State<Long> {
    val context = LocalContext.current
    val timeState = remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                timeState.longValue = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return timeState
}

@Composable
fun rememberBatteryState(): State<BatteryInfo> {
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(BatteryInfo()) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val percentage = if (scale > 0) (level * 100) / scale else 0
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                batteryState.value = BatteryInfo(percentage, isCharging)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return batteryState
}

@Composable
fun HeaderSection(
    weatherData: WeatherData? = null,
    modifier: Modifier = Modifier,
) {
    val currentTime = rememberCurrentTime()
    val batteryInfo = rememberBatteryState()

    val timeMillis = currentTime.value
    val date = Date(timeMillis)
    val timeFormat = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    val timeText = timeFormat.format(date)
    val dateText = dateFormat.format(date)
    val batteryText = "${batteryInfo.value.percentage}%"

    val infoRow = buildString {
        append(dateText)
        append(" · ")
        append(batteryText)
        if (weatherData != null) {
            append(" · ")
            append(weatherData.displayText)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = infoRow,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
