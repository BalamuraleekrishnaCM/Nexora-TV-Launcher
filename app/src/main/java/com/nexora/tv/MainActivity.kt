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
    var showWeather by remember { mutableStateOf(prefs.getBoolean("weather", true)) }
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember { mutableStateOf("Weather unavailable") }
    var apps by remember { mutableStateOf(activity.installedApps()) }
    var favorite by remember { mutableStateOf(prefs.getString("favorite", "") ?: "") }
    val background = if (themeDark) midnight else Color(0xFFE1E9F5)
    val foreground = if (themeDark) white else Color(0xFF172442)
    LaunchedEffect(Unit) { while (true) { clock = LocalDateTime.now(); delay(30_000) } }
    LaunchedEffect(showWeather) {
        if (showWeather) {
            // User-configurable location should replace these example coordinates in a future settings screen.
            weather = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val conn = URL("https://api.open-meteo.com/v1/forecast?latitude=9.93&longitude=76.27&current=temperature_2m&timezone=auto").openConnection().apply {
                        connectTimeout = 4000; readTimeout = 4000
                    }
                    val value = conn.getInputStream().bufferedReader().use { it.readText() }
                    "${JSONObject(value).getJSONObject("current").getDouble("temperature_2m").toInt()}°C · Kochi"
                }.getOrDefault("Weather unavailable")
            }
        }
    }
    Column(Modifier.fillMaxSize().background(background).padding(horizontal = 28.dp, vertical = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NEXORA TV", color = foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            listOf("Home", "Apps", "Inputs", "Settings").forEach { tab ->
                FocusTile(tab, selected = page == tab, onClick = { page = tab })
            }
        }
        if (page == "Home") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPanel(Modifier.weight(1.1f), "TIME", clock.format(DateTimeFormatter.ofPattern("h:mm a")), clock.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
                if (showWeather) InfoPanel(Modifier.weight(1f), "WEATHER", weather, "Open-Meteo · refresh on launch")
                InfoPanel(Modifier.weight(1f), "CALENDAR", clock.format(DateTimeFormatter.ofPattern("d MMMM")), clock.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() })
                InfoPanel(Modifier.weight(.75f), "NETWORK", if (isConnected(activity)) "Connected" else "Offline", "Android connectivity")
            }
            Section("Favorite Apps", foreground)
            val ordered = apps.sortedBy { if (it.packageName == favorite) 0 else 1 }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ordered.take(10)) { app -> FocusTile(app.name, large = true, onClick = { activity.openApp(app.packageName) }, onLongClick = { favorite = app.packageName; prefs.edit().putString("favorite", favorite).apply() }) }
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
            InfoPanel(Modifier.fillMaxWidth(), "NOW PLAYING", "Media controls", "Requires a compatible active media session; integration planned")
        } else if (page == "Apps") {
            Section("Installed TV Apps", foreground)
            apps.chunked(5).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { app -> FocusTile(app.name, large = true, onClick = { activity.openApp(app.packageName) }, onLongClick = { favorite = app.packageName; prefs.edit().putString("favorite", favorite).apply() }) }
            } }
            FocusTile("Refresh app list", onClick = { apps = activity.installedApps() })
        } else if (page == "Inputs") {
            Section("TV Inputs", foreground)
            Text("HDMI source switching depends on Xiaomi firmware. Use the TV Input/Source remote key if the shortcut does not work.", color = foreground)
            FocusTile("Open TV / Source app", onClick = { activity.openTvInput() })
            FocusTile("Android Settings", onClick = { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) })
        } else {
            Section("Appearance & Preferences", foreground)
            FocusTile("Theme: ${if (themeDark) "Dark" else "Light"}", onClick = { themeDark = !themeDark; prefs.edit().putBoolean("dark", themeDark).apply() })
            FocusTile("Weather: ${if (showWeather) "On" else "Off"}", onClick = { showWeather = !showWeather; prefs.edit().putBoolean("weather", showWeather).apply() })
            Text("Ambient screensaver: select Nexora Ambient in your TV's screensaver settings if supported.", color = foreground)
            Text("Daily wallpaper, location settings and advanced widget layouts are upcoming features.", color = foreground)
        }
        Text("D-pad: Navigate   •   OK: Open   •   Long OK: Favorite app   •   Back: Exit", fontSize = 12.sp, color = foreground.copy(alpha = .65f))
    }
}

private fun isConnected(activity: Activity): Boolean {
    val cm = activity.getSystemService(ConnectivityManager::class.java)
    return cm.activeNetwork != null
}

@Composable private fun Section(text: String, color: Color) { Text(text, fontSize = 20.sp, color = color, fontWeight = FontWeight.SemiBold) }

@Composable private fun InfoPanel(modifier: Modifier, label: String, value: String, detail: String) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(panel).border(1.dp, Color(0xFF435A86), RoundedCornerShape(18.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = accent)
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
        .background(if (focused || selected) Color(0xFF304D88) else panel)
        .border(if (focused) 3.dp else 1.dp, if (focused) accent else Color(0xFF435A86), RoundedCornerShape(14.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = if (large) 16.sp else 14.sp, fontWeight = FontWeight.Medium, color = white, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
