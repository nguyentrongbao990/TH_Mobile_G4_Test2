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
        authorized = checkAdmin();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing()) authorized = checkAdmin();
    }
    protected boolean checkAdmin() {
        try { return session.requireAdmin(this); }
        catch (android.database.SQLException e) {
            android.widget.Toast.makeText(this, "Không đọc được dữ liệu. Hãy mở lại màn hình để thử lại.", android.widget.Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
    }
}
