package com.darshpandya.taskmaster.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes and reads backup files through the Storage Access Framework. The user
 * picks the file with ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT, so it can
 * live in Downloads, on an SD card, or in any installed document provider.
 */
class DocumentBackupProvider(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BackupProvider {
    override val id = "document"
    override val displayName = "File (Storage Access Framework)"
    override val isAvailable = true
    override val unavailableReason: String? = null

    override suspend fun write(location: String, bytes: ByteArray) = withContext(io) {
        val out = resolver.openOutputStream(Uri.parse(location), "wt")
            ?: throw BackupException("Could not open $location for writing")
        out.use { it.write(bytes) }
    }

    override suspend fun read(location: String): ByteArray = withContext(io) {
        val input = resolver.openInputStream(Uri.parse(location))
            ?: throw BackupException("Could not open $location for reading")
        input.use { it.readBytes() }
    }
}
