package ir.irnoti.sdk

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Safety net for the direct transport: every ~15 minutes (the Android minimum) the device opens the channel once to pick up what
 * arrived while the live connection was down (service killed by the vendor's battery manager, no foreground service allowed, …).
 */
class IrnotiCatchUpJob : JobService() {
    @Volatile private var thread: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (Irnoti.configOrNull() == null || Irnoti.currentTransport() != "direct") return false
        thread = Thread {
            val ok = DirectChannel.drain()
            jobFinished(params, !ok)
        }.also { it.isDaemon = true; it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        thread?.interrupt()
        return true
    }

    companion object {
        private const val JOB_ID = 0x1AF1

        fun schedule(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.allPendingJobs.any { it.id == JOB_ID }) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, IrnotiCatchUpJob::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            runCatching { js.schedule(job) }
        }

        fun cancel(context: Context) {
            runCatching { (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(JOB_ID) }
        }
    }
}

/** Restarts the direct channel after a reboot or an app update. */
class IrnotiBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The process was just created, so the host Application has already run Irnoti.init().
        Irnoti.resumeDirect(context.applicationContext)
    }
}
