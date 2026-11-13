package com.nexora.tv

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val midnight = Color(0xFF0A1228)
private val panel = Color(0xDD1B2946)
private val accent = Color(0xFF89B5FF)
private val white = Color(0xFFF6F8FF)
private val LocalPanel = compositionLocalOf { panel }
private val LocalAccent = compositionLocalOf { accent }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen(this) }
    }

    fun installedApps(): List<TvApp> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(intent, 0)
        return apps.map { TvApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != packageName }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    fun openApp(packageName: String) {
        val launch = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) runCatching { startActivity(launch) }
    }

    fun mediaKey(code: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun openTvInput() {
        val candidates = listOf("com.google.android.tv", "com.android.tv", "com.xiaomi.mitv.tvplayer")
        val target = candidates.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
        if (target != null) openApp(target) else runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

data class TvApp(val name: String, val packageName: String)

@Composable
fun LauncherScreen(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("nexora", Activity.MODE_PRIVATE) }
    var page by remember { mutableStateOf("Home") }
    var themeDark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var uiMode by remember { mutableStateOf(prefs.getString("ui_mode", "balanced") ?: "balanced") }
    var showWeather by remember { mutableStateOf(prefs.getBoolean("weather", true)) }
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember { mutableStateOf("Weather unavailable") }
    var apps by remember { mutableStateOf(activity.installedApps()) }
    var favorites by remember { mutableStateOf(prefs.getString("favorites", "")!!.split("|").filter { it.isNotBlank() }) }
    var wallpaperOn by remember { mutableStateOf(prefs.getBoolean("daily_wallpaper", true)) }
    var wallpaperOffset by remember { mutableIntStateOf(prefs.getInt("wallpaper_offset", 0)) }
    var showCalendar by remember { mutableStateOf(prefs.getBoolean("calendar", true)) }
    var showMedia by remember { mutableStateOf(prefs.getBoolean("media", true)) }
    var showNetwork by remember { mutableStateOf(prefs.getBoolean("network", true)) }
    val background = when { !themeDark -> Color(0xFFE1E9F5); uiMode == "lite" -> Color(0xFF080C14); uiMode == "premium" -> Color(0xFF101D3A); else -> midnight }
    val activePanel = when (uiMode) { "lite" -> Color(0xFF18202D); "premium" -> Color(0xCC24395C); else -> panel }
    val activeAccent = if (uiMode == "lite") Color(0xFFAAC8ED) else accent
    val foreground = if (themeDark) white else Color(0xFF172442)
    val palettes = listOf(
        listOf(Color(0xFF10182E), Color(0xFF3B4C78), Color(0xFF9A5575)),
        listOf(Color(0xFF0A1C2D), Color(0xFF28556A), Color(0xFFAF7B71)),
        listOf(Color(0xFF14152D), Color(0xFF4D4274), Color(0xFF9C648A))
    )
    val wallpaperIndex = Math.floorMod((clock.toLocalDate().toEpochDay() + wallpaperOffset).toInt(), palettes.size)
    val wallpaperBrush = Brush.verticalGradient(if (wallpaperOn && uiMode != "lite") palettes[wallpaperIndex] else listOf(background, background))
    LaunchedEffect(Unit) { while (true) { clock = LocalDateTime.now(); delay(30_000) } }
    LaunchedEffect(showWeather) {
        if (showWeather) {
            val cached = prefs.getString("weather_cache", null)
            if (cached != null) weather = cached
            val lastFetch = prefs.getLong("weather_last_fetch", 0L)
            if (cached != null && System.currentTimeMillis() - lastFetch < 60 * 60 * 1000L) return@LaunchedEffect
            weather = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val conn = URL("https://api.open-meteo.com/v1/forecast?latitude=9.93&longitude=76.27&current=temperature_2m&timezone=auto").openConnection().apply {
                        connectTimeout = 4000; readTimeout = 4000
                    }
                    val value = conn.getInputStream().bufferedReader().use { it.readText() }
                    "${JSONObject(value).getJSONObject("current").getDouble("temperature_2m").toInt()}°C · Kochi"
                }.getOrDefault(cached ?: "Weather unavailable")
            }
            if (weather != "Weather unavailable") prefs.edit().putString("weather_cache", weather).putLong("weather_last_fetch", System.currentTimeMillis()).apply()
        }
    }
    CompositionLocalProvider(LocalPanel provides activePanel, LocalAccent provides activeAccent) {
    Column(Modifier.fillMaxSize().background(wallpaperBrush).padding(horizontal = 28.dp, vertical = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NEXORA TV", color = foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            listOf("Home", "Apps", "Inputs", "Widgets", "Customize", "Settings").forEach { tab ->
                FocusTile(tab, selected = page == tab, onClick = { page = tab })
            }
        }
        if (page == "Home") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPanel(Modifier.weight(1.1f), "TIME", clock.format(DateTimeFormatter.ofPattern("h:mm a")), clock.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
                if (showWeather) InfoPanel(Modifier.weight(1f), "WEATHER", weather, "Open-Meteo · refresh on launch")
                if (showCalendar) InfoPanel(Modifier.weight(1f), "CALENDAR", clock.format(DateTimeFormatter.ofPattern("d MMMM")), clock.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() })
                if (showNetwork) InfoPanel(Modifier.weight(.75f), "NETWORK", if (isConnected(activity)) "Connected" else "Offline", "Android connectivity")
            }
            Section("Favorite Apps", foreground)
            val ordered = apps.sortedBy { if (favorites.contains(it.packageName)) 0 else 1 }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ordered.take(10)) { app -> FocusTile(app.name, large = true, onClick = { activity.openApp(app.packageName) }, onLongClick = { favorites = if (favorites.contains(app.packageName)) favorites - app.packageName else favorites + app.packageName; prefs.edit().putString("favorites", favorites.joinToString("|")).apply() }) }
                item { FocusTile("All Apps", large = true, onClick = { page = "Apps" }) }
            }
            Section("TV & Media Inputs", foreground)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("Live TV", "HDMI / Source", "USB Media", "Screen Cast")) { name ->
                    FocusTile(name, large = true, onClick = {
                        when (name) {
                            "Live TV", "HDMI / Source" -> activity.openTvInput()
                            "USB Media" -> runCatching { activity.startActivity(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)) }
                            else -> runCatching { activity.startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }
                        }
                    })
                }
            }
            if (showMedia) {
                Section("Now Playing", foreground)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusTile("Previous", onClick = { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
                    FocusTile("Play / Pause", onClick = { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
                    FocusTile("Next", onClick = { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) })
                }
            }
            FocusTile("Next wallpaper", onClick = { wallpaperOffset++; prefs.edit().putInt("wallpaper_offset", wallpaperOffset).apply() })
        } else if (page == "Apps") {
            Section("Installed TV Apps", foreground)
            apps.chunked(5).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { app -> FocusTile(app.name, large = true, onClick = { activity.openApp(app.packageName) }, onLongClick = { favorites = if (favorites.contains(app.packageName)) favorites - app.packageName else favorites + app.packageName; prefs.edit().putString("favorites", favorites.joinToString("|")).apply() }) }
            } }
            FocusTile("Refresh app list", onClick = { apps = activity.installedApps() })
        } else if (page == "Inputs") {
            Section("TV Inputs", foreground)
            Text("HDMI source switching depends on Xiaomi firmware. Use the TV Input/Source remote key if the shortcut does not work.", color = foreground)
            FocusTile("Open TV / Source app", onClick = { activity.openTvInput() })
            FocusTile("Android Settings", onClick = { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) })
        } else if (page == "Widgets" || page == "Customize") {
            Section("Home Customization", foreground)
            FocusTile("Weather: " + if (showWeather) "On" else "Off", onClick = { showWeather = !showWeather; prefs.edit().putBoolean("weather", showWeather).apply() })
            FocusTile("Calendar: " + if (showCalendar) "On" else "Off", onClick = { showCalendar = !showCalendar; prefs.edit().putBoolean("calendar", showCalendar).apply() })
            FocusTile("Network: " + if (showNetwork) "On" else "Off", onClick = { showNetwork = !showNetwork; prefs.edit().putBoolean("network", showNetwork).apply() })
            FocusTile("Media: " + if (showMedia) "On" else "Off", onClick = { showMedia = !showMedia; prefs.edit().putBoolean("media", showMedia).apply() })
            FocusTile("Daily background: " + if (wallpaperOn) "On" else "Off", onClick = { wallpaperOn = !wallpaperOn; prefs.edit().putBoolean("daily_wallpaper", wallpaperOn).apply() })
            FocusTile("Next background", onClick = { wallpaperOffset++; prefs.edit().putInt("wallpaper_offset", wallpaperOffset).apply() })
        } else {
            Section("Appearance & Preferences", foreground)
            Text("Performance mode (saved on device)", color = foreground)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("lite", "balanced", "premium").forEach { mode ->
                    FocusTile(mode.replaceFirstChar { it.uppercase() }, selected = uiMode == mode, onClick = {
                        uiMode = mode
                        prefs.edit().putString("ui_mode", mode).apply()
                    })
                }
            }
            Text("Lite uses opaque dark cards; Balanced uses subtle glass; Premium uses richer translucent panels. All modes avoid real-time blur.", color = foreground, fontSize = 12.sp)
            FocusTile("Theme: ${if (themeDark) "Dark" else "Light"}", onClick = { themeDark = !themeDark; prefs.edit().putBoolean("dark", themeDark).apply() })
            FocusTile("Weather: ${if (showWeather) "On" else "Off"}", onClick = { showWeather = !showWeather; prefs.edit().putBoolean("weather", showWeather).apply() })
            Text("Ambient screensaver: select Nexora Ambient in your TV's screensaver settings if supported.", color = foreground)
            Text("Daily wallpaper, location settings and advanced widget layouts are upcoming features.", color = foreground)
        }
        Text("D-pad: Navigate   •   OK: Open   •   Long OK: Favorite app   •   Back: Exit", fontSize = 12.sp, color = foreground.copy(alpha = .65f))
    }
    }
}

private fun isConnected(activity: Activity): Boolean {
    val cm = activity.getSystemService(ConnectivityManager::class.java)
    return cm.activeNetwork != null
}

@Composable private fun Section(text: String, color: Color) { Text(text, fontSize = 20.sp, color = color, fontWeight = FontWeight.SemiBold) }

@Composable private fun InfoPanel(modifier: Modifier, label: String, value: String, detail: String) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(LocalPanel.current).border(1.dp, Color(0xFF435A86), RoundedCornerShape(18.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = LocalAccent.current)
        Text(value, fontSize = 24.sp, color = white, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, fontSize = 12.sp, color = white.copy(alpha = .75f), maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FocusTile(label: String, selected: Boolean = false, large: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.width(if (large) 154.dp else 112.dp).height(if (large) 90.dp else 48.dp)
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(14.dp))
        .background(if (focused || selected) Color(0xFF304D88) else LocalPanel.current)
        .border(if (focused) 3.dp else 1.dp, if (focused) LocalAccent.current else Color(0xFF435A86), RoundedCornerShape(14.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = if (large) 16.sp else 14.sp, fontWeight = FontWeight.Medium, color = white, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
