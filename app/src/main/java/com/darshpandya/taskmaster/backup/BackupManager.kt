package com.darshpandya.taskmaster.backup

import com.darshpandya.taskmaster.data.TaskRepository

/** Moves the whole task list to and from a [BackupProvider]. */
class BackupManager(
    private val repository: TaskRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Returns the number of tasks written. */
    suspend fun backup(provider: BackupProvider, location: String): Int {
        requireAvailable(provider)
        val tasks = repository.getAll()
        provider.write(location, BackupCodec.encode(tasks, clock()))
        return tasks.size
    }

    /** Replaces all tasks with the backup's contents. Returns the number restored. */
    suspend fun restore(provider: BackupProvider, location: String): Int {
        requireAvailable(provider)
        val tasks = BackupCodec.decode(provider.read(location))
        repository.replaceAll(tasks)
        return tasks.size
    }

    private fun requireAvailable(provider: BackupProvider) {
        if (!provider.isAvailable) throw BackupException(provider.unavailableReason ?: "${provider.displayName} is unavailable")
    }
}
