package com.darshpandya.taskmaster.reminders

/** Schedules the "task is due" notification for a task. */
interface ReminderScheduler {
    fun schedule(taskId: Long, title: String, dueAt: Long)
    fun cancel(taskId: Long)
}
