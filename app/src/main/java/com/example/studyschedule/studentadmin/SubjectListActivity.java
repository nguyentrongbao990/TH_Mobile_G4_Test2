package com.example.studyschedule.studentadmin;

import android.os.Bundle;
import com.example.studyschedule.R;

public final class SubjectListActivity extends AdminActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!authorized) return;
        setContentView(R.layout.subject_activity_management);
        fitSystemBars(R.id.subject_management_root);
        findViewById(R.id.subject_back).setOnClickListener(v -> finish());
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.subject_container, new AdminDashboardFragment()).commit();
    }
}
