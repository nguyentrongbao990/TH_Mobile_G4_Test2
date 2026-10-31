# Member 2 â€” Admin Management

Implemented Java/XML UI: `com.example.studyschedule.studentadmin.AdminDashboardFragment`.
The fragment contains student and subject tabs, search, add/edit forms, lists, and confirmed deletion.
The host must validate an authenticated Admin session before showing the fragment. This module does not invent a session key or implement authentication.

## Integration for Member 1 / host owner

After authenticating Admin, show the fragment in the host's FragmentContainerView:

```java
getSupportFragmentManager().beginTransaction()
    .replace(R.id.your_fragment_container, new AdminDashboardFragment())
    .commit();
```

Replace the example container ID with the host layout's actual ID. No new activity, Manifest entry, or Gradle dependency is required. MainActivity and its layout were left untouched.

## Shared database implementation

After pulling Member 1 commit 73a444a, DatabaseHelper uses Member 1's implementation unchanged, including the frozen student/subject APIs.
Student creation inserts account (role Student) and student atomically. Student deletion removes its account, cascading student and schedule rows. Student updates preserve accountId. Subject deletion respects existing schedule references. Searches use bound parameters and escape LIKE wildcards.
Member 1's shared database now implements authentication, student lookup, schedule CRUD/conflict checks, and PBKDF2 password hashing. Session storage and login UI still require integration by the host owner.

## Verification

Run `gradlew.bat assembleDebug` from the project root.
With an Android emulator/device connected, run `gradlew.bat connectedDebugAndroidTest`. AdminCrudTest exercises account rollback, deletion cleanup, subject persistence, and foreign-key deletion restrictions within rolled-back test transactions.

Manual UI checks after host integration: create student with account; reject duplicate code/username; edit and search; create/edit subject; reject negative/invalid credits; reject deletion of a referenced subject; cancel and confirm student deletion; rotate and reopen screens.

The repository ownership documents still describe the previous assignment. The current user-approved assignment is Member 2 = Admin Management; coordinate documentation updates with Member 1.
