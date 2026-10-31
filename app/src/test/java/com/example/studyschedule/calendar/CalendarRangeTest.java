package com.example.studyschedule.calendar;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class CalendarRangeTest {
    @Test public void weekStartsMondayAndCrossesYearBoundary() {
        LocalDate selected = LocalDate.of(2027,1,1);
        assertEquals(LocalDate.of(2026,12,28), CalendarRange.start(selected, CalendarRange.Mode.WEEK));
        assertEquals(LocalDate.of(2027,1,3), CalendarRange.end(selected, CalendarRange.Mode.WEEK));
        assertTrue(CalendarRange.contains(selected, CalendarRange.Mode.WEEK, "2026-12-31"));
        assertFalse(CalendarRange.contains(selected, CalendarRange.Mode.WEEK, "2027-01-04"));
    }
    @Test public void monthlyRangeHandlesLeapYear() {
        LocalDate selected = LocalDate.of(2028,2,10);
        assertEquals(LocalDate.of(2028,2,29), CalendarRange.end(selected, CalendarRange.Mode.MONTH));
        assertTrue(CalendarRange.contains(selected, CalendarRange.Mode.MONTH, "2028-02-01"));
        assertFalse(CalendarRange.contains(selected, CalendarRange.Mode.MONTH, "2028-03-01"));
    }
    @Test public void dailyAndAllRanges() {
        LocalDate selected = LocalDate.of(2026,10,31);
        assertTrue(CalendarRange.contains(selected, CalendarRange.Mode.DAY, "2026-10-31"));
        assertFalse(CalendarRange.contains(selected, CalendarRange.Mode.DAY, "2026-11-01"));
        assertTrue(CalendarRange.contains(selected, CalendarRange.Mode.ALL, "2020-01-01"));
        assertEquals(LocalDate.of(2026,11,7), CalendarRange.move(selected, CalendarRange.Mode.WEEK, 1));
        assertEquals(LocalDate.of(2026,11,30), CalendarRange.move(selected, CalendarRange.Mode.MONTH, 1));
    }
}
