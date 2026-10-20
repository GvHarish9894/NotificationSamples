package com.techgv.notificationsamples

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MIN

/** Channels (API 26+) decide sound/vibration/importance; the user can override each one in settings. */
object Channels {
    const val DEFAULT = "default"
    const val MESSAGES = "messages"
    const val PROGRESS = "progress"
    const val ALERTS = "alerts"
    const val SILENT = "silent"

    // A channel's sound is fixed once created; to change the sound, ship a new channel id.
    const val SOUND = "custom_sound_glisten"

    private const val GROUP_SOCIAL = "social"
    private const val GROUP_SYSTEM = "system"

    fun soundUri(context: Context): Uri =
        // Name-based, not the numeric R.raw id: ids can change between builds, but the channel keeps this Uri forever.
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/glisten".toUri()

    fun create(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannelGroupsCompat(
            listOf(
                NotificationChannelGroupCompat.Builder(GROUP_SOCIAL).setName("Social").build(),
                NotificationChannelGroupCompat.Builder(GROUP_SYSTEM).setName("System").build(),
            )
        )
        nm.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(DEFAULT, IMPORTANCE_DEFAULT)
                    .setName("General")
                    .setDescription("Basic, styled and grouped samples")
                    .setGroup(GROUP_SYSTEM)
                    .build(),
                NotificationChannelCompat.Builder(MESSAGES, IMPORTANCE_HIGH)
                    .setName("Messages")
                    .setDescription("Conversations, direct reply and bubbles")
                    .setGroup(GROUP_SOCIAL)
                    .build(),
                NotificationChannelCompat.Builder(PROGRESS, IMPORTANCE_LOW)
                    .setName("Progress")
                    .setDescription("Downloads and background work")
                    .setShowBadge(false)
                    .setGroup(GROUP_SYSTEM)
                    .build(),
                NotificationChannelCompat.Builder(ALERTS, IMPORTANCE_HIGH)
                    .setName("Alerts")
                    .setDescription("Heads-up and alarm-style notifications")
                    .setVibrationEnabled(true)
                    .setVibrationPattern(longArrayOf(0, 250, 150, 250))
                    .setLightsEnabled(true)
                    .setGroup(GROUP_SYSTEM)
                    .build(),
                NotificationChannelCompat.Builder(SOUND, IMPORTANCE_HIGH)
                    .setName("Custom sound")
                    .setDescription("Plays res/raw/glisten.ogg")
                    .setSound(
                        soundUri(context),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setGroup(GROUP_SOCIAL)
                    .build(),
                NotificationChannelCompat.Builder(SILENT, IMPORTANCE_MIN)
                    .setName("Silent")
                    .setDescription("No sound, no badge")
                    .setShowBadge(false)
                    .setGroup(GROUP_SYSTEM)
                    .build(),
            )
        )
    }
}
