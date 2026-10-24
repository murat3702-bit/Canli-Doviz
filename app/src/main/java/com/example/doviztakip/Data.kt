package com.example.doviztakip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

enum class Market(val label: String, val slug: String) {
    KAPALI("KAPALI ÇARŞI", "kapali-carsi"),
    SERBEST("SERBEST PİYASA", "serbest-piyasa")
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
}
