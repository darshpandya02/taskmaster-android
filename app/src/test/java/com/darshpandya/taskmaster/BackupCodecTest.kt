package com.darshpandya.taskmaster

import com.darshpandya.taskmaster.backup.BackupCodec
import com.darshpandya.taskmaster.backup.BackupException
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val tasks = listOf(
        Task(1, "Write report", "numbers", 1_790_000_000_000, Priority.HIGH, false, 10, 20),
        Task(2, "Book flights", "", null, Priority.LOW, true, 30, 40),
    )

    private fun decode(json: String) = BackupCodec.decode(json.toByteArray())

    @Test fun roundTripKeepsEveryField() {
        val bytes = BackupCodec.encode(tasks, exportedAt = 99)
        assertEquals(tasks, BackupCodec.decode(bytes))
    }

    @Test fun encodedFileNamesFormatAndVersion() {
        val text = BackupCodec.encode(tasks, 99).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"format\": \"taskmaster-backup\""))
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"exportedAt\": 99"))
    }

    @Test fun emptyListRoundTrips() {
        assertEquals(emptyList<Task>(), BackupCodec.decode(BackupCodec.encode(emptyList(), 1)))
    }

    @Test fun rejectsInvalidJson() {
        assertThrows(BackupException::class.java) { decode("not json at all") }
    }

    @Test fun rejectsOtherJsonDocuments() {
        assertThrows(BackupException::class.java) { decode("""{"hello": "world"}""") }
        assertThrows(BackupException::class.java) {
            decode("""{"format":"something-else","version":1,"exportedAt":0,"tasks":[]}""")
        }
    }

    @Test fun rejectsNewerVersion() {
        val e = assertThrows(BackupException::class.java) {
            decode("""{"format":"taskmaster-backup","version":2,"exportedAt":0,"tasks":[]}""")
        }
        assertEquals("Backup version 2 is newer than this app supports", e.message)
    }

    @Test fun rejectsBlankTitleAndDuplicateIds() {
        assertThrows(BackupException::class.java) {
            decode("""{"format":"taskmaster-backup","version":1,"exportedAt":0,"tasks":[{"id":1,"title":" ","createdAt":0,"updatedAt":0}]}""")
        }
        assertThrows(BackupException::class.java) {
            decode(
                """{"format":"taskmaster-backup","version":1,"exportedAt":0,"tasks":[
                {"id":1,"title":"a","createdAt":0,"updatedAt":0},{"id":1,"title":"b","createdAt":0,"updatedAt":0}]}"""
            )
        }
    }

    @Test fun toleratesUnknownFieldsAndUnknownPriority() {
        val t = decode(
            """{"format":"taskmaster-backup","version":1,"exportedAt":0,"extra":true,
            "tasks":[{"id":5,"title":"x","priority":"URGENT","colour":"red","createdAt":1,"updatedAt":2}]}"""
        ).single()
        assertEquals(Priority.MEDIUM, t.priority)
        assertEquals("", t.notes)
        assertEquals(null, t.dueAt)
    }
}
