package com.techgv.notificationsamples

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.RemoteInput

/** Handles notification action buttons, direct replies and the scheduled alarm. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val nm = NotificationManagerCompat.from(context)
        when (intent.action) {
            ACTION_ACCEPT -> {
                Samples.toast(context, "Accepted ✅")
                nm.cancel(id)
            }
            ACTION_DECLINE -> {
                Samples.toast(context, "Declined ❌")
                nm.cancel(id)
            }
            ACTION_DISMISS -> nm.cancel(id)
            ACTION_REPLY -> {
                val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Samples.KEY_REPLY) ?: return
                // Re-posting is what stops the "sending" spinner on the reply field.
                Samples.post(context, Ids.REPLY, Samples.replyNotification(context, reply))
            }
            ACTION_SCHEDULED -> Samples.scheduledNotification(context)
            ACTION_SLIDE -> Samples.slider(context, intent.getIntExtra(EXTRA_INDEX, 0))
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.techgv.notificationsamples.ACCEPT"
        const val ACTION_DECLINE = "com.techgv.notificationsamples.DECLINE"
        const val ACTION_DISMISS = "com.techgv.notificationsamples.DISMISS"
        const val ACTION_REPLY = "com.techgv.notificationsamples.REPLY"
        const val ACTION_SCHEDULED = "com.techgv.notificationsamples.SCHEDULED"
        const val ACTION_SLIDE = "com.techgv.notificationsamples.SLIDE"
        private const val EXTRA_ID = "id"
        private const val EXTRA_INDEX = "index"

        /** One PendingIntent per target slide: same action, so the request code must differ. */
        fun slideIntent(context: Context, index: Int): PendingIntent? =
            PendingIntentCompat.getBroadcast(
                context,
                Ids.SLIDER * 100 + index,
                Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_SLIDE).putExtra(EXTRA_INDEX, index),
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )

        /** Different actions make distinct PendingIntents even with the same request code. */
        fun intent(context: Context, action: String, notificationId: Int): PendingIntent? =
            PendingIntentCompat.getBroadcast(
                context,
                notificationId,
                Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
    }
}
