package com.techgv.notificationsamples

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import com.techgv.notificationsamples.ui.theme.NotificationSamplesTheme

/** Target of the full-screen intent: shows over the lock screen like an alarm clock. */
class FullScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContent {
            NotificationSamplesTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("⏰", style = MaterialTheme.typography.displayLarge)
                        Text("Wake up!", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = {
                            NotificationManagerCompat.from(this@FullScreenActivity).cancel(Ids.FULL_SCREEN)
                            finish()
                        }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
