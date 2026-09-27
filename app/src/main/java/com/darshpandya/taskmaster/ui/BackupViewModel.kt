package com.darshpandya.taskmaster.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshpandya.taskmaster.backup.BackupException
import com.darshpandya.taskmaster.backup.BackupManager
import com.darshpandya.taskmaster.backup.BackupProvider
import kotlinx.coroutines.launch

class BackupViewModel(private val manager: BackupManager) : ViewModel() {

    private val _messages = MutableLiveData<Event<String>>()
    val messages: LiveData<Event<String>> = _messages

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    fun export(provider: BackupProvider, location: String) = run {
        val n = manager.backup(provider, location)
        "Exported $n ${plural(n)}"
    }

    fun import(provider: BackupProvider, location: String) = run {
        val n = manager.restore(provider, location)
        "Imported $n ${plural(n)}"
    }

    private fun run(block: suspend () -> String) {
        _busy.value = true
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: BackupException) {
                e.message ?: "Backup failed"
            } catch (e: java.io.IOException) {
                "Backup failed: ${e.message}"
            }
            _busy.value = false
            _messages.value = Event(message)
        }
    }

    private fun plural(n: Int) = if (n == 1) "task" else "tasks"
}
