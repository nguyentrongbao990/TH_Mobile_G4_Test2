package com.example.studyschedule;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AdminCrudTest {
    private Context context;
    private String prefix;
    @org.junit.Before public void isolateDatabase() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefix = "admin_crud_" + System.nanoTime() + "_";
        context = new android.content.ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public java.io.File getDatabasePath(String name) { return new java.io.File(target.getCacheDir(), prefix + name); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, android.database.DatabaseErrorHandler handler) {
                return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory, SQLiteDatabase.CREATE_IF_NECESSARY, handler);
            }
        };
    }
    @org.junit.After public void deleteFixtureDatabase() {
        SQLiteDatabase.deleteDatabase(context.getDatabasePath("study_schedule.db"));
    }
    @Test public void studentCreationRollsBackAndDeletionCascades() {
        String key = "test_" + System.nanoTime();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            {
                long id = helper.addStudent(new Student(0,key,"Test", "", "",0), key, "pass");
                assertTrue(id > 0);
                assertEquals(-1, helper.addStudent(new Student(0,key,"Duplicate", "", "",0), key + "other", "pass"));
                try (android.database.Cursor c = db.rawQuery("SELECT count(*) FROM accounts WHERE username = ?", new String[]{key + "other"})) {
                    assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0));
                }
                assertEquals(1,helper.searchStudents(key).size());
                assertTrue(helper.deleteStudent(id));
                try (android.database.Cursor c = db.rawQuery("SELECT count(*) FROM accounts WHERE username = ?", new String[]{key})) {
                    assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0));
                }
            }
        }
    }
    @Test public void subjectCrudAndReferencedSubjectRestriction() {
        String key = "test_" + System.nanoTime();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            {
                long subjectId = helper.addSubject(new Subject(0,key,"Subject","Description",3));
                assertTrue(subjectId > 0);
                assertTrue(helper.updateSubject(new Subject(subjectId,key,"Updated","",4)));
                assertEquals(4,helper.searchSubjects(key).get(0).getCredits());
                long studentId = helper.addStudent(new Student(0,key,"Test","","",0),key,"pass");
                db.execSQL("INSERT INTO study_schedules(studentId,subjectId,studyDate,startTime,endTime,status) VALUES(?,?,'2026-11-01','09:00','10:00','Pending')",new Object[]{studentId,subjectId});
                assertFalse(helper.deleteSubject(subjectId));
                assertTrue(helper.deleteStudent(studentId));
                assertTrue(helper.deleteSubject(subjectId));
            }
        }
    }
}
