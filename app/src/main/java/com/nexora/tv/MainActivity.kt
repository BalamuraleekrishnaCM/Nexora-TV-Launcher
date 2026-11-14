package com.nexora.tv

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

private val midnight = Color(0xFF061329)
private val deepBlue = Color(0xFF0A1C3A)
private val panel = Color(0x601B2B4D)
private val panelStrong = Color(0x78203155)
private val panelEdge = Color(0xFF6585C9)
private val neonBlue = Color(0xFF8CB6FF)
private val electricBlue = Color(0xFF3E80FF)
private val violet = Color(0xFFB76CFF)
private val mint = Color(0xFF35D589)
private val white = Color(0xFFF7F8FF)
private val muted = Color(0xFFBBC5DB)
private val LocalPanel = compositionLocalOf { panel }
private val LocalAccent = compositionLocalOf { neonBlue }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen(this) }
    }

    fun installedApps(): List<TvApp> {
        val pm = packageManager
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return (pm.queryIntentActivities(tvIntent, 0) + pm.queryIntentActivities(mobileIntent, 0))
            .map {
                TvApp(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    artwork = it.activityInfo.loadBanner(pm) ?: it.loadIcon(pm)
                )
            }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
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

data class TvApp(val name: String, val packageName: String, val artwork: Drawable?)

@Composable
fun LauncherScreen(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("nexora", Activity.MODE_PRIVATE) }
    var page by remember { mutableStateOf("Home") }
    var uiMode by remember { mutableStateOf(prefs.getString("ui_mode", "premium") ?: "premium") }
    var showWeather by remember { mutableStateOf(prefs.getBoolean("weather", true)) }
    var showCalendar by remember { mutableStateOf(prefs.getBoolean("calendar", true)) }
    var showMedia by remember { mutableStateOf(prefs.getBoolean("media", true)) }
    var showNetwork by remember { mutableStateOf(prefs.getBoolean("network", true)) }
    var wallpaperOn by remember { mutableStateOf(prefs.getBoolean("daily_wallpaper", true)) }
    var wallpaperOffset by remember { mutableIntStateOf(prefs.getInt("wallpaper_offset", 0)) }
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember { mutableStateOf("Weather unavailable") }
    var apps by remember { mutableStateOf(activity.installedApps()) }
    var favorites by remember {
        mutableStateOf(prefs.getString("favorites", "")!!.split("|").filter { it.isNotBlank() })
    }
    val defaultRowOrder = listOf("apps", "inputs", "media")
    var rowOrder by remember {
        val saved = prefs.getString("row_order", null)
            ?.split("|")
            ?.filter { it in defaultRowOrder }
            .orEmpty()
        mutableStateOf(if (saved.toSet() == defaultRowOrder.toSet()) saved else defaultRowOrder)
    }

    val palettes = listOf(
        listOf(Color(0xFF061329), Color(0xFF132A58), Color(0xFFB45573)),
        listOf(Color(0xFF071A31), Color(0xFF193E5A), Color(0xFFB17472)),
        listOf(Color(0xFF0A1331), Color(0xFF342E68), Color(0xFFA6588B))
    )
    val wallpaperIndex = Math.floorMod(
        (clock.toLocalDate().toEpochDay() + wallpaperOffset).toInt(),
        palettes.size
    )
    val activePalette = if (wallpaperOn && uiMode != "lite") {
        palettes[wallpaperIndex]
    } else {
        listOf(midnight, deepBlue, Color(0xFF162447))
    }
    val activePanel = when (uiMode) {
        "lite" -> Color(0xF21A263C)
        "balanced" -> Color(0x901B2B4D)
        else -> panel
    }
    val activeAccent = if (uiMode == "lite") Color(0xFFB8CCEF) else neonBlue
    val configuration = LocalConfiguration.current
    val systemDensity = LocalDensity.current
    // Respect Android's actual density. Artificial density scaling made the launcher
    // overflow or render too small on TVs with nonstandard DPI settings.
    val designDensity = systemDensity.density

    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalDateTime.now()
            delay(30_000)
        }
    }
    LaunchedEffect(showWeather) {
        if (showWeather) {
            val cached = prefs.getString("weather_cache", null)
            if (cached != null) weather = cached
            val lastFetch = prefs.getLong("weather_last_fetch", 0L)
            if (cached != null && System.currentTimeMillis() - lastFetch < 60 * 60 * 1000L) {
                return@LaunchedEffect
            }
            weather = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val connection = URL(
                        "https://api.open-meteo.com/v1/forecast?latitude=9.93&longitude=76.27&current=temperature_2m&timezone=auto"
                    ).openConnection().apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                    }
                    val body = connection.getInputStream().bufferedReader().use { it.readText() }
                    "${JSONObject(body).getJSONObject("current").getDouble("temperature_2m").toInt()}°C · Kochi"
                }.getOrDefault(cached ?: "Weather unavailable")
            }
            if (weather != "Weather unavailable") {
                prefs.edit()
                    .putString("weather_cache", weather)
                    .putLong("weather_last_fetch", System.currentTimeMillis())
                    .apply()
            }
        }
    }

    CompositionLocalProvider(
        LocalPanel provides activePanel,
        LocalAccent provides activeAccent,
        LocalDensity provides Density(designDensity, systemDensity.fontScale)
    ) {
        Box(Modifier.fillMaxSize().background(midnight)) {
            MountainBackdrop(activePalette)
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = 36.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LauncherHeader(page, clock, isConnected(activity), onPage = { page = it })
                when (page) {
                    "Home" -> HomePage(
                        activity = activity,
                        clock = clock,
                        weather = weather,
                        apps = apps,
                        favorites = favorites,
                        showWeather = showWeather,
                        showCalendar = showCalendar,
                        showNetwork = showNetwork,
                        showMedia = showMedia,
                        rowOrder = rowOrder,
                        onToggleFavorite = { packageName ->
                            favorites = if (favorites.contains(packageName)) {
                                favorites - packageName
                            } else {
                                favorites + packageName
                            }
                            prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
                        },
                        onPage = { page = it }
                    )
                    "Apps" -> AppsPage(activity, apps, favorites, onRefresh = {
                        apps = activity.installedApps()
                    }, onToggleFavorite = { packageName ->
                        favorites = if (favorites.contains(packageName)) favorites - packageName else favorites + packageName
                        prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
                    })
                    "Inputs" -> InputsPage(activity)
                    "Media" -> MediaPage(activity)
                    "Widgets" -> WidgetsPage(
                        weather = showWeather,
                        calendar = showCalendar,
                        network = showNetwork,
                        media = showMedia,
                        rowOrder = rowOrder,
                        onWeather = {
                            showWeather = !showWeather
                            prefs.edit().putBoolean("weather", showWeather).apply()
                        },
                        onCalendar = {
                            showCalendar = !showCalendar
                            prefs.edit().putBoolean("calendar", showCalendar).apply()
                        },
                        onNetwork = {
                            showNetwork = !showNetwork
                            prefs.edit().putBoolean("network", showNetwork).apply()
                        },
                        onMedia = {
                            showMedia = !showMedia
                            prefs.edit().putBoolean("media", showMedia).apply()
                        },
                        onMoveRow = { key, direction ->
                            val from = rowOrder.indexOf(key)
                            val to = (from + direction).coerceIn(0, rowOrder.lastIndex)
                            if (from >= 0 && from != to) {
                                rowOrder = rowOrder.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                                prefs.edit().putString("row_order", rowOrder.joinToString("|")).apply()
                            }
                        }
                    )
                    else -> SettingsPage(
                        uiMode = uiMode,
                        wallpaperOn = wallpaperOn,
                        onMode = {
                            uiMode = it
                            prefs.edit().putString("ui_mode", it).apply()
                        },
                        onWallpaper = {
                            wallpaperOn = !wallpaperOn
                            prefs.edit().putBoolean("daily_wallpaper", wallpaperOn).apply()
                        },
                        onNextWallpaper = {
                            wallpaperOffset++
                            prefs.edit().putInt("wallpaper_offset", wallpaperOffset).apply()
                        }
                    )
                }
                RemoteHelpBar(onCustomize = { page = "Widgets" })
            }
        }
    }
}

