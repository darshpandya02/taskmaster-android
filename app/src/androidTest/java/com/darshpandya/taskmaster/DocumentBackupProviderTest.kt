package com.darshpandya.taskmaster

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshpandya.taskmaster.backup.BackupManager
import com.darshpandya.taskmaster.backup.DocumentBackupProvider
import com.darshpandya.taskmaster.data.Priority
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentBackupProviderTest {

    @Test fun exportThenImportThroughContentResolver() = runBlocking {
        val container = installTestContainer().first
        val repo = container.repository
        repo.add("Renew passport", "photos first", System.currentTimeMillis() + 86_400_000, Priority.HIGH)
        repo.add("Water plants", "", null, Priority.LOW)

        val file = File(app.cacheDir, "backup-test.json").apply { delete() }
        val uri = Uri.fromFile(file).toString()
        val provider = DocumentBackupProvider(app.contentResolver)
        val manager = BackupManager(repo)

        assertEquals(2, manager.backup(provider, uri))
        assertTrue(file.readText().contains("\"format\": \"taskmaster-backup\""))

        repo.getAll().forEach { repo.delete(it) }
        repo.add("Will be replaced", "", null, Priority.MEDIUM)

        assertEquals(2, manager.restore(provider, uri))
        assertEquals(listOf("Renew passport", "Water plants"), repo.getAll().map { it.title })
    }
}
