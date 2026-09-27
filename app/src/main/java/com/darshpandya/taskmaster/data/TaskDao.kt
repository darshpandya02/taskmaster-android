package com.darshpandya.taskmaster.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface TaskDao {

    /**
     * Tasks matching a search string (title or notes) and a filter ordinal
     * (0 all, 1 active, 2 completed). Open tasks come first, then by due date
     * with undated tasks last, then newest first.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE (:text = '' OR title LIKE '%' || :text || '%' OR notes LIKE '%' || :text || '%')
          AND (:filter = 0 OR (:filter = 1 AND completed = 0) OR (:filter = 2 AND completed = 1))
        ORDER BY completed ASC,
                 CASE WHEN due_at IS NULL THEN 1 ELSE 0 END ASC,
                 due_at ASC,
                 created_at DESC
        """
    )
    fun observe(text: String, filter: Int): LiveData<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): LiveData<Task?>

    @Query("SELECT COUNT(*) FROM tasks WHERE completed = 0")
    fun observeActiveCount(): LiveData<Int>

    @Insert
    suspend fun insert(task: Task): Long

    @Insert
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task): Int

    @Delete
    suspend fun delete(task: Task): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET completed = :completed, updated_at = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, now: Long): Int

    /** Replaces every row, used when a backup is restored. Runs atomically. */
    @Transaction
    suspend fun replaceAll(tasks: List<Task>) {
        deleteAll()
        insertAll(tasks)
    }
}
