package com.techgv.notificationsamples

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSamplesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val nm = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        cancelAll() // no leftovers from earlier tests can satisfy an assertion
    }

    @After
    fun tearDown() = cancelAll()

    private fun cancelAll() {
        instrumentation.runOnMainSync { Samples.cancelAll(context) }
        assertTrue("shade not cleared", waitUntil { nm.activeNotifications.isEmpty() })
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    private fun text(id: Int): String? =
        nm.activeNotifications.firstOrNull { it.id == id }?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    /** Android drops posts beyond ~5/s per app, so one post right after a burst can be lost: retry. */
    private fun postedEventually(id: Int, post: () -> Unit): Boolean = (1..10).any {
        post()
        waitUntil(500) { nm.activeNotifications.any { it.id == id } }
    }

    @Test
    fun simpleNotificationIsPosted() {
        assertTrue(postedEventually(Ids.SIMPLE) { Samples.simple(context) })
    }

    @Test
    fun updateReplacesTextInPlace() {
        assertTrue(postedEventually(Ids.UPDATABLE) { Samples.postUpdatable(context) })
        Samples.update(context)
        assertTrue(waitUntil { text(Ids.UPDATABLE) == "Updated 1 time(s), same id" })
        assertEquals(1, nm.activeNotifications.count { it.id == Ids.UPDATABLE })
    }

    @Test
    fun cancelAllStopsRunningProgress() {
        instrumentation.runOnMainSync { Samples.determinateProgress(context) }
        assertTrue(waitUntil { nm.activeNotifications.any { it.id == Ids.PROGRESS } })
        cancelAll()
        Thread.sleep(1_500) // longer than the 500 ms update tick: a surviving job would re-post
        assertTrue(nm.activeNotifications.none { it.id == Ids.PROGRESS })
    }

    @Test
    fun cancelAllStopsAQueuedForegroundServiceStart() {
        // Same main-thread block: the service's start command can't run before cancelAll().
        instrumentation.runOnMainSync {
            Samples.foregroundService(context)
            Samples.cancelAll(context)
        }
        // Past several 1 s ticks: a service that escaped the stop would keep its notification up.
        Thread.sleep(2_500)
        assertTrue(nm.activeNotifications.none { it.id == Ids.FOREGROUND })
    }

    @Test
    fun sliderWrapsAroundFromFirstSlide() {
        assertTrue(postedEventually(Ids.SLIDER) { Samples.slider(context, -1) })
    }

    @Test
    fun deepLinksMapToKnownRoutesOnly() {
        assertEquals("order/42", routeOf("notificationsamples://order/42".toUri()))
        assertEquals("chat/Alice", routeOf("notificationsamples://chat/Alice".toUri()))
        assertEquals("profile", routeOf("notificationsamples://profile".toUri()))
        assertEquals("gallery/3", routeOf("notificationsamples://gallery/3".toUri()))
        assertNull(routeOf("notificationsamples://gallery/9".toUri()))
        assertNull(routeOf("notificationsamples://order".toUri()))
        assertNull(routeOf("notificationsamples://admin/1".toUri()))
        assertNull(routeOf("https://order/42".toUri()))
        assertNull(routeOf(null))
    }

    @Test
    fun warmDeepLinkNavigatesWithinRunningActivity() {
        fun open(route: String) = Intent(Intent.ACTION_VIEW, deepLinkUri(route), context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(open("gallery/0")).use { scenario ->
            compose.onNodeWithText("1 / 4 · swipe to browse").assertExists()

            // Same flags as the notification PendingIntent: delivered to onNewIntent, no relaunch.
            scenario.onActivity { it.startActivity(open("gallery/2").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("3 / 4 · swipe to browse") }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("1 / 4 · swipe to browse").assertExists()
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextExists(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Smoke test only: every catalog entry starts without throwing. Behaviour is covered by the tests above. */
    @Test
    fun everySampleStartsWithoutCrashing() {
        instrumentation.runOnMainSync {
            sections.flatMap { it.second }
                // Skip entries that leave the app (settings screens).
                .filter { Build.VERSION.SDK_INT >= it.minSdk && it.title !in setOf("Channel settings", "Full-screen intent") }
                .forEach { it.run(context) }
        }
    }
}
