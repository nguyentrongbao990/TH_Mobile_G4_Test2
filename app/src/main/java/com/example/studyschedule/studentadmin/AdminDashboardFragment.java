package com.example.studyschedule.studentadmin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.studyschedule.R;
import com.example.studyschedule.auth.OperationViewModel;
import com.example.studyschedule.auth.SessionManager;
import com.example.studyschedule.data.DatabaseHelper;
import com.example.studyschedule.model.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TV2 subject management; uses TV1's existing student screen instead of duplicating it. */
public class AdminDashboardFragment extends Fragment {
    private final ExecutorService reads = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private OperationViewModel writes;
    private View root;
    private EditText search;
    private List<Subject> rows = new ArrayList<>();
    private int generation;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        root = inflater.inflate(R.layout.subject_admin_dashboard, container, false);
        search = root.findViewById(R.id.admin_search);
        writes = new ViewModelProvider(this).get(OperationViewModel.class);
        root.findViewById(R.id.admin_students).setOnClickListener(v -> startActivity(
                new Intent(requireContext(), StudentListActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        root.findViewById(R.id.admin_subjects).setOnClickListener(v -> refresh());
        root.findViewById(R.id.admin_add).setOnClickListener(v -> openForm(-1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });
        ((ListView)root.findViewById(R.id.admin_list)).setOnItemClickListener((parent, v, pos, id) -> {
            if (writes.hasState() || pos >= rows.size()) return;
            Subject subject = rows.get(pos);
            new AlertDialog.Builder(requireContext()).setTitle(subject.getSubjectName())
                    .setItems(new String[]{"Xem / Sửa", "Xóa"}, (d, action) -> {
                        if (action == 0) openForm(subject.getId()); else confirmDelete(subject);
                    }).show();
        });
        return root;
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        writes.state().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            root.findViewById(R.id.admin_add).setEnabled(!result.running);
            if (result.running) return;
            writes.consume();
            toast(result.error != null ? "Không xóa được môn học. Vui lòng thử lại."
                    : Boolean.TRUE.equals(result.value) ? "Đã xóa môn học." : "Môn đang có lịch học hoặc đã bị xóa. Không thể xóa.");
            refresh();
        });
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    private void openForm(long id) {
        if (!new SessionManager(requireContext()).requireAdmin(requireActivity())) return;
        startActivity(new Intent(requireContext(), SubjectFormActivity.class).putExtra(SubjectFormActivity.EXTRA_ID, id));
    }
    private void refresh() {
        if (root == null) return;
        int token = ++generation;
        String keyword = search.getText().toString();
        Context context = requireContext().getApplicationContext();
        ((TextView)root.findViewById(R.id.admin_count)).setText("Đang tải…");
        reads.execute(() -> {
            try (DatabaseHelper db = new DatabaseHelper(context)) {
                if (!new SessionManager(context).isAdmin()) throw new SecurityException();
                List<Subject> found = db.searchSubjects(keyword);
                main.post(() -> {
                    if (root == null || token != generation) return;
                    rows = found;
                    List<String> labels = new ArrayList<>();
                    for (Subject s : found) labels.add(s.getSubjectCode() + " — " + s.getSubjectName() + "\n" + s.getCredits() + " tín chỉ · " + s.getDescription());
                    ((ListView)root.findViewById(R.id.admin_list)).setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
                    ((TextView)root.findViewById(R.id.admin_count)).setText("Môn học: " + found.size());
                    root.findViewById(R.id.admin_empty).setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (RuntimeException e) {
                main.post(() -> {
                    if (root == null || token != generation) return;
                    rows.clear();
                    ((ListView)root.findViewById(R.id.admin_list)).setAdapter(null);
                    ((TextView)root.findViewById(R.id.admin_count)).setText("Không tải được môn học. Bấm Môn học để thử lại.");
                });
            }
        });
    }
    private void confirmDelete(Subject subject) {
        new AlertDialog.Builder(requireContext()).setTitle("Xóa môn học?")
                .setMessage(subject.getSubjectName() + "? Môn đang có lịch học không thể xóa.")
                .setNegativeButton("Hủy", null).setPositiveButton("Xóa", (d, w) -> {
                    Context context = requireContext().getApplicationContext();
                    writes.submit(() -> {
                        if (!new SessionManager(context).isAdmin()) throw new SecurityException();
                        try (DatabaseHelper db = new DatabaseHelper(context)) { return db.deleteSubject(subject.getId()); }
                    });
                }).show();
    }
    private void toast(String message) { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }
    @Override public void onDestroyView() { generation++; root = null; super.onDestroyView(); }
    @Override public void onDestroy() { reads.shutdown(); super.onDestroy(); }
}
