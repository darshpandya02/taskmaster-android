package com.darshpandya.taskmaster.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.darshpandya.taskmaster.R
import com.darshpandya.taskmaster.TaskMasterApp
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.databinding.ActivityEditTaskBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class EditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTaskBinding
    private val container by lazy { (application as TaskMasterApp).container }
    private val viewModel: EditTaskViewModel by viewModels { container.viewModels() }
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getLongExtra(EXTRA_ID, 0)
        setTitle(if (id > 0) R.string.title_edit_task else R.string.title_new_task)
        viewModel.load(id)

        val formatter = DueDateFormatter(use24Hour = DateFormat.is24HourFormat(this))

        viewModel.loaded.observe(this) { task ->
            if (task != null && savedInstanceState == null) {
                binding.titleInput.setText(task.title)
                binding.notesInput.setText(task.notes)
            }
            invalidateOptionsMenu()
        }
        viewModel.dueAt.observe(this) { due ->
            binding.dueButton.text = if (due == null) getString(R.string.no_due_date)
            else formatter.format(due, System.currentTimeMillis())
            binding.clearDue.isVisible = due != null
        }
        viewModel.priority.observe(this) { p ->
            binding.priorityGroup.check(
                when (p ?: Priority.MEDIUM) {
                    Priority.LOW -> R.id.priority_low
                    Priority.MEDIUM -> R.id.priority_medium
                    Priority.HIGH -> R.id.priority_high
                }
            )
        }
        viewModel.titleError.observe(this) { binding.titleLayout.error = it }
        viewModel.finished.observe(this) { it.consume()?.let { finish() } }

        binding.priorityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setPriority(
                when (checkedId) {
                    R.id.priority_low -> Priority.LOW
                    R.id.priority_high -> Priority.HIGH
                    else -> Priority.MEDIUM
                }
            )
        }
        binding.dueButton.setOnClickListener { pickDate() }
        binding.clearDue.setOnClickListener { viewModel.setDueAt(null) }
        binding.save.setOnClickListener {
            viewModel.save(binding.titleInput.text?.toString().orEmpty(), binding.notesInput.text?.toString().orEmpty())
        }
    }

    /** Date first, then time. A new due date defaults to one hour from now, on the hour. */
    private fun pickDate() {
        val start = viewModel.dueAt.value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            ?: LocalDateTime.now(zone).plusHours(1).truncatedTo(ChronoUnit.HOURS)
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, hour, minute ->
                val picked = LocalDateTime.of(y, m + 1, d, hour, minute)
                viewModel.setDueAt(picked.atZone(zone).toInstant().toEpochMilli())
            }, start.hour, start.minute, DateFormat.is24HourFormat(this)).show()
        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit, menu)
        menu.findItem(R.id.action_delete).isVisible = !viewModel.isNew
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_delete -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete() }
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_ID = "task_id"
        fun intent(context: Context, id: Long) = Intent(context, EditTaskActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
