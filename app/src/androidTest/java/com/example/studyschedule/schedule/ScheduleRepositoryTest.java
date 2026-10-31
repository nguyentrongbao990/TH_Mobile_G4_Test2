package com.example.studyschedule.schedule;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.StudySchedule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real SQLite tests; in-memory fixtures never write the app's database or login preferences. */
@RunWith(AndroidJUnit4.class)
public class ScheduleRepositoryTest {
    private SQLiteDatabase db;
    private ScheduleRepository first, second;
    @Before public void setup() {
        db = SQLiteDatabase.create(null); db.setForeignKeyConstraintsEnabled(true);
        try (DatabaseHelper helper = new DatabaseHelper(InstrumentationRegistry.getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        // TV1 seeds admin/admin123 in onCreate. Use distinct IDs/usernames and retain that seed.
        db.execSQL("INSERT INTO accounts(id,username,password,role) VALUES(101,'s1','fixture','Student'),(102,'s2','fixture','Student'),(103,'fixture_admin','fixture','Admin')");
        db.execSQL("INSERT INTO students(id,studentCode,fullname,accountId) VALUES(10,'SV01','First',101),(20,'SV02','Second',102)");
        db.execSQL("INSERT INTO subjects(id,subjectCode,subjectName,credits) VALUES(1,'MOB','Android',3),(2,'DB','Database',3)");
        first = new ScheduleRepository(db,101); second = new ScheduleRepository(db,102);
    }
    @After public void close() { db.close(); }
    private StudySchedule schedule(long student, String start, String end) {
        return new StudySchedule(0,student,1,"2028-05-10",start,end,"A101","Pending");
    }
    @Test public void resolvesStudentIdFromAccountRatherThanAssumingSameId() {
        assertEquals(10, first.currentStudent().getId()); assertEquals(20, second.currentStudent().getId());
        assertEquals(2,first.subjects().size());
        assertNull(new ScheduleRepository(db,103).currentStudent());
        assertThrows(IllegalStateException.class, () -> new ScheduleRepository(db,103).schedules());
        assertThrows(IllegalStateException.class, () -> new ScheduleRepository(db,999).schedules());
    }
    @Test public void roundTripPersistsAllFieldsAndDelete() {
        StudySchedule s = schedule(10,"09:00","10:00"); long id = first.save(s);
        StudySchedule saved = first.find(id); assertNotNull(saved);
        assertEquals("A101",saved.getLocation()); assertEquals("2028-05-10",saved.getStudyDate());
        assertEquals("09:00",saved.getStartTime()); assertEquals("10:00",saved.getEndTime());
        saved.setLocation("Thư viện"); saved.setSubjectId(2); first.save(saved);
        assertEquals("Thư viện",first.find(id).getLocation()); assertEquals(2,first.find(id).getSubjectId());
        assertThrows(IllegalArgumentException.class, () -> first.changeStatus(id,"Confirmed"));
        assertEquals("Pending",first.find(id).getStatus());
        first.delete(id); assertTrue(first.schedules().isEmpty());
    }
    @Test public void rejectsOverlapsButAllowsAdjacentDifferentDaysAndStudents() {
        first.save(schedule(10,"09:00","10:00"));
        for (String[] time : new String[][]{{"09:30","10:30"},{"08:00","11:00"},{"09:15","09:45"},{"09:00","10:00"}})
            assertThrows(IllegalArgumentException.class, () -> first.save(schedule(10,time[0],time[1])));
        first.save(schedule(10,"10:00","11:00")); first.save(schedule(10,"08:00","09:00"));
        second.save(schedule(20,"09:00","10:00"));
        StudySchedule anotherDay = schedule(10,"09:00","10:00"); anotherDay.setStudyDate("2028-05-11"); first.save(anotherDay);
        assertEquals(4, first.schedules().size()); assertEquals(1,second.schedules().size());
    }
    @Test public void updateExcludesSelfAndConflictFailureLeavesOriginalUnchanged() {
        long id = first.save(schedule(10,"09:00","10:00"));
        StudySchedule s = first.find(id); s.setLocation("A102"); first.save(s);
        first.save(schedule(10,"10:00","11:00"));
        s.setEndTime("10:30");
        assertThrows(IllegalArgumentException.class, () -> first.save(s));
        assertEquals("10:00",first.find(id).getEndTime()); assertEquals("A102",first.find(id).getLocation());
    }
    @Test public void cancelledDoesNotBlockButReactivationChecksConflict() {
        long id = first.save(schedule(10,"09:00","10:00")); first.changeStatus(id,"Cancelled");
        long replacement = first.save(schedule(10,"09:30","10:30"));
        assertThrows(IllegalArgumentException.class, () -> first.changeStatus(id,"Confirmed"));
        assertEquals("Cancelled",first.find(id).getStatus());
        first.delete(replacement);
        assertThrows(IllegalArgumentException.class, () -> first.changeStatus(id,"Pending"));
        assertEquals("Cancelled",first.find(id).getStatus());
    }
    @Test public void cannotReadModifyDeleteOrSpoofOtherStudentsSchedule() {
        long id = first.save(schedule(10,"09:00","10:00"));
        assertNull(second.find(id));
        assertThrows(IllegalArgumentException.class, () -> second.delete(id));
        assertThrows(IllegalArgumentException.class, () -> second.changeStatus(id,"Cancelled"));
        StudySchedule spoofed = schedule(20,"11:00","12:00"); spoofed.setId(id);
        assertThrows(IllegalArgumentException.class, () -> second.save(spoofed));
        assertThrows(IllegalArgumentException.class, () -> second.save(schedule(10,"11:00","12:00")));
        assertEquals(1,first.schedules().size());
    }
    @Test public void invalidDateStatusSubjectAndLocationNeverPersist() {
        StudySchedule s = schedule(10,"09:00","10:00"); s.setStudyDate("2027-02-29");
        assertThrows(IllegalArgumentException.class, () -> first.save(s));
        s.setStudyDate("2028-05-10"); s.setSubjectId(999);
        assertThrows(IllegalArgumentException.class, () -> first.save(s));
        s.setSubjectId(1); s.setLocation(" ");
        assertThrows(IllegalArgumentException.class, () -> first.save(s));
        s.setLocation("A101"); s.setStatus("Invalid");
        assertThrows(IllegalArgumentException.class, () -> first.save(s));
        assertTrue(first.schedules().isEmpty());
    }
    @Test public void listsInChronologicalOrder() {
        first.save(schedule(10,"15:00","16:00")); first.save(schedule(10,"08:00","09:00"));
        assertEquals("08:00",first.schedules().get(0).getStartTime());
    }
    @Test public void studentsCannotSelfConfirmOrModifyCancelledSchedule() {
        StudySchedule s = schedule(10,"09:00","10:00");
        s.setStatus("Confirmed");
        assertThrows(IllegalArgumentException.class, () -> first.save(s));
        s.setStatus("Pending");
        long id = first.save(s);
        db.execSQL("UPDATE study_schedules SET status='Confirmed' WHERE id=?", new Object[]{id});
        StudySchedule edit = first.find(id); edit.setStartTime("09:15");
        assertThrows(IllegalArgumentException.class, () -> first.save(edit));
        edit.setStatus("Pending"); first.save(edit);
        assertEquals("Pending",first.find(id).getStatus());
        first.changeStatus(id,"Cancelled");
        assertThrows(IllegalArgumentException.class, () -> first.save(edit));
        assertEquals("Cancelled",first.find(id).getStatus());
    }
    @Test public void newBookingsInThePastAreRejected() {
        StudySchedule past = schedule(10,"09:00","10:00");
        past.setStudyDate(java.time.LocalDate.now().minusDays(1).toString());
        assertThrows(IllegalArgumentException.class, () -> first.save(past));
        assertTrue(first.schedules().isEmpty());
    }
}
