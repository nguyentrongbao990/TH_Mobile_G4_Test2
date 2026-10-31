# Shared project contract

Student Study Schedule Management and Booking Application.

## Coding rules

- Application package: `com.example.studyschedule`.
- Use Java application code, XML layouts, SQLiteOpenHelper for storage, and SharedPreferences for session/preferences when implemented.
- Gradle uses Kotlin DSL; this does not authorize Kotlin application code.
- Do not use Jetpack Compose, Room, Firebase, or Kotlin application code.
- Preserve the existing Android project structure and implement only the assigned module scope.
- Do not rename, remove, or change public shared APIs, model fields, or agreed packages. Coordinate contract changes with Member 1 first.
- MainActivity.java and activity_main.xml belong to Member 3 and must remain untouched by other members.
- Only Member 1 edits AndroidManifest.xml and Gradle files. Other members must report required edits to Member 1.

## Ownership and packages

- Member 1: `com.example.studyschedule.model`, `.data`, `.auth`, `.studentadmin`, plus shared build/manifest configuration.
- Member 2: `com.example.studyschedule.subject`, `.schedule`; use `subject_` and `schedule_` prefixes for module XML files.
- Member 3: `MainActivity.java`, `res/layout/activity_main.xml`, `com.example.studyschedule.calendar`, `.notification`, and related XML UI.

Model/data, auth/studentadmin and schedule/calendar/notification modules now exist. Ownership rules above remain in force. docs/TV3_INTEGRATION.md reports a newer schedule/ assignment to Member 3; the team must confirm that reassignment before changing these ownership rules.

## Models

All models are in `com.example.studyschedule.model`. Each exposes a public zero-argument constructor, the full-field constructor below, and public Java getters/setters for every field. Getter/setter suffixes preserve field spelling with an uppercase first letter (for example `getFullname`, `setFullname`).

- `Account(long id, String username, String password, String role)`
- `Student(long id, String studentCode, String fullname, String email, String phone, long accountId)`
- `Subject(long id, String subjectCode, String subjectName, String description, int credits)`
- `StudySchedule(long id, long studentId, long subjectId, String studyDate, String startTime, String endTime, String location, String status)`

## Database

`com.example.studyschedule.data.DatabaseHelper extends SQLiteOpenHelper`.
Database file: `study_schedule.db`; version: `1`. Foreign keys enabled in `onConfigure`.
Field names match model names exactly; all IDs are SQLite INTEGER / Java long.

| Table | Fields and constraints |
| --- | --- |
| accounts | id INTEGER PRIMARY KEY AUTOINCREMENT; username TEXT NOT NULL UNIQUE; password TEXT NOT NULL; role TEXT NOT NULL |
| students | id INTEGER PRIMARY KEY AUTOINCREMENT; studentCode TEXT NOT NULL UNIQUE; fullname TEXT NOT NULL; email TEXT; phone TEXT; accountId INTEGER NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE |
| subjects | id INTEGER PRIMARY KEY AUTOINCREMENT; subjectCode TEXT NOT NULL UNIQUE; subjectName TEXT NOT NULL; description TEXT; credits INTEGER NOT NULL CHECK credits >= 0 |
| study_schedules | id INTEGER PRIMARY KEY AUTOINCREMENT; studentId INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE; subjectId INTEGER NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT; studyDate TEXT NOT NULL; startTime TEXT NOT NULL; endTime TEXT NOT NULL; location TEXT; status TEXT NOT NULL DEFAULT 'Pending' |

