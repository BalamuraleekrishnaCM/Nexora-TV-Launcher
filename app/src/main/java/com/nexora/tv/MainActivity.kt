package com.nexora.tv

import android.app.Activity
import android.media.MediaMetadata
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val midnight = Color(0xFF061329)
private val deepBlue = Color(0xFF0A1C3A)
private val panel = Color(0x601B2B4D)
private val panelEdge = Color(0xFF6585C9)
private val neonBlue = Color(0xFF8CB6FF)
private val electricBlue = Color(0xFF3E80FF)
private val violet = Color(0xFFB76CFF)
private val mint = Color(0xFF35D589)
private val white = Color(0xFFF7F8FF)
private val muted = Color(0xFFBBC5DB)
private val LocalPanel = compositionLocalOf { panel }
private val LocalCompact = compositionLocalOf { false }
private val LocalLite = compositionLocalOf { false }
private val LocalAccent = compositionLocalOf { neonBlue }

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("nexora", MODE_PRIVATE) }
    internal val mediaSessions by lazy { MediaSessions(this) }
    internal var apps by mutableStateOf<List<TvApp>>(emptyList())
        private set
    private var appScan: kotlinx.coroutines.Job? = null
    internal var wallpaperUri by mutableStateOf<Uri?>(null)
        private set

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && uri != wallpaperUri) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val previous = wallpaperUri
                prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
                wallpaperUri = uri
                releaseWallpaper(previous)
            } catch (_: SecurityException) {
                feedback("This provider cannot keep the image available. Choose another image.")
            }
        }
    }

    fun chooseWallpaper() {
        try {
            wallpaperPicker.launch(arrayOf("image/*"))
        } catch (_: ActivityNotFoundException) {
            feedback("A document picker is unavailable on this TV.")
        }
    }

    fun resetWallpaper() {
        val previous = wallpaperUri
        prefs.edit().remove("wallpaper_uri").apply()
        wallpaperUri = null
        releaseWallpaper(previous)
    }

    private fun releaseWallpaper(uri: Uri?) {
        if (uri != null) runCatching {
            contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private val usbPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            openIntent(
                Intent(Intent.ACTION_VIEW, uri)
                    .setDataAndType(uri, contentResolver.getType(uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Install a compatible media player to open this file."
            )
        }
    }

    fun browseUsb() {
        try {
            usbPicker.launch(arrayOf("video/*", "audio/*", "image/*"))
        } catch (_: ActivityNotFoundException) {
            feedback("A document picker is unavailable on this TV.")
        }
    }

    fun requestMediaAccess() {
        openIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Open notification access in device settings.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallpaperUri = prefs.getString("wallpaper_uri", null)?.let(Uri::parse)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        lifecycle.addObserver(mediaSessions)
        setContent { LauncherScreen(this) }
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
    }

    fun refreshApps() {
        appScan?.cancel()
        appScan = lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { installedApps() }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedApps(): List<TvApp> {
        val pm = packageManager
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return (pm.queryIntentActivities(tvIntent, 0) + pm.queryIntentActivities(mobileIntent, 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                TvApp(
                    name = it.loadLabel(pm).toString(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    resolveInfo = it
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun openApp(packageName: String) {
        val app = apps.firstOrNull { it.packageName == packageName } ?: return
        val launch = Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        openIntent(launch, "Unable to open ${app.name}. Refresh the app list.")
    }

    internal fun openIntent(intent: Intent, fallback: String) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            feedback(fallback)
        } catch (_: SecurityException) {
            feedback(fallback)
        }
    }

    internal fun feedback(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    fun mediaKey(code: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun tvInputs(): List<TvInputInfo> = runCatching {
        getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
    }.getOrDefault(emptyList())

    fun openTvInput(input: TvInputInfo? = null) {
        val fallback = "Use the TV remote’s Input or Source button."
        if (input?.isPassthroughInput == true) {
            openIntent(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)), fallback)
            return
        }
        val candidates = listOf("com.google.android.tv", "com.android.tv", "com.xiaomi.mitv.tvplayer")
        val launch = candidates.firstNotNullOfOrNull {
            packageManager.getLeanbackLaunchIntentForPackage(it) ?: packageManager.getLaunchIntentForPackage(it)
        }
        if (launch == null) feedback(fallback) else openIntent(launch, fallback)
    }
}

data class TvApp(val name: String, val component: ComponentName, val resolveInfo: android.content.pm.ResolveInfo) {
    val packageName: String get() = component.packageName
}

private suspend fun decodeArtwork(activity: Activity, uri: Uri?, limit: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        if (uri == null || uri.scheme !in setOf("content", "android.resource")) return@withContext null
        runCatching {
            val resolver = activity.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > limit * 2) inSampleSize *= 2
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
        }.getOrNull()
    }

@Composable
fun LauncherScreen(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("nexora", Activity.MODE_PRIVATE) }
    var page by rememberSaveable { mutableStateOf("Home") }
    var uiMode by remember { mutableStateOf(prefs.getString("ui_mode", "premium") ?: "premium") }
    var showWeather by remember { mutableStateOf(prefs.getBoolean("weather", true)) }
    var showCalendar by remember { mutableStateOf(prefs.getBoolean("calendar", true)) }
    var showMedia by remember { mutableStateOf(prefs.getBoolean("media", true)) }
    var showNetwork by remember { mutableStateOf(prefs.getBoolean("network", true)) }
    var wallpaperOn by remember { mutableStateOf(prefs.getBoolean("daily_wallpaper", true)) }
    var wallpaperOffset by remember { mutableIntStateOf(prefs.getInt("wallpaper_offset", 0)) }
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var weatherStatus by remember { mutableStateOf("") }
    val lifecycleState by activity.lifecycle.currentStateFlow.collectAsState()
    val started = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    var tick by remember { mutableIntStateOf(0) }
    var opacity by remember { mutableIntStateOf(prefs.getInt("glass_opacity", 45).coerceIn(25, 85)) }
    var city by remember {
        val savedCity = prefs.getString("weather_city", null)
        mutableStateOf(savedCity?.takeUnless { it.equals("Kochi", ignoreCase = true) } ?: DEFAULT_WEATHER_CITY)
    }
    var cityDraft by remember { mutableStateOf(city) }
    var weatherRefresh by remember { mutableIntStateOf(0) }
    val apps = activity.apps
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
        "lite" -> Color(0xFF1A263C)
        "balanced" -> panel.copy(alpha = (opacity + 12).coerceAtMost(95) / 100f)
        else -> panel.copy(alpha = opacity / 100f)
    }
    val activeAccent = if (uiMode == "lite") Color(0xFFB8CCEF) else neonBlue

    BackHandler(page != "Home") { page = "Home" }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (true) {
            clock = LocalDateTime.now()
            tick++
            delay(30_000)
        }
    }
    LaunchedEffect(started, showWeather, city, weatherRefresh, tick / 60) {
        if (!started || !showWeather) return@LaunchedEffect
        val key = "weather_v2_" + city.lowercase(Locale.ROOT)
        val cached = prefs.getString(key, null)?.let { runCatching { Weather.fromJson(it) }.getOrNull() }
        weather = cached
        val age = System.currentTimeMillis() - prefs.getLong("${key}_time", 0L)
        weatherStatus = ""
        if (cached != null && age in 0 until 1_800_000L) return@LaunchedEffect
        weatherStatus = if (cached == null) "Fetching weather" else "Cached weather"
        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(Weather.fetch(city))
            } catch (e: java.io.IOException) {
                Result.failure(e)
            } catch (e: org.json.JSONException) {
                Result.failure(e)
            } catch (e: UnknownWeatherCity) {
                Result.failure(e)
            }
        }
        result.onSuccess {
            weather = it
            weatherStatus = ""
            prefs.edit().putString(key, it.toJson()).putLong("${key}_time", System.currentTimeMillis()).apply()
        }.onFailure {
            weatherStatus = when {
                it is UnknownWeatherCity -> "City not found"
                cached != null -> "Cached weather"
                else -> "Weather unavailable"
            }
        }
    }

    val wallpaperUri = if (uiMode == "lite") null else activity.wallpaperUri
    var customWallpaper by remember(wallpaperUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallpaperUri) {
        customWallpaper = decodeArtwork(activity, wallpaperUri, 1920)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(midnight)) {
        val compact = maxWidth < 1050.dp
        CompositionLocalProvider(
            LocalPanel provides activePanel,
            LocalAccent provides activeAccent,
            LocalCompact provides compact,
            LocalLite provides (uiMode == "lite")
        ) {
            if (customWallpaper == null && uiMode != "lite") MountainBackdrop(activePalette)
            customWallpaper?.let {
                Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
            }
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = if (compact) 18.dp else 36.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LauncherHeader(page, clock, isConnected(activity), onPage = { page = it })
                when (page) {
                    "Home" -> HomePage(
                        activity = activity,
                        clock = clock,
                        weather = weather,
                        city = city,
                        weatherStatus = weatherStatus,
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
                        activity.refreshApps()
                    }, onToggleFavorite = { packageName ->
                        favorites = if (favorites.contains(packageName)) favorites - packageName else favorites + packageName
                        prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
                    })
                    "Inputs" -> InputsPage(activity)
                    "Media" -> MediaPage(activity, apps)
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
                        },
                        onChooseWallpaper = activity::chooseWallpaper,
                        onResetWallpaper = activity::resetWallpaper,
                        opacity = opacity,
                        onOpacity = {
                            opacity = (opacity + it).coerceIn(25, 85)
                            prefs.edit().putInt("glass_opacity", opacity).apply()
                        },
                        city = cityDraft,
                        onCityChange = { cityDraft = it },
                        onSaveCity = {
                            val input = cityDraft.trim()
                            city = if (input.isBlank() || input.equals("Kochi", ignoreCase = true)) DEFAULT_WEATHER_CITY else input
                            cityDraft = city
                            prefs.edit().putString("weather_city", city).remove("weather_cache").apply()
                            weatherRefresh++
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
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    val tabs = listOf(
        "⌂" to "Home",
        "▦" to "Apps",
        "▭" to "Inputs",
        "▷" to "Media",
        "◇" to "Widgets",
        "⚙" to "Settings"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NexoraMark()
            Spacer(Modifier.width(10.dp))
            Text("NEXORA TV", color = white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(clock.format(DateTimeFormatter.ofPattern("h:mm a")), color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (connected) "Online" else "Offline", color = muted, fontSize = 12.sp)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tabs, key = { it.second }) { (icon, label) ->
                NavButton(
                    icon, label, selected = page == label,
                    modifier = if (label == "Home") Modifier.focusRequester(initialFocus) else Modifier,
                    onClick = { onPage(label) }
                )
            }
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
private fun NavButton(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        modifier = modifier.width(if (LocalCompact.current) 106.dp else 128.dp).height(50.dp),
        selected = selected,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(icon, color = white, fontSize = 18.sp)
            Spacer(Modifier.width(5.dp))
            Text(label, color = white, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomePage(
    activity: MainActivity,
    clock: LocalDateTime,
    weather: Weather?,
    city: String,
    weatherStatus: String,
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
    val compact = LocalCompact.current
    Row(Modifier.fillMaxWidth().height(if (compact) 142.dp else 170.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClockCard(Modifier.weight(1f), clock)
        if (showWeather) WeatherCard(Modifier.weight(1f), weather, city, weatherStatus)
        if (showNetwork && !compact) NetworkCard(Modifier.weight(.72f), networkLabel(activity))
        if (showCalendar && !compact) CalendarCard(Modifier.weight(1f), clock)
    }
    if (compact && (showNetwork || showCalendar)) {
        Row(Modifier.fillMaxWidth().height(138.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showNetwork) NetworkCard(Modifier.weight(1f), networkLabel(activity))
            if (showCalendar) CalendarCard(Modifier.weight(1f), clock)
        }
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
    val byPackage = apps.associateBy { it.packageName }
    val orderedApps = favorites.mapNotNull(byPackage::get).ifEmpty { apps.take(7) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(orderedApps, key = { it.packageName }) { app ->
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
        Brush.linearGradient(listOf(Color(0x25293C69), Color(0x25493A66), Color(0x258B526B)))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(clock.format(DateTimeFormatter.ofPattern("h:mm")), color = white, fontSize = if (LocalCompact.current) 44.sp else 58.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(clock.format(DateTimeFormatter.ofPattern("a")), color = white, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(clock.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), color = muted, fontSize = 19.sp)
        }
    }
}

@Composable
private fun WeatherCard(modifier: Modifier, weather: Weather?, city: String, status: String) {
    GlassPanel(modifier, Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(weather?.let { "${it.temperature}°C" } ?: "Weather", color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(weather?.place ?: city, color = white, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (weather != null) {
                Text("${weather.condition} · ↑ ${weather.high}° · ↓ ${weather.low}°", color = muted, fontSize = 13.sp)
            }
            Text(if (status.isBlank()) "Open-Meteo" else status, color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NetworkCard(modifier: Modifier, network: String) {
    GlassPanel(modifier, Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiMark()
            Text("Network", color = white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(network, color = if (network == "Offline") muted else mint, fontSize = 15.sp)
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
    GlassPanel(modifier, Brush.linearGradient(listOf(Color(0x25283B63), Color(0x25473A66)))) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = white, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (-3..3).forEach { offset ->
                    val date = today.plusDays(offset.toLong())
                    val selected = offset == 0
                    Column(
                        Modifier.weight(1f)
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
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier, background: Brush, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(LocalPanel.current)
            .background(if (LocalLite.current) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) else background)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .10f), Color.Transparent, violet.copy(alpha = .07f))
                )
            )
            .border(1.dp, panelEdge.copy(alpha = .95f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var image by remember(app) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app, context) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val artwork = app.resolveInfo.activityInfo.loadBanner(pm) ?: app.resolveInfo.loadIcon(pm)
                artwork?.toBitmap(128, 96)?.asImageBitmap()
            }.getOrNull()
        }
    }
    FocusSurface(
        modifier = Modifier.width(190.dp).height(122.dp),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            image?.let { artwork ->
                Image(
                    bitmap = artwork,
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

private data class InputShortcut(
    val key: String,
    val icon: String,
    val label: String,
    val detail: String,
    val open: () -> Unit
)

@Composable
private fun InputRow(activity: MainActivity) {
    val inputs = remember(activity) {
        val detected = activity.tvInputs().filter { it.isPassthroughInput }.map { input ->
            val custom = input.loadCustomLabel(activity)?.toString()
            val label = custom?.takeIf { it.isNotBlank() } ?: input.loadLabel(activity).toString()
            InputShortcut(input.id, "▱", label, "TV input") { activity.openTvInput(input) }
        }
        val sources = detected.ifEmpty {
            listOf(InputShortcut("source", "▱", "HDMI / AV", "Use remote Input or Source") {
                activity.feedback("Use the TV remote’s Input or Source button.")
            })
        }
        listOf(InputShortcut("live", "▣", "Live TV", "Channels") { activity.openTvInput() }) +
            sources + listOf(
                InputShortcut("usb", "↯", "USB Media", "Open a media file", activity::browseUsb),
                InputShortcut("cast", "◩", "Screen Cast", "Device casting settings") {
                    activity.openIntent(Intent(Settings.ACTION_CAST_SETTINGS), "Casting settings are unavailable.")
                }
            )
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(inputs, key = { it.key }) { (_, icon, label, detail, open) ->
            FocusSurface(Modifier.width(205.dp).height(94.dp), onClick = open) {
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
    val sessions = activity.mediaSessions
    val details = sessions.metadata
    val description = details?.description
    val title = description?.title ?: details?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = details?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: description?.subtitle
    val artworkUri = description?.iconUri
    var loadedArtwork by remember(artworkUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artworkUri) {
        loadedArtwork = decodeArtwork(activity, artworkUri, 256)
    }
    val artwork = remember(description) { description?.iconBitmap?.asImageBitmap() } ?: loadedArtwork
    val playing = sessions.playback?.state == PlaybackState.STATE_PLAYING
    val compact = LocalCompact.current
    Row(
        Modifier.fillMaxWidth().height(116.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(LocalPanel.current)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .08f), Color.Transparent)))
            .border(1.dp, panelEdge, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(if (compact) 56.dp else 74.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(violet, Color(0xFF1B6DB1), Color(0xFFFF8A78))))
        ) {
            if (artwork != null) {
                Image(artwork, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Text("♫", Modifier.align(Alignment.Center), color = white, fontSize = 34.sp)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text(title?.toString() ?: "No active media", color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist?.toString() ?: if (sessions.accessGranted) "Open a media app" else "Enable access in Media", color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val size = if (compact) 42.dp else 54.dp
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MediaControl("⏮", "Previous", size) {
                sessions.control(PlaybackState.ACTION_SKIP_TO_PREVIOUS) { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            }
            MediaControl(if (playing) "Ⅱ" else "▶", if (playing) "Pause" else "Play", size) {
                sessions.control(PlaybackState.ACTION_PLAY_PAUSE) { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            }
            MediaControl("⏭", "Next", size) {
                sessions.control(PlaybackState.ACTION_SKIP_TO_NEXT) { activity.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
            }
        }
    }
}

@Composable
private fun MediaControl(label: String, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    FocusSurface(Modifier.size(size).semantics { contentDescription = description }, onClick = onClick, circular = true) {
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
    ActionTile("↻", "Refresh app list", "Scan for newly installed apps", onRefresh)
    if (apps.isEmpty()) Text("No launchable apps found.", color = muted)
    val columns = if (LocalCompact.current) 3 else 5
    apps.chunked(columns).forEach { row ->
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row, key = { it.packageName }) { app ->
                AppTile(app, favorites.contains(app.packageName), { activity.openApp(app.packageName) }) {
                    onToggleFavorite(app.packageName)
                }
            }
        }
    }
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
        activity.openIntent(Intent(Settings.ACTION_SETTINGS), "Device settings are unavailable.")
    }
}

@Composable
private fun MediaPage(activity: MainActivity, apps: List<TvApp>) {
    PageTitle("Media", "Controls are sent to the active Android media session")
    MediaBar(activity)
    if (!activity.mediaSessions.accessGranted) {
        ActionTile("⚙", "Enable Now Playing access", "Allow Nexora to read active media metadata") { activity.requestMediaAccess() }
    }
    val mediaApps = apps.filter { app ->
        listOf("youtube", "music", "spotify", "vlc", "kodi", "plex", "jiosaavn", "wynk", "prime", "netflix")
            .any { it in app.name.lowercase() || it in app.packageName.lowercase() }
    }
    if (mediaApps.isEmpty()) Text("No recognized media apps installed", color = muted)
    mediaApps.chunked(4).forEach { group ->
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(group, key = { it.packageName }) { app ->
                ActionTile("▶", app.name, "Open player") { activity.openApp(app.packageName) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                .background(LocalPanel.current)
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
    onNextWallpaper: () -> Unit,
    onChooseWallpaper: () -> Unit,
    onResetWallpaper: () -> Unit,
    opacity: Int,
    onOpacity: (Int) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    onSaveCity: () -> Unit
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
    if (uiMode != "lite") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Glass opacity: $opacity%", color = white)
            MediaControl("−", "Decrease glass opacity", 42.dp) { onOpacity(-10) }
            MediaControl("+", "Increase glass opacity", 42.dp) { onOpacity(10) }
        }
    }
    ActionTile("▧", "Choose wallpaper", "Select an image from TV or USB storage", onChooseWallpaper)
    ActionTile("↶", "Reset wallpaper", "Return to the built-in background", onResetWallpaper)
    SectionTitle("Weather city (no GPS required)")
    androidx.compose.material3.OutlinedTextField(
        value = city,
        onValueChange = { onCityChange(it.take(80)) },
        label = { Text("City, region or country") },
        singleLine = true,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = white, unfocusedTextColor = white,
            focusedLabelColor = neonBlue, unfocusedLabelColor = muted
        )
    )
    ActionTile("✓", "Save weather city", "Update the forecast location", onSaveCity)
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
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val lite = LocalLite.current
    val shape = if (circular) RoundedCornerShape(50) else RoundedCornerShape(18.dp)
    val fill = if (focused || selected) {
        Brush.linearGradient(listOf(Color(0xD5345897), Color(0xCF2A3D75), Color(0xC76B3E86)))
    } else {
        Brush.linearGradient(listOf(LocalPanel.current, LocalPanel.current))
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = if (focused && !lite) 1.035f else 1f
                scaleY = if (focused && !lite) 1.035f else 1f
            }
            .shadow(if (focused && !lite) 10.dp else 0.dp, shape, clip = false)
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
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        palette[0].copy(alpha = .18f),
                        Color.Transparent,
                        palette[2].copy(alpha = .25f),
                        midnight.copy(alpha = .70f)
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

private fun isConnected(activity: Activity): Boolean = networkLabel(activity) != "Offline"

private fun networkLabel(activity: Activity): String {
    val connectivity = activity.getSystemService(ConnectivityManager::class.java) ?: return "Offline"
    val network = connectivity.activeNetwork ?: return "Offline"
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return "Offline"
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet connected"
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Connected"
        else -> "Offline"
    }
}
