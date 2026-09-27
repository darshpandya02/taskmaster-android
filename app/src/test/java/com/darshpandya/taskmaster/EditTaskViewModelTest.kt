package com.darshpandya.taskmaster

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.darshpandya.taskmaster.data.DefaultTaskRepository
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.ui.EditTaskViewModel
import com.darshpandya.taskmaster.ui.EditTaskViewModel.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditTaskViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainDispatcherRule()

    private lateinit var clock: TestClock
    private lateinit var reminders: FakeReminderScheduler
    private lateinit var repo: DefaultTaskRepository
    private lateinit var vm: EditTaskViewModel

    @Before fun setUp() {
        clock = TestClock()
        reminders = FakeReminderScheduler()
        repo = DefaultTaskRepository(FakeTaskDao(), reminders, clock)
        vm = EditTaskViewModel(repo)
    }

    @Test fun blankTitleIsRejected() = runTest {
        vm.load(0)
        vm.save("   ", "notes")
        assertEquals("Title is required", vm.titleError.valueNow())
        assertNull(vm.finished.valueNow())
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun overlongTitleIsRejected() = runTest {
        vm.load(0)
        vm.save("x".repeat(EditTaskViewModel.MAX_TITLE + 1), "")
        assertEquals("Title must be at most 120 characters", vm.titleError.valueNow())
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun newTaskIsSavedWithDueDateAndPriority() = runTest {
        vm.load(0)
        assertTrue(vm.isNew)
        val due = clock.now + 3_600_000
        vm.setDueAt(due)
        vm.setPriority(Priority.HIGH)
        vm.save("Submit form", "before noon")
        val saved = repo.getAll().single()
        assertEquals("Submit form", saved.title)
        assertEquals("before noon", saved.notes)
        assertEquals(due, saved.dueAt)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(due, reminders.pending[saved.id])
        assertEquals(Result.SAVED, vm.finished.valueNow()!!.consume())
        assertNull(vm.titleError.valueNow())
    }

    @Test fun existingTaskLoadsIntoFormAndSavesChanges() = runTest {
        val due = clock.now + 3_600_000
        val id = repo.add("Old title", "old notes", due, Priority.LOW)
        vm.load(id)
        assertFalse(vm.isNew)
        assertEquals("Old title", vm.loaded.valueNow()!!.title)
        assertEquals(due, vm.dueAt.valueNow())
        assertEquals(Priority.LOW, vm.priority.valueNow())

        vm.setDueAt(null)
        vm.save("New title", "new notes")
        val t = repo.get(id)!!
        assertEquals("New title", t.title)
        assertEquals("new notes", t.notes)
        assertNull(t.dueAt)
        assertNull(reminders.pending[id])
        assertEquals(1, repo.getAll().size)
    }

    @Test fun deleteRemovesTheTask() = runTest {
        val id = repo.add("Delete me", "", null, Priority.MEDIUM)
        vm.load(id)
        vm.delete()
        assertNull(repo.get(id))
        assertEquals(Result.DELETED, vm.finished.valueNow()!!.consume())
    }

    @Test fun missingTaskReportsNotFound() = runTest {
        vm.load(42)
        assertEquals(Result.NOT_FOUND, vm.finished.valueNow()!!.consume())
    }

    @Test fun loadingTheSameIdTwiceKeepsEdits() = runTest {
        val id = repo.add("Keep", "", null, Priority.MEDIUM)
        vm.load(id)
        vm.setPriority(Priority.HIGH)
        vm.load(id) // e.g. activity recreated after rotation
        assertEquals(Priority.HIGH, vm.priority.valueNow())
    }
}
