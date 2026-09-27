package com.darshpandya.taskmaster

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.darshpandya.taskmaster.data.DefaultTaskRepository
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.data.TaskFilter
import com.darshpandya.taskmaster.ui.TaskListViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskListViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainDispatcherRule()

    private lateinit var clock: TestClock
    private lateinit var repo: DefaultTaskRepository
    private lateinit var vm: TaskListViewModel
    private var latest: List<Task> = emptyList()
    private val observer = Observer<List<Task>> { latest = it }

    @Before fun setUp() = runTest {
        clock = TestClock()
        repo = DefaultTaskRepository(FakeTaskDao(), FakeReminderScheduler(), clock)
        repo.add("Write report", "quarterly numbers", clock.now + 7_200_000, Priority.HIGH)
        repo.add("Buy groceries", "milk, eggs", clock.now + 3_600_000, Priority.MEDIUM)
        val done = repo.add("Book flights", "", null, Priority.LOW)
        repo.setCompleted(repo.get(done)!!, true)
        vm = TaskListViewModel(repo)
        vm.tasks.observeForever(observer)
    }

    @After fun tearDown() = vm.tasks.removeObserver(observer)

    private fun titles() = latest.map { it.title }

    @Test fun showsAllTasksOpenFirstThenByDueDate() {
        assertEquals(listOf("Buy groceries", "Write report", "Book flights"), titles())
        assertEquals(TaskFilter.ALL, vm.filter.valueNow())
    }

    @Test fun filterActiveAndCompleted() {
        vm.setFilter(TaskFilter.ACTIVE)
        assertEquals(listOf("Buy groceries", "Write report"), titles())
        vm.setFilter(TaskFilter.COMPLETED)
        assertEquals(listOf("Book flights"), titles())
        assertEquals(TaskFilter.COMPLETED, vm.filter.valueNow())
    }

    @Test fun searchMatchesTitleAndNotesCaseInsensitively() {
        vm.setSearchText("MILK")
        assertEquals(listOf("Buy groceries"), titles())
        vm.setSearchText("report")
        assertEquals(listOf("Write report"), titles())
        vm.setSearchText("")
        assertEquals(3, latest.size)
    }

    @Test fun searchCombinesWithFilter() {
        vm.setFilter(TaskFilter.COMPLETED)
        vm.setSearchText("report")
        assertTrue(latest.isEmpty())
        assertEquals("report", vm.currentQuery.text)
    }

    @Test fun completingATaskUpdatesListAndActiveCount() {
        val groceries = latest.first { it.title == "Buy groceries" }
        vm.setCompleted(groceries, true)
        assertEquals(listOf("Write report", "Buy groceries", "Book flights"), titles())
        assertEquals(1, vm.activeCount.valueNow())
    }

    @Test fun deleteEmitsEventAndUndoRestores() {
        val report = latest.first { it.title == "Write report" }
        vm.delete(report)
        assertEquals(listOf("Buy groceries", "Book flights"), titles())
        val event = vm.deleted.valueNow()!!
        assertEquals(report, event.consume())
        assertNull(event.consume())

        vm.undoDelete(report)
        assertEquals(listOf("Buy groceries", "Write report", "Book flights"), titles())
        assertEquals("Restored \"Write report\"", vm.messages.valueNow()!!.consume())
    }
}
