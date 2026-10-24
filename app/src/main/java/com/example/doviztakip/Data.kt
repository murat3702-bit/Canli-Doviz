package com.example.doviztakip

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.util.Locale

enum class Market(val label: String, val slug: String) {
    KAPALI("KAPALI ÇARŞI", "kapali-carsi"),
    SERBEST("SERBEST PİYASA", "serbest-piyasa"),
    IKO("İKO", "iko")
}

enum class Category(val slug: String) {
    DOVIZ("doviz-kurlari"),
    ALTIN("altin-fiyatlari")
}

data class Quote(
    val name: String,
    val market: Market,
    val category: Category,
    val buy: Double,
    val sell: Double,
    val change: Double,
    val pct: Double,
    val time: String
) {
    val key get() = "${market.name}|$name"
}

data class Holding(val key: String, val amount: Double)

object Repo {
    private val client = OkHttpClient()
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    // Adresler tek yerde: site yapısı değişirse sadece burayı düzenleyin.
    private fun url(c: Category, m: Market) = "https://canlidoviz.com/${c.slug}/${m.slug}"

    suspend fun fetch(c: Category, m: Market): List<Quote> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(c, m)).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            parse(r.body!!.string(), c, m)
        }
    }

    private fun num(s: String): Double? =
        s.trim().replace(".", "").replace(",", ".").toDoubleOrNull()

    private fun parse(html: String, c: Category, m: Market): List<Quote> {
        val out = mutableListOf<Quote>()
        for (tr in Jsoup.parse(html).select("table tr")) {
            val tds = tr.select("td")
            if (tds.size < 3) continue
            val first = tds[0].text().trim()
            // Örn: "Gram Altın %-0,03 11:32:55"
            val name = Regex("^(.*?)\\s*(%|\\d{1,2}:\\d{2})").find(first)?.groupValues?.get(1)?.trim() ?: first
            val time = Regex("\\d{1,2}:\\d{2}").find(first)?.value ?: ""
            val nums = tds.drop(1).mapNotNull { num(it.text()) }
            if (name.isEmpty() || nums.size < 2) continue
            val buy = nums[0]
            val sell = nums[1]
            val change: Double
            val pct: Double
            if (c == Category.ALTIN && nums.size >= 5 && nums[4] != 0.0) {
                change = sell - nums[4]            // satış - kapanış
                pct = change / nums[4] * 100
            } else {
                pct = Regex("%\\s*(-?[\\d.,]+)").find(first)?.groupValues?.get(1)?.let { num(it) } ?: 0.0
                change = sell - sell / (1 + pct / 100)
            }
            out += Quote(name, m, c, buy, sell, change, pct, time)
        }
        return out
    }
    // ---------- İKO sağlayıcısı (ikinci veri kaynağı) ----------
    // Adres tek yerde: değişirse sadece burayı düzenleyin.
    const val IKO_URL = "http://146.19.208.20:8021/Urun/MobilFiyat?BolgeId=1025"

    /** Tüm kaynakları paralel çeker. Sonuncu eleman İKO kaynağıdır. */
    suspend fun fetchAll(): List<Result<List<Quote>>> = coroutineScope {
        val jobs = mutableListOf<Deferred<Result<List<Quote>>>>()
        for (c in Category.values()) {
            for (m in listOf(Market.KAPALI, Market.SERBEST)) {
                jobs += async { runCatching { fetch(c, m) } }
            }
        }
        jobs += async { runCatching { fetchIko() } }
        jobs.awaitAll()
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body!!.string()
        }
    }

    suspend fun rawIko(): String = download(IKO_URL)

    suspend fun fetchIko(): List<Quote> = parseIko(download(IKO_URL))

    fun parseIko(body: String): List<Quote> {
        val t = body.trim()
        return if (t.startsWith("{") || t.startsWith("[")) parseJson(t) else parse(t, Category.ALTIN, Market.IKO)
    }

    private fun norm(s: String): String = s.lowercase(Locale.ROOT)
        .replace("\u0307", "").replace("ı", "i").replace("ş", "s").replace("ğ", "g")
        .replace("ü", "u").replace("ö", "o").replace("ç", "c")

    private fun parseNum(s: String): Double? {
        var t = s.trim().replace(" ", "").replace("₺", "")
        if (t.isEmpty()) return null
        t = if (t.contains(',') && t.contains('.')) t.replace(".", "").replace(",", ".")
        else if (t.contains(',')) t.replace(",", ".")
        else t
        return t.toDoubleOrNull()
    }

    private fun numOf(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> parseNum(v)
        else -> null
    }

    private fun parseJson(text: String): List<Quote> {
        val root: Any = runCatching { JSONTokener(text).nextValue() }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Quote>()
        fun walk(n: Any?) {
            when (n) {
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
                is JSONObject -> {
                    val q = toQuote(n)
                    if (q != null) out += q else n.keys().forEach { walk(n.opt(it)) }
                }
            }
        }
        walk(root)
        return out
    }

    /** Alanları adlarından tahmin eder: alış/satış/ürün adı/tarih/kapanış. */
    private fun toQuote(o: JSONObject): Quote? {
        val keys = o.keys().asSequence().toList()
        fun find(vararg parts: String): String? {
            for (p in parts) keys.firstOrNull { norm(it).contains(p) }?.let { return it }
            return null
        }
        val buy = numOf(o.opt(find("alis", "buy", "bid") ?: return null)) ?: return null
        val sell = numOf(o.opt(find("satis", "sell", "ask") ?: return null)) ?: return null
        if (buy <= 0.0 && sell <= 0.0) return null
        val name = find("urunadi", "urunad", "adi", "name", "isim", "baslik", "title", "kod", "code")
            ?.let { o.opt(it)?.toString()?.trim() }?.takeIf { it.isNotEmpty() }
            ?: keys.firstNotNullOfOrNull { k ->
                (o.opt(k) as? String)?.takeIf { v -> v.any { ch -> ch.isLetter() } && numOf(v) == null }
            } ?: return null
        val time = find("tarih", "zaman", "time", "date", "guncelle")
            ?.let { Regex("\\d{1,2}:\\d{2}").find(o.opt(it)?.toString().orEmpty())?.value } ?: ""
        val prev = find("kapanis", "onceki", "close", "prev")?.let { numOf(o.opt(it)) }
        val pctKey = find("yuzde", "oran", "percent", "degisim")?.let { numOf(o.opt(it)) }
        val change: Double
        val pct: Double
        if (prev != null && prev != 0.0) {
            change = sell - prev
            pct = change / prev * 100
        } else if (pctKey != null) {
            pct = pctKey
            change = sell - sell / (1 + pct / 100)
        } else {
            change = 0.0
            pct = 0.0
        }
        return Quote(name, Market.IKO, Category.ALTIN, buy, sell, change, pct, time)
    }

}
