package com.example.studyschedule.notification;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.example.studyschedule.MainActivity;
import com.example.studyschedule.R;
import com.example.studyschedule.model.StudySchedule;
import com.example.studyschedule.schedule.ScheduleValidator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Inexact local reminders, without the special exact-alarm permission. */
public final class ScheduleNotifications {
    private static final String CHANNEL = "study_schedule_reminders";
    private static final String PREFS = "schedule_notifications";
    private static final String IDS = "scheduled_ids";
    private static final String DISPLAYED = "displayed_tags";
    private ScheduleNotifications() {}

    public static boolean configured(Context context) {
        try {
            context.getPackageManager().getReceiverInfo(new ComponentName(context, ScheduleReminderReceiver.class), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }
    public static boolean allowed(Context context) {
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
                && context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    public static long startsAt(StudySchedule schedule) {
        return LocalDate.parse(schedule.getStudyDate()).atTime(LocalTime.parse(schedule.getStartTime()))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    static String fingerprint(StudySchedule s) {
        return s.getStudyDate() + "|" + s.getStartTime() + "|" + s.getEndTime() + "|"
                + s.getSubjectId() + "|" + s.getLocation() + "|" + s.getStatus();
    }
    static String deliveryKey(long account, long id) { return "delivered:" + account + ":" + id; }

    public static synchronized void reconcile(Context context, long accountId, List<StudySchedule> schedules) {
        cancelAll(context);
        if (accountId != com.example.studyschedule.schedule.StudentSession.accountId(context)
                || !configured(context) || !allowed(context)) return;
        Set<String> ids = new HashSet<>();
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        for (StudySchedule s : schedules) {
            if (!ScheduleValidator.CONFIRMED.equals(s.getStatus()) || startsAt(s) <= now) continue;
            String fingerprint = fingerprint(s);
            if (fingerprint.equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(deliveryKey(accountId, s.getId()), ""))) continue;
            Intent intent = alarmIntent(context, accountId, s.getId()).putExtra("fingerprint", fingerprint);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            ids.add(accountId + ":" + s.getId());
            // Persist before scheduling, so logout can still cancel after a process restart.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(IDS, new HashSet<>(ids)).apply();
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(now + 1000, startsAt(s) - 15 * 60_000), pending);
        }
    }
    public static synchronized void cancelAll(Context context) {
        Set<String> ids = new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(IDS, new HashSet<>()));
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        for (String id : ids) {
            String[] parts = id.split(":");
            PendingIntent pending = PendingIntent.getBroadcast(context, 0,
                    alarmIntent(context, Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) { alarms.cancel(pending); pending.cancel(); }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(IDS).apply();
    }
    private static Intent alarmIntent(Context context, long account, long schedule) {
        return new Intent(context, ScheduleReminderReceiver.class)
                .setData(Uri.parse("studyschedule://reminder/" + account + "/" + schedule))
                .putExtra("accountId", account).putExtra("scheduleId", schedule);
    }
    public static void change(Context context, long id, String message) {
        change(context, com.example.studyschedule.schedule.StudentSession.accountId(context), id, message);
    }
    public static void change(Context context, long accountId, long id, String message) {
        show(context, accountId, "change:" + id, "Lịch học đã thay đổi", message);
    }
    public static void clearReminder(Context context, long scheduleId) {
        context.getSystemService(NotificationManager.class).cancel("reminder:" + scheduleId, 1);
    }
    public static synchronized void clearDisplayed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (String tag : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(DISPLAYED, new HashSet<>()))
            manager.cancel(tag, 1);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(DISPLAYED).apply();
    }
    static synchronized boolean upcoming(Context context, long accountId, StudySchedule schedule, String subject) {
        if (!ScheduleValidator.CONFIRMED.equals(schedule.getStatus())) return false;
        boolean posted = show(context, accountId, "reminder:" + schedule.getId(), "Sắp đến giờ học: " + subject,
                schedule.getStartTime() + "–" + schedule.getEndTime() + " · " + schedule.getLocation());
        if (posted) markDelivered(context, accountId, schedule);
        return posted;
    }
    private static synchronized boolean show(Context context, long accountId, String tag, String title, String message) {
        // Uses the same lock as StudentSession.clear/establish: logout either removes an
        // already-posted notification or makes this check reject the stale worker.
        if (accountId <= 0 || accountId != com.example.studyschedule.schedule.StudentSession.accountId(context)
                || !configured(context) || !allowed(context)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Lịch học", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.schedule_ic_notification)
                .setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open).setAutoCancel(true).build();
        manager.notify(tag, 1, notification);
        Set<String> tags = new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(DISPLAYED, new HashSet<>()));
        tags.add(tag);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(DISPLAYED, tags).apply();
        return true;
    }
    static void markDelivered(Context context, long account, StudySchedule schedule) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(deliveryKey(account, schedule.getId()), fingerprint(schedule)).apply();
    }
}
