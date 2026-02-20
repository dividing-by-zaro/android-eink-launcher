package com.seasalt.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.seasalt.launcher.data.WeatherData
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
fun BatteryIcon(
    percentage: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 20.dp, height = 11.dp)) {
        val strokeWidth = 1.5.dp.toPx()
        val bodyWidth = size.width - 3.dp.toPx()
        val bodyHeight = size.height
        val cornerRadius = 2.dp.toPx()

        // Battery body outline
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset.Zero,
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth),
        )

        // Battery terminal nub
        val nubWidth = 2.5.dp.toPx()
        val nubHeight = 5.dp.toPx()
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(bodyWidth, (bodyHeight - nubHeight) / 2f),
            size = Size(nubWidth, nubHeight),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )

        // Fill level
        val inset = strokeWidth + 1.dp.toPx()
        val fillWidth = (bodyWidth - inset * 2) * (percentage / 100f)
        val fillHeight = bodyHeight - inset * 2
        if (fillWidth > 0f) {
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, fillHeight),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
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
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val timeText = timeFormat.format(date)
    val dateText = dateFormat.format(date)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "·",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            BatteryIcon(percentage = batteryInfo.value.percentage)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${batteryInfo.value.percentage}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (weatherData != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = weatherIcon(weatherData.weatherCode),
                    contentDescription = weatherData.condition,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black,
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${weatherData.temperature.toInt()}°F",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun weatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> Icons.Filled.WbSunny
    2 -> Icons.Filled.WbCloudy
    3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.Cloud
    51, 53, 55, 56, 57 -> Icons.Filled.WaterDrop
    61, 63, 65, 66, 67 -> Icons.Filled.WaterDrop
    71, 73, 75, 77 -> Icons.Filled.AcUnit
    80, 81, 82 -> Icons.Filled.WaterDrop
    85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.WbSunny
}
