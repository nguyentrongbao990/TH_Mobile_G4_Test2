package com.example.studyschedule.schedule;

import com.example.studyschedule.model.StudySchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

/** Pure Java rules, shared by the editor and the transactional repository. */
public final class ScheduleValidator {
    public static final String PENDING = "Pending";
    public static final String CONFIRMED = "Confirmed";
    public static final String CANCELLED = "Cancelled";
    public static final String[] STATUSES = {PENDING, CONFIRMED, CANCELLED};
    private ScheduleValidator() {}

    public static void validate(StudySchedule schedule) {
        if (schedule == null || schedule.getStudentId() <= 0 || schedule.getSubjectId() <= 0)
            throw new IllegalArgumentException("Vui lòng chọn sinh viên và môn học hợp lệ.");
        try {
            String date = schedule.getStudyDate(), start = schedule.getStartTime(), end = schedule.getEndTime();
            if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")
                    || start == null || !start.matches("\\d{2}:\\d{2}")
                    || end == null || !end.matches("\\d{2}:\\d{2}"))
                throw new IllegalArgumentException("Ngày phải là yyyy-MM-dd, giờ phải là HH:mm.");
            LocalDate.parse(date);
            if (!LocalTime.parse(start).isBefore(LocalTime.parse(end)))
                throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ngày hoặc giờ không hợp lệ.");
        }
        if (schedule.getLocation() == null || schedule.getLocation().trim().isEmpty())
            throw new IllegalArgumentException("Vui lòng nhập địa điểm học.");
        if (!Arrays.asList(STATUSES).contains(schedule.getStatus()))
            throw new IllegalArgumentException("Trạng thái lịch học không hợp lệ.");
    }

    public static boolean overlaps(String start, String end, String otherStart, String otherEnd) {
        return start.compareTo(otherEnd) < 0 && end.compareTo(otherStart) > 0;
    }

    public static String statusLabel(String status) {
        if (CONFIRMED.equals(status)) return "Đã xác nhận";
        if (CANCELLED.equals(status)) return "Đã hủy";
        return "Chờ xác nhận";
    }
}
