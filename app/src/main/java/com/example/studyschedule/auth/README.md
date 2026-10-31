# Member 1 integration

LoginActivity is the launcher. It restores a saved session only if the account identity/role and Student link still exist in SQLite. Login routes to the existing MainActivity for both roles. No passwords or hashes enter SharedPreferences.

MainActivity and its XML remain owned by Member 3. Add these calls there when integrating navigation:

```java
SessionManager session = new SessionManager(this);
// In onResume, after super.onResume():
if (!session.requireLogin(this)) return;
// Show student management only for Admin:
if (session.isAdmin()) {
    startActivity(new Intent(this, StudentListActivity.class));
}
// Logout button:
session.logout(this);
```

Imports: com.example.studyschedule.auth.SessionManager,
com.example.studyschedule.studentadmin.StudentListActivity, android.content.Intent.
No dashboard entry button was added to Member 3 files. StudentListActivity is ready for the above navigation and also has a working logout button. MainActivity must call requireLogin on resume to guard an existing task after session invalidation.

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
logout() clears preferences; logout(Activity) also clears the navigation task and opens LoginActivity. requireLogin redirects invalid sessions; requireAdmin additionally rejects Student accounts and finishes the protected screen.

StudentFormActivity accepts the optional long extra StudentFormActivity.EXTRA_STUDENT_ID to edit. Without it, it creates a student plus linked Student account using DatabaseHelper.addStudent. Editing updates only the profile; credential changes are outside the existing database API.

Demo Admin: admin / admin123, seeded by DatabaseHelper only at initial database creation. Existing version-1 databases created before the seed was implemented are not silently modified. Create Student logins through Student Management; no fake Student account is seeded.

The historical PROJECT_CONTRACT.md text describing database methods as placeholders is outdated; its public signatures are preserved and the existing DatabaseHelper implementation is used unchanged.
