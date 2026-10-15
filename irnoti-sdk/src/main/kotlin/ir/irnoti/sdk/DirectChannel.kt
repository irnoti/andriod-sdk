package ir.irnoti.sdk

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * IRNoti's own push channel (Server-Sent Events, docs/device-api.md "ارسال مستقیم"): one long-lived HTTPS request per device.
 * The server replays everything pending on connect, then streams new messages and a ping every ~25 s, so a read that stays silent for
 * [READ_TIMEOUT_MS] means the connection is dead and is re-opened with exponential backoff.
 */
internal object DirectChannel {
    private const val READ_TIMEOUT_MS = 70_000
    private const val DRAIN_TIMEOUT_MS = 20_000
    private const val MIN_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 60_000L

    @Volatile private var thread: Thread? = null
    @Volatile private var stopped = true
    @Volatile private var conn: HttpURLConnection? = null

    fun isRunning() = thread?.isAlive == true && !stopped

    @Synchronized
    fun start() {
        if (isRunning()) return
        stopped = false
        thread = Thread({ loop() }, "irnoti-direct").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        stopped = true
        conn?.disconnect()
        thread?.interrupt()
        thread = null
    }

    /** One-shot catch-up used by the periodic job: fetch what is pending, then return. Returns false when the network failed. */
    fun drain(): Boolean = try {
        runOnce(drain = true)
        true
    } catch (e: ApiException) {
        if (e.deviceGone) Irnoti.onDeviceGone()
        false
    } catch (_: Exception) {
        false
    }

    private fun loop() {
        var backoff = MIN_BACKOFF_MS
        while (!stopped) {
            try {
                runOnce(drain = false)
                backoff = MIN_BACKOFF_MS
            } catch (e: ApiException) {
                if (e.deviceGone) {
                    // The server forgot us: stop, re-register, and the registration restarts the channel.
                    stopped = true
                    Irnoti.onDeviceGone()
                    return
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                // network drop / timeout: fall through to the backoff
            }
            if (stopped) return
            try {
                Thread.sleep(backoff + Random.nextLong(0, backoff / 2 + 1))
            } catch (_: InterruptedException) {
                return
            }
            backoff = minOf(backoff * 2, MAX_BACKOFF_MS)
        }
    }

    private fun runOnce(drain: Boolean) {
        val cfg = Irnoti.cfg
        val store = Irnoti.store
        val deviceId = store.deviceId
        val token = store.deviceToken
        if (deviceId == null || token == null) throw ApiException(401, "invalid_device_token", "not registered")

        val url = cfg.apiBase.trimEnd('/') + "/devices/$deviceId/stream" + if (drain) "?drain=1" else ""
        val c = URL(url).openConnection() as HttpURLConnection
        conn = c
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 10_000
            c.readTimeout = if (drain) DRAIN_TIMEOUT_MS else READ_TIMEOUT_MS
            c.setRequestProperty("X-Irnoti-Key", cfg.appKey)
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "text/event-stream")
            c.setRequestProperty("Cache-Control", "no-cache")
            val status = c.responseCode
            if (status != 200) {
                val err = runCatching { JSONObject(c.errorStream?.bufferedReader()?.use { it.readText() } ?: "{}").optJSONObject("error") }.getOrNull()
                throw ApiException(status, err?.optString("code"), err?.optString("message") ?: "HTTP $status")
            }
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { reader ->
                var event = ""
                val data = StringBuilder()
                while (!stopped || drain) {
                    val line = reader.readLine() ?: return // server closed the stream
                    when {
                        line.isEmpty() -> {
                            if (event == "message" && data.isNotEmpty()) dispatch(data.toString())
                            if (event == "idle") return
                            event = ""
                            data.setLength(0)
                        }
                        line.startsWith(":") -> Unit // keep-alive comment
                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> data.append(line.substring(5).trimStart())
                    }
                }
            }
        } finally {
            c.disconnect()
            if (conn === c) conn = null
        }
    }

    private fun dispatch(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val map = HashMap<String, String>()
        for (k in o.keys()) map[k] = o.optString(k)
        Irnoti.onDirectMessage(map)
    }
}
