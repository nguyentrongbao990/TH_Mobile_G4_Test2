package com.example.studyschedule.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.example.studyschedule.MainActivity;
import com.example.studyschedule.R;
import com.example.studyschedule.studentadmin.StudentListActivity;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Account;

public class LoginActivity extends ModuleActivity {
    private SessionManager session;
    private EditText username, password;
    private TextView error;
    private Button login;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_login);
        fitSystemBars(R.id.auth_login_root);
        session = new SessionManager(this);
        username = findViewById(R.id.auth_username);
        password = findViewById(R.id.auth_password);
        error = findViewById(R.id.auth_error);
        login = findViewById(R.id.auth_login);
        login.setOnClickListener(v -> signIn());
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { signIn(); return true; }
            return false;
        });
        busy = true;
        login.setEnabled(false);
        execute(() -> session.isLoggedIn(), valid -> {
            busy = false;
            login.setEnabled(true);
            if (valid) openHome();
        }, e -> showError("Cannot open the database. Please try again."));
    }

    private void signIn() {
        if (busy) return;
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (user.isEmpty()) { username.setError("Enter username"); return; }
        if (pass.trim().isEmpty()) { password.setError("Enter password"); return; }
        busy = true;
        login.setEnabled(false);
        error.setText("");
        execute(() -> {
            try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                Account account = db.authenticate(user, pass);
                if (account == null) return "Invalid username or password.";
                if (!("Admin".equals(account.getRole()) || "Student".equals(account.getRole())))
                    return "This account has an unsupported role.";
                if (!session.saveSession(account)) return "Account could not be linked or session could not be saved.";
                return "";
            }
        }, message -> {
            password.setText("");
            if (message.isEmpty()) openHome(); else showError(message);
        }, e -> showError("Unable to sign in. Please try again."));
    }

    private void showError(String message) {
        busy = false;
        login.setEnabled(true);
        error.setText(message);
    }

    private void openHome() {
        startActivity(new Intent(this, "Admin".equals(session.getRole()) ? StudentListActivity.class : MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
