package com.darshpandya.taskmaster.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.data.TaskRepository
import kotlinx.coroutines.launch

/** Holds the edit form. Survives rotation, so picker choices are not lost. */
class EditTaskViewModel(private val repository: TaskRepository) : ViewModel() {

    private var original: Task? = null
    private var loadedId: Long? = null

    val isNew: Boolean get() = original == null

    private val _loaded = MutableLiveData<Task?>()
    /** The stored task once loaded, or null for a new task. */
    val loaded: LiveData<Task?> = _loaded

    private val _dueAt = MutableLiveData<Long?>(null)
    val dueAt: LiveData<Long?> = _dueAt

    private val _priority = MutableLiveData(Priority.MEDIUM)
    val priority: LiveData<Priority> = _priority

    private val _titleError = MutableLiveData<String?>(null)
    val titleError: LiveData<String?> = _titleError

    private val _finished = MutableLiveData<Event<Result>>()
    val finished: LiveData<Event<Result>> = _finished

    enum class Result { SAVED, DELETED, NOT_FOUND }

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        if (id <= 0) {
            _loaded.value = null
            return
        }
        viewModelScope.launch {
            val task = repository.get(id)
            if (task == null) {
                _finished.value = Event(Result.NOT_FOUND)
                return@launch
            }
            original = task
            _dueAt.value = task.dueAt
            _priority.value = task.priority
            _loaded.value = task
        }
    }

    fun setDueAt(value: Long?) { _dueAt.value = value }

    fun setPriority(value: Priority) { _priority.value = value }

    fun save(title: String, notes: String) {
        val cleanTitle = title.trim()
        when {
            cleanTitle.isEmpty() -> { _titleError.value = "Title is required"; return }
            cleanTitle.length > MAX_TITLE -> { _titleError.value = "Title must be at most $MAX_TITLE characters"; return }
        }
        _titleError.value = null
        val due = _dueAt.value
        val priority = _priority.value ?: Priority.MEDIUM
        viewModelScope.launch {
            val existing = original
            if (existing == null) {
                repository.add(cleanTitle, notes, due, priority)
            } else {
                repository.update(existing.copy(title = cleanTitle, notes = notes, dueAt = due, priority = priority))
            }
            _finished.value = Event(Result.SAVED)
        }
    }

    fun delete() {
        val existing = original ?: return
        viewModelScope.launch {
            repository.delete(existing)
            _finished.value = Event(Result.DELETED)
        }
    }

    companion object {
        const val MAX_TITLE = 120
    }
}
