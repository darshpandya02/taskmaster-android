package com.darshpandya.taskmaster.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.darshpandya.taskmaster.R
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.databinding.ItemTaskBinding

class TaskAdapter(
    private val formatter: DueDateFormatter,
    private val now: () -> Long,
    private val onClick: (Task) -> Unit,
    private val onCheckedChange: (Task, Boolean) -> Unit,
) : ListAdapter<Task, TaskAdapter.Holder>(Diff) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemTaskBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(task: Task) {
            val ctx = b.root.context
            b.title.text = task.title
            b.title.paintFlags = if (task.completed) b.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else b.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            b.title.alpha = if (task.completed) 0.6f else 1f

            b.notes.text = task.notes
            b.notes.isVisible = task.notes.isNotBlank()

            val due = task.dueAt
            b.due.isVisible = due != null
            if (due != null) {
                val overdue = task.isOverdue(now())
                b.due.text = if (overdue) ctx.getString(R.string.overdue_prefix, formatter.format(due, now()))
                else formatter.format(due, now())
                b.due.setTextColor(
                    ContextCompat.getColor(ctx, if (overdue) R.color.overdue else R.color.text_secondary)
                )
            }

            b.priority.setBackgroundColor(ContextCompat.getColor(ctx, priorityColor(task.priority)))
            b.priority.contentDescription = ctx.getString(R.string.priority_description, task.priority.name.lowercase())

            // Detach the listener while setting the state, so recycling does not fire it.
            b.done.setOnCheckedChangeListener(null)
            b.done.isChecked = task.completed
            b.done.contentDescription = ctx.getString(R.string.mark_done_description, task.title)
            b.done.setOnCheckedChangeListener { _, checked -> onCheckedChange(task, checked) }
            b.root.setOnClickListener { onClick(task) }
        }
    }

    private fun priorityColor(p: Priority) = when (p) {
        Priority.HIGH -> R.color.priority_high
        Priority.MEDIUM -> R.color.priority_medium
        Priority.LOW -> R.color.priority_low
    }

    object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
