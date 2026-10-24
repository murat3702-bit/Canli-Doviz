package com.example.doviztakip

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainVM(app: Application) : AndroidViewModel(app) {
    val quotes = MutableStateFlow<List<Quote>>(emptyList())
    val status = MutableStateFlow("Yükleniyor…")
    val holdings = MutableStateFlow<List<Holding>>(emptyList())
    val favorites = MutableStateFlow<Set<String>>(emptySet())
    val startTab = MutableStateFlow("FAV")

    private val prefs = app.getSharedPreferences("portfoy", 0)
    private val lock = Mutex()
    private val order = LinkedHashMap<String, Int>()
    @Volatile private var active = true

    init {
        favorites.value = prefs.getString("fav", "").orEmpty().lines().filter { it.isNotBlank() }.toSet()
        startTab.value = prefs.getString("start", "FAV") ?: "FAV"
        holdings.value = prefs.getString("h", "").orEmpty().lines().mapNotNull { line ->
            val p = line.split("\t")
            if (p.size == 2) p[1].toDoubleOrNull()?.let { Holding(p[0], it) } else null
        }
        viewModelScope.launch {
            while (isActive) {
                if (active) refresh()
                delay(10_000)
            }
        }
    }

    fun setActive(b: Boolean) {
        active = b
        if (b) viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (!lock.tryLock()) return
        try {
            val combos = Category.values().flatMap { c -> Market.values().map { m -> c to m } }
            val results = coroutineScope {
                combos.map { (c, m) -> async { runCatching { Repo.fetch(c, m) } } }.awaitAll()
            }
            val ok = results.mapNotNull { it.getOrNull() }.flatten()
            if (ok.isEmpty()) {
                val err = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.message ?: "veri bulunamadı"
                status.value = "Bağlantı hatası: $err"
                return
            }
            ok.forEach { order.getOrPut(it.name) { order.size } }
            val merged = quotes.value.associateBy { it.key } + ok.associateBy { it.key }
            quotes.value = merged.values.sortedWith(
                compareBy({ it.category.ordinal }, { order[it.name] ?: 0 }, { it.market.ordinal })
            )
            status.value = "Son güncelleme " + SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date())
        } finally {
            lock.unlock()
        }
    }

    private fun save(l: List<Holding>) {
        prefs.edit().putString("h", l.joinToString("\n") { "${it.key}\t${it.amount}" }).apply()
        holdings.value = l
    }

    fun addHolding(key: String, amount: Double) {
        val l = holdings.value
        val ex = l.find { it.key == key }
        save(if (ex == null) l + Holding(key, amount) else l.map { if (it.key == key) it.copy(amount = it.amount + amount) else it })
    }

    fun removeHolding(key: String) = save(holdings.value.filter { it.key != key })

    fun toggleFav(key: String) {
        val n = if (key in favorites.value) favorites.value - key else favorites.value + key
        prefs.edit().putString("fav", n.joinToString("\n")).apply()
        favorites.value = n
    }

    fun setStart(v: String) {
        prefs.edit().putString("start", v).apply()
        startTab.value = v
    }
}
