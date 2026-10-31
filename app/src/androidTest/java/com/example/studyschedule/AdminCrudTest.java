package com.example.studyschedule;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import java.io.File;
import org.junit.Before;
import org.junit.After;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AdminCrudTest {
    private Context context;
    @Before public void isolatedDatabase() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "admin_crud_" + System.nanoTime() + "_";
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return new File(target.getCacheDir(), prefix + name); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory,
                        SQLiteDatabase.CREATE_IF_NECESSARY, handler);
            }
        };
    }
    @After public void cleanDatabase() { SQLiteDatabase.deleteDatabase(context.getDatabasePath("study_schedule.db")); }

    @Test public void studentCreationRollsBackAndDeletionCascades() {
        String key = "test_" + System.nanoTime();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();

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
    @Test public void subjectCrudAndReferencedSubjectRestriction() {
        String key = "test_" + System.nanoTime();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();
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
    @Test public void sharedScheduleApiAlsoLocksConfirmedBookings() {
        String key = "lock_" + System.nanoTime();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();
                long subject = helper.addSubject(new Subject(0,key,"Lock test","",3));
                long student = helper.addStudent(new Student(0,key,"Lock test","","",0),key,"pass");
                StudySchedule booking = new StudySchedule(0,student,subject,"2028-05-10","09:00","10:00","A101","Pending");
                long id = helper.addSchedule(booking); assertTrue(id > 0);
                assertTrue(helper.updateScheduleStatus(id,student,"Confirmed"));
                booking.setId(id); booking.setLocation("Changed");
                assertFalse(helper.updateSchedule(booking));
                assertFalse(helper.updateScheduleStatus(id,student,"Pending"));
                assertFalse(helper.updateScheduleStatus(id,student,"Cancelled"));
                assertFalse(helper.deleteSchedule(id,student));
                StudySchedule saved = helper.getScheduleById(id,student);
                assertEquals("A101",saved.getLocation()); assertEquals("Confirmed",saved.getStatus());
        }
    }
}
