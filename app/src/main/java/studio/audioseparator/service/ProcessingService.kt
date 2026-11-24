package studio.audioseparator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import studio.audioseparator.MainActivity

/** Ayrıştırma sürerken uygulamanın arka planda öldürülmemesi için ön plan servisi + CPU wake lock. */
class ProcessingService : Service() {
    companion object {
        private const val CH = "processing"
        private const val ID = 1

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, ProcessingService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ProcessingService::class.java))
        }

        fun update(ctx: Context, percent: Int, text: String) {
            try {
                ctx.getSystemService(NotificationManager::class.java).notify(ID, build(ctx, percent, text))
            } catch (_: Exception) {}
        }

        private fun build(ctx: Context, percent: Int, text: String): Notification {
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Audio Separator Studio")
                .setContentText("%$percent · $text")
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Ayrıştırma", NotificationManager.IMPORTANCE_LOW))
        val n = build(this, 0, "Hazırlanıyor")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, n)
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studio:separation")
                .apply { acquire(3L * 60 * 60 * 1000) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
    }
}
