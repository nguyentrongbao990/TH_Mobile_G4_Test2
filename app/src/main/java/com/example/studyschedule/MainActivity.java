package com.example.studyschedule;

import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.studyschedule.schedule.ScheduleFragment;
import com.example.studyschedule.auth.SessionManager;
import com.example.studyschedule.studentadmin.StudentListActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private SessionManager session;
    private View studentContent;
    private AlertDialog sessionError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        session = new SessionManager(this);
        studentContent = findViewById(R.id.schedule_activity_root);
        findViewById(R.id.schedule_logout).setOnClickListener(v -> session.logout(this));
        ViewCompat.setOnApplyWindowInsetsListener(studentContent, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSession();
    }

    private void checkSession() {
        // Recheck even when returning from the background or opening a reminder.
        // Keep restored Student views hidden until the current identity is validated.
        studentContent.setVisibility(View.INVISIBLE);
        try {
            if (!session.requireLogin(this)) return;
            if ("Admin".equals(session.getRole())) {
                startActivity(new Intent(this, StudentListActivity.class));
                finish();
                return;
            }
        } catch (SQLException e) {
            showSessionError();
            return;
        }

        // A different Student may have logged in while this activity was stopped.
        // Do not reuse that previous Student's screen or unfinished editor.
        String tag = "student_schedule_" + session.getAccountId();
        if (getSupportFragmentManager().findFragmentByTag(tag) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main, new ScheduleFragment(), tag).commitNow();
        }
        if (sessionError != null) {
            sessionError.dismiss();
            sessionError = null;
        }
        studentContent.setVisibility(View.VISIBLE);
    }

    private void showSessionError() {
        if (isFinishing() || (sessionError != null && sessionError.isShowing())) return;
        sessionError = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.schedule_session_error_title)
                .setMessage(R.string.schedule_session_error_message)
                .setCancelable(false)
                .setPositiveButton(R.string.schedule_retry, (dialog, which) -> {
                    dialog.dismiss();
                    sessionError = null;
                    checkSession();
                })
                .setNegativeButton(R.string.schedule_logout, (dialog, which) -> session.logout(this))
                .create();
        sessionError.show();
    }

    @Override
    protected void onStop() {
        studentContent.setVisibility(View.INVISIBLE);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (sessionError != null) sessionError.dismiss();
        super.onDestroy();
    }
}
