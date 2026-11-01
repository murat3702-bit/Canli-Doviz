package com.mb.altintakip.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
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

// Son yeni veriden bu yana geçebilecek azami süre; aşılırsa akış "canlı değil" sayılır.
private const val LIVE_TIMEOUT_MS = 20_000L

class MainVM(app: Application) : AndroidViewModel(app) {
    private val ctx: Application = app

    val quotes = MutableStateFlow<List<Quote>>(emptyList())
    val status = MutableStateFlow("Yükleniyor…")
    // Canlılık: internet varken VE son 20 sn içinde API'den gerçekten yeni (değişmiş) veri geldiyse true.
    // Bağlantı kopması, sunucunun yanıt vermemesi, boş veya hiç değişmeyen yanıtlar (piyasa kapalı) false yapar.
    val isLive = MutableStateFlow(false)
    @Volatile private var lastDataTime = 0L // SystemClock.elapsedRealtime() cinsinden, 0 = henüz veri yok
    // Cihazın internet bağlantısı (Wi-Fi / mobil veri / ethernet / VPN); yoksa uyarı penceresi açılır.
    val online = MutableStateFlow(true)
    val holdings = MutableStateFlow<List<Holding>>(emptyList())
    val favorites = MutableStateFlow<List<String>>(emptyList())
    val startTab = MutableStateFlow("FAV")
    val theme = MutableStateFlow("SYSTEM")
    val swipe = MutableStateFlow(true)
    val alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val sound = MutableStateFlow("")
    val refreshSec = MutableStateFlow(5)
    val flashMs = MutableStateFlow(2000)

    private val prefs = app.getSharedPreferences("portfoy", 0)

    // Kalem bazında durum: son gerçek (sıfır olmayan) değişim + son fiyat + fiyatın en son hareket ettiği an.
    // Kapalı piyasada site değişimi %0,00'a sıfırlasa da Cuma kapanışındaki değişim gösterilebilsin diye saklanır.
    private class ItemState(var change: Double, var pct: Double, var buy: Double, var sell: Double, var moveAt: Long)

    private val items = HashMap<String, ItemState>()
    private var itemsSavedAt = 0L

    // Fiyat bu süre hiç oynamadıysa kalem "donmuş" (kapalı) sayılır.
    private val frozenMs = 120_000L

    private fun loadItems() {
        prefs.getString("itemState", "").orEmpty().lines().forEach { line ->
            val p = line.split('\t')
            if (p.size == 6) {
                val v = p.drop(1).map { it.toDoubleOrNull() }
                if (v.none { it == null }) items[p[0]] = ItemState(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!.toLong())
            }
        }
        // Eski sürümden (yalnızca değişim saklayan) geçiş: fiyat bilgisi yok, donmuş sayılması için süre dolmalı.
        prefs.getString("lastChg", "").orEmpty().lines().forEach { line ->
            val p = line.split('\t')
            if (p.size == 3 && p[0] !in items) {
                val c = p[1].toDoubleOrNull()
                val pc = p[2].toDoubleOrNull()
                if (c != null && pc != null) items[p[0]] = ItemState(c, pc, Double.NaN, Double.NaN, 0L)
            }
        }
    }

    private fun saveItems() {
        prefs.edit().putString(
            "itemState",
            items.entries.joinToString("\n") { (k, v) -> "$k\t${v.change}\t${v.pct}\t${v.buy}\t${v.sell}\t${v.moveAt}" }
        ).apply()
        itemsSavedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Kalem bazlı kural (her kalem kendi fiyat hareketine bakar):
     *  - Site sıfırdan farklı değişim gönderiyorsa: olduğu gibi göster, "son gerçek değişim" olarak sakla.
     *  - Site değişimi 0,00 gönderiyor VE bu kalemin fiyatı son 2 dk'dır hiç oynamadıysa (kapalı): saklanan
     *    son gerçek değişimi (Cuma kapanışı) göster ve closed = true yap ("KAPANIŞ" yazısı).
     *  - Site 0,00 gönderiyor ama fiyat oynuyorsa (işlem görüyor): sitenin değerini göster.
     */
    private fun carryClosingChange(list: List<Quote>): List<Quote> {
        val now = System.currentTimeMillis()
        var dirty = false
        val out = list.map { q ->
            val st = items.getOrPut(q.key) { dirty = true; ItemState(0.0, 0.0, Double.NaN, Double.NaN, now) }
            val moved = st.buy != q.buy || st.sell != q.sell
            if (moved) { st.buy = q.buy; st.sell = q.sell; st.moveAt = now; dirty = true }
            val frozen = !moved && now - st.moveAt >= frozenMs
            when {
                q.change != 0.0 || q.pct != 0.0 -> {
                    if (st.change != q.change || st.pct != q.pct) { st.change = q.change; st.pct = q.pct; dirty = true }
                    q
                }
                frozen && (st.change != 0.0 || st.pct != 0.0) -> q.copy(change = st.change, pct = st.pct, closed = true)
                else -> q
            }
        }
        if (dirty && SystemClock.elapsedRealtime() - itemsSavedAt > 30_000L) saveItems()
        return out
    }
    private val lock = Mutex()
    private val order = LinkedHashMap<String, Int>()
    @Volatile private var active = true
    private val kick = Channel<Unit>(Channel.CONFLATED)

    private val cm = app.getSystemService(ConnectivityManager::class.java)

    private fun isOnline(): Boolean {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** isLive = internet var ve son yeni veri 20 sn'den eski değil. */
    private fun updateLive() {
        val t = lastDataTime
        isLive.value = online.value && t != 0L && SystemClock.elapsedRealtime() - t <= LIVE_TIMEOUT_MS
    }

    private fun setOnline(on: Boolean) {
        if (online.value == on) return
        online.value = on
        if (on) {
            kick.trySend(Unit) // bağlantı geri geldi: beklemeden yenile
        } else {
            isLive.value = false
            status.value = "Ağ bağlantısı yok"
        }
    }

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) setOnline(true)
        }

