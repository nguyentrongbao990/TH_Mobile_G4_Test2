package com.example.studyschedule.studentadmin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.studyschedule.R;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import java.util.ArrayList;
import java.util.List;

/** Host must authorize the Admin session before displaying this fragment. */
public class AdminDashboardFragment extends Fragment {
    private DatabaseHelper db;
    private boolean students = true;
    private EditText search;
    private ListView list;
    private TextView count;
    private List<Student> studentRows = new ArrayList<>();
    private List<Subject> subjectRows = new ArrayList<>();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.subject_admin_dashboard, container, false);
        db = new DatabaseHelper(requireContext());
        if (state != null) students = state.getBoolean("students", true);
        search = v.findViewById(R.id.admin_search); list = v.findViewById(R.id.admin_list); count = v.findViewById(R.id.admin_count);
        list.setEmptyView(v.findViewById(R.id.admin_empty));
        v.findViewById(R.id.admin_students).setOnClickListener(w -> switchMode(true));
        v.findViewById(R.id.admin_subjects).setOnClickListener(w -> switchMode(false));
        v.findViewById(R.id.admin_add).setOnClickListener(w -> edit(-1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int n, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int n) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener((parent, row, position, id) -> new AlertDialog.Builder(requireContext())
            .setTitle("Thao tác").setItems(new String[]{"Xem / Sửa", "Xóa"}, (d, action) -> { if (action == 0) edit(position); else confirmDelete(position); }).show());
        refresh(); return v;
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); out.putBoolean("students", students); }
    private void switchMode(boolean value) { students = value; search.setText(""); refresh(); }
    private void refresh() {
        List<String> labels = new ArrayList<>();
        try {
            if (students) {
                studentRows = db.searchStudents(search.getText().toString());
                for (Student s : studentRows) labels.add(s.getStudentCode() + " — " + s.getFullname() + "\n" + safe(s.getEmail()) + " · " + safe(s.getPhone()));
            } else {
                subjectRows = db.searchSubjects(search.getText().toString());
                for (Subject s : subjectRows) labels.add(s.getSubjectCode() + " — " + s.getSubjectName() + "\n" + s.getCredits() + " tín chỉ · " + safe(s.getDescription()));
            }
            count.setText((students ? "Sinh viên: " : "Môn học: ") + labels.size());
            list.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
        } catch (android.database.SQLException e) { toast("Không thể đọc dữ liệu SQLite."); }
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private EditText field(View v, int id) { return v.findViewById(id); }
    private String text(View v, int id) { return field(v, id).getText().toString().trim(); }
    private void edit(int position) {
        final boolean studentMode = students;
        Student student = studentMode && position >= 0 ? studentRows.get(position) : null;
        Subject subject = !studentMode && position >= 0 ? subjectRows.get(position) : null;
        View form = getLayoutInflater().inflate(R.layout.subject_admin_form, null);
        for (int id : new int[]{R.id.admin_email, R.id.admin_phone}) form.findViewById(id).setVisibility(studentMode ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.admin_description, R.id.admin_credits}) form.findViewById(id).setVisibility(studentMode ? View.GONE : View.VISIBLE);
        for (int id : new int[]{R.id.admin_username, R.id.admin_password}) form.findViewById(id).setVisibility(studentMode && student == null ? View.VISIBLE : View.GONE);
        field(form, R.id.admin_name).setHint(studentMode ? "Họ và tên" : "Tên môn học");
        if (student != null) {
            field(form,R.id.admin_code).setText(student.getStudentCode()); field(form,R.id.admin_name).setText(student.getFullname());
            field(form,R.id.admin_email).setText(student.getEmail()); field(form,R.id.admin_phone).setText(student.getPhone());
        }
        if (subject != null) {
            field(form,R.id.admin_code).setText(subject.getSubjectCode()); field(form,R.id.admin_name).setText(subject.getSubjectName());
            field(form,R.id.admin_description).setText(subject.getDescription()); field(form,R.id.admin_credits).setText(String.valueOf(subject.getCredits()));
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle((position < 0 ? "Thêm " : "Sửa ") + (studentMode ? "sinh viên" : "môn học"))
            .setView(form).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String code = text(form,R.id.admin_code), name = text(form,R.id.admin_name);
            if (code.isEmpty() || name.isEmpty()) { toast("Vui lòng nhập mã và tên."); return; }
            try {
                boolean ok;
                if (studentMode) {
                    String email = text(form,R.id.admin_email);
                    if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { field(form,R.id.admin_email).setError("Email không hợp lệ"); return; }
                    Student value = new Student(student == null ? 0 : student.getId(), code, name, email, text(form,R.id.admin_phone), student == null ? 0 : student.getAccountId());
                    if (student == null) {
                        String username = text(form,R.id.admin_username), password = field(form,R.id.admin_password).getText().toString();
                        if (username.isEmpty() || password.trim().isEmpty()) { toast("Nhập tên đăng nhập và mật khẩu."); return; }
                        ok = db.addStudent(value, username, password) > 0;
                    } else ok = db.updateStudent(value);
                } else {
                    int credits;
                    try { credits = Integer.parseInt(text(form,R.id.admin_credits)); } catch (NumberFormatException e) { field(form,R.id.admin_credits).setError("Nhập số nguyên không âm"); return; }
                    if (credits < 0) { toast("Số tín chỉ phải không âm."); return; }
                    Subject value = new Subject(subject == null ? 0 : subject.getId(), code, name, text(form,R.id.admin_description), credits);
                    ok = subject == null ? db.addSubject(value) > 0 : db.updateSubject(value);
                }
                if (ok) { dialog.dismiss(); refresh(); toast("Đã lưu."); }
                else toast("Không thể lưu. Kiểm tra mã hoặc tên đăng nhập bị trùng; bản ghi có thể đã bị xóa.");
            } catch (android.database.SQLException e) { toast("Không thể ghi dữ liệu SQLite."); }
        })); dialog.show();
    }
    private void confirmDelete(int position) {
        boolean studentMode = students;
        long id = studentMode ? studentRows.get(position).getId() : subjectRows.get(position).getId();
        new AlertDialog.Builder(requireContext()).setTitle("Xác nhận xóa")
            .setMessage(studentMode ? "Xóa sinh viên cùng tài khoản và toàn bộ lịch học liên quan?" : "Xóa môn học? Môn đã được đặt lịch không thể xóa.")
            .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, w) -> {
                try { boolean ok = studentMode ? db.deleteStudent(id) : db.deleteSubject(id); toast(ok ? "Đã xóa." : "Không thể xóa. Bản ghi không còn hoặc môn đang được đặt lịch."); refresh(); }
                catch (android.database.SQLException e) { toast("Không thể xóa dữ liệu SQLite."); }
            }).show();
    }
    private void toast(String message) { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }
    @Override public void onDestroyView() { super.onDestroyView(); db.close(); }
}
