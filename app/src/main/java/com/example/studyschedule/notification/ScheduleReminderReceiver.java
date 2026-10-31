package com.example.studyschedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.studyschedule.model.StudySchedule;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.schedule.ScheduleRepository;
import com.example.studyschedule.schedule.ScheduleValidator;
import com.example.studyschedule.schedule.StudentSession;

public final class ScheduleReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long accountId = intent.getLongExtra("accountId", -1);
        if (accountId <= 0 || accountId != StudentSession.accountId(context)) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try (ScheduleRepository repository = new ScheduleRepository(context, accountId)) {
                StudySchedule schedule = repository.find(intent.getLongExtra("scheduleId", -1));
                if (StudentSession.accountId(context) != accountId || schedule == null
                        || ScheduleValidator.CANCELLED.equals(schedule.getStatus())
                        || ScheduleNotifications.startsAt(schedule) <= System.currentTimeMillis()
                        || !ScheduleNotifications.fingerprint(schedule).equals(intent.getStringExtra("fingerprint"))) return;
                String subjectName = "Môn học";
                for (Subject subject : repository.subjects())
                    if (subject.getId() == schedule.getSubjectId()) { subjectName = subject.getSubjectName(); break; }
                if (ScheduleNotifications.allowed(context)) {
                    ScheduleNotifications.upcoming(context, schedule, subjectName);
                    ScheduleNotifications.markDelivered(context, accountId, schedule);
                }
            } catch (RuntimeException e) { Log.e("ScheduleReminder", "Unable to deliver reminder", e); }
            finally { pending.finish(); }
        }, "schedule-reminder").start();
    }
}
