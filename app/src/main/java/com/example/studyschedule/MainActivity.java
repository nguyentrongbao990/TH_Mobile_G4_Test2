package com.example.studyschedule;

import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.example.studyschedule.auth.SessionManager;
import com.example.studyschedule.schedule.ScheduleFragment;
import com.example.studyschedule.studentadmin.AdminDashboardFragment;

/** Shared authenticated host for the Admin and Student modules. */
public class MainActivity extends AppCompatActivity {
    private SessionManager session;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionManager(this);
        if (!session.requireLogin(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(findViewById(R.id.main));
        findViewById(R.id.main_logout).setOnClickListener(v -> session.logout(this));
    }

    @Override protected void onResumeFragments() {
        super.onResumeFragments();
        if (isFinishing() || !session.requireLogin(this)) return;
        boolean admin = "Admin".equals(session.getRole());
        ((TextView) findViewById(R.id.main_identity)).setText(
                getString(R.string.integration_identity, session.getUsername(), session.getRole()));
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.main_content);
        if ((admin && !(current instanceof AdminDashboardFragment))
                || (!admin && !(current instanceof ScheduleFragment))) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_content, admin ? new AdminDashboardFragment() : new ScheduleFragment())
                    .commitNow();
        }
    }
}
