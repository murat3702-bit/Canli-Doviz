package com.example.doviztakip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        val quotes = Repo.fetchAll().mapNotNull { it.getOrNull() }.flatten().associateBy { it.key }
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
        private var player: MediaPlayer? = null
        private val handler = Handler(Looper.getMainLooper())

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

        fun stopSound() {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
        }

        /**
         * Telefondaki herhangi bir ses dosyasını (ya da bildirim sesini) çalar.
         * Alarm ses akışı kullanıldığı için sessiz modda da duyulur.
         * [custom] boşsa ayarlardaki varsayılan ses, o da boşsa telefonun bildirim sesi çalar.
         */
        fun playSound(c: Context, custom: String, maxMs: Long = 30_000) {
            val app = c.applicationContext
            val chosen = custom.ifBlank { Store.soundUri(app) }
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val uri: Uri = if (chosen.isNotBlank()) Uri.parse(chosen) else default
            handler.post {
                stopSound()
                val fallback = { runCatching { RingtoneManager.getRingtone(app, default).play() } }
                try {
                    val mp = MediaPlayer()
                    player = mp
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.setDataSource(app, uri)
                    mp.setOnPreparedListener { it.start() }
                    mp.setOnCompletionListener { stopSound() }
                    mp.setOnErrorListener { _, _, _ -> stopSound(); fallback(); true }
                    mp.prepareAsync()
                    handler.postDelayed({ if (player === mp) stopSound() }, maxMs)
                } catch (e: Exception) {
                    stopSound()
                    fallback()
                }
            }
        }

        fun notifyHit(c: Context, a: Alarm, q: Quote, price: Double) {
            val nm = c.getSystemService(NotificationManager::class.java)
            // Eski sürümlerden kalan sesli kanalları temizle; ses artık uygulama içinden çalınıyor.
            nm.notificationChannels.filter { it.id.startsWith("alarm_") }.forEach { nm.deleteNotificationChannel(it.id) }
            val ch = NotificationChannel("fiyat_alarm", "Fiyat alarmları", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
            val dir = if (a.above) "hedefin üstüne çıktı" else "hedefin altına düştü"
            val n = NotificationCompat.Builder(c, "fiyat_alarm")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("${q.name} $dir")
                .setContentText("${q.market.label}: ${fmtPrice(price)} (hedef ${fmtPrice(a.target)}, ${if (a.useSell) "satış" else "alış"})")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .build()
            nm.notify(a.id.toInt(), n)
            playSound(c, a.sound)
        }
    }
}
