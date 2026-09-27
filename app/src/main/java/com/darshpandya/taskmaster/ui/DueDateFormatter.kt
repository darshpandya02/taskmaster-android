package com.darshpandya.taskmaster.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats due dates relative to today: "Today 14:05", "Tomorrow 09:00", "Mon, Sep 28 09:00". */
class DueDateFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    private val use24Hour: Boolean = true,
) {
    private val time = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
    private val sameYear = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val otherYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

    fun format(dueAt: Long, now: Long): String {
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date: LocalDate = due.toLocalDate()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (date.year == today.year) sameYear.format(due) else otherYear.format(due)
        }
        return "$day ${time.format(due)}"
    }
}
