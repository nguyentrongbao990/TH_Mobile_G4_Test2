package com.example.studyschedule.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.studyschedule.model.Account;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;

import java.util.Collections;
import java.util.List;

/** Shared schema foundation. CRUD and authentication methods are TODO placeholders. */
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: Add explicit non-destructive migrations before increasing DATABASE_VERSION.
        throw new UnsupportedOperationException("No database migration defined from "
                + oldVersion + " to " + newVersion);
    }

    public Account authenticate(String username, String password) {
        // TODO: Implement authenticate; this placeholder performs no database operation.
        return null;
    }

    public Student getStudentByAccountId(long accountId) {
        // TODO: Implement getStudentByAccountId; this placeholder performs no database operation.
        return null;
    }

    public long addStudent(Student student, String username, String password) {
        // TODO: Implement addStudent; this placeholder performs no database operation.
        return -1;
    }

    public boolean updateStudent(Student student) {
        // TODO: Implement updateStudent; this placeholder performs no database operation.
        return false;
    }

    public boolean deleteStudent(long studentId) {
        // TODO: Implement deleteStudent; this placeholder performs no database operation.
        return false;
    }

    public List<Student> getAllStudents() {
        // TODO: Implement getAllStudents; this placeholder performs no database operation.
        return Collections.emptyList();
    }

    public List<Student> searchStudents(String keyword) {
        // TODO: Implement searchStudents; this placeholder performs no database operation.
        return Collections.emptyList();
    }

    public long addSubject(Subject subject) {
        // TODO: Implement addSubject; this placeholder performs no database operation.
        return -1;
    }

    public boolean updateSubject(Subject subject) {
        // TODO: Implement updateSubject; this placeholder performs no database operation.
        return false;
    }

    public boolean deleteSubject(long subjectId) {
        // TODO: Implement deleteSubject; this placeholder performs no database operation.
        return false;
    }

    public List<Subject> getAllSubjects() {
        // TODO: Implement getAllSubjects; this placeholder performs no database operation.
        return Collections.emptyList();
    }

    public List<Subject> searchSubjects(String keyword) {
        // TODO: Implement searchSubjects; this placeholder performs no database operation.
        return Collections.emptyList();
    }

    public long addSchedule(StudySchedule schedule) {
        // TODO: Implement addSchedule; this placeholder performs no database operation.
        return -1;
    }

    public boolean updateSchedule(StudySchedule schedule) {
        // TODO: Implement updateSchedule; this placeholder performs no database operation.
        return false;
    }

    public boolean deleteSchedule(long scheduleId, long studentId) {
        // TODO: Implement deleteSchedule; this placeholder performs no database operation.
        return false;
    }

    public StudySchedule getScheduleById(long scheduleId, long studentId) {
        // TODO: Implement getScheduleById; this placeholder performs no database operation.
        return null;
    }

    public List<StudySchedule> getSchedulesByStudent(long studentId) {
        // TODO: Implement getSchedulesByStudent; this placeholder performs no database operation.
        return Collections.emptyList();
    }

    public boolean hasScheduleConflict(long studentId, String date, String startTime, String endTime, long excludeScheduleId) {
        // TODO: Implement hasScheduleConflict; this placeholder performs no database operation.
        return false;
    }

    public boolean updateScheduleStatus(long scheduleId, long studentId, String status) {
        // TODO: Implement updateScheduleStatus; this placeholder performs no database operation.
        return false;
    }
}
