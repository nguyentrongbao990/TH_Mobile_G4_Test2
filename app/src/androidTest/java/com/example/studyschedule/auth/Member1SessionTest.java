package com.example.studyschedule.auth;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Account;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.schedule.StudentSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

/** Runs against isolated test storage, never the installed app's live database/session. */
@RunWith(AndroidJUnit4.class)
public class Member1SessionTest {
    private Context context;
    private DatabaseHelper db;
    private SessionManager session;
    private String prefix;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefix = "member1_test_" + System.nanoTime() + "_";
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) {
                return new File(target.getCacheDir(), prefix + name);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory,
                        SQLiteDatabase.CREATE_IF_NECESSARY, handler);
            }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return target.getSharedPreferences(prefix + name, mode);
            }
        };
        db = new DatabaseHelper(context);
        session = new SessionManager(context);
    }

    @After public void tearDown() {
        db.close();
        SQLiteDatabase.deleteDatabase(context.getDatabasePath("study_schedule.db"));
        session.logout();
        for (String name : new String[]{"auth_session", "study_schedule_student_session", "schedule_notifications"}) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
            InstrumentationRegistry.getInstrumentation().getTargetContext().deleteSharedPreferences(prefix + name);
        }
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .deleteSharedPreferences(prefix + "auth_session");
    }

    @Test public void adminLoginRestoreAndLogout() {
        assertNull(db.authenticate("admin", "wrong"));
        Account admin = db.authenticate("admin", "admin123");
        assertNotNull(admin);
        assertNull(admin.getPassword());
        assertTrue(session.saveSession(admin));
        SessionManager restored = new SessionManager(context);
        assertTrue(restored.isLoggedIn());
        assertTrue(restored.isAdmin());
        assertEquals(admin.getId(), restored.getAccountId());
        assertEquals("admin", restored.getUsername());
        assertEquals("Admin", restored.getRole());
        assertEquals(-1L, restored.getStudentId());
        assertFalse(context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
                .getAll().toString().contains("admin123"));
        restored.logout();
        assertFalse(session.isLoggedIn());
        assertEquals(-1L, session.getAccountId());
    }

    @Test public void studentLinkCrudAndDeletedSession() {
        Student student = new Student(0, "ST01", "Test Student", "student@example.com", "0123456789", 0);
        long id = db.addStudent(student, "test_student", "initial123");
        assertTrue(id > 0);
        assertEquals(-1L, db.addStudent(student, "another_user", "initial123"));
        assertNull(db.authenticate("another_user", "initial123")); // Rollback removed account.
        Account account = db.authenticate("test_student", "initial123");
        assertNotNull(account);
        assertTrue(session.saveSession(account));
        assertTrue(session.isLoggedIn());
        assertFalse(session.isAdmin());
        assertEquals(id, session.getStudentId());
        assertEquals(account.getId(), StudentSession.accountId(context));
        Student linked = db.getStudentByAccountId(session.getAccountId());
        assertNotNull(linked);
        linked.setFullname("Updated Student");
        assertTrue(db.updateStudent(linked));
        assertEquals(1, db.searchStudents("Updated").size());
        assertTrue(db.deleteStudent(id));
        assertFalse(new SessionManager(context).isLoggedIn());
        assertEquals(-1L, StudentSession.accountId(context));
        assertNull(db.authenticate("test_student", "initial123"));
    }
    @Test public void scheduleBridgeRestoresSwitchesAndClears() {
        Student first = new Student(0, "FIRST", "First Student", "", "", 0);
        Student second = new Student(0, "SECOND", "Second Student", "", "", 0);
        assertTrue(db.addStudent(first, "first", "password1") > 0);
        assertTrue(db.addStudent(second, "second", "password2") > 0);
        Account a = db.authenticate("first", "password1");
        Account b = db.authenticate("second", "password2");
        assertTrue(session.saveSession(a));
        assertEquals(a.getId(), StudentSession.accountId(context));
        StudentSession.clear(context); // Simulate an older login without the schedule bridge.
        assertTrue(new SessionManager(context).isLoggedIn());
        assertEquals(a.getId(), StudentSession.accountId(context));
        assertTrue(session.saveSession(b));
        assertEquals(b.getId(), StudentSession.accountId(context));
        assertTrue(session.saveSession(db.authenticate("admin", "admin123")));
        assertEquals(-1L, StudentSession.accountId(context));
        assertTrue(session.saveSession(a));
        session.logout();
        assertFalse(session.isLoggedIn());
        assertEquals(-1L, StudentSession.accountId(context));
        // An orphaned bridge must be cleared even with no main login preferences.
        StudentSession.establish(context, b.getId());
        assertFalse(session.isLoggedIn());
        assertEquals(-1L, StudentSession.accountId(context));
    }
}
