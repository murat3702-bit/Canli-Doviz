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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.roundToInt

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

/**
 * Kesikli (adımlı) slider: yalnızca verilen duraklara oturur, her durakta hafif titreşim verir.
 * Etiketler, slider üzerindeki durakların tam altına hizalanır.
 */
@Composable
fun StepSlider(labels: List<String>, index: Int, onChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val last = (labels.size - 1).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val i = it.roundToInt().coerceIn(0, labels.lastIndex)
                if (i != index) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(i)
                }
            },
            valueRange = 0f..labels.lastIndex.toFloat().coerceAtLeast(1f),
            steps = (labels.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = pal.teal, activeTrackColor = pal.teal, inactiveTrackColor = pal.line,
                activeTickColor = pal.bg, inactiveTickColor = pal.muted
            )
        )
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val thumbR = 10.dp
            val labelW = 72.dp
            val track = maxWidth - thumbR * 2
            labels.forEachIndexed { i, l ->
                Text(
                    l, color = if (i == index) pal.teal else pal.muted, fontSize = 13.sp,
                    fontWeight = if (i == index) FontWeight.Bold else null,
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    modifier = Modifier.width(labelW).offset(x = thumbR + track * i / last - labelW / 2)
                )
            }
        }
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
    val refreshSec by vm.refreshSec.collectAsState()
    var showRaw by remember { mutableStateOf(false) }
    if (showRaw) RawDialog { showRaw = false }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = pal.text, fontSize = 26.sp, modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Text("Ayarlar", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }

        Section("AÇILIŞ SAYFASI")
        StepSlider(listOf("Favoriler", "Portföy"), if (start == "PORT") 1 else 0) { vm.setStart(if (it == 1) "PORT" else "FAV") }

        Section("TEMA")
        val themes = listOf("SYSTEM", "LIGHT", "DARK")
        StepSlider(listOf("Sistem", "Açık", "Koyu"), themes.indexOf(theme).coerceAtLeast(0)) { vm.setTheme(themes[it]) }

        Section("SAYFA GEÇİŞİ")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kaydırarak geçiş", color = pal.text, fontSize = 15.sp)
                Text("Ekranı sağa/sola kaydırarak sekmeler arasında geçin", color = pal.muted, fontSize = 12.sp)
            }
            Switch(checked = swipe, onCheckedChange = { vm.setSwipe(it) })
        }

        Section("VERİ YENİLEME")
        val steps = listOf(3, 5, 7, 10)
        StepSlider(steps.map { "$it sn." }, steps.indexOf(refreshSec).coerceAtLeast(0)) { vm.setRefreshSec(steps[it]) }

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
            QuoteRow(q, q.key in favs, q.key in alarmKeys, onFav, onAlarm, edit, i > 0, i < list.lastIndex, onMove)
            HorizontalDivider(color = pal.line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun MoveButtons(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 8.dp)) {
        Text(
            "▲", color = pal.teal, fontSize = 20.sp,
            modifier = Modifier.alpha(if (canUp) 1f else 0.2f).clickable(enabled = canUp) { onUp() }.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            "▼", color = pal.teal, fontSize = 20.sp,
            modifier = Modifier.alpha(if (canDown) 1f else 0.2f).clickable(enabled = canDown) { onDown() }.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Parametreleri kararlı (stable) olduğu için, verisi değişmeyen satır yeniden çizilmez. */
@Composable
fun QuoteRow(
    q: Quote, fav: Boolean, hasAlarm: Boolean, onFav: (String) -> Unit, onAlarm: (Quote) -> Unit,
    edit: Boolean, canUp: Boolean, canDown: Boolean, onMove: (String, Int) -> Unit
) {
    val color = when { q.change > 0.0049 -> pal.green; q.change < -0.0049 -> pal.red; else -> pal.muted }
    val nameTxt = remember(q.name) { q.name.uppercase(TR) }
    val buyTxt = remember(q.buy) { px(q.buy) }
    val sellTxt = remember(q.sell) { px(q.sell) }
    val chgTxt = remember(q.change, q.pct) {
        val arrow = when { q.change > 0.0049 -> "▲"; q.change < -0.0049 -> "▼"; else -> "■" }
        "$arrow ${signed(q.change)} (${String.format(TR, "%.2f", q.pct)}%)"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (edit) MoveButtons(canUp, canDown, { onMove(q.key, -1) }, { onMove(q.key, 1) }) else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 8.dp)) {
            Text(
                if (fav) "★" else "☆", color = if (fav) Color(0xFFFFB300) else pal.muted, fontSize = 20.sp,
                modifier = Modifier.clickable { onFav(q.key) }.padding(horizontal = 2.dp, vertical = 2.dp)
            )
            Text(
                "🔔", fontSize = 16.sp,
                modifier = Modifier.alpha(if (hasAlarm) 1f else 0.35f).clickable { onAlarm(q) }.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(nameTxt, color = pal.teal, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(q.market.label, color = pal.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
            Text(q.time, color = pal.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
        Box(Modifier.width(88.dp)) { FlashText(q.buy, buyTxt, pal.muted, 15.sp, false) }
        Column(Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
            FlashText(q.sell, sellTxt, pal.text, 16.sp, true)
            Text(chgTxt, color = color, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Fiyat değişince yazı rengi 2 sn. boyunca düz renkte kalır (artışta yeşil, düşüşte kırmızı),
 * sonra aniden normale döner. Fade yok. Yalnızca bu küçük bileşen yeniden çizilir.
 */
@Composable
private fun FlashText(value: Double, text: String, base: Color, fontSize: TextUnit, bold: Boolean) {
    val prev = remember { DoubleArray(1) { value } }
    var flash by remember { mutableStateOf(0) } // 0: normal, 1: yükseliş, -1: düşüş
    LaunchedEffect(value) {
        if (value != prev[0]) {
            flash = if (value > prev[0]) 1 else -1
            prev[0] = value
            delay(2000)
            flash = 0
        }
    }
    Text(
        text, color = when (flash) { 1 -> pal.green; -1 -> pal.red; else -> base }, fontSize = fontSize,
        fontWeight = if (bold) FontWeight.Bold else null, maxLines = 1, softWrap = false
    )
}

@Composable
private fun QuotesPage(
    vm: MainVM, page: Int, favs: List<String>, alarmKeys: Set<String>, edit: Boolean, onAlarm: (Quote) -> Unit
) {
    val quotes by vm.quotes.collectAsState()
    val list = remember(quotes, favs, page) {
        when (page) {
            1 -> { val m = quotes.associateBy { it.key }; favs.mapNotNull { m[it] } }
            2 -> quotes.filter { it.category == Category.ALTIN }
            else -> quotes.filter { it.category == Category.DOVIZ }
        }
    }
    val onFav = remember(vm) { { k: String -> vm.toggleFav(k) } }
    val onMove = remember(vm) { { k: String, d: Int -> vm.moveFav(k, d) } }
    QuoteList(
        list, favs, alarmKeys, onFav, onAlarm,
        if (page == 1) "Henüz favori yok.\nDöviz veya Altın sekmesinde ☆ ile ekleyin." else "Veri bekleniyor…",
        edit && page == 1, onMove
    )
}

@Composable
private fun PortfoyPage(vm: MainVM, edit: Boolean, onEdit: (Holding) -> Unit, onAdd: () -> Unit) {
    val quotes by vm.quotes.collectAsState()
    val holdings by vm.holdings.collectAsState()
    val onRemove = remember(vm) { { k: String -> vm.removeHolding(k) } }
    val onMove = remember(vm) { { k: String, d: Int -> vm.moveHolding(k, d) } }
    Portfoy(quotes, holdings, edit, onRemove, onMove, onEdit, onAdd)
}

@Composable
private fun StatusLine(vm: MainVM, onConverter: () -> Unit) {
    val status by vm.status.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.ic_convert), contentDescription = "Döviz çevirici",
            modifier = Modifier.size(44.dp).clickable { onConverter() }.padding(10.dp)
        )
        Text(status, color = pal.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun Portfoy(
    quotes: List<Quote>, holdings: List<Holding>, edit: Boolean,
    onRemove: (String) -> Unit, onMove: (String, Int) -> Unit, onEdit: (Holding) -> Unit, onAdd: () -> Unit
) {
    val byKey = quotes.associateBy { it.key }
    fun value(h: Holding) = byKey[h.key]?.let { it.buy * h.amount } ?: 0.0
    val titles = mapOf(
        Market.IKO to "İKO Varlıkları",
        Market.KAPALI to "Kapalı Çarşı Varlıkları",
        Market.SERBEST to "Serbest Piyasa Varlıkları"
    )
    val shortTitles = mapOf(Market.IKO to "İKO", Market.KAPALI to "Kapalı Çarşı", Market.SERBEST to "Serbest Piyasa")
    val groups = listOf(Market.IKO, Market.KAPALI, Market.SERBEST).mapNotNull { m ->
        val l = holdings.filter { it.key.substringBefore("|") == m.name }
        if (l.isEmpty()) null else m to l
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("TOPLAM DEĞER (ALIŞ FİYATIYLA)", color = pal.muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            groups.forEach { (m, list) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(shortTitles[m] ?: m.label, color = pal.muted, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    AmountText("₺ ${fmt(list.sumOf { value(it) }, 2)}", pal.text, 22.sp, FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(color = pal.div)
        Box(Modifier.weight(1f)) {
            if (holdings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz varlık yok.\nSağ alttaki + ile ekleyin.", color = pal.muted, textAlign = TextAlign.Center)
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (m, list) ->
                    item(key = "grp_${m.name}") {
                        Row(
                            Modifier.fillMaxWidth().background(pal.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(titles[m] ?: m.label, color = pal.red, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            AmountText("₺ ${fmt(list.sumOf { value(it) }, 2)}", pal.red, 18.sp, FontWeight.Bold)
                        }
                    }
                    itemsIndexed(list, key = { _, it -> it.key }) { i, h ->
                        val q = byKey[h.key]
                        Row(Modifier.fillMaxWidth().clickable { onEdit(h) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (edit) MoveButtons(i > 0, i < list.lastIndex, { onMove(h.key, -1) }, { onMove(h.key, 1) })
                            Column(Modifier.weight(1f)) {
                                Text(h.key.substringAfter("|").uppercase(TR), color = pal.teal, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("Miktar: ${fmt(h.amount, if (h.amount % 1.0 == 0.0) 0 else 2)}", color = pal.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
                            }
                            Text(
                                q?.let { "₺ ${fmt(it.buy * h.amount, 2)}" } ?: "—",
                                color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                            )
                            Text("✕", color = pal.muted, fontSize = 18.sp, modifier = Modifier.clickable { onRemove(h.key) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
                        }
                        HorizontalDivider(color = pal.div, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                item(key = "fab_space") { Spacer(Modifier.height(88.dp)) }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp)
                    .clip(CircleShape).background(pal.selBg).clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) { Text("+", color = pal.teal, fontSize = 24.sp) }
        }
    }
}

@Composable
fun AddDialog(quotes: List<Quote>, onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    var sel by remember { mutableStateOf<Quote?>(null) }
    var open by remember { mutableStateOf(false) }
    var amt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Varlık ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(sel?.let { "${it.name} · ${it.market.label}" } ?: "Varlık seç")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        quotes.forEach { q ->
                            DropdownMenuItem(
                                text = { Text("${q.name} · ${q.market.label}", fontSize = 14.sp) },
                                onClick = { sel = q; open = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amt, onValueChange = { amt = it }, singleLine = true,
                    label = { Text("Miktar (adet / gram / birim)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.replace(',', '.').toDoubleOrNull()
                val s = sel
                if (s != null && a != null && a > 0) onAdd(s.key, a)
            }) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}


@Composable
fun SoundRow(sound: String, emptyLabel: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val name = remember(sound) { if (sound.isBlank()) emptyLabel else soundTitle(ctx, sound) }
    val ring = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33)
                r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onChange(uri?.toString() ?: "")
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onChange(uri.toString())
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("Ses: $name", color = pal.text, fontSize = 14.sp, maxLines = 2)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { AlarmService.playSound(ctx, sound, 5000) }) { Text("Dene") }
            TextButton(onClick = {
                ring.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sesi")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUriOrDefault(sound))
                })
            }) { Text("Telefon sesleri") }
            TextButton(onClick = { file.launch(arrayOf("audio/*")) }) { Text("Müzik / dosya seç") }
            TextButton(onClick = { onChange("") }) { Text("Varsayılan") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSheet(q: Quote, existing: List<Alarm>, vm: MainVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var above by remember { mutableStateOf(true) }
    var useSell by remember { mutableStateOf(false) }
    var amt by remember { mutableStateOf("") }
    var snd by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pal.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(q.name.uppercase(TR), color = pal.teal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${q.market.label} · Alış ${fmt(q.buy)} · Satış ${fmt(q.sell)}", color = pal.muted, fontSize = 12.sp)

            if (existing.isNotEmpty()) {
                Text("MEVCUT ALARMLAR", color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                existing.forEach { a ->
                    val sName = remember(a.sound) { if (a.sound.isBlank()) "Varsayılan ses" else soundTitle(ctx, a.sound) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${if (a.above) "▲ üstüne çıkınca" else "▼ altına düşünce"} ${fmt(a.target)} (${if (a.useSell) "satış" else "alış"})",
                                color = pal.text, fontSize = 14.sp
                            )
                            Text("Ses: $sName", color = pal.muted, fontSize = 11.sp, maxLines = 1)
                        }
                        Switch(checked = a.enabled, onCheckedChange = { vm.toggleAlarm(a.id) })
                        Text("✕", color = pal.muted, fontSize = 18.sp, modifier = Modifier.clickable { vm.removeAlarm(a.id) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
                    }
                }
            }

            Text("YENİ ALARM", color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Choice("Fiyat altına düşünce", !above) { above = false }
            Choice("Fiyat üstüne çıkınca", above) { above = true }
            Choice("Alış fiyatına göre", !useSell) { useSell = false }
            Choice("Satış fiyatına göre", useSell) { useSell = true }
            OutlinedTextField(
                value = amt, onValueChange = { amt = it }, singleLine = true,
                label = { Text("Hedef fiyat (₺)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            SoundRow(snd, "Varsayılan alarm sesi") { snd = it }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val t = amt.replace(',', '.').toDoubleOrNull()
                    if (t != null && t > 0) {
                        vm.addAlarm(q.key, t, above, useSell, snd)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Alarm kur") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun RawDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("Yükleniyor…") }
    LaunchedEffect(Unit) {
        text = try {
            val raw = Repo.rawIko()
            "Ayrıştırılan kalem sayısı: ${Repo.parseIko(raw).size}\n\n" + raw.take(3000)
        } catch (e: Exception) {
            "Hata: ${e.message}"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("İKO veri testi") },
        text = { SelectionContainer { Text(text, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}


@Composable
fun AlarmGroupCard(key: String, list: List<Alarm>, vm: MainVM) {
    val ctx = LocalContext.current
    var open by remember(key) { mutableStateOf(false) }
    val name = key.substringAfter("|").uppercase(TR)
    val market = Market.valueOf(key.substringBefore("|")).label
    val active = list.count { it.enabled }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(pal.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, color = pal.teal, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "$market · ${list.size} alarm" + if (active != list.size) " ($active aktif)" else "",
                    color = pal.muted, fontSize = 11.sp
                )
            }
            Text(if (open) "▲" else "▼", color = pal.muted, fontSize = 14.sp)
        }
        if (open) {
            list.forEach { a ->
                HorizontalDivider(color = pal.div)
                val sName = remember(a.sound) { if (a.sound.isBlank()) "Varsayılan ses" else soundTitle(ctx, a.sound) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${if (a.above) "▲ üstüne çıkınca" else "▼ altına düşünce"} ${fmt(a.target)} (${if (a.useSell) "satış" else "alış"})",
                            color = pal.text, fontSize = 14.sp
                        )
                        Text("Ses: $sName", color = pal.muted, fontSize = 11.sp, maxLines = 1)
                    }
                    Switch(checked = a.enabled, onCheckedChange = { vm.toggleAlarm(a.id) })
                    Text("✕", color = pal.muted, fontSize = 18.sp, modifier = Modifier.clickable { vm.removeAlarm(a.id) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
                }
            }
        }
    }
}


@Composable
fun EditHoldingDialog(h: Holding, onDismiss: () -> Unit, onSave: (Double) -> Unit, onDelete: () -> Unit) {
    var amt by remember { mutableStateOf(if (h.amount % 1.0 == 0.0) h.amount.toLong().toString() else h.amount.toString()) }
    val name = h.key.substringAfter("|").uppercase(TR)
    val market = Market.valueOf(h.key.substringBefore("|")).label
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(market, color = pal.muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = amt, onValueChange = { amt = it }, singleLine = true,
                    label = { Text("Miktar (adet / gram / birim)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.replace(',', '.').toDoubleOrNull()
                if (a != null && a > 0) onSave(a)
            }) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Sil", color = pal.red) }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        }
    )
}


private fun fmtN(v: Double, d: Int): String {
    var t = String.format(Locale.US, "%.${d}f", v)
    if (d > 2 && t.contains('.')) t = t.trimEnd('0').trimEnd('.')
    return t.replace('.', ',')
}

private fun unitOf(q: Quote?): String = when {
    q == null -> ""
    q.category == Category.DOVIZ -> q.name
    Regex("(?i)gram|ayar|\\bgr\\b|has").containsMatchIn(q.name) -> "gr"
    else -> "adet"
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
        cursorBrush = SolidColor(pal.teal),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(112.dp)
            .border(1.dp, pal.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun ConvRow(q: Quote?, options: List<Quote>, value: String, onPick: (String) -> Unit, onValue: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.clickable { open = true }.padding(vertical = 4.dp)) {
                Text((q?.name ?: "Seç").uppercase(TR) + "  ▾", color = pal.text, fontSize = 15.sp, maxLines = 1)
                Text(q?.market?.label ?: "", color = pal.muted, fontSize = 10.sp, maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text("${o.name} · ${o.market.label}", fontSize = 14.sp) },
                        onClick = { onPick(o.key); open = false }
                    )
                }
            }
        }
        NumField(value, onValue)
        Text(unitOf(q), color = pal.muted, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(38.dp).padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterSheet(quotes: List<Quote>, onDismiss: () -> Unit) {
    fun firstKey(cat: Category, name: String): String? =
        (quotes.firstOrNull { it.category == cat && it.market == Market.KAPALI && it.name.contains(name, true) }
            ?: quotes.firstOrNull { it.category == cat && it.name.contains(name, true) })?.key
    val defaults = listOfNotNull(firstKey(Category.DOVIZ, "USD"), firstKey(Category.DOVIZ, "EUR"), firstKey(Category.ALTIN, "Gram"))
    var keys by remember { mutableStateOf(List(3) { i -> defaults.getOrNull(i) ?: quotes.firstOrNull()?.key ?: "" }) }
    val texts = remember { mutableStateListOf("1", "", "", "") }
    var useSell by remember { mutableStateOf(true) }
    var active by remember { mutableIntStateOf(0) }

    fun price(i: Int): Double {
        val q = quotes.find { it.key == keys[i] } ?: return 0.0
        val p = if (useSell) q.sell else q.buy
        return if (p > 0.0) p else maxOf(q.buy, q.sell)
    }

    // [from] satırındaki miktardan TL değerini bulur, diğer satırları ona göre doldurur.
    fun recompute(from: Int) {
        val n = texts[from].replace(',', '.').toDoubleOrNull()
        if (n == null) {
            for (j in 0..3) if (j != from) texts[j] = ""
            return
        }
        val tl = if (from == 3) n else n * price(from)
        for (j in 0..3) {
            if (j == from) continue
            texts[j] = if (j == 3) fmtN(tl, 2) else fmtN(if (price(j) > 0.0) tl / price(j) else 0.0, 4)
        }
    }

    LaunchedEffect(quotes.isEmpty()) { if (quotes.isNotEmpty()) recompute(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pal.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("DÖVİZ ÇEVİRİCİ", color = pal.teal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (quotes.isEmpty()) {
                Text("Fiyat verisi bekleniyor…", color = pal.muted, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                for (i in 0..2) {
                    ConvRow(
                        quotes.find { it.key == keys[i] }, quotes, texts[i],
                        { k -> keys = keys.toMutableList().also { it[i] = k }; recompute(active) },
                        { v -> texts[i] = v; active = i; recompute(i) }
                    )
                }
                HorizontalDivider(color = pal.div, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(false to "ALIŞ", true to "SATIŞ").forEach { (sell, label) ->
                            Text(
                                label, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = if (useSell == sell) pal.teal else pal.muted,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (useSell == sell) pal.selBg else Color.Transparent)
                                    .clickable { useSell = sell; recompute(active) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    NumField(texts[3]) { v -> texts[3] = v; active = 3; recompute(3) }
                    Text("TL", color = pal.muted, fontSize = 12.sp, modifier = Modifier.width(38.dp).padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}



/**
 * Tutarları sütun gibi hizalar (tabular-nums mantığı): her rakam aynı genişlikte bir hücreye,
 * nokta/virgül dar sabit hücreye yazılır. Yazı tipi "tnum" özelliğini desteklemese bile
 * rakamlar ve virgüller alt alta kusursuz durur. Tutarlar sağa yaslıdır.
 */
@Composable
private fun AmountText(text: String, color: Color, fontSize: TextUnit, weight: FontWeight? = null) {
    val density = LocalDensity.current
    val digitW = with(density) { fontSize.toDp() * (if (weight == FontWeight.Bold) 0.64f else 0.60f) }
    val sepW = with(density) { fontSize.toDp() * 0.28f }
    val spaceW = with(density) { fontSize.toDp() * 0.30f }
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            val w = when {
                ch.isDigit() -> digitW
                ch == '.' || ch == ',' -> sepW
                ch == ' ' -> spaceW
                else -> null
            }
            Text(
                ch.toString(), color = color, fontSize = fontSize, fontWeight = weight,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                modifier = if (w != null) Modifier.width(w) else Modifier
            )
        }
    }
}
