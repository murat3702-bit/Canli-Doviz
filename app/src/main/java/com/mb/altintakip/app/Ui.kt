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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
                Text("⚙", color = pal.muted, fontSize = 22.sp, modifier = Modifier.clickable { showSettings = true }.padding(8.dp))
            }
            if (tab != 0) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("(CANLI)", color = pal.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("ALIŞ", color = pal.muted, fontSize = 12.sp, modifier = Modifier.width(88.dp))
                    Text("SATIŞ", color = pal.muted, fontSize = 12.sp, modifier = Modifier.width(100.dp), textAlign = TextAlign.End)
                }
            }
            HorizontalPager(state = pagerState, userScrollEnabled = swipe, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> PortfoyPage(vm, editOrder, onEditHolding) { showAdd = true }
                    else -> QuotesPage(vm, page, favs, alarmKeys, editOrder, onAlarmPick)
                }
            }
            StatusLine(vm) { showConverter = true }
        }
    }

    if (showAdd) {
        val quotes by vm.quotes.collectAsState()
        AddDialog(quotes, { showAdd = false }) { k, a -> vm.addHolding(k, a); showAdd = false }
    }
    if (showConverter) {
        val quotes by vm.quotes.collectAsState()
        ConverterSheet(quotes) { showConverter = false }
    }
    editHolding?.let { h ->
        EditHoldingDialog(
            h, { editHolding = null },
            { a -> vm.setHoldingAmount(h.key, a); editHolding = null },
            { vm.removeHolding(h.key); editHolding = null }
        )
    }
    alarmFor?.let { q -> AlarmSheet(q, alarms.filter { it.key == q.key }, vm) { alarmFor = null } }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = pal.text, fontSize = 15.sp)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp))
}

private fun soundTitle(c: Context, s: String): String {
    if (s.isBlank()) return "Varsayılan bildirim sesi"
    val uri = Uri.parse(s)
    runCatching {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
        }
    }
    return runCatching { RingtoneManager.getRingtone(c, uri)?.getTitle(c) }.getOrNull() ?: "Seçili ses"
}

private fun soundUriOrDefault(s: String): Uri =
    if (s.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(s)

@Composable
fun SettingsScreen(vm: MainVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val start by vm.startTab.collectAsState()
    val theme by vm.theme.collectAsState()
    val sound by vm.sound.collectAsState()
    val alarms by vm.alarms.collectAsState()
    val swipe by vm.swipe.collectAsState()
    var showRaw by remember { mutableStateOf(false) }
    if (showRaw) RawDialog { showRaw = false }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = pal.text, fontSize = 26.sp, modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Text("Ayarlar", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }

        Section("AÇILIŞ SAYFASI")
        Choice("Favoriler", start == "FAV") { vm.setStart("FAV") }
        Choice("Portföy", start == "PORT") { vm.setStart("PORT") }

        Section("TEMA")
        Choice("Sistem", theme == "SYSTEM") { vm.setTheme("SYSTEM") }
        Choice("Açık", theme == "LIGHT") { vm.setTheme("LIGHT") }
        Choice("Koyu", theme == "DARK") { vm.setTheme("DARK") }

        Section("SAYFA GEÇİŞİ")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kaydırarak geçiş", color = pal.text, fontSize = 15.sp)
                Text("Ekranı sağa/sola kaydırarak sekmeler arasında geçin", color = pal.muted, fontSize = 12.sp)
            }
            Switch(checked = swipe, onCheckedChange = { vm.setSwipe(it) })
        }

        Section("ALARMLAR")
        Text("Varsayılan alarm sesi", color = pal.text, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { SoundRow(sound, "Varsayılan bildirim sesi") { vm.setSound(it) } }
        TextButton(
            onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { Text("Pil kısıtlamasını kaldır (arka plan alarmı için)") }
        if (alarms.isEmpty()) {
            Text(
                "Alarm yok. Fiyat satırlarındaki 🔔 simgesiyle ekleyin.\nAlarm tetiklenince bir kez çalar ve kapanır.",
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
            )
        } else alarms.groupBy { it.key }.forEach { (key, list) -> AlarmGroupCard(key, list, vm) }

        Section("VERİ KAYNAKLARI")
        Text("canlidoviz.com: Kapalı Çarşı, Serbest Piyasa", color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Text("İKO altın fiyatları: 146.19.208.20", color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        TextButton(onClick = { showRaw = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("İKO verisini test et") }

        Section("HAKKINDA")
        Text("Geliştirici: Murat BOSTANCI ®", color = pal.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Text("Sürüm 1.15", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Text("Veri kaynağı: canlidoviz.com", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun QuoteList(
    list: List<Quote>, favs: List<String>, alarmKeys: Set<String>,
    onFav: (String) -> Unit, onAlarm: (Quote) -> Unit, emptyText: String = "Veri bekleniyor…",
    edit: Boolean = false, onMove: (String, Int) -> Unit = { _, _ -> }
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = pal.muted, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, it -> it.key }) { i, q ->
            QuoteRow(q, q.
