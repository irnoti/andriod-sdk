package ir.irnoti.sdk

import androidx.annotation.DrawableRes

/**
 * @param appKey   the app's public key (pk_live_…) from the IRNoti panel. Never put a service-account file or a broker link here.
 * @param smallIcon monochrome notification icon
 * @param apiBase  device API base URL; the default is the production one
 */
data class IrnotiConfig(
    val appKey: String,
    @DrawableRes val smallIcon: Int,
    val apiBase: String = DEFAULT_API_BASE,
    val defaultChannelId: String = "irnoti_default",
    val defaultChannelName: String = "اعلان‌ها",
) {
    companion object {
        const val DEFAULT_API_BASE = "https://api.irnoti.ir/v1/sdk"
    }
}
