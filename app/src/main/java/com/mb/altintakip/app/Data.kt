package com.mb.altintakip.app

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