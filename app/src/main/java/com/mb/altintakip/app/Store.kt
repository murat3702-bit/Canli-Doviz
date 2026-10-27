package com.mb.altintakip.app

import android.content.Context

data class Alarm(
    val id: Long,
    val key: String,
    val target: Double,
    val above: Boolean,   // true: fiyat hedefin üstüne çıkınca, false: altına düşünce
    val useSell: Boolean, // true: satış fiyatı, false: alış fiyatı
    val enabled: Boolean,
    val sound: String = "" // boşsa varsayılan alarm sesi kullanılır
)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("alarmlar", Context.MODE_PRIVATE)

    @Synchronized
    fun load(c: Context): List<Alarm> =
        p(c).getString("a", "").orEmpty().lines().mapNotNull { line ->
            val t = line.split("\t")
            if (t.size < 6) return@mapNotNull null
            val id = t[0].toLongOrNull() ?: return@mapNotNull null
            val target = t[2].toDoubleOrNull() ?: return@mapNotNull null
            Alarm(id, t[1], target, t[3] == "1", t[4] == "1", t[5] == "1", t.getOrElse(6) { "" })
        }

    @Synchronized
    fun update(c: Context, f: (List<Alarm>) -> List<Alarm>) {
        val l = f(load(c))
        p(c).edit().putString(
            "a",
            l.joinToString("\n") {
                val snd = it.sound.replace("\t", "").replace("\n", "")
                "${it.id}\t${it.key}\t${it.target}\t${if (it.above) 1 else 0}\t${if (it.useSell) 1 else 0}\t${if (it.enabled) 1 else 0}\t$snd"
            }
        ).apply()
    }

    fun soundUri(c: Context): String = p(c).getString("sound", "").orEmpty()
    fun setSound(c: Context, s: String) = p(c).edit().putString("sound", s).apply()
}
