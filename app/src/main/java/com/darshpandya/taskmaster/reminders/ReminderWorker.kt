package com.darshpandya.taskmaster.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darshpandya.taskmaster.TaskMasterApp

/** Posts the reminder notification, unless the task was completed or deleted meanwhile. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_TASK_ID, -1)
        if (id < 0) return Result.failure()
        val repository = (applicationContext as TaskMasterApp).container.repository
        val task = repository.get(id) ?: return Result.success()
        if (task.completed) return Result.success()
        Notifications.showReminder(applicationContext, task)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TITLE = "title"
    }
}
