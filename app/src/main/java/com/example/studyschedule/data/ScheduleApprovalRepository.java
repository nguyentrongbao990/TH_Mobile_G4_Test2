package com.example.studyschedule.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.studyschedule.model.StudySchedule;
import com.example.studyschedule.schedule.ScheduleValidator;
import java.util.ArrayList;
import java.util.List;

/** Admin-only approval boundary; keeps DatabaseHelper's frozen generic API unchanged. */
public final class ScheduleApprovalRepository {
    private final DatabaseHelper helper;
    private final long adminAccountId;
    public ScheduleApprovalRepository(DatabaseHelper helper, long adminAccountId) {
        this.helper = helper; this.adminAccountId = adminAccountId;
    }
    private void requireAdmin() {
        try (Cursor c = helper.getReadableDatabase().query("accounts", new String[]{"id"},
                "id=? AND role='Admin'", new String[]{Long.toString(adminAccountId)}, null, null, null)) {
            if (!c.moveToFirst()) throw new SecurityException("Chỉ Admin được duyệt lịch.");
        }
    }
    public List<StudySchedule> getAllSchedules() {
        requireAdmin();
        List<StudySchedule> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("study_schedules", new String[]{"id", "studentId"},
                null, null, null, null, "studyDate, startTime, id")) {
            while (c.moveToNext()) {
                StudySchedule s = helper.getScheduleById(c.getLong(0), c.getLong(1));
                if (s != null) result.add(s);
            }
        }
        return result;
    }
    public boolean setStatus(long scheduleId, String status) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            requireAdmin();
            if (!("Confirmed".equals(status) || "Cancelled".equals(status)))
                throw new IllegalArgumentException("Admin chỉ xác nhận hoặc hủy lịch.");
            long studentId;
            try (Cursor c = db.query("study_schedules", new String[]{"studentId"}, "id=?",
                    new String[]{Long.toString(scheduleId)}, null, null, null)) {
                if (!c.moveToFirst()) return false;
                studentId = c.getLong(0);
            }
            if ("Confirmed".equals(status)) ScheduleValidator.validateFuture(
                    helper.getScheduleById(scheduleId, studentId), java.time.Clock.systemDefaultZone());
            boolean updated = helper.updateScheduleStatus(scheduleId, studentId, status);
            if (updated) db.setTransactionSuccessful();
            return updated;
        } finally { db.endTransaction(); }
    }
}