- Dates use `yyyy-MM-dd`; times use zero-padded 24-hour `HH:mm`, stored as TEXT. Schema checks date shape, valid hour/minute ranges, and startTime < endTime. DatabaseHelper validates real calendar dates, exact time formats and startTime < endTime before writes.
- Exact status values: `Pending`, `Confirmed`, `Cancelled`, enforced by CHECK.
- Each student has one unique account. Deleting an account cascades to its student and schedules. deleteStudent removes the linked account transactionally, cascading the student and schedules.
- Subjects referenced by schedules cannot be deleted (RESTRICT).
- Index: idx_schedule_student_date(studentId, studyDate).
- All authentication/CRUD/conflict APIs below are implemented with SQLite. Admin admin/admin123 is seeded only during initial database creation. Passwords use salted PBKDF2 hashing; plaintext passwords are never persisted. Mutation failures return -1/false; reads propagate database errors.
- Schedule operations scope by studentId. addStudent creates the account and student atomically. Overlap checks and writes share a transaction; Cancelled schedules do not block time, and reactivation checks conflicts again.
- Database version changes require explicit non-destructive migrations; onUpgrade currently fails explicitly rather than deleting data.

## Frozen public DatabaseHelper API

```java
public DatabaseHelper(Context context);
public Account authenticate(String username, String password);
public Student getStudentByAccountId(long accountId);
public long addStudent(Student student, String username, String password);
public boolean updateStudent(Student student);
public boolean deleteStudent(long studentId);
public List<Student> getAllStudents();
public List<Student> searchStudents(String keyword);
public long addSubject(Subject subject);
public boolean updateSubject(Subject subject);
public boolean deleteSubject(long subjectId);
public List<Subject> getAllSubjects();
public List<Subject> searchSubjects(String keyword);
public long addSchedule(StudySchedule schedule);
public boolean updateSchedule(StudySchedule schedule);
public boolean deleteSchedule(long scheduleId, long studentId);
public StudySchedule getScheduleById(long scheduleId, long studentId);
public List<StudySchedule> getSchedulesByStudent(long studentId);
public boolean hasScheduleConflict(long studentId, String date, String startTime, String endTime, long excludeScheduleId);
public boolean updateScheduleStatus(long scheduleId, long studentId, String status);
```

## Authentication integration

SessionManager in auth/ stores only accountId, username, role and linked studentId in SharedPreferences. Roles are exactly Admin and Student. Public SessionManager signatures and TV3 handoff are documented in auth/README.md.

LoginActivity is the launcher. Both fresh login and restored sessions route Admin to the existing StudentListActivity and Student to MainActivity. No new dashboard is created. SessionManager synchronizes StudentSession on save/restore, and clears the schedule session and its reminders on logout, invalid session or switching to Admin.

The manifest registers the notification permission, boot permission and reminder/restore receivers supplied by Member 3. Runtime notification permission remains requested by the schedule UI.

MainActivity now guards the session on resume, redirects Admin and exposes Student logout using SessionManager.logout(Activity), as supplied by the Member 3 branch.

## Integrated approval rules

- Student-facing ScheduleRepository creates/edits only Pending schedules. A Confirmed booking edited by Student is submitted as Pending for Admin review again. Forged Confirmed values are rejected by the repository, not just hidden by the UI.
- Student can cancel their own booking. A Cancelled booking cannot be edited/reactivated by Student; a new booking must be created.
- New or edited bookings must start in the future (device local time). Existing historical data remains readable/cancellable/deletable. Admin cannot confirm a past booking.
- Admin uses Student management → Duyệt lịch to Confirm or Cancel. ScheduleApprovalRepository verifies the account's Admin role in SQLite and delegates ownership/conflict-safe writes to the unchanged DatabaseHelper API.
- Generic DatabaseHelper remains role-neutral for Admin/fixture use. Student UI must use the account-bound ScheduleRepository; do not expose generic mutations directly to Student.
- Only Confirmed future bookings generate reminders. Session changes and notification delivery share a lock to prevent stale-account notifications after logout.
- Accepted UI writes/results use OperationViewModel across configuration changes; reopening a rotated form does not submit the write a second time. This is not a background job guarantee across process death.
- Admin navigation: StudentListActivity → SubjectListActivity (hosts TV2 AdminDashboardFragment), SubjectFormActivity and ScheduleApprovalActivity. Duplicate Student forms in TV2's original fragment are replaced by navigation to TV1's existing Student screen.
- Integration fixes across member-owned files in this change were explicitly authorized by the user; no public shared API or database schema/version was changed.
