package com.darshpandya.taskmaster.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.darshpandya.taskmaster.AppContainer

fun AppContainer.viewModels(): ViewModelProvider.Factory = viewModelFactory {
    initializer { TaskListViewModel(repository) }
    initializer { EditTaskViewModel(repository) }
    initializer { BackupViewModel(backupManager) }
}
