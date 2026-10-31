package com.example.studyschedule.schedule;

import com.example.studyschedule.model.StudySchedule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScheduleValidatorTest {
    private StudySchedule valid() { return new StudySchedule(0, 1, 1, "2028-02-29", "09:00", "10:00", "Thư viện", "Pending"); }
    @Test public void acceptsLeapDayAndAllStatuses() {
        for (String status : ScheduleValidator.STATUSES) {
            StudySchedule s = valid(); s.setStatus(status); ScheduleValidator.validate(s);
        }
    }
    @Test public void rejectsInvalidCalendarDate() {
        StudySchedule s = valid(); s.setStudyDate("2027-02-29");
        assertThrows(IllegalArgumentException.class, () -> ScheduleValidator.validate(s));
    }
    @Test public void rejectsMalformedAndOutOfRangeTimes() {
        for (String time : new String[]{"9:00", "24:00", "09:60", "garbage"}) {
            StudySchedule s = valid(); s.setStartTime(time);
            assertThrows(IllegalArgumentException.class, () -> ScheduleValidator.validate(s));
        }
    }
    @Test public void rejectsZeroDurationReversedAndOvernight() {
        for (String end : new String[]{"09:00", "08:00", "00:30"}) {
            StudySchedule s = valid(); s.setEndTime(end);
            assertThrows(IllegalArgumentException.class, () -> ScheduleValidator.validate(s));
        }
    }
    @Test public void rejectsMissingLocationInvalidStatusAndMissingOwner() {
        StudySchedule s = valid(); s.setLocation("   ");
        assertThrows(IllegalArgumentException.class, () -> ScheduleValidator.validate(s));
        s.setLocation("A101"); s.setStatus("confirmed");
        assertThrows(IllegalArgumentException.class, () -> ScheduleValidator.validate(s));
        s.setStatus("Confirmed"); s.setStudentId(-1);
        assertThrows(IllegalArgumentException.class, () -> ScheduleValidator.validate(s));
    }
    @Test public void overlapHandlesBoundariesContainmentAndIdenticalIntervals() {
        assertFalse(ScheduleValidator.overlaps("09:00","10:00","10:00","11:00"));
        assertFalse(ScheduleValidator.overlaps("09:00","10:00","08:00","09:00"));
        assertTrue(ScheduleValidator.overlaps("09:00","10:00","09:30","10:30"));
        assertTrue(ScheduleValidator.overlaps("09:00","12:00","10:00","11:00"));
        assertTrue(ScheduleValidator.overlaps("10:00","11:00","09:00","12:00"));
        assertTrue(ScheduleValidator.overlaps("09:00","10:00","09:00","10:00"));
    }
}
