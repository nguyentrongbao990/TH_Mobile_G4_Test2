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
    private OperationViewModel operation;
    private static final String LOGIN_REQUIRED = "login_required";

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
        operation = new androidx.lifecycle.ViewModelProvider(this).get(OperationViewModel.class);
        operation.state().observe(this, result -> {
            if (result == null) return;
            busy = result.running;
            login.setEnabled(!busy);
            if (busy) return;
            operation.consume();
            password.setText("");
            if (result.error != null) { showError("Không thể truy cập dữ liệu. Vui lòng thử lại."); return; }
            String message = (String)result.value;
            if (message.isEmpty()) openHome();
            else if (!LOGIN_REQUIRED.equals(message)) showError(message);
        });
        if (!operation.hasState()) {
            android.content.Context context = getApplicationContext();
            operation.submit(() -> new SessionManager(context).isLoggedIn() ? "" : LOGIN_REQUIRED);
        }
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
        android.content.Context context = getApplicationContext();
        operation.submit(() -> {
            try (DatabaseHelper db = new DatabaseHelper(context)) {
                Account account = db.authenticate(user, pass);
                if (account == null) return "Invalid username or password.";
                if (!("Admin".equals(account.getRole()) || "Student".equals(account.getRole())))
                    return "This account has an unsupported role.";
                if (!new SessionManager(context).saveSession(account)) return "Account could not be linked or session could not be saved.";
                return "";
            }
        });
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