@Composable
private fun LauncherHeader(page: String, clock: LocalDateTime, connected: Boolean, onPage: (String) -> Unit) {
    val tabs = listOf(
        "⌂" to "Home",
        "▦" to "Apps",
        "▭" to "Inputs",
        "▷" to "Media",
        "◇" to "Widgets",
        "⚙" to "Settings"
    )
    Row(
        Modifier.fillMaxWidth().height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.width(292.dp), verticalAlignment = Alignment.CenterVertically) {
            NexoraMark()
            Spacer(Modifier.width(14.dp))
            Column {
                Text("NEXORA TV", color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("TV LAUNCHER", color = muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
        tabs.forEach { (icon, label) ->
            NavButton(icon, label, selected = page == label, onClick = { onPage(label) })
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(clock.format(DateTimeFormatter.ofPattern("h:mm a")), color = white, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (connected) "● Online  ·  " else "○ Offline  ·  ") + clock.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                color = if (connected) muted else Color(0xFFFFA6A6),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun NexoraMark() {
    Canvas(Modifier.size(48.dp)) {
        val leftRibbon = Path().apply {
            moveTo(size.width * .08f, size.height * .12f)
            lineTo(size.width * .48f, size.height * .50f)
            lineTo(size.width * .48f, size.height * .86f)
            lineTo(size.width * .08f, size.height * .48f)
            close()
        }
        val rightRibbon = Path().apply {
            moveTo(size.width * .92f, size.height * .12f)
            lineTo(size.width * .52f, size.height * .50f)
            lineTo(size.width * .52f, size.height * .86f)
            lineTo(size.width * .92f, size.height * .48f)
            close()
        }
        val centerRibbon = Path().apply {
            moveTo(size.width * .08f, size.height * .12f)
            lineTo(size.width * .32f, size.height * .12f)
            lineTo(size.width * .92f, size.height * .70f)
            lineTo(size.width * .92f, size.height * .88f)
            lineTo(size.width * .70f, size.height * .88f)
            lineTo(size.width * .08f, size.height * .30f)
            close()
        }
        drawPath(leftRibbon, Brush.linearGradient(listOf(Color(0xFF16E4FF), Color(0xFF2676FF))))
        drawPath(rightRibbon, Brush.linearGradient(listOf(Color(0xFFFF64D4), Color(0xFF885CFF))))
        drawPath(centerRibbon, Brush.linearGradient(listOf(Color(0xFF20D7FF), Color(0xFF8C61FF), Color(0xFFFF60CF))))
    }
}

@Composable
private fun NavButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(
        modifier = Modifier.width(if (label == "Settings") 142.dp else 128.dp).height(54.dp),
        selected = selected,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(icon, color = white, fontSize = 24.sp)
            Spacer(Modifier.width(9.dp))
            Text(label, color = white, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomePage(
    activity: MainActivity,
    clock: LocalDateTime,
    weather: String,
    apps: List<TvApp>,
    favorites: List<String>,
    showWeather: Boolean,
    showCalendar: Boolean,
    showNetwork: Boolean,
    showMedia: Boolean,
    rowOrder: List<String>,
    onToggleFavorite: (String) -> Unit,
    onPage: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(190.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ClockCard(Modifier.weight(1.15f), clock)
        if (showWeather) WeatherCard(Modifier.weight(1f), weather)
        if (showNetwork) NetworkCard(Modifier.weight(.72f), isConnected(activity))
        if (showCalendar) CalendarCard(Modifier.weight(1.05f), clock)
    }

    rowOrder.forEach { row ->
        when (row) {
            "apps" -> FavoriteAppsRow(activity, apps, favorites, onToggleFavorite, onPage)
            "inputs" -> {
                SectionTitle("TV & Media Inputs")
                InputRow(activity)
            }
            "media" -> if (showMedia) MediaBar(activity)
        }
    }
}

@Composable
private fun FavoriteAppsRow(
    activity: MainActivity,
    apps: List<TvApp>,
    favorites: List<String>,
    onToggleFavorite: (String) -> Unit,
    onPage: (String) -> Unit
) {
    SectionTitle("Favorite Apps")
    val orderedApps = apps.sortedWith(
        compareBy<TvApp> { if (favorites.contains(it.packageName)) 0 else 1 }
            .thenBy { it.name.lowercase() }
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(orderedApps.take(7), key = { it.packageName }) { app ->
            AppTile(
                app = app,
                favorite = favorites.contains(app.packageName),
                onClick = { activity.openApp(app.packageName) },
                onLongClick = { onToggleFavorite(app.packageName) }
            )
        }
        item { AllAppsTile { onPage("Apps") } }
    }
}

@Composable
private fun ClockCard(modifier: Modifier, clock: LocalDateTime) {
    GlassPanel(
        modifier,
        Brush.linearGradient(listOf(Color(0xB5293C69), Color(0xA9493A66), Color(0x988B526B)))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(clock.format(DateTimeFormatter.ofPattern("h:mm")), color = white, fontSize = 68.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(clock.format(DateTimeFormatter.ofPattern("a")), color = white, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(clock.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), color = muted, fontSize = 19.sp)
        }
    }
}

@Composable
private fun WeatherCard(modifier: Modifier, weather: String) {
    val temperature = weather.substringBefore("·").trim()
    val location = weather.substringAfter("·", "Kochi").trim()
    GlassPanel(modifier, Brush.linearGradient(listOf(Color(0xB5283A64), Color(0xA2443A68)))) {
        Text("☀", color = Color(0xFFFFCC29), fontSize = 62.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(temperature, color = white, fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Partly Cloudy", color = muted, fontSize = 17.sp)
            Text("⌖  $location, Kerala", color = white.copy(alpha = .88f), fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("↑  31°C", color = white, fontSize = 14.sp)
            Text("↓  24°C", color = white, fontSize = 14.sp)
        }
    }
}

@Composable
private fun NetworkCard(modifier: Modifier, connected: Boolean) {
    GlassPanel(modifier, Brush.linearGradient(listOf(Color(0xAE253A62), Color(0xA64C3C69)))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiMark()
            Text("Wi‑Fi", color = white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(if (connected) "Connected" else "Offline", color = if (connected) mint else Color(0xFFFF9A9A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (connected) "XRHorizon_5G" else "Check connection", color = muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WifiMark() {
    Canvas(Modifier.size(50.dp)) {
        val line = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        drawArc(mint, startAngle = 215f, sweepAngle = 110f, useCenter = false, style = line)
        val inset = size.width * .20f
        drawArc(
            mint,
            startAngle = 215f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            style = line
        )
        drawCircle(mint, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * .82f))
    }
}

@Composable
private fun CalendarCard(modifier: Modifier, clock: LocalDateTime) {
    val today = clock.toLocalDate()
    GlassPanel(modifier, Brush.linearGradient(listOf(Color(0xAE283B63), Color(0xA5473A66)))) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = white, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (-3..3).forEach { offset ->
                    val date = today.plusDays(offset.toLong())
                    val selected = offset == 0
                    Column(
                        Modifier.width(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) electricBlue else Color.Transparent)
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(date.format(DateTimeFormatter.ofPattern("EE")), color = muted, fontSize = 10.sp)
                        Text(date.dayOfMonth.toString(), color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("No upcoming events", color = muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier, background: Brush, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .10f), Color.Transparent, violet.copy(alpha = .07f))
                )
            )
            .border(1.dp, panelEdge.copy(alpha = .95f), RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AppTile(app: TvApp, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val image = remember(app.packageName) {
        runCatching { app.artwork?.toBitmap(128, 96)?.asImageBitmap() }.getOrNull()
    }
    FocusSurface(
        modifier = Modifier.width(190.dp).height(122.dp),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = app.name,
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(app.name, color = white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (favorite) Text("★ Favorite", color = LocalAccent.current, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AllAppsTile(onClick: () -> Unit) {
    FocusSurface(Modifier.width(170.dp).height(122.dp), onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▦", color = white, fontSize = 42.sp)
            Text("All Apps", color = white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InputRow(activity: MainActivity) {
    val inputs = listOf(
        Triple("▣", "Live TV", "Channels"),
        Triple("▱", "HDMI 1", "Gaming / STB"),
        Triple("▱", "HDMI 2", "Laptop / Console"),
        Triple("▱", "HDMI 3", "Media"),
        Triple("●●●", "AV", "Set top box"),
        Triple("↯", "USB Media", "Movies / Photos"),
        Triple("◩", "Screen Cast", "Cast from phone")
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(inputs) { (icon, label, detail) ->
            FocusSurface(
                modifier = Modifier.width(205.dp).height(94.dp),
                onClick = {
                    when (label) {
                        "USB Media" -> runCatching {
                            activity.startActivity(
                                Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                            )
                        }
                        "Screen Cast" -> runCatching { activity.startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }
                        else -> activity.openTvInput()
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(label, color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(detail, color = muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBar(activity: MainActivity) {
    Row(
        Modifier.fillMaxWidth().height(116.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xB823355B), Color(0xA818294A))))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .08f), Color.Transparent)))
            .border(1.dp, panelEdge, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(violet, Color(0xFF1B6DB1), Color(0xFFFF8A78))))
        ) {
            Text("♫", Modifier.align(Alignment.Center), color = white, fontSize = 34.sp)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text("Ready for playback", color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Open a media app to begin", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF596889)))
        }
        Spacer(Modifier.width(22.dp))
        MediaControl("|◀") { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        Spacer(Modifier.width(12.dp))
        MediaControl("▶") { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        Spacer(Modifier.width(12.dp))
        MediaControl("▶|") { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

@Composable
private fun MediaControl(label: String, onClick: () -> Unit) {
    FocusSurface(Modifier.size(62.dp), onClick = onClick, circular = true) {
        Text(label, color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AppsPage(
    activity: MainActivity,
    apps: List<TvApp>,
    favorites: List<String>,
    onRefresh: () -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    PageTitle("Installed TV Apps", "Long-press OK to add or remove a favorite")
    apps.chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { app ->
                AppTile(app, favorites.contains(app.packageName), { activity.openApp(app.packageName) }) {
                    onToggleFavorite(app.packageName)
                }
            }
        }
    }
    ActionTile("↻", "Refresh app list", "Scan for newly installed TV apps", onRefresh)
}

@Composable
private fun InputsPage(activity: MainActivity) {
    PageTitle("TV & Media Inputs", "Hardware source switching depends on the TV manufacturer")
    InputRow(activity)
    Text(
        "If an HDMI shortcut cannot switch sources, use the physical remote's Input/Source key.",
        color = muted,
        fontSize = 14.sp
    )
    ActionTile("⚙", "Android TV settings", "Open device input and display settings") {
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@Composable
private fun MediaPage(activity: MainActivity) {
    PageTitle("Media", "Controls are sent to the active Android media session")
    MediaBar(activity)
    ActionTile("♫", "Open an installed media app", "Choose a player from the Apps page") { }
}

@Composable
private fun WidgetsPage(
    weather: Boolean,
    calendar: Boolean,
    network: Boolean,
    media: Boolean,
    rowOrder: List<String>,
    onWeather: () -> Unit,
    onCalendar: () -> Unit,
    onNetwork: () -> Unit,
    onMedia: () -> Unit,
    onMoveRow: (String, Int) -> Unit
) {
    PageTitle("Customize Home", "Choose widgets and arrange the home rows")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ToggleTile("☀", "Weather", weather, onWeather)
        ToggleTile("□", "Calendar", calendar, onCalendar)
        ToggleTile("⌁", "Network", network, onNetwork)
        ToggleTile("♫", "Now Playing", media, onMedia)
    }
    SectionTitle("Arrange Home Rows")
    rowOrder.forEachIndexed { index, key ->
        val title = when (key) {
            "apps" -> "Favorite Apps"
            "inputs" -> "TV & Media Inputs"
            else -> "Now Playing"
        }
        val icon = when (key) {
            "apps" -> "▦"
            "inputs" -> "▭"
            else -> "♫"
        }
        RowOrderTile(
            position = index + 1,
            icon = icon,
            title = title,
            canMoveUp = index > 0,
            canMoveDown = index < rowOrder.lastIndex,
            onMoveUp = { onMoveRow(key, -1) },
            onMoveDown = { onMoveRow(key, 1) }
        )
    }
}

@Composable
private fun RowOrderTile(
    position: Int,
    icon: String,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(LocalPanel.current, panelStrong.copy(alpha = .72f))))
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent)))
                .border(1.dp, panelEdge.copy(alpha = .9f), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(position.toString(), color = LocalAccent.current, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(18.dp))
            Text(icon, color = white, fontSize = 25.sp)
            Spacer(Modifier.width(14.dp))
            Text(title, color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Use arrows to move this row", color = muted, fontSize = 12.sp)
        }
        FocusSurface(Modifier.size(58.dp), onClick = { if (canMoveUp) onMoveUp() }) {
            Text("↑", color = if (canMoveUp) white else muted.copy(alpha = .35f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        FocusSurface(Modifier.size(58.dp), onClick = { if (canMoveDown) onMoveDown() }) {
            Text("↓", color = if (canMoveDown) white else muted.copy(alpha = .35f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsPage(
    uiMode: String,
    wallpaperOn: Boolean,
    onMode: (String) -> Unit,
    onWallpaper: () -> Unit,
    onNextWallpaper: () -> Unit
) {
    PageTitle("Appearance & Preferences", "Saved locally on this Android TV")
    SectionTitle("Performance mode")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("lite", "balanced", "premium").forEach { mode ->
            FocusSurface(
                Modifier.width(190.dp).height(76.dp),
                selected = uiMode == mode,
                onClick = { onMode(mode) }
            ) {
                Text(mode.replaceFirstChar { it.uppercase() }, color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Text(
        "Lite uses opaque cards. Balanced and Premium use layered gradients without real-time blur.",
        color = muted,
        fontSize = 13.sp
    )
    ToggleTile("◈", "Daily background", wallpaperOn, onWallpaper)
    ActionTile("↻", "Next background", "Cycle to the next mountain palette", onNextWallpaper)
    Text("Ambient screensaver: select Nexora Ambient in Android TV screensaver settings.", color = muted, fontSize = 13.sp)
}

@Composable
private fun PageTitle(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = muted, fontSize = 14.sp)
    }
}

@Composable
private fun ToggleTile(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    FocusSurface(Modifier.width(210.dp).height(92.dp), selected = enabled, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = if (enabled) LocalAccent.current else muted, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(label, color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (enabled) "Enabled" else "Hidden", color = if (enabled) mint else muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActionTile(icon: String, title: String, detail: String, onClick: () -> Unit) {
    FocusSurface(Modifier.width(360.dp).height(84.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = LocalAccent.current, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RemoteHelpBar(onCustomize: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("◆  Navigate with remote", color = white, fontSize = 13.sp)
        Spacer(Modifier.width(26.dp))
        Text("OK  Open", color = muted, fontSize = 13.sp)
        Spacer(Modifier.width(26.dp))
        Text("☰  Menu / Options", color = muted, fontSize = 13.sp)
        Spacer(Modifier.width(26.dp))
        Text("↶  Back", color = muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        FocusSurface(Modifier.width(220.dp).height(50.dp), onClick = onCustomize) {
            Text("✎  Customize Layout", color = white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusSurface(
    modifier: Modifier,
    selected: Boolean = false,
    circular: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = if (circular) RoundedCornerShape(50) else RoundedCornerShape(18.dp)
    val fill = if (focused || selected) {
        Brush.linearGradient(listOf(Color(0xD5345897), Color(0xCF2A3D75), Color(0xC76B3E86)))
    } else {
        Brush.linearGradient(listOf(LocalPanel.current, panelStrong.copy(alpha = .78f)))
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = if (focused) 1.035f else 1f
                scaleY = if (focused) 1.035f else 1f
            }
            .shadow(if (focused) 14.dp else 0.dp, shape, clip = false)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(fill)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .06f), Color.Transparent)))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color(0xFFB8D4FF) else panelEdge.copy(alpha = .85f),
                shape = shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun MountainBackdrop(palette: List<Color>) {
    Box(Modifier.fillMaxSize().background(midnight)) {
        Image(
            painter = painterResource(R.drawable.nexora_mountain_sunset),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.35f
                scaleY = 1.35f
                translationY = -280f
            },
            contentScale = ContentScale.Crop
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        palette[0].copy(alpha = .18f),
                        Color.Transparent,
                        midnight.copy(alpha = .32f),
                        midnight.copy(alpha = .82f)
                    )
                )
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(midnight.copy(alpha = .18f), Color.Transparent, palette[1].copy(alpha = .10f))
                )
            )
        }
    }
}

private fun isConnected(activity: Activity): Boolean {
    val connectivity = activity.getSystemService(ConnectivityManager::class.java)
    return connectivity.activeNetwork != null
}
