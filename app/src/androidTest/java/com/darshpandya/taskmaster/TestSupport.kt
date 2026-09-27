package com.darshpandya.taskmaster

import android.os.AsyncTask
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import com.darshpandya.taskmaster.data.TaskDatabase
import com.darshpandya.taskmaster.reminders.ReminderScheduler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.Matcher

class RecordingReminderScheduler : ReminderScheduler {
    val pending = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    override fun schedule(taskId: Long, title: String, dueAt: Long) { pending[taskId] = dueAt }
    override fun cancel(taskId: Long) { pending.remove(taskId) }
}

val app: TaskMasterApp get() = ApplicationProvider.getApplicationContext()

/**
 * An in-memory database whose queries run on the AsyncTask pool. Espresso waits
 * for that pool to be idle, so Room's LiveData updates finish before each check.
 */
@Suppress("DEPRECATION")
fun espressoFriendlyDatabase(): TaskDatabase =
    Room.inMemoryDatabaseBuilder(app, TaskDatabase::class.java)
        .setQueryExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        .setTransactionExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        .allowMainThreadQueries()
        .build()

/** Swaps the app's dependencies for a fresh in-memory database and a recording scheduler. */
fun installTestContainer(): Pair<AppContainer, RecordingReminderScheduler> {
    val reminders = RecordingReminderScheduler()
    val container = AppContainer(app, espressoFriendlyDatabase(), reminders)
    app.container = container
    return container to reminders
}

fun <T> LiveData<T>.awaitValue(timeoutSeconds: Long = 5, predicate: (T) -> Boolean = { true }): T {
    var result: T? = null
    val latch = CountDownLatch(1)
    val observer = Observer<T> { v -> if (predicate(v)) { result = v; latch.countDown() } }
    androidx.arch.core.executor.ArchTaskExecutor.getInstance().executeOnMainThread { observeForever(observer) }
    try {
        check(latch.await(timeoutSeconds, TimeUnit.SECONDS)) { "LiveData value never arrived" }
    } finally {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().executeOnMainThread { removeObserver(observer) }
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** Clicks a child view (by id) inside a RecyclerView item. */
fun clickChild(id: Int) = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isDisplayed()
    override fun getDescription() = "click child view $id"
    override fun perform(uiController: UiController, view: View) {
        view.findViewById<View>(id).performClick()
        uiController.loopMainThreadForAtLeast(100)
    }
}
