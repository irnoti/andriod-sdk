package ir.irnoti.sdk

import org.json.JSONArray
import org.json.JSONObject

/**
 * Who the user is, for the app owner's IRNoti phonebook. Only [phone] is required; the person is then filed (or updated) as a contact
 * of the owner and this device is tied to them, so the owner can also reach them by SMS or bot campaigns.
 * Blank values never erase what the owner already stored for the person.
 *
 *     Irnoti.identify("user-123", profile = IrnotiProfile(phone = "09120000000", name = "Ali", birthday = "06/31"))
 */
data class IrnotiProfile @JvmOverloads constructor(
    /** Mobile number in any common spelling (`0912…`, `+98912…`). */
    val phone: String,
    val name: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val city: String? = null,
    val company: String? = null,
    /** Free text, up to 500 characters. */
    val note: String? = null,
    /** Solar Hijri birthday as `MM/DD` (e.g. `06/31`), used for occasion SMS; a malformed value is ignored by the server. */
    val birthday: String? = null,
    /** Extra phonebook groups by name (created on first use), at most 10. */
    val groups: List<String> = emptyList(),
    /** Extra columns (key: letters, digits, `_ . -`, up to 40 chars), at most 12. */
    val fields: Map<String, String> = emptyMap(),
) {
    internal fun toJson(): JSONObject {
        val o = JSONObject().put("phone", phone)
        fun opt(key: String, v: String?) { v?.trim()?.takeIf { it.isNotEmpty() }?.let { o.put(key, it) } }
        opt("name", name)
        opt("last_name", lastName)
        opt("email", email)
        opt("city", city)
        opt("company", company)
        opt("note", note)
        opt("birthday", birthday)
        groups.map { it.trim() }.filter { it.isNotEmpty() }.take(10).takeIf { it.isNotEmpty() }?.let { o.put("groups", JSONArray(it)) }
        if (fields.isNotEmpty()) o.put("fields", JSONObject(fields))
        return o
    }
}
