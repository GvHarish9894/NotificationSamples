package com.techgv.notificationsamples

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.techgv.notificationsamples.ui.theme.NotificationSamplesTheme

data class Sample(val title: String, val description: String, val minSdk: Int = 1, val run: (Context) -> Unit)

/** The whole catalog: each entry maps one button to one function in [Samples]. */
val sections: List<Pair<String, List<Sample>>> = listOf(
    "Basic" to listOf(
        Sample("Simple", "Title, text and small icon") { Samples.simple(it) },
        Sample("Large icon & color", "setLargeIcon, setColor, setSubText") { Samples.largeIcon(it) },
        // setTimeoutAfter() has no effect before API 26.
        Sample("Auto timeout", "Countdown chronometer, disappears after 10 s", minSdk = 26) { Samples.timeout(it) },
    ),
    "Styles" to listOf(
        Sample("BigTextStyle", "Long text that expands") { Samples.bigText(it) },
        Sample("BigPictureStyle", "Large image when expanded") { Samples.bigPicture(it) },
        Sample("InboxStyle", "Up to 5 summary lines") { Samples.inbox(it) },
        Sample("MessagingStyle", "Group conversation with Person avatars") { Samples.messaging(it) },
    ),
    "Interaction" to listOf(
        Sample("Action buttons", "Accept / Decline handled by a BroadcastReceiver") { Samples.actions(it) },
        Sample("Direct reply", "Reply inline with RemoteInput") { Samples.directReply(it) },
    ),
    "Deep links" to listOf(
        Sample("Order screen", "notificationsamples://order/42") {
            Samples.deepLink(it, Ids.DEEP_LINK, "order/42", "📦 Order #42 is out for delivery")
        },
        Sample("Chat screen", "notificationsamples://chat/Alice") {
            Samples.deepLink(it, Ids.DEEP_LINK_CHAT, "chat/Alice", "💬 Alice sent you a message")
        },
        Sample("Profile screen", "notificationsamples://profile") {
            Samples.deepLink(it, Ids.DEEP_LINK_PROFILE, "profile", "👤 Complete your profile")
        },
    ),
    "Sound & images" to listOf(
        Sample("Custom sound", "Own channel playing an OGG from res/raw") { Samples.customSound(it) },
        Sample("Image slider", "Prev / Next buttons, tap opens the gallery") { Samples.slider(it) },
        Sample("Auto-play carousel", "ViewFlipper cycles 4 images; no pause, so prefer the slider for accessibility") { Samples.carousel(it) },
    ),
    "Progress & ongoing" to listOf(
        Sample("Determinate progress", "0 → 100% then a completion message") { Samples.determinateProgress(it) },
        Sample("Indeterminate progress", "Ongoing spinner with a Stop action") { Samples.indeterminateProgress(it) },
        Sample("Foreground service", "Service-owned notification with progress") { Samples.foregroundService(it) },
    ),
    "Grouping & priority" to listOf(
        Sample("Grouped", "3 messages bundled under a summary") { Samples.grouped(it) },
        Sample("Heads-up", "High-importance channel, peeks on screen") { Samples.headsUp(it) },
        Sample("Full-screen intent", "Alarm-style activity over the lock screen") { Samples.fullScreen(it) },
        Sample("Silent", "Min importance, no sound, no badge") { Samples.silent(it) },
    ),
    "Advanced" to listOf(
        Sample("Custom layout", "RemoteViews + DecoratedCustomViewStyle") { Samples.customLayout(it) },
        Sample("Bubble", "Floating chat bubble backed by a shortcut", minSdk = 30) { Samples.bubble(it) },
        Sample("Scheduled", "AlarmManager posts in 10 s or later (inexact), even if the app is closed") { Samples.schedule(it) },
        Sample("Post updatable", "Posts a notification with a fixed id") { Samples.postUpdatable(it) },
        Sample("Update it", "Re-posts with the same id: replaced in place") { Samples.update(it) },
        Sample("Cancel all", "Removes every notification from this app") { Samples.cancelAll(it) },
        Sample("Channel settings", "Where users change or block a channel", minSdk = 26) {
            Samples.openSettings(it, Channels.DEFAULT)
        },
    ),
)

class MainActivity : ComponentActivity() {
    private val backStack = mutableStateListOf<String>()
    private var notificationsEnabled by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        backStack += savedInstanceState?.getStringArrayList(KEY_BACK_STACK) ?: listOf(HOME)
        if (savedInstanceState == null) navigate(intent)
        // singleTop: taps while the app is running arrive here, so we navigate instead of relaunching.
        addOnNewIntentListener(::navigate)

        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            NotificationSamplesTheme {
                val route = backStack.last()
                BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
                if (route == HOME) {
                    SamplesScreen(notificationsEnabled, onOpenSettings = { Samples.openSettings(this) })
                } else {
                    // key(route): a new route gets fresh screen state (e.g. the gallery pager's start page).
                    key(route) { DestinationScreen(route, onBack = { backStack.removeAt(backStack.lastIndex) }) }
                }
            }
        }
    }

    /** Pushes the deep-linked screen on top of whatever is open; Back returns there. */
    private fun navigate(intent: Intent) {
        val route = routeOf(intent.data) ?: return
        if (backStack.last() != route) backStack += route
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_BACK_STACK, ArrayList(backStack))
    }

    override fun onResume() {
        super.onResume()
        // The user may have toggled notifications in settings while we were away.
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private companion object {
        const val KEY_BACK_STACK = "back_stack"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplesScreen(notificationsEnabled: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Notification Samples") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!notificationsEnabled) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notifications are disabled, so samples can't be shown.")
                        Button(onClick = onOpenSettings) { Text("Open settings") }
                    }
                }
            }
            sections.forEach { (title, samples) ->
                item {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(samples) { sample ->
                    val supported = Build.VERSION.SDK_INT >= sample.minSdk
                    Card(onClick = { sample.run(context) }, enabled = supported, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(sample.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (supported) sample.description else "Requires API ${sample.minSdk}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SamplesScreenPreview() {
    NotificationSamplesTheme {
        SamplesScreen(notificationsEnabled = false, onOpenSettings = {})
    }
}
