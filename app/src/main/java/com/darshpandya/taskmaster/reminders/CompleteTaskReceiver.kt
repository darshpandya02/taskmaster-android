package com.darshpandya.taskmaster.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.darshpandya.taskmaster.TaskMasterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the "Mark done" action on a reminder notification. */
class CompleteTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as TaskMasterApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = app.container.repository
                repository.get(id)?.let { repository.setCompleted(it, true) }
                Notifications.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        fun intent(context: Context, taskId: Long) =
            Intent(context, CompleteTaskReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}
