package ir.irnoti.sdk

import androidx.annotation.DrawableRes

/** How the device receives pushes. */
enum class Transport {
    /** Use Firebase (FCM) when the device can reach it, otherwise IRNoti's own channel. Re-checked automatically. Recommended. */
    AUTO,

    /** FCM only (the behavior of SDK 1.0). */
    FCM,

    /** IRNoti's own channel only; Firebase is never touched, so `google-services.json` is not needed. */
    DIRECT,
}

/**
 * @param appKey   the app's public key (pk_live_…) from the IRNoti panel. Never put a service-account file or a broker link here.
 * @param smallIcon monochrome notification icon
 * @param apiBase  device API base URL; the default is the production one
 * @param transport see [Transport]
 * @param directService keep IRNoti's own channel open through a foreground service (instant delivery, shows a small permanent
 *        notification). When false the channel only lives while the app process does, plus a periodic catch-up every ~15 minutes.
 */
data class IrnotiConfig @JvmOverloads constructor(
    val appKey: String,
    @DrawableRes val smallIcon: Int,
    val apiBase: String = DEFAULT_API_BASE,
    val defaultChannelId: String = "irnoti_default",
    val defaultChannelName: String = "اعلان‌ها",
    val transport: Transport = Transport.AUTO,
    val directService: Boolean = true,
    val directNotificationTitle: String = "اتصال برای دریافت اعلان‌ها",
    val directNotificationText: String = "برنامه آماده‌ی دریافت اعلان است",
) {
    companion object {
        const val DEFAULT_API_BASE = "https://api.irnoti.ir/v1/sdk"
    }
}
