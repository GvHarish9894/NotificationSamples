package com.techgv.notificationsamples

import android.app.Application

class NotificationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Creating an existing channel is a no-op, so doing it on every start is safe.
        Channels.create(this)
    }
}
