# Contract: Open-Meteo Weather API

**Type**: External HTTP API (GET)
**Base URL**: `https://api.open-meteo.com`
**Authentication**: None (free, no API key)

## Endpoint: Current Weather

```
GET /v1/forecast
```

### Request Parameters

| Parameter | Required | Type | Value |
|-----------|----------|------|-------|
| `latitude` | Yes | Float | Device latitude (e.g., `36.17`) |
| `longitude` | Yes | Float | Device longitude (e.g., `-115.14`) |
| `current` | Yes | String | `temperature_2m,weather_code` |
| `temperature_unit` | Yes | String | `fahrenheit` |
| `timezone` | Yes | String | `auto` |

### Example Request

```
GET /v1/forecast?latitude=36.17&longitude=-115.14&current=temperature_2m,weather_code&temperature_unit=fahrenheit&timezone=auto
```

### Success Response (200 OK)

```json
{
  "latitude": 36.175,
  "longitude": -115.125,
  "current": {
    "time": "2026-02-20T14:00",
    "interval": 900,
    "temperature_2m": 68.2,
    "weather_code": 1
  },
  "current_units": {
    "temperature_2m": "°F",
    "weather_code": "wmo code"
  }
}
```

### Fields Used by Launcher

| JSON Path | Type | Maps To |
|-----------|------|---------|
| `current.temperature_2m` | Float | `WeatherData.temperature` |
| `current.weather_code` | Int | `WeatherData.weatherCode` |

### Error Handling

| Condition | Behavior |
|-----------|----------|
| HTTP non-200 | Log error, use cached data |
| Network unreachable | Catch `IOException`, use cached data |
| Malformed JSON | Catch `JSONException`, use cached data |
| No cached data available | Display nothing in weather area |

### WMO Weather Code → Display Text

| Codes | Display |
|-------|---------|
| 0, 1 | Clear |
| 2 | Cloudy |
| 3 | Overcast |
| 45, 48 | Fog |
| 51, 53, 55, 56, 57 | Drizzle |
| 61, 63, 65, 66, 67 | Rain |
| 71, 73, 75, 77 | Snow |
| 80, 81, 82 | Showers |
| 85, 86 | Snow |
| 95, 96, 99 | Storms |

### Rate Limiting

No documented rate limit for personal use. The launcher calls this
endpoint at most once every 2 hours, which is well within acceptable
usage.
