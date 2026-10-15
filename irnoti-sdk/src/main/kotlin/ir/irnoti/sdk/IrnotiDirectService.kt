package ir.irnoti.sdk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps [DirectChannel] open so pushes arrive instantly even when the app is closed. Only runs for devices
 * on the direct transport (devices that can use FCM never start it). It shows one minimal, silent notification, as Android requires.
 */
class IrnotiDirectService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cfg = Irnoti.configOrNull()
        if (cfg == null || Irnoti.currentTransport() != "direct") {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(cfg)
        DirectChannel.start()
        return START_STICKY
    }

    override fun onDestroy() {
        DirectChannel.stop()
        super.onDestroy()
    }

    private fun startInForeground(cfg: IrnotiConfig) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, cfg.directNotificationTitle, NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
            }
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(cfg.smallIcon)
            .setContentTitle(cfg.directNotificationTitle)
            .setContentText(cfg.directNotificationText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
    }

    companion object {
        private const val CHANNEL = "irnoti_connection"
        private const val ID = 0x1AF0

        /** Starts the service; returns false when Android refuses (app in background on API 31+), so the caller can fall back. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, IrnotiDirectService::class.java))
            true
        } catch (_: Exception) {
            false
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, IrnotiDirectService::class.java)) }
        }
    }
}
