# Integration and regression fixes — 31 October 2026

Integrated branches:
- TV3: codex/member3-session-logout (03e7afa).
- TV2: codex/member2-admin-management (a06f264), merged into local main.

## Behavior

Admin login opens Student management. Môn học opens the integrated TV2 subject list with search/create/edit/delete. Duyệt lịch opens the Admin-only schedule approval screen. Student CRUD remains available. The sample-data loader and its button have been removed; the existing sample rows remain in the emulator database.

Student bookings are Pending on creation and edit. Confirmed cannot be supplied by a Student; editing an approved booking requires review again. Student can cancel but cannot reactivate a cancelled booking. Admin approval checks the persisted Admin role, conflicts and future start time. New/edited bookings in the past are rejected. Generic DatabaseHelper signatures/schema/database version are unchanged. Only Confirmed bookings are scheduled for reminders.

Notification delivery and Student session changes share a lock. Workers carry the originating accountId and recheck it atomically before posting, so logout/switch either removes an existing notification or prevents a stale one from posting.

OperationViewModel keeps accepted login/CRUD/schedule operations and their results across configuration changes. Forms do not issue a second write when recreated. Initial profile/subject loading tracks whether data had actually loaded before deciding to preserve restored fields. Admin lifecycle guards display a database-error message instead of propagating SQLite exceptions.

## Verification

Command: `gradlew.bat assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug --no-configuration-cache --offline --console=plain`.

Final run: BUILD SUCCESSFUL; 11 JVM tests and 23 instrumentation tests passed on Pixel_8 Android 17; Android Lint completed without errors. The UI test exercised Admin login, subject creation/edit, Admin approval, Student login/edit resetting Pending, cancellation and logout. Separate tests cover forbidden Student confirmation/reactivation, conflict/ownership rules, past times, retained operation/results, notification locking, fixture rollback and CRUD.

The TV2 CRUD test was changed to use an isolated disposable database and independent transactions. Its original extra transaction inspected a failed nested transaction before the outer transaction ended, producing a misleading rollback assertion. The UI test creates unique disposable rows, removes only its own rows, and restores the prior login preferences/session. Other database tests use isolated fixtures.

One intermediate run was externally interrupted by `am force-stop`; logcat showed a deployment force-stop rather than a Java crash. The complete subsequent run passed.

Scope limits: physical-device/reboot reminder delivery was not manually tested. Operation retention covers configuration changes, not guaranteed background completion across process death. Existing records are preserved; no migration rewrites historic statuses.

AGP remains 9.3.0. Implementation fixes have not been committed/pushed; the merge commit integrates the already-committed TV2 branch. Personal .idea files remain outside the work.


## Direct database verification and debug cleanup

At the user's request, sample data is now stored directly in the installed emulator's SQLite database. No database file or password hashes were exported to the workspace. The runtime seeder, sample-data markdown file, debug button/resource, seeder-only tests and temporary provisioning source were removed. ScheduleApprovalRepository remains the production Admin approval boundary. Regression/UI tests for real features remain.

Important test-run incident: the full Gradle connected-test runner uninstalled the target app and cleared its storage at cleanup. The app was reinstalled and the sample dataset recreated directly on-device afterwards using a one-time manual instrumentation invocation, without the Gradle uninstall stage. User-entered records or edits outside the defined samples had no backup and were not recovered. This limitation was reported to the user.

The final installed database contains 2 Student profiles/accounts (demo_sv01 and demo_sv02, password student123), 4 sample subjects and 8 sample schedules. New schedules use device-local tomorrow and the following three days, with Confirmed/Pending/Confirmed/Cancelled states per student. The initial Admin account is admin/admin123. Passwords use the existing salted hash implementation. Foreign keys, linked profiles, committed schedule counts and both Student logins were verified on-device. The one-time provisioning tool was removed after success, and only its separate test APK was uninstalled; the main app and database were left installed.

The final source passed assembleDebug, testDebugUnitTest, connectedDebugAndroidTest and lintDebug: 11 JVM tests and 20 device tests. No connected-test cleanup was run after the final on-device data provisioning. New installations or clearing app storage do not copy this emulator's sample rows; only the initial Admin seed is part of application initialization.
