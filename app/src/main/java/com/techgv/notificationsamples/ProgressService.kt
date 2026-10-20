package com.techgv.notificationsamples

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Foreground service: its notification is mandatory and stays until the service stops. */
class ProgressService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            job?.cancel() // no more progress posts once stopping has begun
            stopSelf()
            return START_NOT_STICKY
        }
        val stop = PendingIntentCompat.getService(
            this, 0, Intent(this, ProgressService::class.java).setAction(ACTION_STOP), 0, false,
        )
        val builder = Samples.base(this, Channels.PROGRESS)
            .setContentTitle("Foreground service running")
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_notification, "Stop", stop)

        // Must be called within a few seconds of startForegroundService(), even if notifications are denied.
        ServiceCompat.startForeground(
            this, Ids.FOREGROUND,
            builder.setContentText("Starting…").setProgress(100, 0, false).build(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (job?.isActive != true) {
            job = scope.launch {
                for (p in 0..100 step 5) {
                    Samples.post(this@ProgressService, Ids.FOREGROUND, builder.setContentText("Working… $p%").setProgress(100, p, false).build())
                    delay(1_000)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        active = false
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // A progress post still in flight would outlive the foreground notification as a plain one; cancel it too.
        NotificationManagerCompat.from(this).cancel(Ids.FOREGROUND)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.techgv.notificationsamples.STOP"

        /** True from the start request until onDestroy, so a stop also catches a start that is still queued. */
        private var active = false

        fun start(context: Context) {
            active = true
            ContextCompat.startForegroundService(context, Intent(context, ProgressService::class.java))
        }

        /**
         * Stops via a queued command instead of stopService(). Commands are delivered in order, so a pending start
         * still reaches startForeground() first; stopService() before that point crashes the app on API < 31.
         */
        fun stop(context: Context) {
            if (active) context.startService(Intent(context, ProgressService::class.java).setAction(ACTION_STOP))
        }
    }
}
