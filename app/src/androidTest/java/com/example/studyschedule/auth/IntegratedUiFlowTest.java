package com.example.studyschedule.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.studyschedule.R;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;
import com.example.studyschedule.notification.ScheduleNotifications;
import com.example.studyschedule.schedule.StudentSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** UI integration uses uniquely named disposable rows and restores the previous login afterwards. */
@RunWith(AndroidJUnit4.class)
public class IntegratedUiFlowTest {
    private Context context;
    private DatabaseHelper db;
    private String key;
    private long adminStudent = -1, student = -1, booking = -1;
    private Map<String,?> auth, bridge;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        key = "UIT" + System.nanoTime();
        auth = context.getSharedPreferences("auth_session",0).getAll();
        bridge = context.getSharedPreferences("study_schedule_student_session",0).getAll();
        db = new DatabaseHelper(context);
        adminStudent = db.addStudent(new Student(0,key+"A","UI Admin","","",0),key+"admin","UiPass123");
        student = db.addStudent(new Student(0,key+"S","UI Student "+key,"","",0),key+"student","UiPass123");
        assertTrue(adminStudent > 0 && student > 0);
        db.getWritableDatabase().execSQL("UPDATE accounts SET role='Admin' WHERE id=(SELECT accountId FROM students WHERE id=?)",new Object[]{adminStudent});
        new SessionManager(context).logout();
        ActivityScenario.launch(new Intent(context,LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    @After public void cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)))
                activity.finish();
        });
        if (context != null) new SessionManager(context).logout();
        if (db != null) {
            if (student > 0) db.deleteStudent(student);
            if (adminStudent > 0) db.deleteStudent(adminStudent);
            if (key != null) for (Subject subject : db.searchSubjects(key)) db.deleteSubject(subject.getId());
            restore("auth_session",auth); restore("study_schedule_student_session",bridge);
            SessionManager previous = new SessionManager(context);
            if (previous.isLoggedIn() && "Student".equals(previous.getRole()))
                ScheduleNotifications.reconcile(context,previous.getAccountId(),db.getSchedulesByStudent(previous.getStudentId()));
            db.close();
        }
    }
    private void restore(String file, Map<String,?> values) {
        if (values == null) return;
        SharedPreferences.Editor e=context.getSharedPreferences(file,0).edit().clear();
        for (Map.Entry<String,?> entry:values.entrySet()) {
            Object v=entry.getValue();
            if (v instanceof Long) e.putLong(entry.getKey(),(Long)v);
            else if (v instanceof String) e.putString(entry.getKey(),(String)v);
            else if (v instanceof Boolean) e.putBoolean(entry.getKey(),(Boolean)v);
            else if (v instanceof Integer) e.putInt(entry.getKey(),(Integer)v);
        }
        e.commit();
    }
    private void awaitView(org.hamcrest.Matcher<android.view.View> view) {
        long until=SystemClock.uptimeMillis()+30000;
        Throwable last=null;
        do {
            try { onView(view).check(matches(isDisplayed())); return; }
            catch (RuntimeException|AssertionError e) { last=e; SystemClock.sleep(100); }
        } while(SystemClock.uptimeMillis()<until);
        throw new AssertionError("UI did not become ready",last);
    }
    private void awaitDb(Callable<Boolean> condition) throws Exception {
        long until=SystemClock.uptimeMillis()+15000;
        do { if(condition.call()) return; SystemClock.sleep(100); } while(SystemClock.uptimeMillis()<until);
        fail("Database state did not reach expected result");
    }
    private void login(String username) {
        awaitView(allOf(withId(R.id.auth_login),isEnabled()));
        onView(withId(R.id.auth_username)).perform(replaceText(username));
        onView(withId(R.id.auth_password)).perform(replaceText("UiPass123"),closeSoftKeyboard());
        onView(withId(R.id.auth_login)).perform(click());
    }
    @Test public void adminSubjectCrudApprovalThenStudentEditAndCancel() throws Exception {
        login(key+"admin");
        awaitView(withId(R.id.studentadmin_subjects));
        onView(withId(R.id.studentadmin_subjects)).perform(click());
        awaitView(withId(R.id.admin_add)); onView(withId(R.id.admin_add)).perform(click());
        awaitView(withId(R.id.subject_code));
        onView(withId(R.id.subject_code)).perform(replaceText(key+"CODE"));
        onView(withId(R.id.subject_name)).perform(replaceText(key+" Subject"));
        onView(withId(R.id.subject_credits)).perform(replaceText("3"),closeSoftKeyboard());
        onView(withId(R.id.subject_save)).perform(scrollTo(),click());
        awaitDb(() -> db.searchSubjects(key+"CODE").size()==1);
        awaitView(withText(containsString(key+" Subject")));
        onView(withText(containsString(key+" Subject"))).perform(click());
        onView(withText("Xem / Sửa")).perform(click());
        awaitView(withId(R.id.subject_credits));
        onView(withId(R.id.subject_credits)).perform(replaceText("4"),closeSoftKeyboard());
        onView(withId(R.id.subject_save)).perform(scrollTo(),click());
        awaitDb(() -> db.searchSubjects(key+"CODE").get(0).getCredits()==4);
        long subject=db.searchSubjects(key+"CODE").get(0).getId();
        booking=db.addSchedule(new StudySchedule(0,student,subject,LocalDate.now().plusDays(1).toString(),"09:00","10:00","UI initial","Pending"));
        assertTrue(booking>0);
        awaitView(withId(R.id.subject_back)); onView(withId(R.id.subject_back)).perform(click());
        awaitView(withId(R.id.studentadmin_approvals)); onView(withId(R.id.studentadmin_approvals)).perform(click());
        awaitView(withId(R.id.studentadmin_approval_list));
        onData(allOf(instanceOf(String.class),containsString("UI Student "+key)))
                .inAdapterView(withId(R.id.studentadmin_approval_list)).perform(click());
        onView(withText("Xác nhận (Confirmed)")).perform(click());
        awaitDb(() -> "Confirmed".equals(db.getScheduleById(booking,student).getStatus()));
        onView(withId(R.id.studentadmin_approval_back)).perform(click());
        awaitView(withId(R.id.studentadmin_logout)); onView(withId(R.id.studentadmin_logout)).perform(click());
        login(key+"student");
        awaitView(withId(R.id.schedule_create));
        String label=key+"CODE · "+key+" Subject";
        awaitView(withText(label)); onView(withText(label)).perform(scrollTo(),click());
        onView(withText("Sửa lịch (gửi duyệt lại)")).perform(click());
        awaitView(withId(R.id.schedule_editor_status));
        onView(withId(R.id.schedule_editor_status)).check(matches(not(isAssignableFrom(android.widget.Spinner.class))));
        onView(withId(R.id.schedule_editor_location)).perform(scrollTo(),replaceText("UI changed"),closeSoftKeyboard());
        onView(withText("Lưu lịch")).perform(click());
        awaitDb(() -> "Pending".equals(db.getScheduleById(booking,student).getStatus())
                && "UI changed".equals(db.getScheduleById(booking,student).getLocation()));
        awaitView(withText(label)); onView(withText(label)).perform(scrollTo(),click());
        onView(withText("Hủy lịch")).perform(click()); onView(withText("Đồng ý")).perform(click());
        awaitDb(() -> "Cancelled".equals(db.getScheduleById(booking,student).getStatus()));
        onView(withId(R.id.schedule_logout)).perform(click());
        awaitView(withId(R.id.auth_login));
        assertFalse(new SessionManager(context).isLoggedIn());
        assertEquals(-1L,StudentSession.accountId(context));
    }
}
