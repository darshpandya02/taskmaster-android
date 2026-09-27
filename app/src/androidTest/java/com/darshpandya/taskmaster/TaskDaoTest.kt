package com.darshpandya.taskmaster

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import com.darshpandya.taskmaster.data.TaskDao
import com.darshpandya.taskmaster.data.TaskDatabase
import com.darshpandya.taskmaster.data.TaskFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private lateinit var db: TaskDatabase
    private lateinit var dao: TaskDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, TaskDatabase::class.java).allowMainThreadQueries().build()
        dao = db.taskDao()
    }

    @After fun tearDown() = db.close()

    private fun task(title: String, due: Long? = null, done: Boolean = false, notes: String = "", created: Long = 0) =
        Task(title = title, notes = notes, dueAt = due, completed = done, createdAt = created, updatedAt = created)

    private fun observe(text: String = "", filter: TaskFilter = TaskFilter.ALL) =
        dao.observe(text, filter.ordinal).awaitValue().map { it.title }

    @Test fun insertAssignsIdsAndGetByIdReadsBack() = runTest {
        val id = dao.insert(task("Write report", due = 5000, notes = "numbers").copy(priority = Priority.HIGH))
        val stored = dao.getById(id)!!
        assertEquals("Write report", stored.title)
        assertEquals("numbers", stored.notes)
        assertEquals(5000L, stored.dueAt)
        assertEquals(Priority.HIGH, stored.priority)
        assertTrue(dao.insert(task("Second")) > id)
    }

    @Test fun updateAndDelete() = runTest {
        val id = dao.insert(task("Draft"))
        assertEquals(1, dao.update(dao.getById(id)!!.copy(title = "Final")))
        assertEquals("Final", dao.getById(id)!!.title)
        assertEquals(1, dao.delete(dao.getById(id)!!))
        assertNull(dao.getById(id))
    }

    @Test fun orderingPutsOpenTasksFirstThenDueDateWithUndatedLast() = runTest {
        dao.insert(task("undated old", created = 1))
        dao.insert(task("undated new", created = 2))
        dao.insert(task("due later", due = 200))
        dao.insert(task("due soon", due = 100))
        dao.insert(task("done early", due = 50, done = true))
        assertEquals(listOf("due soon", "due later", "undated new", "undated old", "done early"), observe())
    }

    @Test fun searchMatchesTitleOrNotesIgnoringCase() = runTest {
        dao.insert(task("Buy groceries", notes = "Milk and eggs"))
        dao.insert(task("Call plumber", notes = "kitchen sink"))
        assertEquals(listOf("Buy groceries"), observe("milk"))
        assertEquals(listOf("Call plumber"), observe("PLUMB"))
        assertEquals(emptyList<String>(), observe("dentist"))
    }

    @Test fun filtersByCompletion() = runTest {
        dao.insert(task("open"))
        dao.insert(task("closed", done = true))
        assertEquals(listOf("open"), observe(filter = TaskFilter.ACTIVE))
        assertEquals(listOf("closed"), observe(filter = TaskFilter.COMPLETED))
        assertEquals(2, observe(filter = TaskFilter.ALL).size)
    }

    @Test fun setCompletedUpdatesFlagTimestampAndActiveCount() = runTest {
        val id = dao.insert(task("finish me"))
        dao.insert(task("other"))
        assertEquals(2, dao.observeActiveCount().awaitValue())
        assertEquals(1, dao.setCompleted(id, true, now = 777))
        val t = dao.getById(id)!!
        assertTrue(t.completed)
        assertEquals(777L, t.updatedAt)
        assertEquals(1, dao.observeActiveCount().awaitValue())
    }

    @Test fun liveDataEmitsAfterWrites() = runTest {
        val live = dao.observe("", 0)
        assertEquals(0, live.awaitValue().size)
        dao.insert(task("appears"))
        assertEquals(listOf("appears"), live.awaitValue { it.isNotEmpty() }.map { it.title })
    }

    @Test fun priorityIsStoredAsItsName() = runTest {
        dao.insert(task("p").copy(priority = Priority.LOW))
        db.openHelper.readableDatabase.query("SELECT priority FROM tasks").use { c ->
            c.moveToFirst()
            assertEquals("LOW", c.getString(0))
        }
    }

    @Test fun replaceAllSwapsContents() = runTest {
        dao.insert(task("old"))
        dao.replaceAll(listOf(task("new 1").copy(id = 10), task("new 2").copy(id = 11)))
        assertEquals(listOf(10L, 11L), dao.getAll().map { it.id })
    }

    @Test fun replaceAllRollsBackWhenAnInsertFails() = runTest {
        dao.insert(task("survivor"))
        try {
            dao.replaceAll(listOf(task("a").copy(id = 5), task("b").copy(id = 5)))
            fail("expected a constraint failure")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
        }
        assertEquals(listOf("survivor"), dao.getAll().map { it.title })
    }
}
