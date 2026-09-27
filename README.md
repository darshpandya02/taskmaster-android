# TaskMaster (Android)

Rebuilt from scratch in 2026. The original 2022-23 project code was not preserved.

A task manager for Android in Kotlin: create, edit, complete, search and delete tasks,
get a notification when a task is due, and back the task list up to a file and restore it.

- Demo video, screenshots and test results: https://project-demos-gamma.vercel.app/taskmaster/
- APK: see [Releases](https://github.com/darshpandya02/taskmaster-android/releases)

## Stack: this rebuild vs. the resume

| | Original project (per resume) | This rebuild |
|---|---|---|
| Language | Kotlin | Kotlin 2.2 |
| Architecture | MVVM with ViewModel and LiveData | MVVM: `ViewModel` + `LiveData` (`switchMap` over the search/filter query), a repository, manual DI (`AppContainer`) |
| Storage | Room Persistence Library | Room 2.8 (KSP), one `tasks` table, schema exported to `app/schemas/` |
| List UI | RecyclerView | `RecyclerView` + `ListAdapter`/`DiffUtil`, swipe to delete with undo, Material 3 |
| Notifications | Notifications | WorkManager one-time job per task at its due time, a `reminders` notification channel, a "Mark done" action |
| Backup / sync | Google Drive API | `BackupProvider` interface. Working provider: JSON file export/import through the Storage Access Framework. **Google Drive: not active** (stub, see below) |
| Tests | Espresso UI tests | JVM unit tests, Room DAO instrumented tests, WorkManager/notification tests, Espresso UI tests |

### Google Drive backup is not active

Drive access needs an OAuth client registered in a Google Cloud project for this package
name and signing certificate. None is configured, so `DriveBackupProvider` reports itself
unavailable. The menu shows "Google Drive backup (not configured)" and the dialog explains
why. No Drive API code talks to Google. Backups go to a file the user picks (Downloads,
Drive's own document provider if installed, etc.) through `DocumentBackupProvider`.

A real Drive provider would implement the same `BackupProvider` interface
(`write(location, bytes)` / `read(location)`); `BackupManager` and the UI would not change.

## Features

- Task list ordered open-first, then by due date (undated last); overdue dates in red
- Filter chips (All / Active / Done) and a search box over title and notes
- Create and edit: title (required, 120 characters max), notes, due date and time, priority
- Tick to complete; swipe to delete with Undo; delete from the edit screen with confirmation
- Due reminders: scheduling is kept in step with every write (add, edit, complete, reopen,
  delete, undo, restore), so an open task with a future due date has exactly one pending job
- Backup: `Export backup…` writes versioned JSON (`taskmaster-backup`, version 1);
  `Import backup…` validates the file and replaces all tasks in one Room transaction

## Layout

```
app/src/main/java/com/darshpandya/taskmaster/
  data/       Task entity, TaskDao (Room), TaskDatabase, TaskRepository
  reminders/  ReminderScheduler, WorkManager scheduler, ReminderWorker, notification channel
  backup/     BackupProvider, DocumentBackupProvider, DriveBackupProvider (stub), BackupCodec, BackupManager
  ui/         MainActivity, EditTaskActivity, TaskAdapter, ViewModels
app/src/test/         JVM unit tests (fakes for the DAO and scheduler)
app/src/androidTest/  DAO, WorkManager, SAF and Espresso tests
scripts/demo.py       adb + uiautomator script that drove the app for the demo recording
                      (recorded with `adb shell screenrecord` on the emulator)
```

## Build and test

Requires JDK 17 and the Android SDK (compileSdk 36). minSdk 26.

```sh
./gradlew :app:testDebugUnitTest           # JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # needs a running emulator or device
./gradlew :app:assembleRelease
```

Results on 2026-09-26, Android 14 (API 34) arm64 emulator on an Apple M5 MacBook Air:

| Suite | Tests | Result |
|---|---|---|
| JVM unit tests (repository, ViewModels, backup codec/manager, date formatting) | 40 | 40 passed |
| Instrumented: Room DAO | 10 | 10 passed |
| Instrumented: ReminderWorker / WorkManager scheduler / notifications | 4 | 4 passed |
| Instrumented: SAF document backup round trip | 1 | 1 passed |
| Espresso UI flows | 10 | 10 passed |

One instrumented run had a failure in a notification test: `NotificationManager.notify()` is
handled asynchronously by the system, so the test now polls for the posted notification.
After that fix the 25 instrumented tests passed on three runs in a row.

GitHub Actions (`.github/workflows/ci.yml`) runs the unit tests and builds both APKs, and
runs the instrumented suite on an API 34 x86_64 emulator.

## Release signing

The release APK on the Releases page is signed with a throwaway keystore created for this
rebuild. The keystore and its passwords are not in the repository (`keystore.properties` and
`*.jks` are ignored). Without a `keystore.properties`, `assembleRelease` falls back to the
debug key, which is what CI does. Since the key is not a Play Store key, installing needs
"install unknown apps" to be allowed.
