package com.example.studyschedule.schedule;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;
import java.util.ArrayList;
import java.util.List;

/** TV3 persistence, using TV1's schema and an account-bound ownership scope.
 * One repository is bound to the authenticated account, not an arbitrary student ID.
 * Keep DB calls off the main thread. Close with the screen's background executor.
 */
public final class ScheduleRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final SQLiteDatabase db;
    private final long accountId;

    public ScheduleRepository(Context context, long authenticatedAccountId) {
        helper = new DatabaseHelper(context.getApplicationContext());
        db = helper.getWritableDatabase();
        accountId = authenticatedAccountId;
    }

    // In-memory database injection for instrumentation tests; caller owns the database.
    ScheduleRepository(SQLiteDatabase database, long authenticatedAccountId) {
        helper = null;
        db = database;
        accountId = authenticatedAccountId;
    }

    public Student currentStudent() {
        try (Cursor c = db.rawQuery("SELECT s.* FROM students s JOIN accounts a ON a.id=s.accountId "
                + "WHERE a.id=? AND a.role='Student'", new String[]{String.valueOf(accountId)})) {
            if (!c.moveToFirst()) return null;
            return new Student(number(c, "id"), string(c, "studentCode"), string(c, "fullname"),
                    string(c, "email"), string(c, "phone"), number(c, "accountId"));
        }
    }

    private long requireStudent() {
        Student student = currentStudent();
        if (student == null) throw new IllegalStateException("Phiên sinh viên không hợp lệ. Vui lòng đăng nhập lại.");
        return student.getId();
    }

    public List<Subject> subjects() {
        requireStudent();
        List<Subject> result = new ArrayList<>();
        try (Cursor c = db.query("subjects", null, null, null, null, null, "subjectName COLLATE NOCASE")) {
            while (c.moveToNext()) result.add(new Subject(number(c, "id"), string(c, "subjectCode"),
                    string(c, "subjectName"), string(c, "description"), c.getInt(c.getColumnIndexOrThrow("credits"))));
        }
        return result;
    }

    public List<StudySchedule> schedules() {
        long studentId = requireStudent();
        List<StudySchedule> result = new ArrayList<>();
        try (Cursor c = db.query("study_schedules", null, "studentId=?", args(studentId),
                null, null, "studyDate ASC, startTime ASC, id ASC")) {
            while (c.moveToNext()) result.add(read(c));
        }
        return result;
    }

    public StudySchedule find(long id) { return findOwned(id, requireStudent()); }

    private StudySchedule findOwned(long id, long studentId) {
        try (Cursor c = db.query("study_schedules", null, "id=? AND studentId=?", args(id, studentId), null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public long save(StudySchedule schedule) {
        db.beginTransaction();
        try {
            long studentId = requireStudent();
            if (schedule.getStudentId() != studentId) throw new IllegalArgumentException("Bạn chỉ được sửa lịch của mình.");
            if (schedule.getId() == 0) {
                // The student-facing booking API always creates a pending request.
                schedule.setStatus(ScheduleValidator.PENDING);
            } else {
                StudySchedule existing = findOwned(schedule.getId(), studentId);
                if (existing == null) throw new IllegalArgumentException("Lịch không tồn tại hoặc không thuộc về bạn.");
                if (!existing.getStatus().equals(schedule.getStatus()))
                    throw new IllegalArgumentException("Hãy dùng thao tác trạng thái riêng để thay đổi lịch.");
            }
            ScheduleValidator.validate(schedule);
            if (schedule.getId() < 0) throw new IllegalArgumentException("Mã lịch không hợp lệ.");
            if (schedule.getId() > 0) {
                StudySchedule current = findOwned(schedule.getId(), studentId);
                if (current == null)
                    throw new IllegalArgumentException("Lịch không tồn tại hoặc không thuộc về bạn.");
                if (ScheduleValidator.CONFIRMED.equals(current.getStatus()))
                    throw new IllegalArgumentException("Lịch đã được xác nhận, không thể sửa nội dung.");
            }
            try (Cursor c = db.query("subjects", new String[]{"id"}, "id=?", args(schedule.getSubjectId()), null, null, null)) {
                if (!c.moveToFirst()) throw new IllegalArgumentException("Môn học không còn tồn tại.");
            }
            if (!ScheduleValidator.CANCELLED.equals(schedule.getStatus()) && conflicts(schedule, studentId))
                throw new IllegalArgumentException("Trùng lịch: bạn đã có buổi học trong khoảng thời gian này.");
            ContentValues values = values(schedule);
            long id = schedule.getId();
            if (id == 0) id = db.insertOrThrow("study_schedules", null, values);
            else if (db.update("study_schedules", values, "id=? AND studentId=?", args(id, studentId)) != 1)
                throw new IllegalStateException("Không thể cập nhật lịch học.");
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    private boolean conflicts(StudySchedule schedule, long studentId) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM study_schedules WHERE studentId=? AND studyDate=? "
                + "AND status!='Cancelled' AND id!=? AND startTime<? AND endTime>? LIMIT 1",
                new String[]{String.valueOf(studentId), schedule.getStudyDate(), String.valueOf(schedule.getId()),
                        schedule.getEndTime(), schedule.getStartTime()})) {
            return c.moveToFirst();
        }
    }

    /** Students may cancel their own booking, but cannot confirm/reactivate a booking. */
    public void cancel(long id) {
        db.beginTransaction();
        try {
            StudySchedule schedule = find(id);
            if (schedule == null) throw new IllegalArgumentException("Không tìm thấy lịch của bạn.");
            schedule.setStatus(ScheduleValidator.CANCELLED);
            ContentValues values = new ContentValues();
            values.put("status", ScheduleValidator.CANCELLED);
            if (db.update("study_schedules", values, "id=? AND studentId=?", args(id, schedule.getStudentId())) != 1)
                throw new IllegalStateException("Không thể hủy lịch học.");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void delete(long id) {
        if (db.delete("study_schedules", "id=? AND studentId=? AND status<>'Confirmed'", args(id, requireStudent())) != 1)
            throw new IllegalArgumentException("Không thể xóa: lịch đã xác nhận, không tồn tại hoặc không thuộc về bạn.");
    }

    private static ContentValues values(StudySchedule s) {
        ContentValues v = new ContentValues();
        v.put("studentId", s.getStudentId()); v.put("subjectId", s.getSubjectId());
        v.put("studyDate", s.getStudyDate()); v.put("startTime", s.getStartTime()); v.put("endTime", s.getEndTime());
        v.put("location", s.getLocation().trim()); v.put("status", s.getStatus());
        return v;
    }
    private static StudySchedule read(Cursor c) {
        return new StudySchedule(number(c,"id"), number(c,"studentId"), number(c,"subjectId"),
                string(c,"studyDate"), string(c,"startTime"), string(c,"endTime"), string(c,"location"), string(c,"status"));
    }
    private static String string(Cursor c, String field) { return c.getString(c.getColumnIndexOrThrow(field)); }
    private static long number(Cursor c, String field) { return c.getLong(c.getColumnIndexOrThrow(field)); }
    private static String[] args(long... values) {
        String[] result = new String[values.length];
        for (int i=0; i<values.length; i++) result[i] = String.valueOf(values[i]);
        return result;
    }
    @Override public void close() { if (helper != null) helper.close(); }
}
