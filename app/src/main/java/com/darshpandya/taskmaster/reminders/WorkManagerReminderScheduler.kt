package com.darshpandya.taskmaster.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * One unique WorkManager job per task, named after the task id, so scheduling
 * again (for example after the due date is edited) replaces the old job.
 */
class WorkManagerReminderScheduler(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderScheduler {

    private val workManager = WorkManager.getInstance(context)

    override fun schedule(taskId: Long, title: String, dueAt: Long) {
        val delay = (dueAt - clock()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TASK_ID to taskId, ReminderWorker.KEY_TITLE to title))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(taskId: Long) {
        workManager.cancelUniqueWork(workName(taskId))
    }

    companion object {
        const val TAG = "task-reminder"
        fun workName(taskId: Long) = "reminder-$taskId"
    }
}
