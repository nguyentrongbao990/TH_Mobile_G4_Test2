package com.example.studyschedule.studentadmin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.example.studyschedule.R;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;
import java.util.ArrayList;
import java.util.List;

public class StudentListActivity extends AdminActivity {
    private EditText search;
    private TextView message;
    private ListView list;
    private List<Student> students = new ArrayList<>();
    private int request;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!authorized) return;
        setContentView(R.layout.studentadmin_activity_list);
        fitSystemBars(R.id.studentadmin_list_root);
        search = findViewById(R.id.studentadmin_search);
        message = findViewById(R.id.studentadmin_message);
        list = findViewById(R.id.studentadmin_list);
        findViewById(R.id.studentadmin_add).setOnClickListener(v -> {
            if (session.requireAdmin(this)) startActivity(new Intent(this, StudentFormActivity.class));
        });
        findViewById(R.id.studentadmin_back).setOnClickListener(v -> finish());
        findViewById(R.id.studentadmin_logout).setOnClickListener(v -> session.logout(this));
        findViewById(R.id.studentadmin_refresh).setOnClickListener(v -> refresh());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < students.size()) showActions(students.get(position));
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (authorized && search != null) refresh();
    }

    private void refresh() {
        final int version = ++request;
        String keyword = search.getText().toString().trim();
        message.setText("Loading students…");
        execute(() -> {
            if (!session.isAdmin()) throw new SecurityException();
            try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                return keyword.isEmpty() ? db.getAllStudents() : db.searchStudents(keyword);
            }
        }, result -> {
            if (version != request || !session.requireAdmin(this)) return;
            students = result;
            List<String> rows = new ArrayList<>();
            for (Student s : result) rows.add(s.getFullname() + " (" + s.getStudentCode() + ")\n"
                    + safe(s.getEmail()) + "  " + safe(s.getPhone()));
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
            message.setText(result.isEmpty() ? "No students found." : result.size() + " student(s). Tap to edit or delete.");
        }, e -> {
            if (version == request) {
                students.clear();
                list.setAdapter(null);
                message.setText("Unable to load students. Use Refresh to try again.");
            }
        });
    }

    private String safe(String s) { return s == null ? "" : s; }

    private void showActions(Student student) {
        if (!session.requireAdmin(this)) return;
        new AlertDialog.Builder(this).setTitle(student.getFullname())
                .setItems(new String[]{"Edit student", "Delete student"}, (dialog, which) -> {
                    if (which == 0) startActivity(new Intent(this, StudentFormActivity.class)
                            .putExtra(StudentFormActivity.EXTRA_STUDENT_ID, student.getId()));
                    else confirmDelete(student);
                }).show();
    }

    private void confirmDelete(Student student) {
        new AlertDialog.Builder(this).setTitle("Delete student?")
                .setMessage("Delete " + student.getFullname() + "? Their login account and all study schedules will also be deleted.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                    execute(() -> {
                        if (!session.isAdmin()) throw new SecurityException();
                        try (DatabaseHelper db = new DatabaseHelper(getApplicationContext())) {
                            return db.deleteStudent(student.getId());
                        }
                    }, deleted -> {
                        Toast.makeText(this, deleted ? "Student deleted" : "Student was not deleted. Refresh and try again.", Toast.LENGTH_LONG).show();
                        refresh();
                    }, e -> Toast.makeText(this, "Unable to delete student. Please try again.", Toast.LENGTH_LONG).show());
                }).show();
    }
}
