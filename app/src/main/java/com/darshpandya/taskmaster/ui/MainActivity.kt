package com.darshpandya.taskmaster.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.darshpandya.taskmaster.R
import com.darshpandya.taskmaster.TaskMasterApp
import com.darshpandya.taskmaster.data.TaskFilter
import com.darshpandya.taskmaster.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val container by lazy { (application as TaskMasterApp).container }
    private val listViewModel: TaskListViewModel by viewModels { container.viewModels() }
    private val backupViewModel: BackupViewModel by viewModels { container.viewModels() }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backupViewModel.export(container.documentBackup, uri.toString())
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupViewModel.import(container.documentBackup, uri.toString())
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val adapter = TaskAdapter(
            formatter = DueDateFormatter(use24Hour = DateFormat.is24HourFormat(this)),
            now = System::currentTimeMillis,
            onClick = { startActivity(EditTaskActivity.intent(this, it.id)) },
            onCheckedChange = { task, checked -> listViewModel.setCompleted(task, checked) },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        swipeToDelete(adapter).attachToRecyclerView(binding.list)

        binding.fab.setOnClickListener { startActivity(EditTaskActivity.intent(this, 0)) }

        binding.filters.setOnCheckedStateChangeListener { _, ids ->
            listViewModel.setFilter(
                when (ids.firstOrNull()) {
                    R.id.filter_active -> TaskFilter.ACTIVE
                    R.id.filter_done -> TaskFilter.COMPLETED
                    else -> TaskFilter.ALL
                }
            )
        }

        listViewModel.tasks.observe(this) { tasks ->
            adapter.submitList(tasks)
            binding.empty.isVisible = tasks.isEmpty()
            binding.empty.setText(
                if (listViewModel.currentQuery.text.isNotBlank()) R.string.empty_search else R.string.empty_list
            )
        }
        listViewModel.activeCount.observe(this) { n ->
            supportActionBar?.subtitle = resources.getQuantityString(R.plurals.active_count, n, n)
        }
        listViewModel.deleted.observe(this) { event ->
            event.consume()?.let { task ->
                Snackbar.make(binding.root, getString(R.string.deleted_message, task.title), Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.fab)
                    .setAction(R.string.undo) { listViewModel.undoDelete(task) }
                    .show()
            }
        }
        listViewModel.messages.observe(this) { it.consume()?.let(::showMessage) }
        backupViewModel.messages.observe(this) { it.consume()?.let(::showMessage) }
        backupViewModel.busy.observe(this) { binding.progress.isVisible = it }

        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showMessage(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).setAnchorView(binding.fab).show()
    }

    private fun swipeToDelete(adapter: TaskAdapter) = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) listViewModel.delete(adapter.currentList[position])
            }
        }
    )

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val search = searchItem.actionView as SearchView
        search.queryHint = getString(R.string.search_hint)
        val current = listViewModel.currentQuery.text
        if (current.isNotEmpty()) {
            searchItem.expandActionView()
            search.setQuery(current, false)
        }
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = true.also { search.clearFocus() }
            override fun onQueryTextChange(newText: String) = true.also { listViewModel.setSearchText(newText) }
        })
        menu.findItem(R.id.action_drive).title = getString(R.string.action_drive_backup) +
            if (container.driveBackup.isAvailable) "" else getString(R.string.suffix_not_configured)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_export -> {
            exportLauncher.launch("taskmaster-backup-${LocalDate.now()}.json"); true
        }
        R.id.action_import -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_title)
                .setMessage(R.string.import_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.import_confirm) { _, _ ->
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                .show()
            true
        }
        R.id.action_drive -> {
            val drive = container.driveBackup
            MaterialAlertDialogBuilder(this)
                .setTitle(drive.displayName)
                .setMessage(drive.unavailableReason ?: getString(R.string.drive_available))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
