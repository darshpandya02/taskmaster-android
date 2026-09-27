package com.darshpandya.taskmaster.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.data.TaskFilter
import com.darshpandya.taskmaster.data.TaskQuery
import com.darshpandya.taskmaster.data.TaskRepository
import kotlinx.coroutines.launch

class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {

    private val query = MutableLiveData(TaskQuery())

    /** The list for the current search text and filter, re-queried whenever either changes. */
    val tasks: LiveData<List<Task>> = query.switchMap { repository.observe(it) }

    val filter: LiveData<TaskFilter> = query.map { it.filter }

    val activeCount: LiveData<Int> = repository.observeActiveCount()

    private val _messages = MutableLiveData<Event<String>>()
    val messages: LiveData<Event<String>> = _messages

    private val _deleted = MutableLiveData<Event<Task>>()
    /** Fires after a delete so the screen can offer undo. */
    val deleted: LiveData<Event<Task>> = _deleted

    val currentQuery: TaskQuery get() = query.value ?: TaskQuery()

    fun setSearchText(text: String) {
        val current = currentQuery
        if (current.text != text) query.value = current.copy(text = text)
    }

    fun setFilter(filter: TaskFilter) {
        val current = currentQuery
        if (current.filter != filter) query.value = current.copy(filter = filter)
    }

    fun setCompleted(task: Task, completed: Boolean) {
        if (task.completed == completed) return
        viewModelScope.launch { repository.setCompleted(task, completed) }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repository.delete(task)
            _deleted.value = Event(task)
        }
    }

    fun undoDelete(task: Task) {
        viewModelScope.launch {
            repository.restore(task)
            _messages.value = Event("Restored \"${task.title}\"")
        }
    }
}
