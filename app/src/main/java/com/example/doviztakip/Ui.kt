package com.example.doviztakip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val BG = Color(0xFF0B0D10)
private val TEAL = Color(0xFF4FC3DC)
private val MUTED = Color(0xFF8E9AA8)
private val SEL_BG = Color(0xFF2F5D78)
private val GREEN = Color(0xFF6FD36F)
private val RED = Color(0xFFFF5A4D)
private val DIV = Color(0xFF1E2329)
private val TR = Locale("tr", "TR")

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TEAL, background = BG, surface = Color(0xFF14181D),
            onSurface = Color.White, onBackground = Color.White
        ),
        content = content
    )
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
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val tabs = listOf("PORTFÖY", "DÖVİZ", "ALTIN")

    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, t ->
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (tab == i) SEL_BG else Color.Transparent)
                        .clickable { tab = i }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(t, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = if (tab == i) TEAL else MUTED)
                }
            }
        }
        if (tab != 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("(CANLI)", color = MUTED, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("ALIŞ", color = MUTED, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                Text("SATIŞ", color = MUTED, fontSize = 12.sp, modifier = Modifier.width(100.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Portfoy(quotes, holdings, vm::removeHolding) { showAdd = true }
                1 -> QuoteList(quotes.filter { it.category == Category.DOVIZ })
                else -> QuoteList(quotes.filter { it.category == Category.ALTIN })
            }
        }
        Text(status, color = MUTED, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    if (showAdd) AddDialog(quotes, { showAdd = false }) { k, a -> vm.addHolding(k, a); showAdd = false }
}

@Composable
fun QuoteList(list: List<Quote>) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Veri bekleniyor…", color = MUTED) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.key }) { q ->
            QuoteRow(q)
            HorizontalDivider(color = DIV, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun QuoteRow(q: Quote) {
    val color = when { q.change > 0.0049 -> GREEN; q.change < -0.0049 -> RED; else -> MUTED }
    val arrow = when { q.change > 0.0049 -> "▲"; q.change < -0.0049 -> "▼"; else -> "■" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(q.name.uppercase(TR), color = TEAL, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${q.market.label} · ${q.time}", color = MUTED, fontSize = 11.sp)
        }
        Text(fmt(q.buy), color = MUTED, fontSize = 17.sp, modifier = Modifier.width(92.dp))
        Column(Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
            Text(fmt(q.sell), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("$arrow ${signed(q.change)} (${String.format(TR, "%.2f", q.pct)}%)", color = color, fontSize = 11.sp)
        }
    }
}

@Composable
fun Portfoy(quotes: List<Quote>, holdings: List<Holding>, onRemove: (String) -> Unit, onAdd: () -> Unit) {
    val byKey = quotes.associateBy { it.key }
    val total = holdings.sumOf { h -> byKey[h.key]?.let { it.buy * h.amount } ?: 0.0 }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("TOPLAM DEĞER (ALIŞ FİYATIYLA)", color = MUTED, fontSize = 12.sp)
            Text("₺ ${fmt(total, 2)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = DIV)
        Box(Modifier.weight(1f)) {
            if (holdings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz varlık yok.\nSağ alttaki + ile ekleyin.", color = MUTED, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(holdings, key = { it.key }) { h ->
                    val q = byKey[h.key]
                    val market = Market.valueOf(h.key.substringBefore("|")).label
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.key.substringAfter("|").uppercase(TR), color = TEAL, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("$market · ${fmt(h.amount, if (h.amount % 1.0 == 0.0) 0 else 2)} adet/birim", color = MUTED, fontSize = 11.sp)
                        }
                        Text(
                            q?.let { "₺ ${fmt(it.buy * h.amount, 2)}" } ?: "—",
                            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                        )
                        Text("✕", color = MUTED, fontSize = 18.sp, modifier = Modifier.clickable { onRemove(h.key) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
                    }
                    HorizontalDivider(color = DIV, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            FloatingActionButton(
                onClick = onAdd, containerColor = SEL_BG, contentColor = TEAL,
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
