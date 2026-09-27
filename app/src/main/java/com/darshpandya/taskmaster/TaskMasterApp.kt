package com.darshpandya.taskmaster

import android.app.Application
import com.darshpandya.taskmaster.data.TaskDatabase
import com.darshpandya.taskmaster.reminders.Notifications

class TaskMasterApp : Application() {

    /** Replaced by instrumented tests with a container around an in-memory database. */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        container = AppContainer(this, TaskDatabase.create(this))
    }
}
