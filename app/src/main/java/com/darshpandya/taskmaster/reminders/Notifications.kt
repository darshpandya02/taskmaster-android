package com.darshpandya.taskmaster.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.darshpandya.taskmaster.R
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.ui.EditTaskActivity

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_reminders_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notificationId(taskId: Long): Int = taskId.toInt()

    fun showReminder(context: Context, task: Task) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            notificationId(task.id),
            EditTaskActivity.intent(context, task.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context,
            notificationId(task.id),
            CompleteTaskReceiver.intent(context, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = task.notes.ifBlank { context.getString(R.string.reminder_due_now) }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_mark_done), done)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(task.id), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }
}
