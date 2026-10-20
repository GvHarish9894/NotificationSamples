package com.techgv.notificationsamples

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/*
 * Navigation is a back stack of route strings ("home", "order/42", "chat/Alice", "profile", "gallery/2").
 * The same routes double as deep links: notificationsamples://order/42
 */

const val DEEP_LINK_SCHEME = "notificationsamples"
const val HOME = "home"

fun deepLinkUri(route: String): Uri = "$DEEP_LINK_SCHEME://$route".toUri()

/** Maps an incoming deep link to a known route, or null. Anyone can send this Uri, so only known shapes pass. */
fun routeOf(uri: Uri?): String? {
    if (uri?.scheme != DEEP_LINK_SCHEME) return null
    val parts = listOfNotNull(uri.host) + uri.pathSegments
    return when {
        parts == listOf("profile") -> "profile"
        parts.size == 2 && parts[0] in setOf("order", "chat") && parts[1].isNotBlank() -> parts.joinToString("/")
        parts.size == 2 && parts[0] == "gallery" && parts[1].toIntOrNull() in Samples.slideTitles.indices -> parts.joinToString("/")
        else -> null
    }
}

@Composable
fun DestinationScreen(route: String, onBack: () -> Unit) {
    val name = route.substringBefore("/")
    val arg = route.substringAfter("/", "")
    when (name) {
        "order" -> DetailScaffold("Order #$arg", onBack) {
            Text("📦 Out for delivery", style = MaterialTheme.typography.headlineSmall)
            Text("Arriving in about 25 minutes")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("1 × Coffee beans")
                    Text("2 × Ceramic mug")
                    Text("Total: $24.90", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        "chat" -> DetailScaffold("Chat with $arg", onBack) {
            Text("$arg: Are we still on for Saturday?")
            Text("Bob: Yes! I'll bring snacks 🍿")
            Text("$arg: Great, see you at 9")
        }
        "profile" -> DetailScaffold("Profile", onBack) {
            val avatar = remember { Samples.avatar("M", 0xFF6650A4.toInt()).asImageBitmap() }
            Image(avatar, contentDescription = null, modifier = Modifier.size(96.dp))
            Text("Your profile is 60% complete", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
        }
        "gallery" -> DetailScaffold("Gallery", onBack) {
            val slides = remember { Samples.slideTitles.indices.map { Samples.slide(it).asImageBitmap() } }
            val pager = rememberPagerState(initialPage = arg.toIntOrNull() ?: 0) { slides.size }
            HorizontalPager(pager, pageSpacing = 8.dp) { page ->
                Image(slides[page], contentDescription = Samples.slideTitles[page], modifier = Modifier.fillMaxWidth().aspectRatio(2f))
            }
            Text("${pager.currentPage + 1} / ${slides.size} · swipe to browse")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
