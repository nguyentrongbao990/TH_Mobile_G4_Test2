# Member 1 integration

LoginActivity is the launcher. It restores a saved session only if the account identity/role and Student link still exist in SQLite. Fresh login and restored sessions route Admin to the existing StudentListActivity, and Student to MainActivity. No new dashboard is created. No passwords or hashes enter SharedPreferences.

MainActivity and its XML remain owned by Member 3. Add these calls there when integrating navigation:

```java
SessionManager session = new SessionManager(this);
// In onResume, after super.onResume():
if (!session.requireLogin(this)) return;
// MainActivity is currently the Student calendar. Redirect an Admin opening it directly:
if (session.isAdmin()) {
    startActivity(new Intent(this, StudentListActivity.class));
    finish();
    return;
}
// Logout button:
session.logout(this);
```

Imports: com.example.studyschedule.auth.SessionManager,
com.example.studyschedule.studentadmin.StudentListActivity, android.content.Intent.
Admin can reach StudentListActivity directly from login and use its logout button. No Member 3 UI file was edited. MainActivity must call requireLogin on resume to guard an existing task after session invalidation.

## Exact public SessionManager API

```java
public SessionManager(Context context);
public boolean saveSession(Account account);
public boolean isLoggedIn();
public long getAccountId();
public String getUsername();
public String getRole();
public long getStudentId();
public boolean isAdmin();
public void logout();
public void logout(Activity activity);
public boolean requireLogin(Activity activity);
public boolean requireAdmin(Activity activity);
```

saveSession is only for a successful DatabaseHelper.authenticate result. It checks the persisted account and resolves the Student link. Roles are exactly Admin/Student. IDs default to -1; Admin studentId is -1. Getters return saved values; requireLogin/isLoggedIn validate against SQLite. isAdmin also validates. Database errors propagate; callers should display a retry error if needed.
saveSession and isLoggedIn also synchronize the schedule module StudentSession using accountId, not studentId. Switching Student accounts replaces the bridge and clears old reminders; restoring a valid Student login repairs a missing bridge. Admin sessions have no Student bridge. Invalid sessions clear both preference stores.

logout() clears preferences and calls StudentSession.clear to cancel reminders and displayed notifications; logout(Activity) also clears the navigation task and opens LoginActivity. requireLogin redirects invalid sessions; requireAdmin additionally rejects Student accounts and finishes the protected screen.

StudentFormActivity accepts the optional long extra StudentFormActivity.EXTRA_STUDENT_ID to edit. Without it, it creates a student plus linked Student account using DatabaseHelper.addStudent. Editing updates only the profile; credential changes are outside the existing database API.

Demo Admin: admin / admin123, seeded by DatabaseHelper only at initial database creation. Existing version-1 databases created before the seed was implemented are not silently modified. Create Student logins through Student Management; no fake Student account is seeded.

AndroidManifest.xml includes POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, ScheduleReminderReceiver and ScheduleRestoreReceiver. Notification permission is requested by the existing schedule UI. Device delivery still requires runtime permission and real-device/emulator testing.

Member 3 remaining integration: requireLogin on MainActivity resume (including openings from a notification), redirect Admin to StudentListActivity, and a Student logout button calling session.logout(this). Member 1 does not edit MainActivity or its layout.

Tests: Member1SessionTest uses isolated database/preferences and covers Admin authentication, Student CRUD/session deletion, bridge repair on restore, Student switching, Admin switching, orphan bridge cleanup and logout. It never resets production app storage.


## Verification — 31 October 2026

`gradlew.bat assembleDebug testDebugUnitTest connectedDebugAndroidTest --no-configuration-cache --console=plain` completed successfully on Pixel_8 emulator (Android 17).

- 10 JVM unit tests passed.
- 12 device instrumentation tests passed (including 3 Member1SessionTest cases).
- Tests use isolated database/preferences. They cover authentication, student profile CRUD, duplicate rollback, session persistence/deletion, Student bridge restore/switch/logout and the existing schedule repository tests.
- Offline connected testing initially failed because the AGP 9.3 UTP artifact was not cached. The online retry downloaded the dependency and passed; no Gradle setting change was needed. AGP remains 9.3.0.
- Manual UI navigation, notification delivery/reboot and the future Student logout button were not exercised by this automated test run.


## Current integration update

TV2 branch is integrated. StudentListActivity now exposes Môn học and Duyệt lịch. Admin subject UI is hosted by SubjectListActivity; approvals are protected by ScheduleApprovalRepository. Student can only submit Pending or cancel their own booking; editing Confirmed requires Admin review again. Past start times are rejected for new/edited bookings. Only Confirmed bookings are scheduled for reminders.

Login, profile/subject saves, deletion and schedule writes use OperationViewModel to preserve accepted tasks/results across rotation. Admin lifecycle guards handle SQLite read failures with an error message. Details and navigation: app/ADMIN_INTEGRATION.md and PROJECT_CONTRACT.md.
