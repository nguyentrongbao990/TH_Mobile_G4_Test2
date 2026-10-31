# Shared project contract

Student Study Schedule Management and Booking Application.

## Coding rules

- Application package: `com.example.studyschedule`.
- Use Java application code, XML layouts, SQLiteOpenHelper for storage, and SharedPreferences for session/preferences when implemented.
- Gradle uses Kotlin DSL; this does not authorize Kotlin application code.
- Do not use Jetpack Compose, Room, Firebase, or Kotlin application code.
- Preserve the existing Android project structure. This task provides only the shared foundation.
- Do not rename, remove, or change public shared APIs, model fields, or agreed packages. Coordinate contract changes with Member 1 first.
- MainActivity.java and activity_main.xml belong to Member 3 and must remain untouched by other members.
- Only Member 1 edits AndroidManifest.xml and Gradle files. Other members must report required edits to Member 1.

## Ownership and packages

- Member 1: `com.example.studyschedule.model`, `.data`, `.auth`, `.studentadmin`, plus shared build/manifest configuration.
- Member 2: `com.example.studyschedule.subject`, `.schedule`; use `subject_` and `schedule_` prefixes for module XML files.
- Member 3: `MainActivity.java`, `res/layout/activity_main.xml`, `com.example.studyschedule.calendar`, `.notification`, and related XML UI.

Only model/ and data/ are created by this foundation; other feature packages are reserved.

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

- Dates use `yyyy-MM-dd`; times use zero-padded 24-hour `HH:mm`, stored as TEXT. Schema checks date shape, valid hour/minute ranges, and startTime < endTime. Full calendar-date validation remains a future implementation responsibility.
- Exact status values: `Pending`, `Confirmed`, `Cancelled`, enforced by CHECK.
- Each student has one unique account. Deleting an account cascades to its student and schedules. Deleting a student cascades schedules but does not automatically remove the account; future deleteStudent implementation must manage account cleanup transactionally.
- Subjects referenced by schedules cannot be deleted (RESTRICT).
- Index: idx_schedule_student_date(studentId, studyDate).
- Schema creation is implemented. All authentication/CRUD/conflict APIs below are explicitly TODO placeholders returning failure, null, or empty lists. No seeded accounts, authentication, session storage, or conflict detection is implemented. A false conflict result is a placeholder and must not be treated as validation.
- Future schedule operations must enforce student ownership, and addStudent must create account/student atomically. Do not wire booking flows to the placeholders as if operational.
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
