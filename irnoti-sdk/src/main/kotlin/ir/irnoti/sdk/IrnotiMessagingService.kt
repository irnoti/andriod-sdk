package ir.irnoti.sdk

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Registered by the library manifest. If your app already has a FirebaseMessagingService, delete nothing: declare
 * yours with a higher priority and call [Irnoti.isIrnotiMessage] / [Irnoti.handleMessage] / [Irnoti.onNewToken] from it.
 */
class IrnotiMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Irnoti.onNewToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        if (Irnoti.isIrnotiMessage(message.data)) Irnoti.handleMessage(this, message.data)
    }
}
