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
import java.util.concurrent.TimeUnit

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
    const val VERSION = "1.1.0"
    private const val HEARTBEAT_MS = 24L * 3600 * 1000
    /** AUTO transport: how often FCM reachability is re-checked (a device on the direct channel returns to FCM once it can). */
    private const val PROBE_MS = 30L * 60 * 1000
    private const val FCM_TOKEN_TIMEOUT_S = 10L

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "irnoti").apply { isDaemon = true } }
    private var app: Context? = null
    private var cfgOrNull: IrnotiConfig? = null
    private var storeOrNull: Storage? = null
    private var apiOrNull: Api? = null

    internal fun configOrNull() = cfgOrNull
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
        stopDirect(context)
        store.clearDevice()
    }

    // ---- hooks used by IrnotiMessagingService / IrnotiClickActivity ----

    /** "fcm" or "direct": the transport this device is currently registered with (null before the first registration). */
    @JvmStatic
    fun currentTransport(): String? = storeOrNull?.provider

    /** Restarts the direct channel after a reboot / app update. Called by the boot receiver; harmless otherwise. */
    @JvmStatic
    fun resumeDirect(context: Context) {
        if (cfgOrNull == null || store.provider != "direct") return
        startDirect(context)
    }

    // ---- hooks used by DirectChannel ----

    /** A message arrived on the direct channel. The server resends until it sees the delivery report, hence the de-duplication. */
    internal fun onDirectMessage(data: Map<String, String>) {
        val id = data["irn_id"] ?: return
        if (store.firstSight(id)) handleMessage(context, data) else report(id, "delivered") // repeat the ack, the first one may have been lost
    }

    /** The server no longer knows this device: register from scratch (which restarts the channel if it is still needed). */
    internal fun onDeviceGone() {
        worker.execute {
            try {
                store.clearDevice()
                register()
            } catch (_: Exception) { /* retried on the next start */ }
        }
    }

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
        if (cfg.transport == Transport.DIRECT) return
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

    /** FCM token or null when Firebase is not configured, Play Services are missing or Google cannot be reached. */
    private fun fcmTokenOrNull(): String? = try {
        Tasks.await(FirebaseMessaging.getInstance().token, FCM_TOKEN_TIMEOUT_S, TimeUnit.SECONDS)?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** Which transport to register with, and the FCM token when it is fcm. */
    private fun chooseTransport(): Pair<String, String?> {
        store.lastProbe = System.currentTimeMillis()
        return when (cfg.transport) {
            Transport.DIRECT -> "direct" to null
            Transport.FCM -> "fcm" to Tasks.await(FirebaseMessaging.getInstance().token) // 1.0 behavior: failures propagate and are retried
            Transport.AUTO -> {
                val token = if (FcmReachability.check()) fcmTokenOrNull() else null
                if (token != null) "fcm" to token else "direct" to null
            }
        }
    }

    private fun ensureRegistered() {
        if (store.deviceId == null || store.deviceToken == null) register()
    }

    private fun register() {
        val c = context
        val (provider, token) = chooseTransport()
        val pkg = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"
        val body = JSONObject()
            .put("install_id", store.installId)
            .put("platform", "android")
            .put("push_provider", provider)
            .put("app_version", pkg)
            .put("os_version", Build.VERSION.RELEASE)
            .put("device_model", Build.MODEL)
            .put("sdk_version", "android/$VERSION")
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timezone", TimeZone.getDefault().id)
            .put("notifications_enabled", androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled())
        if (token != null) body.put("push_token", token)
        val r = api.call("POST", "/devices", body, auth = false)
        store.deviceId = r.getString("device_id")
        r.optString("device_token").takeIf { it.isNotEmpty() }?.let { store.deviceToken = it }
        store.provider = provider
        store.pushToken = token
        store.lastSync = System.currentTimeMillis()
        applyTransport()
    }

    /**
     * Keeps the server in line with the device: registers if needed, otherwise PATCHes when the FCM token changed, the daily heartbeat
     * is due, or (AUTO) a periodic probe finds the best transport is no longer the registered one.
     */
    private fun sync(newToken: String? = null) {
        if (store.deviceId == null || store.deviceToken == null) return register()
        val now = System.currentTimeMillis()
        val stale = now - store.lastSync > HEARTBEAT_MS
        if (newToken != null && store.provider == "fcm") {
            if (newToken != store.pushToken) patch(JSONObject().put("push_token", newToken), "fcm", newToken)
            return
        }
        val probeDue = cfg.transport == Transport.AUTO && now - store.lastProbe > PROBE_MS
        if (!stale && !probeDue) return applyTransport()
        val (provider, token) = chooseTransport()
        val changed = provider != store.provider || (provider == "fcm" && token != store.pushToken)
        if (!changed && !stale) return applyTransport()
        val body = JSONObject()
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("notifications_enabled", androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
        if (changed) body.put("push_provider", provider)
        if (token != null) body.put("push_token", token)
        patch(body, provider, token)
    }

    private fun patch(body: JSONObject, provider: String, token: String?) {
        authed { api.call("PATCH", "/devices/${store.deviceId}", body) }
        store.provider = provider
        store.pushToken = token
        store.lastSync = System.currentTimeMillis()
        applyTransport()
    }

    /** Starts the direct channel for a device on the direct transport and stops it for one on FCM. */
    private fun applyTransport() {
        if (store.provider == "direct") startDirect(context) else stopDirect(context)
    }

    private fun startDirect(c: Context) {
        IrnotiCatchUpJob.schedule(c)
        // The foreground service hosts the live channel; if Android refuses it (app in background), or it is switched off, the channel
        // still runs as long as the process lives and the periodic job covers the rest.
        if (!(cfg.directService && IrnotiDirectService.start(c))) DirectChannel.start()
    }

    private fun stopDirect(c: Context) {
        DirectChannel.stop()
        IrnotiDirectService.stop(c)
        IrnotiCatchUpJob.cancel(c)
    }

    /** Runs [call]; if the server forgot the device, registers again and retries once. */
    private fun <T> authed(call: () -> T): T = try {
        call()
    } catch (e: ApiException) {
        if (!e.deviceGone) throw e
        store.clearDevice()
        register()
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
