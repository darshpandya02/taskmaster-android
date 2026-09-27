package com.darshpandya.taskmaster.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun priorityToString(p: Priority): String = p.name
    @TypeConverter fun stringToPriority(s: String): Priority = Priority.valueOf(s)
}
