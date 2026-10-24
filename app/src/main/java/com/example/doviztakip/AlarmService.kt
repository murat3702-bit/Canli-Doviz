package com.example.doviztakip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Aktif alarm varken arka planda 30 saniyede bir fiyatları kontrol eder. */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("takip", "Alarm takibi", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, "takip")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Fiyat alarmları izleniyor")
            .setContentText("Döviz Takip arka planda fiyatları kontrol ediyor")
            .setOngoing(true)
            .setContentIntent(openApp(this))
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        ServiceCompat.startForeground(this, 1, n, type)
        if (job == null) job = scope.launch { while (isActive) { poll(); delay(30_000) } }
        return START_STICKY
    }

    private suspend fun poll() {
        val active = Store.load(this).filter { it.enabled }
        if (active.isEmpty()) { stopSelf(); return }
        val combos = Category.values().flatMap { c -> Market.values().map { m -> c to m } }
        val quotes = coroutineScope {
            combos.map { (c, m) -> async { runCatching { Repo.fetch(c, m) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().associateBy { it.key }
        for (a in active) {
            val q = quotes[a.key] ?: continue
            val price = if (a.useSell) q.sell else q.buy
            val hit = if (a.above) price >= a.target else price <= a.target
            if (hit) {
                Store.update(this) { l -> l.map { if (it.id == a.id) it.copy(enabled = false) else it } }
                notifyHit(this, a, q, price)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun sync(c: Context) {
            runCatching {
                val i = Intent(c, AlarmService::class.java)
                if (Store.load(c).any { it.enabled }) ContextCompat.startForegroundService(c, i) else c.stopService(i)
            }
        }

        private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun fmtPrice(v: Double) =
            String.format(Locale("tr", "TR"), if (v < 100) "%,.4f" else "%,.2f", v)

        fun notifyHit(c: Context, a: Alarm, q: Quote, price: Double) {
            val nm = c.getSystemService(NotificationManager::class.java)
            val saved = Store.soundUri(c)
            val sound: Uri = if (saved.isNotBlank()) Uri.parse(saved)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            // Kanal sesi sonradan değişmediği için her ses kendi kanalını kullanır.
            val chId = "alarm_" + sound.toString().hashCode()
            nm.notificationChannels
                .filter { it.id.startsWith("alarm_") && it.id != chId }
                .forEach { nm.deleteNotificationChannel(it.id) }
            val ch = NotificationChannel(chId, "Fiyat alarmları", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
            val dir = if (a.above) "hedefin üstüne çıktı" else "hedefin altına düştü"
            val n = NotificationCompat.Builder(c, chId)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("${q.name} $dir")
                .setContentText("${q.market.label}: ${fmtPrice(price)} (hedef ${fmtPrice(a.target)}, ${if (a.useSell) "satış" else "alış"})")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .build()
            nm.notify(a.id.toInt(), n)
        }
    }
}
