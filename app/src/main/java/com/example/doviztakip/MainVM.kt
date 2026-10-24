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
    private val ctx: Application = app

    val quotes = MutableStateFlow<List<Quote>>(emptyList())
    val status = MutableStateFlow("Yükleniyor…")
    val holdings = MutableStateFlow<List<Holding>>(emptyList())
    val favorites = MutableStateFlow<List<String>>(emptyList())
    val startTab = MutableStateFlow("FAV")
    val theme = MutableStateFlow("SYSTEM")
    val alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val sound = MutableStateFlow("")

    private val prefs = app.getSharedPreferences("portfoy", 0)
    private val lock = Mutex()
    private val order = LinkedHashMap<String, Int>()
    @Volatile private var active = true

    init {
        favorites.value = prefs.getString("fav", "").orEmpty().lines().filter { it.isNotBlank() }.distinct()
        startTab.value = prefs.getString("start", "FAV") ?: "FAV"
        theme.value = prefs.getString("theme", "SYSTEM") ?: "SYSTEM"
        holdings.value = prefs.getString("h", "").orEmpty().lines().mapNotNull { line ->
            val p = line.split("\t")
            if (p.size == 2) p[1].toDoubleOrNull()?.let { Holding(p[0], it) } else null
        }
        alarms.value = Store.load(ctx)
        sound.value = Store.soundUri(ctx)
        AlarmService.sync(ctx)
        viewModelScope.launch {
            while (isActive) {
                if (active) {
                    alarms.value = Store.load(ctx)
                    refresh()
                }
                delay(10_000)
            }
        }
    }

    fun setActive(b: Boolean) {
        active = b
        if (b) {
            alarms.value = Store.load(ctx)
            viewModelScope.launch { refresh() }
        }
    }

    private suspend fun refresh() {
        if (!lock.tryLock()) return
        try {
            val results = Repo.fetchAll()
            val ok = results.mapNotNull { it.getOrNull() }.flatten()
            if (ok.isEmpty()) {
                val err = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.message ?: "veri bulunamadı"
                status.value = "Bağlantı hatası: $err"
                return
            }
            ok.forEach { order.getOrPut(it.name.uppercase(Locale("tr", "TR"))) { order.size } }
            val merged = quotes.value.associateBy { it.key } + ok.associateBy { it.key }
            quotes.value = merged.values.sortedWith(
                compareBy({ it.category.ordinal }, { order[it.name.uppercase(Locale("tr", "TR"))] ?: 0 }, { it.market.ordinal })
            )
            status.value = "Son güncelleme " + SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date()) +
                (results.last().exceptionOrNull()?.let { " · İKO alınamadı: " + (it.message ?: it.javaClass.simpleName).take(40) } ?: "")
        } finally {
            lock.unlock()
        }
    }

    // ---- Portföy ----
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

    // ---- Favoriler / ayarlar ----
    fun toggleFav(key: String) {
        val n = if (key in favorites.value) favorites.value - key else favorites.value + key
        prefs.edit().putString("fav", n.joinToString("\n")).apply()
        favorites.value = n
    }

    private fun <T> moved(l: List<T>, i: Int, delta: Int): List<T> {
        val j = i + delta
        if (i < 0 || j < 0 || j >= l.size) return l
        val m = l.toMutableList()
        val t = m[i]; m[i] = m[j]; m[j] = t
        return m
    }

    fun moveFav(key: String, delta: Int) {
        val n = moved(favorites.value, favorites.value.indexOf(key), delta)
        prefs.edit().putString("fav", n.joinToString("\n")).apply()
        favorites.value = n
    }

    // Sadece aynı kaynak (İKO / Kapalı Çarşı / Serbest Piyasa) içindeki komşuyla yer değiştirir.
    fun moveHolding(key: String, delta: Int) {
        val l = holdings.value
        val i = l.indexOfFirst { it.key == key }
        if (i < 0) return
        val m = key.substringBefore("|")
        var j = i + delta
        while (j in l.indices && l[j].key.substringBefore("|") != m) j += delta
        if (j !in l.indices) return
        val n = l.toMutableList()
        val t = n[i]; n[i] = n[j]; n[j] = t
        save(n)
    }

    fun setStart(v: String) {
        prefs.edit().putString("start", v).apply()
        startTab.value = v
    }

    fun setTheme(v: String) {
        prefs.edit().putString("theme", v).apply()
        theme.value = v
    }

    // ---- Alarmlar ----
    private fun reloadAlarms() {
        alarms.value = Store.load(ctx)
        AlarmService.sync(ctx)
    }

    fun addAlarm(key: String, target: Double, above: Boolean, useSell: Boolean, sound: String = "") {
        Store.update(ctx) { it + Alarm(System.currentTimeMillis(), key, target, above, useSell, true, sound) }
        reloadAlarms()
    }

    fun toggleAlarm(id: Long) {
        Store.update(ctx) { l -> l.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
        reloadAlarms()
    }

    fun removeAlarm(id: Long) {
        Store.update(ctx) { l -> l.filter { it.id != id } }
        reloadAlarms()
    }

    fun setSound(s: String) {
        Store.setSound(ctx, s)
        sound.value = s
    }
}
