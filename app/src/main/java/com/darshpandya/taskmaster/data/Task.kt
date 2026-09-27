package com.darshpandya.taskmaster.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Priority { LOW, MEDIUM, HIGH }

@Entity(tableName = "tasks", indices = [Index("completed"), Index("due_at")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val completed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun isOverdue(now: Long): Boolean = !completed && dueAt != null && dueAt < now
}

/** Which tasks the list shows. The ordinal is passed to SQL, so do not reorder. */
enum class TaskFilter { ALL, ACTIVE, COMPLETED }

data class TaskQuery(val text: String = "", val filter: TaskFilter = TaskFilter.ALL)
