package com.example.studyschedule.schedule;

import android.content.Context;
import android.content.SharedPreferences;

/** Integration contract: TV1 calls establish after successful Student authentication. */
public final class StudentSession {
    private static final String FILE = "study_schedule_student_session";
    private static final String ACCOUNT_ID = "accountId";
    private StudentSession() {}
    public static void establish(Context context, long authenticatedAccountId) {
        if (authenticatedAccountId <= 0) throw new IllegalArgumentException("Invalid account ID");
        if (accountId(context) != authenticatedAccountId) {
            com.example.studyschedule.notification.ScheduleNotifications.cancelAll(context);
            com.example.studyschedule.notification.ScheduleNotifications.clearDisplayed(context);
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putLong(ACCOUNT_ID, authenticatedAccountId).apply();
    }
    public static long accountId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return prefs.getLong(ACCOUNT_ID, -1);
    }
    /** Call on logout, including when switching from Student to Admin. */
    public static void clear(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply();
        com.example.studyschedule.notification.ScheduleNotifications.cancelAll(context);
        com.example.studyschedule.notification.ScheduleNotifications.clearDisplayed(context);
    }
}
