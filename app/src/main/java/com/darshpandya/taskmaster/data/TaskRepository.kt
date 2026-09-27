package com.darshpandya.taskmaster.data

import androidx.lifecycle.LiveData
import com.darshpandya.taskmaster.reminders.ReminderScheduler

interface TaskRepository {
    fun observe(query: TaskQuery): LiveData<List<Task>>
    fun observeTask(id: Long): LiveData<Task?>
    fun observeActiveCount(): LiveData<Int>
    suspend fun get(id: Long): Task?
    suspend fun getAll(): List<Task>
    suspend fun add(title: String, notes: String, dueAt: Long?, priority: Priority): Long
    suspend fun update(task: Task)
    suspend fun setCompleted(task: Task, completed: Boolean)
    suspend fun delete(task: Task)
    /** Puts back a task that was just deleted (undo), keeping its id. */
    suspend fun restore(task: Task)
    suspend fun replaceAll(tasks: List<Task>)
}

/**
 * Room-backed repository. Every write also keeps the reminder schedule in
 * step: an open task with a future due date has exactly one pending reminder,
 * and any other task has none.
 */
class DefaultTaskRepository(
    private val dao: TaskDao,
    private val reminders: ReminderScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : TaskRepository {

    override fun observe(query: TaskQuery): LiveData<List<Task>> =
        dao.observe(query.text.trim(), query.filter.ordinal)

    override fun observeTask(id: Long): LiveData<Task?> = dao.observeById(id)

    override fun observeActiveCount(): LiveData<Int> = dao.observeActiveCount()

    override suspend fun get(id: Long): Task? = dao.getById(id)

    override suspend fun getAll(): List<Task> = dao.getAll()

    override suspend fun add(title: String, notes: String, dueAt: Long?, priority: Priority): Long {
        val now = clock()
        val task = Task(
            title = title.trim(),
            notes = notes.trim(),
            dueAt = dueAt,
            priority = priority,
            createdAt = now,
            updatedAt = now,
        )
        val id = dao.insert(task)
        syncReminder(task.copy(id = id))
        return id
    }

    override suspend fun update(task: Task) {
        val updated = task.copy(title = task.title.trim(), notes = task.notes.trim(), updatedAt = clock())
        dao.update(updated)
        syncReminder(updated)
    }

    override suspend fun setCompleted(task: Task, completed: Boolean) {
        val now = clock()
        dao.setCompleted(task.id, completed, now)
        syncReminder(task.copy(completed = completed, updatedAt = now))
    }

    override suspend fun delete(task: Task) {
        dao.delete(task)
        reminders.cancel(task.id)
    }

    override suspend fun restore(task: Task) {
        dao.insert(task)
        syncReminder(task)
    }

    override suspend fun replaceAll(tasks: List<Task>) {
        dao.getAll().forEach { reminders.cancel(it.id) }
        dao.replaceAll(tasks)
        tasks.forEach { syncReminder(it) }
    }

    private fun syncReminder(task: Task) {
        val due = task.dueAt
        if (!task.completed && due != null && due > clock()) {
            reminders.schedule(task.id, task.title, due)
        } else {
            reminders.cancel(task.id)
        }
    }
}
