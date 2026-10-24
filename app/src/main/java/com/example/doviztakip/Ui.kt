package com.example.doviztakip

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale

private val TR = Locale("tr", "TR")

class Pal(
    val bg: Color, val surface: Color, val text: Color, val teal: Color, val muted: Color,
    val selBg: Color, val green: Color, val red: Color, val div: Color
)

private val DarkPal = Pal(
    Color(0xFF0B0D10), Color(0xFF14181D), Color.White, Color(0xFF4FC3DC), Color(0xFF8E9AA8),
    Color(0xFF2F5D78), Color(0xFF6FD36F), Color(0xFFFF5A4D), Color(0xFF1E2329)
)
private val LightPal = Pal(
    Color(0xFFF6F8FA), Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF0B7A93), Color(0xFF5F6B7A),
    Color(0xFFCFE8F0), Color(0xFF1E8E3E), Color(0xFFD93025), Color(0xFFE1E6EB)
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

private fun signed(v: Double) = (if (v > 0) "+" else "") + String.format(TR, "%,.2f", v)

@Composable
fun App(vm: MainVM) {
    val quotes by vm.quotes.collectAsState()
    val holdings by vm.holdings.collectAsState()
    val status by vm.status.collectAsState()
    val favs by vm.favorites.collectAsState()
    val alarms by vm.alarms.collectAsState()
    var tab by remember { mutableIntStateOf(if (vm.startTab.value == "PORT") 0 else 1) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var alarmFor by remember { mutableStateOf<Quote?>(null) }
    val tabs = listOf("PORTFÖY", "FAVORİ", "ALTIN", "DÖVİZ")
    val alarmKeys = alarms.filter { it.enabled }.map { it.key }.toSet()
    val quoteByKey = quotes.associateBy { it.key }
    val favQuotes = favs.mapNotNull { quoteByKey[it] }
    var editOrder by remember { mutableStateOf(false) }

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
                                .clickable { tab = i }
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
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> Portfoy(quotes, holdings, editOrder, vm::removeHolding, vm::moveHolding) { showAdd = true }
                    1 -> QuoteList(
                        favQuotes, favs, alarmKeys, vm::toggleFav, { alarmFor = it },
                        "Henüz favori yok.\nDöviz veya Altın sekmesinde ☆ ile ekleyin.",
                        editOrder, vm::moveFav
                    )
                    2 -> QuoteList(quotes.filter { it.category == Category.ALTIN }, favs, alarmKeys, vm::toggleFav, { alarmFor = it })
                    else -> QuoteList(quotes.filter { it.category == Category.DOVIZ }, favs, alarmKeys, vm::toggleFav, { alarmFor = it })
                }
            }
            Text(status, color = pal.muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center)
        }
    }

    if (showAdd) AddDialog(quotes, { showAdd = false }) { k, a -> vm.addHolding(k, a); showAdd = false }
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
        } else alarms.forEach { a ->
            val name = a.key.substringAfter("|").uppercase(TR)
            val market = Market.valueOf(a.key.substringBefore("|")).label
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = pal.teal, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "$market\n${if (a.above) "▲ üstüne çıkınca" else "▼ altına düşünce"} ${fmt(a.target)} (${if (a.useSell) "satış" else "alış"})",
                        color = pal.muted, fontSize = 11.sp
                    )
                }
                Switch(checked = a.enabled, onCheckedChange = { vm.toggleAlarm(a.id) })
                Text("✕", color = pal.muted, fontSize = 18.sp, modifier = Modifier.clickable { vm.removeAlarm(a.id) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
            }
            HorizontalDivider(color = pal.div, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Section("VERİ KAYNAKLARI")
        Text("canlidoviz.com: Kapalı Çarşı, Serbest Piyasa", color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Text("İKO altın fiyatları: 146.19.208.20", color = pal.text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        TextButton(onClick = { showRaw = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("İKO verisini test et") }

        Section("HAKKINDA")
        Text("Geliştirici: Murat BOSTANCI ®", color = pal.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Text("Sürüm 1.1", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
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
            QuoteRow(
                q, q.key in favs, q.key in alarmKeys, { onFav(q.key) }, { onAlarm(q) },
                edit, i > 0, i < list.lastIndex, { onMove(q.key, -1) }, { onMove(q.key, 1) }
            )
            HorizontalDivider(color = pal.div, modifier = Modifier.padding(horizontal = 16.dp))
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

@Composable
fun QuoteRow(
    q: Quote, fav: Boolean, hasAlarm: Boolean, onFav: () -> Unit, onAlarm: () -> Unit,
    edit: Boolean, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit
) {
    val color = when { q.change > 0.0049 -> pal.green; q.change < -0.0049 -> pal.red; else -> pal.muted }
    val arrow = when { q.change > 0.0049 -> "▲"; q.change < -0.0049 -> "▼"; else -> "■" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (edit) MoveButtons(canUp, canDown, onUp, onDown) else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 8.dp)) {
            Text(
                if (fav) "★" else "☆", color = if (fav) Color(0xFFFFB300) else pal.muted, fontSize = 20.sp,
                modifier = Modifier.clickable { onFav() }.padding(horizontal = 2.dp, vertical = 2.dp)
            )
            Text(
                "🔔", fontSize = 16.sp,
                modifier = Modifier.alpha(if (hasAlarm) 1f else 0.35f).clickable { onAlarm() }.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(q.name.uppercase(TR), color = pal.teal, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(q.market.label, color = pal.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
            Text(q.time, color = pal.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
        Text(fmt(q.buy), color = pal.muted, fontSize = 15.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(88.dp))
        Column(Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
            Text(fmt(q.sell), color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text("$arrow ${signed(q.change)} (${String.format(TR, "%.2f", q.pct)}%)", color = color, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun Portfoy(
    quotes: List<Quote>, holdings: List<Holding>, edit: Boolean,
    onRemove: (String) -> Unit, onMove: (String, Int) -> Unit, onAdd: () -> Unit
) {
    val byKey = quotes.associateBy { it.key }
    val total = holdings.sumOf { h -> byKey[h.key]?.let { it.buy * h.amount } ?: 0.0 }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("TOPLAM DEĞER (ALIŞ FİYATIYLA)", color = pal.muted, fontSize = 12.sp)
            Text("₺ ${fmt(total, 2)}", color = pal.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        HorizontalDivider(color = pal.div)
        Box(Modifier.weight(1f)) {
            if (holdings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz varlık yok.\nSağ alttaki + ile ekleyin.", color = pal.muted, textAlign = TextAlign.Center)
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(holdings, key = { _, it -> it.key }) { i, h ->
                    val q = byKey[h.key]
                    val market = Market.valueOf(h.key.substringBefore("|")).label
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (edit) MoveButtons(i > 0, i < holdings.lastIndex, { onMove(h.key, -1) }, { onMove(h.key, 1) })
                        Column(Modifier.weight(1f)) {
                            Text(h.key.substringAfter("|").uppercase(TR), color = pal.teal, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(market, color = pal.muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
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
            FloatingActionButton(
                onClick = onAdd, containerColor = pal.selBg, contentColor = pal.teal,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Text("+", fontSize = 28.sp) }
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
