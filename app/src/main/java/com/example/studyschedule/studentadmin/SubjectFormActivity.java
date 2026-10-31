package com.example.studyschedule.studentadmin;

import android.content.Context;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import com.example.studyschedule.R;
import com.example.studyschedule.auth.OperationViewModel;
import com.example.studyschedule.auth.SessionManager;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Subject;

public final class SubjectFormActivity extends AdminActivity {
    public static final String EXTRA_ID = "subjectId";
    private EditText code, name, description, credits;
    private TextView error;
    private OperationViewModel writes;
    private long id;
    private boolean loaded, loading;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!authorized) return;
        setContentView(R.layout.subject_activity_form);
        fitSystemBars(R.id.subject_form_root);
        code = findViewById(R.id.subject_code); name = findViewById(R.id.subject_name);
        description = findViewById(R.id.subject_description); credits = findViewById(R.id.subject_credits);
        error = findViewById(R.id.subject_error);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
        writes = new ViewModelProvider(this).get(OperationViewModel.class);
        writes.state().observe(this, result -> {
            if (result == null) return;
            findViewById(R.id.subject_save).setEnabled(!result.running && !loading);
            if (result.running) return;
            writes.consume();
            if (result.error != null) { error.setText("Không lưu được môn học. Vui lòng thử lại."); return; }
            if (!Boolean.TRUE.equals(result.value)) { error.setText("Mã môn đã tồn tại hoặc môn đã bị xóa."); return; }
            Toast.makeText(this, "Đã lưu môn học", Toast.LENGTH_SHORT).show(); finish();
        });
        findViewById(R.id.subject_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.subject_save).setOnClickListener(v -> save());
        if (id > 0) {
            loading = true; findViewById(R.id.subject_save).setEnabled(false);
            Context context = getApplicationContext(); long editing = id;
            execute(() -> {
                if (!new SessionManager(context).isAdmin()) throw new SecurityException();
                try (DatabaseHelper db = new DatabaseHelper(context)) {
                    for (Subject subject : db.getAllSubjects()) if (subject.getId() == editing) return subject;
                    return null;
                }
            }, subject -> {
                if (subject == null) { Toast.makeText(this, "Môn không còn tồn tại", Toast.LENGTH_LONG).show(); finish(); return; }
                if (state == null || !state.getBoolean("loaded")) {
                    code.setText(subject.getSubjectCode()); name.setText(subject.getSubjectName());
                    description.setText(subject.getDescription()); credits.setText(String.valueOf(subject.getCredits()));
                }
                loaded = true; loading = false;
                findViewById(R.id.subject_save).setEnabled(!writes.isRunning());
            }, e -> error.setText("Không tải được môn. Đóng và mở lại để thử lại."));
        }
    }
    private void save() {
        if (loading || writes.hasState() || !checkAdmin()) return;
        String subjectCode = code.getText().toString().trim(), subjectName = name.getText().toString().trim();
        if (subjectCode.isEmpty()) { code.setError("Nhập mã môn"); return; }
        if (subjectName.isEmpty()) { name.setError("Nhập tên môn"); return; }
        int count;
        try { count = Integer.parseInt(credits.getText().toString().trim()); }
        catch (NumberFormatException e) { credits.setError("Nhập số nguyên không âm"); return; }
        if (count < 0) { credits.setError("Số tín chỉ không được âm"); return; }
        Subject subject = new Subject(id, subjectCode, subjectName, description.getText().toString().trim(), count);
        Context context = getApplicationContext();
        error.setText("");
        writes.submit(() -> {
            if (!new SessionManager(context).isAdmin()) throw new SecurityException();
            try (DatabaseHelper db = new DatabaseHelper(context)) {
                return subject.getId() > 0 ? db.updateSubject(subject) : db.addSubject(subject) > 0;
            }
        });
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("loaded", loaded); super.onSaveInstanceState(out); }
}
