package ir.irnoti.sdk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small SharedPreferences wrapper; the device token is a secret, so no backup (see allowBackup note in README). */
internal class Storage(context: Context) {
    private val sp = context.getSharedPreferences("irnoti_sdk", Context.MODE_PRIVATE)

    val installId: String
        get() = sp.getString("install_id", null) ?: UUID.randomUUID().toString().also { sp.edit().putString("install_id", it).apply() }

    var deviceId: String?
        get() = sp.getString("device_id", null)
        set(v) = sp.edit().putString("device_id", v).apply()

    var deviceToken: String?
        get() = sp.getString("device_token", null)
        set(v) = sp.edit().putString("device_token", v).apply()

    var pushToken: String?
        get() = sp.getString("push_token", null)
        set(v) = sp.edit().putString("push_token", v).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    fun clearDevice() = sp.edit().remove("device_id").remove("device_token").remove("push_token").remove("last_sync").apply()

    // ---- report queue (delivery/click receipts waiting for the network) ----
    @Synchronized
    fun queueReport(r: JSONObject) {
        val arr = JSONArray(sp.getString("reports", "[]"))
        arr.put(r)
        // keep the newest 200 so a long offline period cannot grow without bound
        val keep = JSONArray()
        for (i in maxOf(0, arr.length() - 200) until arr.length()) keep.put(arr.get(i))
        sp.edit().putString("reports", keep.toString()).apply()
    }

    @Synchronized
    fun takeReports(max: Int): JSONArray {
        val arr = JSONArray(sp.getString("reports", "[]"))
        val out = JSONArray()
        val rest = JSONArray()
        for (i in 0 until arr.length()) if (i < max) out.put(arr.get(i)) else rest.put(arr.get(i))
        sp.edit().putString("reports", rest.toString()).apply()
        return out
    }

    @Synchronized
    fun putBack(reports: JSONArray) {
        val arr = JSONArray(sp.getString("reports", "[]"))
        val merged = JSONArray()
        for (i in 0 until reports.length()) merged.put(reports.get(i))
        for (i in 0 until arr.length()) merged.put(arr.get(i))
        sp.edit().putString("reports", merged.toString()).apply()
    }
}
