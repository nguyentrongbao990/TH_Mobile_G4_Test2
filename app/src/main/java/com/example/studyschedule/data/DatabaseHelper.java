package com.example.studyschedule.data;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.studyschedule.model.Account;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;

import java.util.ArrayList;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import android.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.List;

/** Shared SQLite store. Mutations return -1/false for invalid input or constraint violations.
 * Read failures propagate SQLite exceptions rather than masquerading as empty results.
 * studentId is a caller-supplied ownership scope; callers must use the authenticated student's ID.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "study_schedule.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL UNIQUE, password TEXT NOT NULL, role TEXT NOT NULL)");
        db.execSQL("CREATE TABLE students ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, studentCode TEXT NOT NULL UNIQUE, "
                + "fullname TEXT NOT NULL, email TEXT, phone TEXT, accountId INTEGER NOT NULL UNIQUE, "
                + "FOREIGN KEY (accountId) REFERENCES accounts(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE subjects ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, subjectCode TEXT NOT NULL UNIQUE, "
                + "subjectName TEXT NOT NULL, description TEXT, "
                + "credits INTEGER NOT NULL CHECK (credits >= 0))");
        db.execSQL("CREATE TABLE study_schedules ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, studentId INTEGER NOT NULL, "
                + "subjectId INTEGER NOT NULL, "
                + "studyDate TEXT NOT NULL CHECK (studyDate GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'), "
                + "startTime TEXT NOT NULL CHECK (startTime GLOB '[0-2][0-9]:[0-5][0-9]' AND startTime < '24:00'), "
                + "endTime TEXT NOT NULL CHECK (endTime GLOB '[0-2][0-9]:[0-5][0-9]' AND endTime < '24:00'), "
                + "location TEXT, status TEXT NOT NULL DEFAULT 'Pending' "
                + "CHECK (status IN ('Pending', 'Confirmed', 'Cancelled')), "
                + "CHECK (startTime < endTime), "
                + "FOREIGN KEY (studentId) REFERENCES students(id) ON DELETE CASCADE, "
                + "FOREIGN KEY (subjectId) REFERENCES subjects(id) ON DELETE RESTRICT)");
        db.execSQL("CREATE INDEX idx_schedule_student_date ON study_schedules(studentId, studyDate)");
        ContentValues admin = new ContentValues();
        admin.put("username", "admin");
        admin.put("password", hashPassword("admin123"));
        admin.put("role", "Admin");
        db.insertOrThrow("accounts", null, admin);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 has no predecessor; future versions must define non-destructive migrations.
        throw new UnsupportedOperationException("No database migration defined from "
                + oldVersion + " to " + newVersion);
    }

    public Account authenticate(String username, String password) {
        if (blank(username) || blank(password)) return null;
        try (Cursor c = getReadableDatabase().query("accounts", null, "username=?",
                new String[]{username.trim()}, null, null, null)) {
            if (!c.moveToFirst() || !verifyPassword(password, string(c, "password"))) return null;
            // Never expose password material through the returned session account.
            return new Account(number(c, "id"), string(c, "username"), null, string(c, "role"));
        }
    }

    public Student getStudentByAccountId(long accountId) {
        try (Cursor c = getReadableDatabase().query("students", null, "accountId=?",
                args(accountId), null, null, null)) {
            return c.moveToFirst() ? student(c) : null;
        }
    }

    public long addStudent(Student student, String username, String password) {
        if (!validStudent(student) || blank(username) || blank(password)) return -1;
        String hash = hashPassword(password);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues account = new ContentValues();
            account.put("username", username.trim());
            account.put("password", hash);
            account.put("role", "Student");
            long accountId = db.insertOrThrow("accounts", null, account);
            ContentValues values = studentValues(student);
            values.put("accountId", accountId);
            long id = db.insertOrThrow("students", null, values);
            db.setTransactionSuccessful();
            return id;
        } catch (SQLiteConstraintException e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    public boolean updateStudent(Student student) {
        if (!validStudent(student) || student.getId() <= 0) return false;
        try {
            // Account links are immutable through profile updates.
            return getWritableDatabase().update("students", studentValues(student), "id=?",
                    args(student.getId())) == 1;
        } catch (SQLiteConstraintException e) {
            return false;
        }
    }

    public boolean deleteStudent(long studentId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long accountId;
            try (Cursor c = db.query("students", new String[]{"accountId"}, "id=?",
                    args(studentId), null, null, null)) {
                if (!c.moveToFirst()) return false;
                accountId = number(c, "accountId");
            }
            // Deleting the student's account cascades to the student and all their bookings.
            if (db.delete("accounts", "id=?", args(accountId)) != 1) return false;
            db.setTransactionSuccessful();
            return true;
        } catch (SQLiteConstraintException e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public List<Student> getAllStudents() {
        return queryStudents(null, null);
    }

    public List<Student> searchStudents(String keyword) {
        String term = searchTerm(keyword);
        return queryStudents("studentCode LIKE ? ESCAPE '\\' OR fullname LIKE ? ESCAPE '\\' "
                + "OR email LIKE ? ESCAPE '\\' OR phone LIKE ? ESCAPE '\\'",
                new String[]{term, term, term, term});
    }

    public long addSubject(Subject subject) {
        if (!validSubject(subject)) return -1;
        try {
            return getWritableDatabase().insertOrThrow("subjects", null, subjectValues(subject));
        } catch (SQLiteConstraintException e) {
            return -1;
        }
    }

    public boolean updateSubject(Subject subject) {
        if (!validSubject(subject) || subject.getId() <= 0) return false;
        try {
            return getWritableDatabase().update("subjects", subjectValues(subject), "id=?",
                    args(subject.getId())) == 1;
        } catch (SQLiteConstraintException e) {
            return false;
        }
    }

    public boolean deleteSubject(long subjectId) {
        try {
            // RESTRICT preserves subjects referenced by any schedule, including cancelled ones.
            return getWritableDatabase().delete("subjects", "id=?", args(subjectId)) == 1;
        } catch (SQLiteConstraintException e) {
            return false;
        }
    }

    public List<Subject> getAllSubjects() {
        return querySubjects(null, null);
    }

    public List<Subject> searchSubjects(String keyword) {
        String term = searchTerm(keyword);
        return querySubjects("subjectCode LIKE ? ESCAPE '\\' OR subjectName LIKE ? ESCAPE '\\' "
                + "OR description LIKE ? ESCAPE '\\'", new String[]{term, term, term});
    }

    public long addSchedule(StudySchedule schedule) {
        if (!validSchedule(schedule)) return -1;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (!"Cancelled".equals(schedule.getStatus()) && conflict(db, schedule.getStudentId(),
                    schedule.getStudyDate(), schedule.getStartTime(), schedule.getEndTime(), -1)) return -1;
            long id = db.insertOrThrow("study_schedules", null, scheduleValues(schedule));
            db.setTransactionSuccessful();
            return id;
        } catch (SQLiteConstraintException e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    public boolean updateSchedule(StudySchedule schedule) {
        if (!validSchedule(schedule) || schedule.getId() <= 0) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            StudySchedule current = getScheduleById(schedule.getId(), schedule.getStudentId());
            if (current == null || "Confirmed".equals(current.getStatus())) return false;
            if (!"Cancelled".equals(schedule.getStatus()) && conflict(db, schedule.getStudentId(),
                    schedule.getStudyDate(), schedule.getStartTime(), schedule.getEndTime(),
                    schedule.getId())) return false;
            ContentValues values = scheduleValues(schedule);
            values.remove("studentId"); // A booking cannot be reassigned to another student.
            if (db.update("study_schedules", values, "id=? AND studentId=?",
                    args(schedule.getId(), schedule.getStudentId())) != 1) return false;
            db.setTransactionSuccessful();
            return true;
        } catch (SQLiteConstraintException e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean deleteSchedule(long scheduleId, long studentId) {
        return getWritableDatabase().delete("study_schedules", "id=? AND studentId=? AND status<>'Confirmed'",
                args(scheduleId, studentId)) == 1;
    }

    public StudySchedule getScheduleById(long scheduleId, long studentId) {
        try (Cursor c = getReadableDatabase().query("study_schedules", null,
                "id=? AND studentId=?", args(scheduleId, studentId), null, null, null)) {
            return c.moveToFirst() ? schedule(c) : null;
        }
    }

    public List<StudySchedule> getSchedulesByStudent(long studentId) {
        List<StudySchedule> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("study_schedules", null, "studentId=?",
                args(studentId), null, null, "studyDate, startTime, id")) {
            while (c.moveToNext()) result.add(schedule(c));
        }
        return result;
    }

    public boolean hasScheduleConflict(long studentId, String date, String startTime, String endTime, long excludeScheduleId) {
        if (studentId <= 0 || !validInterval(date, startTime, endTime)) {
            throw new IllegalArgumentException("Invalid student, date or time interval");
        }
        return conflict(getReadableDatabase(), studentId, date, startTime, endTime, excludeScheduleId);
    }

    public boolean updateScheduleStatus(long scheduleId, long studentId, String status) {
        if (!validStatus(status)) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            StudySchedule current = getScheduleById(scheduleId, studentId);
            if (current == null || "Confirmed".equals(current.getStatus())) return false;
            if (!"Cancelled".equals(status) && conflict(db, studentId, current.getStudyDate(),
                    current.getStartTime(), current.getEndTime(), scheduleId)) return false;
            ContentValues values = new ContentValues();
            values.put("status", status);
            if (db.update("study_schedules", values, "id=? AND studentId=?",
                    args(scheduleId, studentId)) != 1) return false;
            db.setTransactionSuccessful();
            return true;
        } catch (SQLiteConstraintException e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private static boolean conflict(SQLiteDatabase db, long studentId, String date,
                                    String start, String end, long excludeId) {
        try (Cursor c = db.query("study_schedules", new String[]{"id"},
                "studentId=? AND studyDate=? AND status<>? AND startTime<? AND endTime>? AND id<>?",
                new String[]{Long.toString(studentId), date, "Cancelled", end, start,
                        Long.toString(excludeId)}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    private List<Student> queryStudents(String selection, String[] arguments) {
        List<Student> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("students", null, selection, arguments,
                null, null, "fullname COLLATE NOCASE, id")) {
            while (c.moveToNext()) result.add(student(c));
        }
        return result;
    }

    private List<Subject> querySubjects(String selection, String[] arguments) {
        List<Subject> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("subjects", null, selection, arguments,
                null, null, "subjectName COLLATE NOCASE, id")) {
            while (c.moveToNext()) result.add(new Subject(number(c, "id"), string(c, "subjectCode"),
                    string(c, "subjectName"), string(c, "description"), c.getInt(c.getColumnIndexOrThrow("credits"))));
        }
        return result;
    }

    private static Student student(Cursor c) {
        return new Student(number(c, "id"), string(c, "studentCode"), string(c, "fullname"),
                string(c, "email"), string(c, "phone"), number(c, "accountId"));
    }

    private static StudySchedule schedule(Cursor c) {
        return new StudySchedule(number(c, "id"), number(c, "studentId"), number(c, "subjectId"),
                string(c, "studyDate"), string(c, "startTime"), string(c, "endTime"),
                string(c, "location"), string(c, "status"));
    }

    private static ContentValues studentValues(Student s) {
        ContentValues v = new ContentValues();
        v.put("studentCode", s.getStudentCode().trim());
        v.put("fullname", s.getFullname().trim());
        v.put("email", s.getEmail());
        v.put("phone", s.getPhone());
        return v;
    }

    private static ContentValues subjectValues(Subject s) {
        ContentValues v = new ContentValues();
        v.put("subjectCode", s.getSubjectCode().trim());
        v.put("subjectName", s.getSubjectName().trim());
        v.put("description", s.getDescription());
        v.put("credits", s.getCredits());
        return v;
    }

    private static ContentValues scheduleValues(StudySchedule s) {
        ContentValues v = new ContentValues();
        v.put("studentId", s.getStudentId());
        v.put("subjectId", s.getSubjectId());
        v.put("studyDate", s.getStudyDate());
        v.put("startTime", s.getStartTime());
        v.put("endTime", s.getEndTime());
        v.put("location", s.getLocation());
        v.put("status", s.getStatus());
        return v;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean validStudent(Student s) {
        return s != null && !blank(s.getStudentCode()) && !blank(s.getFullname());
    }

    private static boolean validSubject(Subject s) {
        return s != null && !blank(s.getSubjectCode()) && !blank(s.getSubjectName()) && s.getCredits() >= 0;
    }

    private static boolean validSchedule(StudySchedule s) {
        return s != null && s.getStudentId() > 0 && s.getSubjectId() > 0
                && validStatus(s.getStatus()) && validInterval(s.getStudyDate(), s.getStartTime(), s.getEndTime());
    }

    private static boolean validStatus(String status) {
        return "Pending".equals(status) || "Confirmed".equals(status) || "Cancelled".equals(status);
    }

    private static boolean validInterval(String date, String start, String end) {
        if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || date.startsWith("0000") || start == null || end == null
                || !start.matches("([01][0-9]|2[0-3]):[0-5][0-9]")
                || !end.matches("([01][0-9]|2[0-3]):[0-5][0-9]") || start.compareTo(end) >= 0) return false;
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String searchTerm(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String[] args(long... ids) {
        String[] values = new String[ids.length];
        for (int i = 0; i < ids.length; i++) values[i] = Long.toString(ids[i]);
        return values;
    }

    private static String string(Cursor c, String column) {
        return c.getString(c.getColumnIndexOrThrow(column));
    }

    private static long number(Cursor c, String column) {
        return c.getLong(c.getColumnIndexOrThrow(column));
    }

    private static final int HASH_ITERATIONS = 600000;

    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derivePassword(password, salt, HASH_ITERATIONS);
        return "pbkdf2_sha256$" + HASH_ITERATIONS + "$"
                + Base64.encodeToString(salt, Base64.NO_WRAP) + "$"
                + Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    private static byte[] derivePassword(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean verifyPassword(String password, String encoded) {
        if (encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100000 || iterations > 1000000) return false;
            byte[] salt = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[3], Base64.NO_WRAP);
            return salt.length == 16 && expected.length == 32
                    && MessageDigest.isEqual(expected, derivePassword(password, salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
