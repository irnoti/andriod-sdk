package ir.irnoti.sdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Builds the notification from an IRNoti data payload (keys in docs/device-api.md). */
internal object NotificationRenderer {

    fun ensureChannel(c: Context, cfg: IrnotiConfig) = ensureChannel(c, cfg.defaultChannelId, cfg.defaultChannelName)

    private fun ensureChannel(c: Context, id: String, name: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun show(c: Context, cfg: IrnotiConfig, d: Map<String, String>) {
        val id = d["irn_id"] ?: return
        val channel = d["channel"]?.takeIf { it.isNotBlank() } ?: cfg.defaultChannelId
        ensureChannel(c, channel, if (channel == cfg.defaultChannelId) cfg.defaultChannelName else channel)

        val target = d["deeplink"]?.takeIf { it.isNotBlank() } ?: d["url"].orEmpty()
        val b = NotificationCompat.Builder(c, channel)
            .setSmallIcon(cfg.smallIcon)
            .setContentTitle(d["title"].orEmpty())
            .setContentText(d["body"].orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(d["body"].orEmpty()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(click(c, id, target, null, 0))
            .setDeleteIntent(dismiss(c, id))

        d["image"]?.takeIf { it.startsWith("https://") }?.let { url ->
            download(url)?.let { b.setLargeIcon(it).setStyle(NotificationCompat.BigPictureStyle().bigPicture(it).bigLargeIcon(null as Bitmap?)) }
        }

        val actions = runCatching { JSONArray(d["actions"] ?: "[]") }.getOrDefault(JSONArray())
        for (i in 0 until minOf(2, actions.length())) {
            val a = actions.getJSONObject(i)
            b.addAction(0, a.optString("title"), click(c, id, a.optString("url").ifEmpty { target }, a.optString("id"), i + 1))
        }

        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(c).notify(id.hashCode(), b.build())
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS revoked between the check and the call */ }
    }

    private fun click(c: Context, id: String, target: String, actionId: String?, req: Int): PendingIntent {
        val i = Intent(c, IrnotiClickActivity::class.java)
            .putExtra(IrnotiClickActivity.EXTRA_ID, id)
            .putExtra(IrnotiClickActivity.EXTRA_TARGET, target)
            .putExtra(IrnotiClickActivity.EXTRA_ACTION, actionId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(c, id.hashCode() + req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dismiss(c: Context, id: String): PendingIntent {
        val i = Intent(c, IrnotiDismissReceiver::class.java).putExtra(IrnotiClickActivity.EXTRA_ID, id)
        return PendingIntent.getBroadcast(c, id.hashCode() + 99, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun download(url: String): Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
}
