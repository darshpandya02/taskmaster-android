package com.darshpandya.taskmaster

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.darshpandya.taskmaster.backup.BackupCodec
import com.darshpandya.taskmaster.backup.BackupException
import com.darshpandya.taskmaster.backup.BackupManager
import com.darshpandya.taskmaster.backup.DriveBackupProvider
import com.darshpandya.taskmaster.data.DefaultTaskRepository
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.ui.BackupViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupManagerTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainDispatcherRule()

    private lateinit var clock: TestClock
    private lateinit var reminders: FakeReminderScheduler
    private lateinit var repo: DefaultTaskRepository
    private lateinit var manager: BackupManager
    private val provider = InMemoryBackupProvider()

    @Before fun setUp() {
        clock = TestClock()
        reminders = FakeReminderScheduler()
        repo = DefaultTaskRepository(FakeTaskDao(), reminders, clock)
        manager = BackupManager(repo, clock)
    }

    @Test fun backupWritesEveryTask() = runTest {
        repo.add("One", "", null, Priority.LOW)
        repo.add("Two", "", clock.now + 60_000, Priority.HIGH)
        assertEquals(2, manager.backup(provider, "file.json"))
        val decoded = BackupCodec.decode(provider.files.getValue("file.json"))
        assertEquals(listOf("One", "Two"), decoded.map { it.title })
    }

    @Test fun restoreReplacesTasksAndReschedulesReminders() = runTest {
        val keep = repo.add("Backed up", "", clock.now + 60_000, Priority.HIGH)
        manager.backup(provider, "b.json")
        repo.delete(repo.get(keep)!!)
        repo.add("Added after backup", "", null, Priority.LOW)

        assertEquals(1, manager.restore(provider, "b.json"))
        assertEquals(listOf("Backed up"), repo.getAll().map { it.title })
        assertEquals(setOf(keep), reminders.pending.keys)
    }

    @Test fun badFileLeavesTasksUntouched() = runTest {
        repo.add("Safe", "", null, Priority.LOW)
        provider.files["bad.json"] = "garbage".toByteArray()
        try {
            manager.restore(provider, "bad.json")
            fail("expected BackupException")
        } catch (_: BackupException) {
        }
        assertEquals(listOf("Safe"), repo.getAll().map { it.title })
    }

    @Test fun unavailableProviderIsRefused() = runTest {
        val off = InMemoryBackupProvider(isAvailable = false)
        try {
            manager.backup(off, "x")
            fail("expected BackupException")
        } catch (e: BackupException) {
            assertEquals("Memory provider switched off", e.message)
        }
        assertTrue(off.files.isEmpty())
    }

    @Test fun driveProviderIsDisabledWithoutCredentials() = runTest {
        val drive = DriveBackupProvider(clientId = "")
        assertFalse(drive.isAvailable)
        assertEquals("Google Drive backup is not configured in this build (no OAuth client).", drive.unavailableReason)
        try {
            manager.backup(drive, "tasks.json")
            fail("expected BackupException")
        } catch (e: BackupException) {
            assertEquals(drive.unavailableReason, e.message)
        }
    }

    @Test fun viewModelReportsCountsAndErrors() = runTest {
        repo.add("Only", "", null, Priority.LOW)
        val vm = BackupViewModel(manager)
        vm.export(provider, "vm.json")
        assertEquals("Exported 1 task", vm.messages.valueNow()!!.consume())
        assertEquals(false, vm.busy.valueNow())
        vm.import(provider, "vm.json")
        assertEquals("Imported 1 task", vm.messages.valueNow()!!.consume())
        vm.import(DriveBackupProvider(""), "x")
        assertEquals("Google Drive backup is not configured in this build (no OAuth client).", vm.messages.valueNow()!!.consume())
    }
}
