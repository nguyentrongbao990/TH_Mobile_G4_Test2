package com.example.studyschedule.studentadmin;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.studyschedule.R;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;

public class StudentFormActivity extends AdminActivity {
    public static final String EXTRA_STUDENT_ID = "com.example.studyschedule.studentadmin.STUDENT_ID";
    private EditText code, name, email, phone, username, password;
    private TextView error;
    private Button save;
    private long studentId;
    private long accountId;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!authorized) return;
        setContentView(R.layout.studentadmin_activity_form);
        fitSystemBars(R.id.studentadmin_form_root);
        code = findViewById(R.id.studentadmin_code);
        name = findViewById(R.id.studentadmin_name);
        email = findViewById(R.id.studentadmin_email);
        phone = findViewById(R.id.studentadmin_phone);
        username = findViewById(R.id.studentadmin_username);
        password = findViewById(R.id.studentadmin_password);
        error = findViewById(R.id.studentadmin_form_error);
        save = findViewById(R.id.studentadmin_save);
        studentId = getIntent().getLongExtra(EXTRA_STUDENT_ID, -1);
        ((TextView)findViewById(R.id.studentadmin_form_title)).setText(studentId > 0 ? "Edit student" : "Add student");
        save.setOnClickListener(v -> saveStudent());
        findViewById(R.id.studentadmin_cancel).setOnClickListener(v -> finish());
        if (studentId > 0) {
            findViewById(R.id.studentadmin_credentials).setVisibility(View.GONE);
            ((TextView)findViewById(R.id.studentadmin_form_note)).setText("Edit profile details. Existing login credentials stay unchanged.");
            setBusy(true);
            execute(() -> {
                if (!session.isAdmin()) throw new SecurityException();
                try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                    for (Student s : db.getAllStudents()) if (s.getId() == studentId) return s;
                    return null;
                }
            }, s -> {
                if (s == null) {
                    Toast.makeText(this, "Student no longer exists", Toast.LENGTH_LONG).show();
                    finish(); return;
                }
                accountId = s.getAccountId();
                // Preserve edits restored by Android after rotation.
                if (state == null) {
                    code.setText(s.getStudentCode()); name.setText(s.getFullname());
                    email.setText(s.getEmail()); phone.setText(s.getPhone());
                }
                setBusy(false);
            }, e -> { error.setText("Unable to load student. Close and reopen this form."); });
        }
    }

    private String text(EditText field) { return field.getText().toString().trim(); }
    private boolean required(EditText field, String label) {
        if (!text(field).isEmpty()) return true;
        field.setError(label + " is required"); field.requestFocus(); return false;
    }
    private void setBusy(boolean value) { busy = value; save.setEnabled(!value); }

    private void saveStudent() {
        if (busy || !session.requireAdmin(this)) return;
        if (!required(code, "Student code") || !required(name, "Full name")) return;
        if (!text(email).isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(text(email)).matches()) {
            email.setError("Enter a valid email address"); return;
        }
        if (!text(phone).isEmpty() && !Patterns.PHONE.matcher(text(phone)).matches()) {
            phone.setError("Enter a valid phone number"); return;
        }
        if (studentId <= 0 && (!required(username, "Username") || !required(password, "Initial password"))) return;
        Student student = new Student(studentId, text(code), text(name), text(email), text(phone), accountId);
        String user = text(username), pass = password.getText().toString();
        setBusy(true);
        error.setText("");
        execute(() -> {
            if (!session.isAdmin()) throw new SecurityException();
            try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                for (Student other : db.getAllStudents()) {
                    if (other.getId() != studentId && other.getStudentCode().equals(student.getStudentCode()))
                        return "Student code already exists. Choose another code.";
                }
                boolean saved = studentId > 0 ? db.updateStudent(student) : db.addStudent(student, user, pass) > 0;
                return saved ? "" : (studentId > 0 ? "Student was not saved. It may have been deleted or its code is in use."
                        : "Could not create student. Username or student code may already be in use.");
            }
        }, message -> {
            setBusy(false);
            if (!message.isEmpty()) { error.setText(message); return; }
            password.setText("");
            Toast.makeText(this, "Student saved", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }, e -> { setBusy(false); error.setText("Unable to save student. Please try again."); });
    }
}
