package com.techgv.notificationsamples

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Notification ids. Posting again with the same id updates the notification in place. */
object Ids {
    const val SIMPLE = 1
    const val LARGE_ICON = 2
    const val TIMEOUT = 3
    const val BIG_TEXT = 4
    const val BIG_PICTURE = 5
    const val INBOX = 6
    const val MESSAGING = 7
    const val ACTIONS = 8
    const val REPLY = 9
    const val DEEP_LINK = 10
    const val PROGRESS = 11
    const val INDETERMINATE = 12
    const val FOREGROUND = 13
    const val GROUP_SUMMARY = 14
    const val HEADS_UP = 15
    const val FULL_SCREEN = 16
    const val SILENT = 17
    const val CUSTOM = 18
    const val BUBBLE = 19
    const val SCHEDULED = 20
    const val UPDATABLE = 21
    const val CUSTOM_SOUND = 22
    const val SLIDER = 23
    const val CAROUSEL = 24
    const val DEEP_LINK_CHAT = 25
    const val DEEP_LINK_PROFILE = 26
    const val GROUP_CHILD = 100 // children use 100, 101, 102
}

/** Every sample is one function taking a Context, so the button -> code mapping is obvious. */
object Samples {
    const val KEY_REPLY = "key_reply"
    private const val GROUP_KEY = "com.techgv.notificationsamples.INBOX"
    private const val SHORTCUT_ALICE = "alice"
    private const val ACCENT = 0xFF6650A4.toInt()
    private const val BLUE = 0xFF1E88E5.toInt()

    // Process-wide scope so progress keeps updating across rotation; it's only a few seconds of work.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Int, Job>() // notification id -> work that keeps posting to it
    private var updateCount = 0

    val slideTitles = listOf("🏔 Mountains", "🌊 Ocean", "🏜 Desert", "🌲 Forest")
    private val slideColors = listOf(
        0xFF5C6BC0 to 0xFF26C6DA,
        0xFF0277BD to 0xFF4DD0E1,
        0xFFFF8F00 to 0xFFFFD54F,
        0xFF2E7D32 to 0xFF9CCC65,
    )

    // ---------------------------------------------------------------- helpers

    fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)

    /**
     * Opens the app on [route] (e.g. "order/42"), or on the home screen when null.
     * SINGLE_TOP + CLEAR_TOP: a running MainActivity gets onNewIntent and navigates, instead of a relaunch.
     * Each route is a different data Uri, so each gets its own PendingIntent.
     */
    fun openApp(context: Context, route: String? = null): PendingIntent? = PendingIntentCompat.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, route?.let { deepLinkUri(it) }, context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT,
        false,
    )

    /** Single entry point for notify(): checks app + channel state and never crashes on a revoked permission. */
    @SuppressLint("MissingPermission") // checked via areNotificationsEnabled() + SecurityException catch
    fun post(context: Context, id: Int, notification: Notification): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            toast(context, "Notifications are disabled for this app")
            return false
        }
        val channelId = NotificationCompat.getChannelId(notification)
        if (channelId != null && nm.getNotificationChannelCompat(channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            toast(context, "Channel '$channelId' is turned off in settings")
            return false
        }
        return try {
            nm.notify(id, notification)
            true
        } catch (_: SecurityException) {
            toast(context, "Missing notification permission")
            false
        }
    }

    fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** Letter avatar. circle=false fills the whole square, as adaptive icons need. */
    fun avatar(letter: String, color: Int, circle: Boolean = true, size: Int = 192): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        if (circle) canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint) else canvas.drawColor(color)
        paint.color = Color.WHITE
        paint.textSize = size * if (circle) 0.5f else 0.3f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    /** Generated banner so the repo needs no binary image assets. */
    fun banner(): Bitmap {
        val bitmap = createBitmap(1024, 512)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 1024f, 512f, ACCENT, 0xFFE91E63.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1024f, 512f, paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = 84f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("BigPictureStyle", 512f, 280f, paint)
        return bitmap
    }

    /** Generated 2:1 slide for the slider samples and the in-app gallery. */
    fun slide(index: Int, width: Int = 720): Bitmap {
        val height = width / 2
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val (from, to) = slideColors[index]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), from.toInt(), to.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = width * 0.09f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(slideTitles[index], width / 2f, height / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    private fun alice() = Person.Builder()
        .setName("Alice").setKey("alice").setImportant(true)
        .setIcon(IconCompat.createWithBitmap(avatar("A", BLUE)))
        .build()

    private fun bob() = Person.Builder()
        .setName("Bob").setKey("bob")
        .setIcon(IconCompat.createWithBitmap(avatar("B", 0xFF43A047.toInt())))
        .build()

    private val me = Person.Builder().setName("Me").setKey("me").build()

    private fun conversation(reply: CharSequence?): NotificationCompat.MessagingStyle {
        val now = System.currentTimeMillis()
        val alice = alice()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Weekend trip")
            .setGroupConversation(true)
            .addMessage("Are we still on for Saturday?", now - 600_000, alice)
            .addMessage("Yes! I'll bring snacks 🍿", now - 300_000, bob())
            .addMessage("Great, see you at 9", now - 60_000, alice)
        // A null person means the message was sent by the user (the style's "me").
        if (reply != null) style.addMessage(reply, now, null as Person?)
        return style
    }

    // ---------------------------------------------------------------- basic

    fun simple(c: Context) = post(
        c, Ids.SIMPLE, base(c, Channels.DEFAULT)
            .setContentTitle("Hello 👋")
            .setContentText("The simplest notification: small icon, title and text.")
            .build()
    )

    fun largeIcon(c: Context) = post(
        c, Ids.LARGE_ICON, base(c, Channels.DEFAULT)
            .setLargeIcon(avatar("A", BLUE))
            .setSubText("Sub text")
            .setContentTitle("Alice")
            .setContentText("Large icon, accent color and sub text")
            .build()
    )

    fun timeout(c: Context) = post(
        c, Ids.TIMEOUT, base(c, Channels.DEFAULT)
            .setContentTitle("I'll vanish in 10 seconds")
            .setContentText("setTimeoutAfter() cancels me automatically")
            .setTimeoutAfter(10_000)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() + 10_000)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
    )

    // ---------------------------------------------------------------- styles

    fun bigText(c: Context) {
        val text = "BigTextStyle lets a notification expand to show several lines of text. " +
            "Use it for email previews, articles or any message longer than a single line. " +
            "Collapsed, only the first line is shown; expanded, the whole text is visible."
        post(
            c, Ids.BIG_TEXT, base(c, Channels.DEFAULT)
                .setContentTitle("Big text")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText("Expand me"))
                .build()
        )
    }

    fun bigPicture(c: Context) {
        val picture = banner()
        post(
            c, Ids.BIG_PICTURE, base(c, Channels.DEFAULT)
                .setContentTitle("Big picture")
                .setContentText("Expand to see the full image")
                .setLargeIcon(picture)
                // Hide the thumbnail when expanded so the image isn't shown twice.
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
                .build()
        )
    }

    fun inbox(c: Context) = post(
        c, Ids.INBOX, base(c, Channels.DEFAULT)
            .setContentTitle("5 new emails")
            .setContentText("Alice, Bob, Carol, Dave, Eve")
            .setNumber(5)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .addLine("Alice   Lunch tomorrow?")
                    .addLine("Bob   Your PR was approved")
                    .addLine("Carol   Invoice #1042")
                    .addLine("Dave   Re: release notes")
                    .addLine("Eve   Weekend plans")
                    .setSummaryText("you@example.com")
            )
            .build()
    )

    fun messaging(c: Context) = post(
        c, Ids.MESSAGING, base(c, Channels.MESSAGES)
            .setStyle(conversation(null))
            .setContentIntent(openApp(c, "chat/Alice"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
    )

    // ---------------------------------------------------------------- interaction

    fun actions(c: Context) = post(
        c, Ids.ACTIONS, base(c, Channels.DEFAULT)
            .setContentTitle("Meeting invite")
            .setContentText("Design review · Tomorrow 10:00")
            .addAction(R.drawable.ic_notification, "Accept", NotificationActionReceiver.intent(c, NotificationActionReceiver.ACTION_ACCEPT, Ids.ACTIONS))
            .addAction(R.drawable.ic_notification, "Decline", NotificationActionReceiver.intent(c, NotificationActionReceiver.ACTION_DECLINE, Ids.ACTIONS))
            .build()
    )

    fun directReply(c: Context) = post(c, Ids.REPLY, replyNotification(c, null))

    fun replyNotification(c: Context, reply: CharSequence?): Notification {
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Type a reply…").build()
        // RemoteInput needs a mutable PendingIntent so the system can fill in the text.
        val replyIntent = PendingIntentCompat.getBroadcast(
            c, Ids.REPLY,
            Intent(c, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT, true,
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_notification, "Reply", replyIntent)
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        return base(c, Channels.MESSAGES)
            .setStyle(conversation(reply))
            .setContentIntent(openApp(c, "chat/Alice"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .addAction(action)
            .build()
    }

    /** Tapping lands directly on [route]; Back returns to the home screen. */
    fun deepLink(c: Context, id: Int, route: String, title: String) = post(
        c, id, base(c, Channels.DEFAULT)
            .setContentTitle(title)
            .setContentText("Tap to open ${deepLinkUri(route)}")
            .setContentIntent(openApp(c, route))
            .build()
    )

    // ---------------------------------------------------------------- sound & media

    fun customSound(c: Context) = post(
        c, Ids.CUSTOM_SOUND, base(c, Channels.SOUND)
            .setContentTitle("🔔 Custom sound")
            .setContentText("Plays glisten.ogg from res/raw via its own channel")
            .setSound(Channels.soundUri(c)) // API < 26 only; from 26 the channel's sound is used
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    )

    /** Manual slider: Prev/Next buttons re-post this notification with a new index. */
    fun slider(c: Context, index: Int = 0) {
        val i = index.mod(slideTitles.size)
        val expanded = RemoteViews(c.packageName, R.layout.notification_slider).apply {
            setImageViewBitmap(R.id.image, slide(i))
            setContentDescription(R.id.image, slideTitles[i])
            setTextViewText(R.id.dots, slideTitles.indices.joinToString("  ") { if (it == i) "●" else "○" })
            setOnClickPendingIntent(R.id.prev, NotificationActionReceiver.slideIntent(c, (i - 1).mod(slideTitles.size)))
            setOnClickPendingIntent(R.id.next, NotificationActionReceiver.slideIntent(c, (i + 1).mod(slideTitles.size)))
        }
        val collapsed = RemoteViews(c.packageName, R.layout.notification_custom_collapsed).apply {
            setTextViewText(R.id.title, "Photo slider · ${i + 1}/${slideTitles.size}")
            setTextViewText(R.id.body, "${slideTitles[i]} · expand to browse")
        }
        post(
            c, Ids.SLIDER, base(c, Channels.DEFAULT)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .setContentIntent(openApp(c, "gallery/$i"))
                .setOnlyAlertOnce(true) // sliding must not buzz every time
                .build()
        )
    }

    /** Auto-play carousel: a ViewFlipper inside the notification cycles all slides on its own. */
    fun carousel(c: Context) {
        val slideViews = listOf(R.id.slide0, R.id.slide1, R.id.slide2, R.id.slide3)
        val expanded = RemoteViews(c.packageName, R.layout.notification_carousel).apply {
            // Smaller bitmaps: all four travel in one notification.
            slideViews.forEachIndexed { i, viewId ->
                setImageViewBitmap(viewId, slide(i, width = 480))
                setContentDescription(viewId, slideTitles[i])
            }
        }
        val collapsed = RemoteViews(c.packageName, R.layout.notification_custom_collapsed).apply {
            setTextViewText(R.id.title, "Auto-play carousel")
            setTextViewText(R.id.body, "Expand to watch ${slideTitles.size} slides")
        }
        post(
            c, Ids.CAROUSEL, base(c, Channels.DEFAULT)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .setContentIntent(openApp(c, "gallery/0"))
                .build()
        )
    }

    // ---------------------------------------------------------------- progress

    /** Runs [block] as the only writer for notification [id]; rerunning a sample replaces its previous run. */
    private fun launchFor(id: Int, block: suspend CoroutineScope.() -> Unit) {
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch(block = block)
    }

    fun determinateProgress(c: Context) {
        val ctx = c.applicationContext
        launchFor(Ids.PROGRESS) {
            val builder = base(ctx, Channels.PROGRESS)
                .setContentTitle("Downloading file.zip")
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
            for (p in 0..100 step 10) {
                if (!post(ctx, Ids.PROGRESS, builder.setContentText("$p%").setProgress(100, p, false).build())) return@launchFor
                delay(500) // the system drops updates posted faster than ~5/s
            }
            post(
                ctx, Ids.PROGRESS, builder.setContentText("Download complete")
                    .setProgress(0, 0, false).setOngoing(false).setAutoCancel(true).build()
            )
        }
    }

    fun indeterminateProgress(c: Context) = post(
        c, Ids.INDETERMINATE, base(c, Channels.PROGRESS)
            .setContentTitle("Syncing…")
            .setContentText("Indeterminate progress bar")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_notification, "Stop", NotificationActionReceiver.intent(c, NotificationActionReceiver.ACTION_DISMISS, Ids.INDETERMINATE))
            .build()
    )

    fun foregroundService(c: Context) = ProgressService.start(c)

    // ---------------------------------------------------------------- grouping & priority

    fun grouped(c: Context) {
        val messages = listOf("Alice" to "Lunch?", "Bob" to "PR approved ✅", "Carol" to "Call me back")
        messages.forEachIndexed { i, (who, text) ->
            post(
                c, Ids.GROUP_CHILD + i, base(c, Channels.DEFAULT)
                    .setContentTitle(who).setContentText(text)
                    .setGroup(GROUP_KEY)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .build()
            )
        }
        val summary = NotificationCompat.InboxStyle().setSummaryText("Inbox")
        messages.forEach { (who, text) -> summary.addLine("$who   $text") }
        post(
            c, Ids.GROUP_SUMMARY, base(c, Channels.DEFAULT)
                .setContentTitle("${messages.size} new messages")
                .setContentText(messages.joinToString { it.first })
                .setStyle(summary)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .build()
        )
    }

    fun headsUp(c: Context) = post(
        c, Ids.HEADS_UP, base(c, Channels.ALERTS)
            .setContentTitle("Heads-up!")
            .setContentText("High-importance channel → peeks over the current app")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // pre-API 26 equivalent of channel importance
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
    )

    fun fullScreen(c: Context) {
        if (Build.VERSION.SDK_INT >= 34 && !c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            toast(c, "Allow full-screen notifications, then try again")
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${c.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val ctx = c.applicationContext
        toast(c, "Lock your screen: alarm fires in 5 s")
        launchFor(Ids.FULL_SCREEN) {
            delay(5_000) // when the device is unlocked and in use, the system shows a heads-up instead
            val fullScreenIntent = PendingIntentCompat.getActivity(
                ctx, Ids.FULL_SCREEN,
                Intent(ctx, FullScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT, false,
            )
            post(
                ctx, Ids.FULL_SCREEN, base(ctx, Channels.ALERTS)
                    .setContentTitle("⏰ Alarm")
                    .setContentText("Wake up!")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenIntent, true)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .addAction(R.drawable.ic_notification, "Dismiss", NotificationActionReceiver.intent(ctx, NotificationActionReceiver.ACTION_DISMISS, Ids.FULL_SCREEN))
                    .build()
            )
        }
    }

    fun silent(c: Context) = post(
        c, Ids.SILENT, base(c, Channels.SILENT)
            .setContentTitle("Silent")
            .setContentText("No sound, no badge, minimized in the shade")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .build()
    )

    // ---------------------------------------------------------------- advanced

    fun customLayout(c: Context) {
        val collapsed = RemoteViews(c.packageName, R.layout.notification_custom_collapsed).apply {
            setTextViewText(R.id.title, "Custom layout")
            setTextViewText(R.id.body, "RemoteViews inside the system template")
        }
        val expanded = RemoteViews(c.packageName, R.layout.notification_custom_expanded).apply {
            setTextViewText(R.id.title, "Custom layout (expanded)")
            setTextViewText(R.id.body, "DecoratedCustomViewStyle keeps the standard header and actions.")
            setImageViewBitmap(R.id.image, banner())
        }
        post(
            c, Ids.CUSTOM, base(c, Channels.DEFAULT)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .build()
        )
    }

    fun bubble(c: Context) {
        val alice = alice()
        // Bubble icons must be adaptive bitmaps (or resources); a plain bitmap is rejected on API 30+.
        val icon = IconCompat.createWithAdaptiveBitmap(avatar("A", BLUE, circle = false, size = 288))
        // Bubbles require a long-lived shortcut representing the conversation.
        ShortcutManagerCompat.pushDynamicShortcut(
            c, ShortcutInfoCompat.Builder(c, SHORTCUT_ALICE)
                .setShortLabel("Alice")
                .setIcon(icon)
                .setPerson(alice)
                .setLongLived(true)
                .setIntent(Intent(Intent.ACTION_VIEW, deepLinkUri("chat/Alice"), c, MainActivity::class.java))
                .build()
        )
        // The bubble's content intent must be mutable.
        val bubbleIntent = PendingIntentCompat.getActivity(
            c, Ids.BUBBLE, Intent(c, BubbleActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT, true,
        ) ?: return
        val metadata = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
            .setDesiredHeight(600)
            .build()
        post(
            c, Ids.BUBBLE, base(c, Channels.MESSAGES)
                .setContentTitle("Alice")
                .setContentText("Open me as a bubble 🫧")
                .setStyle(NotificationCompat.MessagingStyle(me).addMessage("Open me as a bubble 🫧", System.currentTimeMillis(), alice))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(openApp(c, "chat/Alice"))
                .setShortcutId(SHORTCUT_ALICE)
                .addPerson(alice)
                .setBubbleMetadata(metadata)
                .build()
        )
        toast(c, "No bubble? Tap the bubble icon on the notification or allow bubbles in settings")
    }

    private fun scheduledIntent(c: Context) = PendingIntentCompat.getBroadcast(
        c, Ids.SCHEDULED,
        Intent(c, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_SCHEDULED),
        PendingIntent.FLAG_UPDATE_CURRENT, false,
    )

    fun schedule(c: Context) {
        val intent = scheduledIntent(c) ?: return
        // Inexact alarm: avoids the SCHEDULE_EXACT_ALARM permission, at the cost of possible batching delays.
        AlarmManagerCompat.setAndAllowWhileIdle(
            c.getSystemService(AlarmManager::class.java),
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 10_000,
            intent,
        )
        toast(c, "Scheduled: arrives in 10 s or later, even if you close the app")
    }

    fun scheduledNotification(c: Context) = post(
        c, Ids.SCHEDULED, base(c, Channels.DEFAULT)
            .setContentTitle("⏱ Scheduled notification")
            .setContentText("Posted by AlarmManager, even with the app closed")
            .build()
    )

    private fun updatable(c: Context, text: String) = base(c, Channels.DEFAULT)
        .setContentTitle("Updatable notification")
        .setContentText(text)
        .setOnlyAlertOnce(true) // updates replace silently instead of alerting again
        .build()

    fun postUpdatable(c: Context) {
        updateCount = 0
        post(c, Ids.UPDATABLE, updatable(c, "Original version"))
    }

    fun update(c: Context) {
        updateCount++
        post(c, Ids.UPDATABLE, updatable(c, "Updated $updateCount time(s), same id"))
    }

    /** Clears the shade and everything that would re-post into it: running jobs, the service, the pending alarm. */
    fun cancelAll(c: Context) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scheduledIntent(c)?.let { c.getSystemService(AlarmManager::class.java).cancel(it) }
        ProgressService.stop(c)
        NotificationManagerCompat.from(c).cancelAll()
    }

    /** Opens the channel's settings page, or the app's notification settings when channelId is null. */
    fun openSettings(c: Context, channelId: String? = null) {
        val intent = when {
            Build.VERSION.SDK_INT < 26 ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${c.packageName}".toUri())
            channelId == null ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
            else ->
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }
        c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
