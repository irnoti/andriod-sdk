package ir.irnoti.sdk

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class ApiException(val status: Int, val code: String?, message: String) : Exception(message) {
    /** Server forgot this device: register again. */
    val deviceGone get() = code == "invalid_device_token" || code == "device_not_found"
    /** Worth retrying later (network, 5xx, 429). */
    val transient get() = status == 0 || status >= 500 || status == 429
}

/** Minimal JSON-over-HTTPS client; blocking, call from a background thread only. */
internal class Api(private val cfg: IrnotiConfig, private val store: Storage) {

    fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val c = URL(cfg.apiBase.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("X-Irnoti-Key", cfg.appKey)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            if (auth) c.setRequestProperty("Authorization", "Bearer " + (store.deviceToken ?: ""))
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = c.responseCode
            val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (status !in 200..299 || json.optBoolean("ok", true).not()) {
                val err = json.optJSONObject("error")
                throw ApiException(status, err?.optString("code"), err?.optString("message") ?: "HTTP $status")
            }
            return json.optJSONObject("result") ?: JSONObject()
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(0, null, e.message ?: "network error")
        } finally {
            c.disconnect()
        }
    }
}
