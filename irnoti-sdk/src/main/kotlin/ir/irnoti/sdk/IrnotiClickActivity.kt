package ir.irnoti.sdk

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Receives notification taps: records the click, opens the deeplink/url (or the app launcher), finishes. */
class IrnotiClickActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        val action = intent.getStringExtra(EXTRA_ACTION)
        val target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        if (id != null) Irnoti.report(id, if (action.isNullOrEmpty()) "clicked" else "action", action?.ifEmpty { null })

        val view = if (target.isNotEmpty()) Intent(Intent.ACTION_VIEW, Uri.parse(target)).setPackage(null) else null
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        runCatching { startActivity(view ?: launch) }.onFailure { launch?.let { runCatching { startActivity(it) } } }
        finish()
    }

    internal companion object {
        const val EXTRA_ID = "irn_id"
        const val EXTRA_TARGET = "irn_target"
        const val EXTRA_ACTION = "irn_action"
    }
}

/** Swipe-away of a notification. */
class IrnotiDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(IrnotiClickActivity.EXTRA_ID)?.let { Irnoti.report(it, "dismissed") }
    }
}
