package com.darshpandya.taskmaster

import android.content.Context
import com.darshpandya.taskmaster.backup.BackupManager
import com.darshpandya.taskmaster.backup.BackupProvider
import com.darshpandya.taskmaster.backup.DocumentBackupProvider
import com.darshpandya.taskmaster.backup.DriveBackupProvider
import com.darshpandya.taskmaster.data.DefaultTaskRepository
import com.darshpandya.taskmaster.data.TaskDatabase
import com.darshpandya.taskmaster.data.TaskRepository
import com.darshpandya.taskmaster.reminders.ReminderScheduler
import com.darshpandya.taskmaster.reminders.WorkManagerReminderScheduler

/** Manual dependency wiring for the app. */
class AppContainer(
    context: Context,
    val database: TaskDatabase,
    val reminders: ReminderScheduler = WorkManagerReminderScheduler(context.applicationContext),
) {
    val repository: TaskRepository = DefaultTaskRepository(database.taskDao(), reminders)
    val backupManager = BackupManager(repository)
    val documentBackup: BackupProvider = DocumentBackupProvider(context.applicationContext.contentResolver)
    val driveBackup: BackupProvider = DriveBackupProvider(BuildConfig.DRIVE_CLIENT_ID)
}
