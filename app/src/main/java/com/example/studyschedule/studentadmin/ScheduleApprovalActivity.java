package com.example.studyschedule.studentadmin;

import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import com.example.studyschedule.R;
import com.example.studyschedule.auth.OperationViewModel;
import com.example.studyschedule.auth.SessionManager;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.data.ScheduleApprovalRepository;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScheduleApprovalActivity extends AdminActivity {
    private final List<StudySchedule> schedules = new ArrayList<>();
    private OperationViewModel writes;
    private int generation;
    private ListView list;
    private TextView message;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!authorized) return;
        setContentView(R.layout.studentadmin_schedule_approval);
        fitSystemBars(R.id.studentadmin_approval_root);
        list = findViewById(R.id.studentadmin_approval_list);
        message = findViewById(R.id.studentadmin_approval_message);
        writes = new ViewModelProvider(this).get(OperationViewModel.class);
        writes.state().observe(this, result -> {
            if (result == null) return;
            list.setEnabled(!result.running);
            if (result.running) return;
            writes.consume();
            String text = result.error != null
                    ? (result.error instanceof IllegalArgumentException ? result.error.getMessage() : "Không cập nhật được lịch. Vui lòng thử lại.")
                    : Boolean.TRUE.equals(result.value) ? "Đã cập nhật trạng thái." : "Lịch không còn hoặc trùng giờ với lịch khác.";
            Toast.makeText(this, text, Toast.LENGTH_LONG).show(); refresh();
        });
        findViewById(R.id.studentadmin_approval_back).setOnClickListener(v -> finish());
        findViewById(R.id.studentadmin_approval_refresh).setOnClickListener(v -> refresh());
        list.setOnItemClickListener((parent, v, position, id) -> {
            if (writes.hasState() || position >= schedules.size() || !checkAdmin()) return;
            StudySchedule schedule = schedules.get(position);
            new AlertDialog.Builder(this).setTitle("Duyệt lịch #" + schedule.getId())
                    .setItems(new String[]{"Xác nhận (Confirmed)", "Hủy (Cancelled)"}, (d, action) -> {
                        Context context = getApplicationContext();
                        writes.submit(() -> {
                            SessionManager session = new SessionManager(context);
                            if (!session.isAdmin()) throw new SecurityException();
                            try (DatabaseHelper db = new DatabaseHelper(context)) {
                                return new ScheduleApprovalRepository(db, session.getAccountId()).setStatus(
                                        schedule.getId(), action == 0 ? "Confirmed" : "Cancelled");
                            }
                        });
                    }).show();
        });
    }
    @Override protected void onResume() { super.onResume(); if (authorized && list != null) refresh(); }
    private void refresh() {
        int token = ++generation;
        Context context = getApplicationContext();
        execute(() -> {
            SessionManager session = new SessionManager(context);
            if (!session.isAdmin()) throw new SecurityException();
            try (DatabaseHelper db = new DatabaseHelper(context)) {
                List<StudySchedule> all = new ScheduleApprovalRepository(db, session.getAccountId()).getAllSchedules();
                Map<Long,String> students = new HashMap<>(), subjects = new HashMap<>();
                for (Student s : db.getAllStudents()) students.put(s.getId(), s.getFullname() + " (" + s.getStudentCode() + ")");
                for (Subject s : db.getAllSubjects()) subjects.put(s.getId(), s.getSubjectName());
                List<String> labels = new ArrayList<>();
                for (StudySchedule s : all) labels.add(students.get(s.getStudentId()) + " · " + subjects.get(s.getSubjectId())
                        + "\n" + s.getStudyDate() + " " + s.getStartTime() + "–" + s.getEndTime() + " · " + s.getStatus());
                return new Object[]{all, labels};
            }
        }, result -> {
            if (token != generation) return;
            schedules.clear();
            @SuppressWarnings("unchecked") List<StudySchedule> all = (List<StudySchedule>)result[0];
            @SuppressWarnings("unchecked") List<String> labels = (List<String>)result[1];
            schedules.addAll(all);
            list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
            message.setText(all.isEmpty() ? "Chưa có lịch cần quản lý." : all.size() + " lịch. Chạm để xác nhận hoặc hủy.");
        }, e -> {
            if (token != generation) return;
            schedules.clear(); list.setAdapter(null); message.setText("Không tải được lịch. Bấm Tải lại để thử lại.");
        });
    }
}
