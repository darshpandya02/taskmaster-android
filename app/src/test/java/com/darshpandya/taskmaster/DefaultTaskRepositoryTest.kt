package com.darshpandya.taskmaster

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.darshpandya.taskmaster.data.DefaultTaskRepository
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DefaultTaskRepositoryTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private lateinit var dao: FakeTaskDao
    private lateinit var reminders: FakeReminderScheduler
    private lateinit var clock: TestClock
    private lateinit var repo: DefaultTaskRepository

    private val hour = 3_600_000L

    @Before fun setUp() {
        dao = FakeTaskDao()
        reminders = FakeReminderScheduler()
        clock = TestClock()
        repo = DefaultTaskRepository(dao, reminders, clock)
    }

    @Test fun addTrimsTextAndStampsTimes() = runTest {
        val id = repo.add("  Buy milk  ", "  2 litres ", null, Priority.HIGH)
        val t = repo.get(id)!!
        assertEquals("Buy milk", t.title)
        assertEquals("2 litres", t.notes)
        assertEquals(Priority.HIGH, t.priority)
        assertEquals(clock.now, t.createdAt)
        assertEquals(clock.now, t.updatedAt)
        assertFalse(t.completed)
    }

    @Test fun addWithFutureDueDateSchedulesReminder() = runTest {
        val due = clock.now + hour
        val id = repo.add("Call dentist", "", due, Priority.MEDIUM)
        assertEquals(due, reminders.pending[id])
    }

    @Test fun addWithoutDueDateOrWithPastDueDateSchedulesNothing() = runTest {
        repo.add("No date", "", null, Priority.LOW)
        repo.add("Already late", "", clock.now - hour, Priority.LOW)
        assertTrue(reminders.pending.isEmpty())
    }

    @Test fun updateMovesReminderAndClearingDueDateCancelsIt() = runTest {
        val id = repo.add("Report", "", clock.now + hour, Priority.MEDIUM)
        clock.now += 1000
        repo.update(repo.get(id)!!.copy(dueAt = clock.now + 2 * hour))
        assertEquals(clock.now + 2 * hour, reminders.pending[id])
        assertEquals(clock.now, repo.get(id)!!.updatedAt)

        repo.update(repo.get(id)!!.copy(dueAt = null))
        assertNull(reminders.pending[id])
    }

    @Test fun completingCancelsReminderAndReopeningRestoresIt() = runTest {
        val due = clock.now + hour
        val id = repo.add("Pay rent", "", due, Priority.HIGH)
        repo.setCompleted(repo.get(id)!!, true)
        assertTrue(repo.get(id)!!.completed)
        assertNull(reminders.pending[id])

        repo.setCompleted(repo.get(id)!!, false)
        assertFalse(repo.get(id)!!.completed)
        assertEquals(due, reminders.pending[id])
    }

    @Test fun deleteRemovesRowAndCancelsReminder() = runTest {
        val id = repo.add("Temp", "", clock.now + hour, Priority.LOW)
        repo.delete(repo.get(id)!!)
        assertNull(repo.get(id))
        assertNull(reminders.pending[id])
    }

    @Test fun restoreKeepsIdAndReschedules() = runTest {
        val id = repo.add("Undo me", "", clock.now + hour, Priority.LOW)
        val task = repo.get(id)!!
        repo.delete(task)
        repo.restore(task)
        assertEquals(task, repo.get(id))
        assertEquals(task.dueAt, reminders.pending[id])
    }

    @Test fun replaceAllSwapsRowsAndReminders() = runTest {
        val old = repo.add("Old", "", clock.now + hour, Priority.LOW)
        val incoming = listOf(
            Task(10, "Future", dueAt = clock.now + hour, createdAt = 1, updatedAt = 1),
            Task(11, "Done", dueAt = clock.now + hour, completed = true, createdAt = 1, updatedAt = 1),
            Task(12, "Past", dueAt = clock.now - hour, createdAt = 1, updatedAt = 1),
        )
        repo.replaceAll(incoming)
        assertEquals(listOf(10L, 11L, 12L), repo.getAll().map { it.id })
        assertNull(reminders.pending[old])
        assertEquals(setOf(10L), reminders.pending.keys)
    }

    @Test fun observeActiveCountFollowsCompletion() = runTest {
        val a = repo.add("A", "", null, Priority.LOW)
        repo.add("B", "", null, Priority.LOW)
        assertEquals(2, repo.observeActiveCount().valueNow())
        repo.setCompleted(repo.get(a)!!, true)
        assertEquals(1, repo.observeActiveCount().valueNow())
    }
}