        override fun onLost(network: Network) {
            // Wi-Fi <-> mobil geçişinde anlık kopmayı yok saymak için kısa bekleyip tekrar kontrol et.
            viewModelScope.launch {
                delay(1500)
                setOnline(isOnline())
            }
        }
    }

    init {
        loadItems()
        online.value = isOnline()
        if (!online.value) status.value = "Ağ bağlantısı yok"
        runCatching { cm?.registerDefaultNetworkCallback(netCallback) }
        // Watchdog: saniyede bir canlılığı yeniden hesaplar; veri 20 sn gelmezse isLive false olur.
        viewModelScope.launch {
            while (isActive) {
                updateLive()
                delay(1000)
            }
        }
        favorites.value = prefs.getString("fav", "").orEmpty().lines().filter { it.isNotBlank() }.distinct()
        startTab.value = prefs.getString("start", "FAV") ?: "FAV"
        theme.value = prefs.getString("theme", "SYSTEM") ?: "SYSTEM"
        swipe.value = prefs.getBoolean("swipe", true)
        refreshSec.value = prefs.getInt("refreshSec", 5).let { v -> listOf(3, 5, 7).minByOrNull { kotlin.math.abs(it - v) } ?: 5 }
        flashMs.value = prefs.getInt("flashMs", 2000).let { v -> listOf(500, 1000, 1500, 2000, 2500, 3000).minByOrNull { kotlin.math.abs(it - v) } ?: 2000 }
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
                withTimeoutOrNull(refreshSec.value * 1000L) { kick.receive() }
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
            // Veri çekme döngüsüne ağ kontrolü: bağlantı yoksa istek atma, göstergeyi kırmızıya al.
            if (!isOnline()) {
                setOnline(false)
                isLive.value = false
                status.value = "Ağ bağlantısı yok"
                return
            }
            online.value = true
            val results = Repo.fetchAll()
            val fetched = results.mapNotNull { it.getOrNull() }.flatten()
            val ok = if (fetched.isEmpty()) fetched else carryClosingChange(fetched)
            if (ok.isEmpty()) {
                val err = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.message ?: "veri bulunamadı"
                status.value = "Bağlantı hatası: $err"
                updateLive() // boş yanıt yeni veri sayılmaz; 20 sn dolunca watchdog göstergeyi kırmızıya çeker
                return
            }
            ok.forEach { order.getOrPut(it.name.uppercase(Locale("tr", "TR"))) { order.size } }
            // Yeni veri paketi = en az bir fiyatın (alış/satış) değişmesi ya da yeni bir kalemin gelmesi.
            // Fiyatlar hiç değişmiyorsa (piyasa kapalı, sunucu aynı yanıtı yolluyor) zaman damgası ilerlemez.
            val old = quotes.value.associateBy { it.key }
            val changed = ok.any { q -> old[q.key]?.let { it.buy != q.buy || it.sell != q.sell } ?: true }
            val merged = old + ok.associateBy { it.key }
            quotes.value = merged.values.sortedWith(
                compareBy({ it.category.ordinal }, { order[it.name.uppercase(Locale("tr", "TR"))] ?: 0 }, { it.market.ordinal })
            )
            if (changed) lastDataTime = SystemClock.elapsedRealtime()
            updateLive()
            status.value = "Son güncelleme " + SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date()) +
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

    fun setHoldingAmount(key: String, amount: Double) =
        save(holdings.value.map { if (it.key == key) it.copy(amount = amount) else it })

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

    fun setSwipe(v: Boolean) {
        prefs.edit().putBoolean("swipe", v).apply()
        swipe.value = v
    }

    fun setRefreshSec(v: Int) {
        prefs.edit().putInt("refreshSec", v).apply()
        refreshSec.value = v
        kick.trySend(Unit) // bekleyen döngüyü uyandır, yeni süre hemen geçerli olsun
    }

    fun setFlashMs(v: Int) {
        prefs.edit().putInt("flashMs", v).apply()
        flashMs.value = v
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

    override fun onCleared() {
        runCatching { cm?.unregisterNetworkCallback(netCallback) }
        if (items.isNotEmpty()) saveItems()
        super.onCleared()
    }
}
