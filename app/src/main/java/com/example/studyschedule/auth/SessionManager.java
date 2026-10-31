package com.example.studyschedule.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Account;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.schedule.StudentSession;

/** Shared login state. No passwords or password hashes are saved in preferences.
 * Call requireLogin/requireAdmin in onResume of protected screens.
 */
public final class SessionManager {
    private final Context context;
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("auth_session", Context.MODE_PRIVATE);
    }

    /** Call only with the Account returned by DatabaseHelper.authenticate. */
    public boolean saveSession(Account account) {
        if (account == null || account.getId() <= 0
                || !("Admin".equals(account.getRole()) || "Student".equals(account.getRole()))) return false;
        long studentId = -1;
        try (DatabaseHelper db = new DatabaseHelper(context)) {
            try (Cursor c = db.getReadableDatabase().query("accounts", new String[]{"id"},
                    "id=? AND username=? AND role=?", new String[]{Long.toString(account.getId()),
                            account.getUsername(), account.getRole()}, null, null, null)) {
                if (!c.moveToFirst()) return false;
            }
            if ("Student".equals(account.getRole())) {
                Student student = db.getStudentByAccountId(account.getId());
                if (student == null) return false;
                studentId = student.getId();
            }
        }
        boolean saved = prefs.edit().clear().putLong("accountId", account.getId())
                .putString("username", account.getUsername()).putString("role", account.getRole())
                .putLong("studentId", studentId).commit();
        if (!saved) { logout(); return false; }
        syncStudentSession();
        return true;
    }

    /** Rechecks persisted identity and student link; deleted accounts cannot restore a session. */
    public boolean isLoggedIn() {
        long accountId = getAccountId();
        String role = getRole();
        if (accountId <= 0 || !("Admin".equals(role) || "Student".equals(role))) {
            logout();
            return false;
        }
        boolean valid;
        try (DatabaseHelper db = new DatabaseHelper(context)) {
            try (Cursor c = db.getReadableDatabase().query("accounts", new String[]{"id"},
                    "id=? AND username=? AND role=?", new String[]{Long.toString(accountId),
                            getUsername(), role}, null, null, null)) {
                valid = c.moveToFirst();
            }
            if (valid && "Student".equals(role)) {
                Student student = db.getStudentByAccountId(accountId);
                valid = student != null && student.getId() == getStudentId();
            }
        }
        if (!valid) logout();
        else syncStudentSession();
        return valid;
    }

    public long getAccountId() { return prefs.getLong("accountId", -1); }
    public String getUsername() { return prefs.getString("username", ""); }
    public String getRole() { return prefs.getString("role", ""); }
    public long getStudentId() { return prefs.getLong("studentId", -1); }
    public boolean isAdmin() { return isLoggedIn() && "Admin".equals(getRole()); }
    public void logout() {
        prefs.edit().clear().apply();
        StudentSession.clear(context);
    }

    private void syncStudentSession() {
        if ("Student".equals(getRole())) {
            if (StudentSession.accountId(context) != getAccountId())
                StudentSession.establish(context, getAccountId());
        } else if (StudentSession.accountId(context) > 0) {
            StudentSession.clear(context);
        }
    }

    /** Clears the task as well as preferences, preventing Back from reopening protected UI. */
    public void logout(Activity activity) {
        logout();
        openLogin(activity);
    }

    public boolean requireLogin(Activity activity) {
        if (isLoggedIn()) return true;
        openLogin(activity);
        return false;
    }

    public boolean requireAdmin(Activity activity) {
        if (!requireLogin(activity)) return false;
        if ("Admin".equals(getRole())) return true;
        android.widget.Toast.makeText(activity, "Admin access required", android.widget.Toast.LENGTH_LONG).show();
        activity.finish();
        return false;
    }

    private void openLogin(Activity activity) {
        activity.startActivity(new Intent(activity, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }
}
