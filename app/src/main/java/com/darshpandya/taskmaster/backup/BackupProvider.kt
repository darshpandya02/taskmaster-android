package com.darshpandya.taskmaster.backup

/**
 * A place a backup can be written to and read from. [location] is provider
 * specific: a content URI for the document provider, a file name for Drive.
 */
interface BackupProvider {
    val id: String
    val displayName: String

    /** False when this provider cannot be used in this build (for example, missing credentials). */
    val isAvailable: Boolean

    /** Shown to the user when [isAvailable] is false. */
    val unavailableReason: String?

    suspend fun write(location: String, bytes: ByteArray)
    suspend fun read(location: String): ByteArray
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
