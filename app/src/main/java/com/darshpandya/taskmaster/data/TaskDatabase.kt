package com.darshpandya.taskmaster.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Task::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): TaskDatabase =
            Room.databaseBuilder(context, TaskDatabase::class.java, "taskmaster.db").build()

        fun inMemory(context: Context): TaskDatabase =
            Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java).build()
    }
}
