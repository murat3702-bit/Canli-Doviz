package com.mb.altintakip.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val TR = Locale("tr", "TR")

class Pal(
    val bg: Color, val surface: Color, val text: Color, val teal: Color, val muted: Color,
    val selBg: Color, val green: Color, val red: Color, val div: Color, val line: Color
)

private val DarkPal = Pal(
    Color(0xFF0B0D10), Color(0xFF14181D), Color.White, Color(0xFF4FC3DC), Color(0xFF8E9AA8),
    Color(0xFF2F5D78), Color(0xFF6FD36F), Color(0xFFFF5A4D), Color(0xFF1E2329), Color(0xFF3A4552)
)
private val LightPal = Pal(
    Color(0xFFF6F8FA), Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF0B7A93), Color(0xFF5F6B7A),
    Color(0xFFCFE8F0), Color(0xFF1E8E3E), Color(0xFFD93025), Color(0xFFE1E6EB), Color(0xFFC3CCD6)
)
private val LocalPal = staticCompositionLocalOf { DarkPal }
private val pal: Pal
    @Composable get() = LocalPal.current

@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPal else LightPal
    val d = LocalDensity.current
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        w.statusBarColor = p.bg.toArgb()
        w.navigationBarColor = p.bg.toArgb()
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(
        LocalPal provides p,
        LocalDensity provides Density(d.density, minOf(d.fontScale, 1f))
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme(
                primary = p.teal, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text
            ) else lightColorScheme(
                primary = p.teal, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text
            ),
            content = content
        )
    }
}

private fun fmt(v: Double, digits: Int? = null): String {
    val d = digits ?: if (v < 100) 4 else 2
    return String.format(TR, "%,.${d}f", v)
}

private fun px(v: Double) = if (v <= 0.0) "—" else fmt(v)

private fun signed(v: Double) = (if (v > 0) "+" else "") + String.format(TR, "%,.2f", v)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(vm: MainVM) {
    val favs by vm.favorites.collectAsState()
    val alarms by vm.alarms.collectAsState()
    val swipe by vm.swipe.collectAsState()
    val pagerState = rememberPagerState(initialPage = if (vm.startTab.value == "PORT") 0 else 1) { 4 }
    val tab = pagerState.currentPage
    val pageScope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var editHolding by remember { mutableStateOf<Holding?>(null) }
    var showConverter by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var alarmFor by remember { mutableStateOf<Quote?>(null) }
    val tabs = listOf("PORTFÖY", "FAVORİ", "ALTIN", "DÖVİZ")
    val alarmKeys = remember(alarms) { alarms.filter { it.enabled }.map { it.key }.toSet() }
    var editOrder by remember { mutableStateOf(false) }
    val onEditHolding = remember { { h: Holding -> editHolding = h } }
    val onAlarmPick = remember { { q: Quote -> alarmFor = q } }

    BackHandler(enabled = showSettings) { showSettings = false }

    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding()) {
        if (showSettings) {
            SettingsScreen(vm) { showSettings = false }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    tabs.forEachIndexed { i, t ->
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (tab == i) pal.selBg else Color.Transparent)
                                .clickable { pageScope.launch { pagerState.animateScrollToPage(i) } }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(t, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, color = if (tab == i) pal.teal else pal.muted)
                        }
                    }
                }
                if (tab == 0 || tab == 1) Text(
                    if (editOrder) "✓" else "↕", color = if (editOrder) pal.teal else pal.muted, fontSize = 22.sp,
                    modifier = Modifier.clickable { editOrder = !editOrder }.padding(8.dp)
                )
                Text("⚙", color =
