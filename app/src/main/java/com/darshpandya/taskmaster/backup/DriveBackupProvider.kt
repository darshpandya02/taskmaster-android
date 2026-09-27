package com.darshpandya.taskmaster.backup

/**
 * Placeholder for Google Drive backup to the app data folder.
 *
 * Not active. Drive access needs an OAuth client registered in a Google Cloud
 * project for this package name and signing key, and none is configured. With an
 * empty [clientId] (the default build) the provider reports itself unavailable and
 * every call fails, so the UI shows the option greyed out with the reason.
 */
class DriveBackupProvider(private val clientId: String) : BackupProvider {
    override val id = "google-drive"
    override val displayName = "Google Drive"
    override val isAvailable: Boolean get() = false
    override val unavailableReason: String =
        if (clientId.isBlank()) "Google Drive backup is not configured in this build (no OAuth client)."
        else "Google Drive backup is not implemented in this build."

    override suspend fun write(location: String, bytes: ByteArray) {
        throw BackupException(unavailableReason)
    }

    override suspend fun read(location: String): ByteArray {
        throw BackupException(unavailableReason)
    }
}
