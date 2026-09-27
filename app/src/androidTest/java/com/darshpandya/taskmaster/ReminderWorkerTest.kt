package com.darshpandya.taskmaster

import android.Manifest
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.reminders.Notifications
import com.darshpandya.taskmaster.reminders.ReminderWorker
import com.darshpandya.taskmaster.reminders.WorkManagerReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var container: AppContainer
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        container = installTestContainer().first
        manager.cancelAll()
    }

    @After fun tearDown() = manager.cancelAll()

    private fun posted(id: Long) = manager.activeNotifications.firstOrNull { it.id == Notifications.notificationId(id) }

    /** notify() is handled asynchronously by the system, so poll for the posted notification. */
    private fun awaitPosted(id: Long, timeoutMs: Long = 5_000): android.service.notification.StatusBarNotification? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            posted(id)?.let { return it }
            Thread.sleep(50)
        }
        return null
    }

    private fun runWorker(taskId: Long): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<ReminderWorker>(app)
            .setInputData(workDataOf(ReminderWorker.KEY_TASK_ID to taskId))
            .build()
            .doWork()
    }

    @Test fun postsNotificationForOpenTask() {
        val id = runBlocking { container.repository.add("Pay rent", "Transfer before 5pm", null, Priority.HIGH) }
        assertEquals(ListenableWorker.Result.success(), runWorker(id))
        val sbn = awaitPosted(id)
        assertNotNull(sbn)
        val n = sbn!!.notification
        assertEquals(Notifications.CHANNEL_REMINDERS, n.channelId)
        assertEquals("Pay rent", n.extras.getString("android.title"))
        assertEquals("Transfer before 5pm", n.extras.getCharSequence("android.text").toString())
        assertEquals(1, n.actions.size)
    }

    @Test fun skipsCompletedAndDeletedTasks() {
        val done = runBlocking {
            val id = container.repository.add("Done already", "", null, Priority.LOW)
            container.repository.setCompleted(container.repository.get(id)!!, true)
            id
        }
        assertEquals(ListenableWorker.Result.success(), runWorker(done))
        assertNull(awaitPosted(done, timeoutMs = 1_000))
        assertEquals(ListenableWorker.Result.success(), runWorker(9999))
        assertNull(posted(9999))
    }

    @Test fun missingInputFails() {
        val result = runBlocking { TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork() }
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test fun schedulerEnqueuesDelayedUniqueWorkThatPostsWhenDue() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
        val wm = WorkManager.getInstance(app)
        val scheduler = WorkManagerReminderScheduler(app)
        val id = runBlocking { container.repository.add("Stand-up", "", null, Priority.MEDIUM) }

        scheduler.schedule(id, "Stand-up", System.currentTimeMillis() + 60 * 60_000)
        scheduler.schedule(id, "Stand-up", System.currentTimeMillis() + 30 * 60_000) // replaces the first
        val infos = wm.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.workName(id)).get()
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })

        val work = infos.first { it.state == WorkInfo.State.ENQUEUED }
        WorkManagerTestInitHelper.getTestDriver(app)!!.setInitialDelayMet(work.id)
        // CoroutineWorker finishes on Dispatchers.Default, so wait for the state change.
        val deadline = System.currentTimeMillis() + 5_000
        while (wm.getWorkInfoById(work.id).get()!!.state != WorkInfo.State.SUCCEEDED && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(WorkInfo.State.SUCCEEDED, wm.getWorkInfoById(work.id).get()!!.state)
        assertNotNull(awaitPosted(id))

        scheduler.schedule(id, "Stand-up", System.currentTimeMillis() + 60_000)
        scheduler.cancel(id)
        val after = wm.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.workName(id)).get()
        assertEquals(0, after.count { it.state == WorkInfo.State.ENQUEUED })
    }
}
