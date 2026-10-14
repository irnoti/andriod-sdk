package ir.irnoti.sdk

import android.content.Context
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * IRNoti Android SDK entry point. Wire protocol: docs/device-api.md.
 *
 *     Irnoti.init(this, IrnotiConfig("pk_live_xxx", R.drawable.ic_stat))
 *     Irnoti.identify("user-123")
 *     Irnoti.subscribe("news")
 *
 * Every call is asynchronous and safe to make before the device is registered; operations are queued on one
 * background thread and registration happens first.
 */
object Irnoti {
    const val VERSION = "1.0.0"
    private const val HEARTBEAT_MS = 24L * 3600 * 1000

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "irnoti").apply { isDaemon = true } }
    private var app: Context? = null
    private var cfgOrNull: IrnotiConfig? = null
    private var storeOrNull: Storage? = null
    private var apiOrNull: Api? = null

    internal val cfg get() = cfgOrNull ?: error("Irnoti.init() was not called")
    internal val store get() = storeOrNull ?: error("Irnoti.init() was not called")
    private val api get() = apiOrNull ?: error("Irnoti.init() was not called")
    internal val context get() = app ?: error("Irnoti.init() was not called")

    @JvmStatic
    fun init(context: Context, config: IrnotiConfig) {
        app = context.applicationContext
        cfgOrNull = config
        storeOrNull = Storage(context.applicationContext).also { apiOrNull = Api(config, it) }
        NotificationRenderer.ensureChannel(context.applicationContext, config)
        worker.execute { runCatching { sync(); flushReports() } }
    }

    /** Call when the user changes the notification permission / in-app switch. */
    @JvmStatic
    fun setNotificationsEnabled(enabled: Boolean) = op {
        authed { api.call("PATCH", "/devices/${store.deviceId}", JSONObject().put("notifications_enabled", enabled)) }
    }

    @JvmStatic @JvmOverloads
    fun identify(externalId: String, aliases: Map<String, String> = emptyMap()) = op {
        authed {
            api.call("POST", "/devices/${store.deviceId}/identify",
                JSONObject().put("external_id", externalId).put("aliases", JSONObject(aliases)))
        }
    }

    @JvmStatic
    fun logout() = op { authed { api.call("POST", "/devices/${store.deviceId}/logout", JSONObject()) } }

    @JvmStatic
    fun subscribe(topic: String) = op { authed { api.call("PUT", "/devices/${store.deviceId}/topics/${enc(topic)}", JSONObject()) } }

    @JvmStatic
    fun unsubscribe(topic: String) = op { authed { api.call("DELETE", "/devices/${store.deviceId}/topics/${enc(topic)}") } }

    /** Set tags; a null value removes the tag. */
    @JvmStatic
    fun setTags(tags: Map<String, String?>) = op {
        val o = JSONObject()
        tags.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        authed { api.call("PUT", "/devices/${store.deviceId}/tags", o) }
    }

    @JvmStatic @JvmOverloads
    fun track(name: String, props: Map<String, Any?> = emptyMap()) = op {
        val ev = JSONObject().put("name", name).put("ts", System.currentTimeMillis()).put("props", JSONObject(props))
        authed { api.call("POST", "/events", JSONObject().put("events", JSONArray().put(ev))) }
    }

    /** Full opt-out: deletes the device on the server and forgets local state. */
    @JvmStatic
    fun unregister() = op {
        authed { api.call("DELETE", "/devices/${store.deviceId}") }
        store.clearDevice()
    }

    // ---- hooks used by IrnotiMessagingService / IrnotiClickActivity ----

    /** True when an FCM data payload belongs to IRNoti (lets apps with their own FirebaseMessagingService delegate). */
    @JvmStatic
    fun isIrnotiMessage(data: Map<String, String>) = data.containsKey("irn_id")

    /** Show the notification for an IRNoti payload and record delivery. Call from onMessageReceived if you own the service. */
    @JvmStatic
    fun handleMessage(context: Context, data: Map<String, String>) {
        if (cfgOrNull == null) return // process was started by the push before Application.onCreate ran init: ignore
        val id = data["irn_id"] ?: return
        report(id, "delivered")
        worker.execute { NotificationRenderer.show(context.applicationContext, cfg, data) }
    }

    @JvmStatic
    fun onNewToken(token: String) {
        if (cfgOrNull == null) return
        op { sync(token) }
    }

    internal fun report(messageId: String, type: String, actionId: String? = null) {
        val store = storeOrNull ?: return // tapped before init() ran in this process
        val r = JSONObject().put("message_id", messageId).put("type", type).put("ts", System.currentTimeMillis())
        if (actionId != null) r.put("action_id", actionId)
        store.queueReport(r)
        worker.execute { runCatching { flushReports() } }
    }

    // ---- internals (always on the worker thread) ----

    private fun op(block: () -> Unit) {
        worker.execute {
            try {
                ensureRegistered()
                block()
            } catch (_: Exception) {
                // Best effort: transient failures are retried on the next app start / heartbeat.
            }
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun fcmToken(): String = Tasks.await(FirebaseMessaging.getInstance().token)

    private fun ensureRegistered() {
        if (store.deviceId == null || store.deviceToken == null) register(fcmToken())
    }

    private fun register(token: String) {
        val c = context
        val pkg = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"
        val body = JSONObject()
            .put("install_id", store.installId)
            .put("platform", "android")
            .put("push_provider", "fcm")
            .put("push_token", token)
            .put("app_version", pkg)
            .put("os_version", Build.VERSION.RELEASE)
            .put("device_model", Build.MODEL)
            .put("sdk_version", "android/$VERSION")
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timezone", TimeZone.getDefault().id)
            .put("notifications_enabled", androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled())
        val r = api.call("POST", "/devices", body, auth = false)
        store.deviceId = r.getString("device_id")
        r.optString("device_token").takeIf { it.isNotEmpty() }?.let { store.deviceToken = it }
        store.pushToken = token
        store.lastSync = System.currentTimeMillis()
    }

    /** Register if needed, else PATCH when the token changed or the daily heartbeat is due. */
    private fun sync(newToken: String? = null) {
        val token = newToken ?: fcmToken()
        if (store.deviceId == null || store.deviceToken == null) return register(token)
        val stale = System.currentTimeMillis() - store.lastSync > HEARTBEAT_MS
        if (token == store.pushToken && !stale) return
        authed {
            api.call("PATCH", "/devices/${store.deviceId}", JSONObject()
                .put("push_token", token)
                .put("locale", Locale.getDefault().toLanguageTag())
                .put("notifications_enabled", androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()))
        }
        store.pushToken = token
        store.lastSync = System.currentTimeMillis()
    }

    /** Runs [call]; if the server forgot the device, registers again and retries once. */
    private fun <T> authed(call: () -> T): T = try {
        call()
    } catch (e: ApiException) {
        if (!e.deviceGone) throw e
        store.clearDevice()
        register(fcmToken())
        call()
    }

    private fun flushReports() {
        if (store.deviceToken == null) return
        while (true) {
            val batch = store.takeReports(20)
            if (batch.length() == 0) return
            try {
                authed { api.call("POST", "/reports", JSONObject().put("reports", batch)) }
            } catch (e: ApiException) {
                if (e.transient) store.putBack(batch) // 4xx other than "device gone": drop, they would never succeed
                return
            }
        }
    }
}
