package com.darshpandya.taskmaster

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.darshpandya.taskmaster.backup.BackupProvider
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.data.TaskDao
import com.darshpandya.taskmaster.reminders.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** In-memory TaskDao. Filtering and ordering follow the SQL in TaskDao. */
class FakeTaskDao : TaskDao {
    private val rows = linkedMapOf<Long, Task>()
    private var nextId = 1L
    private val all = MutableLiveData<List<Task>>(emptyList())

    private fun publish() { all.value = rows.values.toList() }

    val snapshot: List<Task> get() = rows.values.toList()

    override fun observe(text: String, filter: Int): LiveData<List<Task>> = all.map { list ->
        list.filter { t ->
            (text.isEmpty() || t.title.contains(text, ignoreCase = true) || t.notes.contains(text, ignoreCase = true)) &&
                (filter == 0 || (filter == 1 && !t.completed) || (filter == 2 && t.completed))
        }.sortedWith(
            compareBy<Task> { it.completed }
                .thenBy { if (it.dueAt == null) 1 else 0 }
                .thenBy { it.dueAt ?: 0 }
                .thenByDescending { it.createdAt }
        )
    }

    override suspend fun getAll(): List<Task> = rows.values.sortedBy { it.id }
    override suspend fun getById(id: Long): Task? = rows[id]
    override fun observeById(id: Long): LiveData<Task?> = all.map { rows[id] }
    override fun observeActiveCount(): LiveData<Int> = all.map { l -> l.count { !it.completed } }

    override suspend fun insert(task: Task): Long {
        val id = if (task.id == 0L) nextId++ else task.id.also { nextId = maxOf(nextId, it + 1) }
        require(id !in rows) { "duplicate id $id" }
        rows[id] = task.copy(id = id)
        publish()
        return id
    }

    override suspend fun insertAll(tasks: List<Task>) { tasks.forEach { insert(it) } }

    override suspend fun update(task: Task): Int {
        if (task.id !in rows) return 0
        rows[task.id] = task
        publish()
        return 1
    }

    override suspend fun delete(task: Task): Int = (if (rows.remove(task.id) != null) 1 else 0).also { publish() }

    override suspend fun deleteAll() { rows.clear(); publish() }

    override suspend fun setCompleted(id: Long, completed: Boolean, now: Long): Int {
        val t = rows[id] ?: return 0
        rows[id] = t.copy(completed = completed, updatedAt = now)
        publish()
        return 1
    }
}

/** Records the reminder that would be pending for each task. */
class FakeReminderScheduler : ReminderScheduler {
    val pending = mutableMapOf<Long, Long>()
    override fun schedule(taskId: Long, title: String, dueAt: Long) { pending[taskId] = dueAt }
    override fun cancel(taskId: Long) { pending.remove(taskId) }
}

class InMemoryBackupProvider(override val isAvailable: Boolean = true) : BackupProvider {
    val files = mutableMapOf<String, ByteArray>()
    override val id = "memory"
    override val displayName = "Memory"
    override val unavailableReason: String? = if (isAvailable) null else "Memory provider switched off"
    override suspend fun write(location: String, bytes: ByteArray) { files[location] = bytes }
    override suspend fun read(location: String): ByteArray =
        files[location] ?: throw java.io.FileNotFoundException(location)
}

/** A clock the test moves by hand. */
class TestClock(var now: Long = 1_790_000_000_000L) : () -> Long {
    override fun invoke(): Long = now
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Reads the current value of a LiveData by observing it once. */
fun <T> LiveData<T>.valueNow(): T? {
    var result: T? = null
    val observer = androidx.lifecycle.Observer<T> { result = it }
    observeForever(observer)
    removeObserver(observer)
    return result
}
