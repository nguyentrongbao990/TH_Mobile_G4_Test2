package com.example.studyschedule.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.studyschedule.schedule.ScheduleRepository;
import com.example.studyschedule.schedule.StudentSession;

/** Restore alarms after reboot, app update, or a device time/timezone change. */
public final class ScheduleRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            long accountId = StudentSession.accountId(context);
            try {
                if (accountId <= 0) { ScheduleNotifications.cancelAll(context); return; }
                try (ScheduleRepository repository = new ScheduleRepository(context, accountId)) {
                    if (repository.currentStudent() != null && StudentSession.accountId(context) == accountId)
                        ScheduleNotifications.reconcile(context, accountId, repository.schedules());
                    else ScheduleNotifications.cancelAll(context);
                }
            } catch (RuntimeException e) { Log.e("ScheduleRestore", "Unable to restore reminders", e); }
            finally { pending.finish(); }
        }, "schedule-restore").start();
    }
}
