# kotlinx.serialization keeps its serializers through the plugin's own rules.
# Keep the backup model names stable so older backup files stay readable.
-keep class com.darshpandya.taskmaster.backup.BackupFile { *; }
-keep class com.darshpandya.taskmaster.backup.BackupTask { *; }
