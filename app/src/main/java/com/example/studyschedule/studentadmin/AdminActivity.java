package com.example.studyschedule.studentadmin;

import android.os.Bundle;
import com.example.studyschedule.auth.ModuleActivity;
import com.example.studyschedule.auth.SessionManager;

abstract class AdminActivity extends ModuleActivity {
    protected SessionManager session;
    protected boolean authorized;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionManager(this);
        authorized = session.requireAdmin(this);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing()) authorized = session.requireAdmin(this);
    }
}
