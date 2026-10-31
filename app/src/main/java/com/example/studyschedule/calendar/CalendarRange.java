package com.example.studyschedule.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class CalendarRange {
    public enum Mode { ALL, DAY, WEEK, MONTH }
    private CalendarRange() {}
    public static LocalDate start(LocalDate selected, Mode mode) {
        if (mode == Mode.WEEK) return selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (mode == Mode.MONTH) return selected.withDayOfMonth(1);
        return selected;
    }
    public static LocalDate end(LocalDate selected, Mode mode) {
        if (mode == Mode.WEEK) return start(selected, mode).plusDays(6);
        if (mode == Mode.MONTH) return selected.withDayOfMonth(selected.lengthOfMonth());
        return selected;
    }
    public static boolean contains(LocalDate selected, Mode mode, String date) {
        if (mode == Mode.ALL) return true;
        LocalDate value = LocalDate.parse(date);
        return !value.isBefore(start(selected, mode)) && !value.isAfter(end(selected, mode));
    }
    public static LocalDate move(LocalDate selected, Mode mode, int direction) {
        if (mode == Mode.MONTH) return selected.plusMonths(direction);
        return selected.plusDays(mode == Mode.WEEK ? direction * 7L : direction);
    }
}
