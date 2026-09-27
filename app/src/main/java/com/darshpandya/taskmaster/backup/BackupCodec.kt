package com.darshpandya.taskmaster.backup

import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class BackupTask(
    val id: Long,
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val priority: String = Priority.MEDIUM.name,
    val completed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val tasks: List<BackupTask>,
)

/** Versioned JSON format for backups. */
object BackupCodec {
    const val FORMAT = "taskmaster-backup"
    const val VERSION = 1

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(tasks: List<Task>, exportedAt: Long): ByteArray {
        val file = BackupFile(FORMAT, VERSION, exportedAt, tasks.map { it.toBackup() })
        return json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<Task> {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException("Not a TaskMaster backup file", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("Not a TaskMaster backup file", e)
        }
        if (file.format != FORMAT) throw BackupException("Not a TaskMaster backup file")
        if (file.version > VERSION) throw BackupException("Backup version ${file.version} is newer than this app supports")
        val ids = HashSet<Long>()
        return file.tasks.map { t ->
            if (t.title.isBlank()) throw BackupException("Backup contains a task without a title")
            if (!ids.add(t.id)) throw BackupException("Backup contains duplicate task id ${t.id}")
            val priority = Priority.entries.firstOrNull { it.name == t.priority } ?: Priority.MEDIUM
            Task(t.id, t.title, t.notes, t.dueAt, priority, t.completed, t.createdAt, t.updatedAt)
        }
    }

    private fun Task.toBackup() = BackupTask(id, title, notes, dueAt, priority.name, completed, createdAt, updatedAt)
}
