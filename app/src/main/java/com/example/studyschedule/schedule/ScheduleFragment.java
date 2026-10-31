package com.example.studyschedule.schedule;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.studyschedule.R;
import com.example.studyschedule.calendar.CalendarRange;
import com.example.studyschedule.model.Student;
import com.example.studyschedule.model.Subject;
import com.example.studyschedule.model.StudySchedule;
import com.example.studyschedule.notification.ScheduleNotifications;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScheduleFragment extends Fragment {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted && getContext() != null) toast("Chưa được cấp quyền thông báo. Bạn vẫn có thể quản lý lịch.");
                refresh();
            });
    private com.example.studyschedule.auth.OperationViewModel mutationState;
    private View root;
    private Student student;
    private long loadedAccount = -1;
    private List<Subject> subjects = new ArrayList<>();
    private List<StudySchedule> schedules = new ArrayList<>();
    private final Map<Long, Subject> subjectById = new HashMap<>();
    private LocalDate selected = LocalDate.now();
    private CalendarRange.Mode mode = CalendarRange.Mode.ALL;
    private int statusFilter;
    private String search = "";
    private boolean busy;
    private int generation;

    public ScheduleFragment() { super(R.layout.schedule_fragment); }

    @Override public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            selected = LocalDate.parse(saved.getString("selected", LocalDate.now().toString()));
            mode = CalendarRange.Mode.valueOf(saved.getString("mode", "ALL"));
            search = saved.getString("search", ""); statusFilter = saved.getInt("statusFilter");
        }
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        root = view;
        Spinner modes = view.findViewById(R.id.schedule_mode);
        modes.setAdapter(adapter(new String[]{"Tất cả", "Ngày", "Tuần", "Tháng"}));
        modes.setSelection(mode.ordinal());
        modes.setOnItemSelectedListener(selection(position -> { mode = CalendarRange.Mode.values()[position]; render(); }));
        Spinner statuses = view.findViewById(R.id.schedule_status_filter);
        statuses.setAdapter(adapter(new String[]{"Mọi trạng thái", "Chờ xác nhận", "Đã xác nhận", "Đã hủy"}));
        statuses.setSelection(statusFilter);
        statuses.setOnItemSelectedListener(selection(position -> { statusFilter = position; render(); }));
        EditText keyword = view.findViewById(R.id.schedule_search); keyword.setText(search);
        keyword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { search = s.toString(); render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        view.findViewById(R.id.schedule_create).setOnClickListener(v -> editor(null));
        view.findViewById(R.id.schedule_refresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.schedule_notification_settings).setOnClickListener(v -> notificationSettings());
        view.findViewById(R.id.schedule_previous).setOnClickListener(v -> { selected = CalendarRange.move(selected, mode, -1); render(); });
        view.findViewById(R.id.schedule_next).setOnClickListener(v -> { selected = CalendarRange.move(selected, mode, 1); render(); });
        view.findViewById(R.id.schedule_today).setOnClickListener(v -> { selected = LocalDate.now(); render(); });
        view.findViewById(R.id.schedule_selected_date).setOnClickListener(v -> new DatePickerDialog(requireContext(),
                (picker,y,m,d) -> { selected = LocalDate.of(y,m+1,d); render(); },
                selected.getYear(), selected.getMonthValue()-1, selected.getDayOfMonth()).show());
        getChildFragmentManager().setFragmentResultListener(ScheduleEditorDialog.REQUEST, getViewLifecycleOwner(),
                (key, data) -> save(ScheduleEditorDialog.fromBundle(data)));
        mutationState = new androidx.lifecycle.ViewModelProvider(this).get(com.example.studyschedule.auth.OperationViewModel.class);
        mutationState.state().observe(getViewLifecycleOwner(), operation -> {
            if (operation == null) return;
            busy = operation.running;
            setBusy(busy);
            Fragment editor = getChildFragmentManager().findFragmentByTag(ScheduleEditorDialog.TAG);
            if (editor instanceof ScheduleEditorDialog) ((ScheduleEditorDialog)editor).setSaving(busy);
            if (busy) return;
            mutationState.consume();
            if (operation.error != null) {
                String message = operation.error instanceof IllegalArgumentException || operation.error instanceof IllegalStateException
                        ? operation.error.getMessage() : "Không lưu được thay đổi. Vui lòng thử lại.";
                if (editor instanceof ScheduleEditorDialog) ((ScheduleEditorDialog)editor).showError(message);
                else toast(message);
            } else {
                if (editor instanceof ScheduleEditorDialog) ((ScheduleEditorDialog)editor).dismissAllowingStateLoss();
                toast((String)operation.value);
            }
            refresh();
        });
        render();
    }
    @Override public void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (root == null || busy) return;
        busy = true; setBusy(true);
        int token = generation;
        Context context = requireContext().getApplicationContext();
        long account = StudentSession.accountId(context);
        if (loadedAccount != account) { clearData(); render(); }
        io.execute(() -> {
            Student found = null;
            List<Subject> loadedSubjects = new ArrayList<>();
            List<StudySchedule> loadedSchedules = new ArrayList<>();
            String error = null;
            if (account > 0) {
                try (ScheduleRepository repository = new ScheduleRepository(context, account)) {
                    found = repository.currentStudent();
                    if (found != null) {
                        loadedSubjects = repository.subjects(); loadedSchedules = repository.schedules();
                        if (StudentSession.accountId(context) == account) {
                            try { ScheduleNotifications.reconcile(context, account, loadedSchedules); }
                            catch (RuntimeException e) { error = "Lịch đã tải; chưa thể cập nhật nhắc lịch."; }
                        }
                    } else ScheduleNotifications.cancelAll(context);
                } catch (RuntimeException e) { error = "Không tải được dữ liệu. Vui lòng thử lại."; }
            } else ScheduleNotifications.cancelAll(context);
            Student result = found;
            List<Subject> resultSubjects = loadedSubjects;
            List<StudySchedule> resultSchedules = loadedSchedules;
            String message = error;
            main.post(() -> {
                if (root == null || generation != token) return;
                busy = false;
                if (StudentSession.accountId(context) != account) { clearData(); refresh(); return; }
                loadedAccount = account; student = result; subjects = resultSubjects; schedules = resultSchedules;
                subjectById.clear(); for (Subject subject : subjects) subjectById.put(subject.getId(), subject);
                setBusy(false); render();
                if (message != null) text(R.id.schedule_notice, message);
            });
        });
    }

    private void render() {
        if (root == null) return;
        text(R.id.schedule_identity, student == null ? "Chưa có phiên sinh viên" : student.getFullname() + " · " + student.getStudentCode());
        String notice = student == null ? "Vui lòng đăng nhập bằng tài khoản Student. Nếu chưa có màn hình đăng nhập, cần tích hợp phần TV1 trước."
                : subjects.isEmpty() ? "Chưa có môn học. Nhờ Admin thêm môn trước khi đặt lịch."
                : !ScheduleNotifications.configured(requireContext()) ? "Nhắc lịch chưa được cấu hình. Bạn vẫn có thể đặt và quản lý lịch học."
                : !ScheduleNotifications.allowed(requireContext()) ? "Thông báo đang tắt. Bấm Thông báo để bật nhắc lịch."
                : "Nhắc trước buổi học khoảng 15 phút; thời điểm nhận có thể trễ do hệ thống.";
        text(R.id.schedule_notice, notice);
        root.findViewById(R.id.schedule_create).setEnabled(!busy && student != null && !subjects.isEmpty());
        boolean all = mode == CalendarRange.Mode.ALL;
        root.findViewById(R.id.schedule_date_navigation).setVisibility(all ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.schedule_today).setVisibility(all ? View.GONE : View.VISIBLE);
        String dateLabel = mode == CalendarRange.Mode.MONTH ? selected.format(DateTimeFormatter.ofPattern("MM/yyyy"))
                : mode == CalendarRange.Mode.WEEK ? display(CalendarRange.start(selected, mode)) + " – " + display(CalendarRange.end(selected, mode))
                : display(selected);
        text(R.id.schedule_selected_date, dateLabel);
        List<StudySchedule> visible = new ArrayList<>();
        String keyword = normalize(search.trim());
        for (StudySchedule s : schedules) {
            if (!CalendarRange.contains(selected, mode, s.getStudyDate())) continue;
            if (statusFilter > 0 && !ScheduleValidator.STATUSES[statusFilter-1].equals(s.getStatus())) continue;
            String haystack = subjectLabel(s.getSubjectId()) + " " + s.getLocation() + " " + s.getStudyDate()
                    + " " + display(LocalDate.parse(s.getStudyDate())) + " " + s.getStartTime() + " " + s.getEndTime();
            if (!normalize(haystack).contains(keyword)) continue;
            visible.add(s);
        }
        renderGrid();
        text(R.id.schedule_summary, visible.size() + " buổi học" + (schedules.isEmpty() ? "" : " / " + schedules.size() + " tổng cộng"));
        LinearLayout list = root.findViewById(R.id.schedule_list); list.removeAllViews();
        root.findViewById(R.id.schedule_empty).setVisibility(visible.isEmpty() && !busy ? View.VISIBLE : View.GONE);
        text(R.id.schedule_empty, student == null ? "Chưa thể xem lịch khi chưa đăng nhập."
                : schedules.isEmpty() ? "Chưa có lịch học. Hãy đặt buổi học đầu tiên."
                : "Không có lịch phù hợp với bộ lọc hiện tại.");
        for (StudySchedule s : visible) {
            View row = getLayoutInflater().inflate(R.layout.schedule_row, list, false);
            ((TextView)row.findViewById(R.id.schedule_row_subject)).setText(subjectLabel(s.getSubjectId()));
            ((TextView)row.findViewById(R.id.schedule_row_time)).setText(display(LocalDate.parse(s.getStudyDate())) + " · " + s.getStartTime() + "–" + s.getEndTime());
            ((TextView)row.findViewById(R.id.schedule_row_location)).setText(s.getLocation());
            ((TextView)row.findViewById(R.id.schedule_row_status)).setText(ScheduleValidator.statusLabel(s.getStatus()) + " · Chạm để quản lý");
            row.setAlpha(ScheduleValidator.CANCELLED.equals(s.getStatus()) ? 0.65f : 1f);
            row.setOnClickListener(v -> actions(s)); list.addView(row);
        }
    }

    private void renderGrid() {
        GridLayout grid = root.findViewById(R.id.schedule_calendar_grid); grid.removeAllViews();
        boolean shown = mode == CalendarRange.Mode.MONTH || mode == CalendarRange.Mode.WEEK;
        grid.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (!shown) return;
        for (String label : new String[]{"T2","T3","T4","T5","T6","T7","CN"}) {
            TextView day = new TextView(requireContext()); day.setText(label); day.setGravity(Gravity.CENTER);
            grid.addView(day, cell(32));
        }
        LocalDate first = CalendarRange.start(selected, mode);
        if (mode == CalendarRange.Mode.MONTH) first = CalendarRange.start(first, CalendarRange.Mode.WEEK);
        Map<String,Integer> count = new HashMap<>();
        for (StudySchedule s : schedules) if (!ScheduleValidator.CANCELLED.equals(s.getStatus()))
            count.put(s.getStudyDate(), count.getOrDefault(s.getStudyDate(), 0)+1);
        int cells = mode == CalendarRange.Mode.MONTH ? 42 : 7;
        for (int i=0; i<cells; i++) {
            LocalDate date = first.plusDays(i);
            int total = count.getOrDefault(date.toString(), 0);
            TextView day = new TextView(requireContext());
            day.setText(date.getDayOfMonth() + (total > 0 ? "\n• " + total : ""));
            day.setGravity(Gravity.CENTER); day.setTextSize(13); day.setClickable(true); day.setFocusable(true);
            day.setContentDescription(display(date) + ", " + total + " lịch chưa hủy");
            if (mode == CalendarRange.Mode.MONTH && date.getMonth() != selected.getMonth()) day.setAlpha(0.45f);
            if (date.equals(selected)) { day.setTypeface(null, android.graphics.Typeface.BOLD); day.setBackgroundColor(0x22365CDA); }
            android.util.TypedValue value = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            day.setForeground(androidx.core.content.ContextCompat.getDrawable(requireContext(), value.resourceId));
            day.setOnClickListener(v -> { selected = date; ((Spinner)root.findViewById(R.id.schedule_mode)).setSelection(CalendarRange.Mode.DAY.ordinal()); });
            grid.addView(day, cell(52));
        }
    }
    private GridLayout.LayoutParams cell(int height) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0; p.height = (int)(height * getResources().getDisplayMetrics().density);
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); return p;
    }

    private void editor(StudySchedule schedule) {
        if (busy || student == null || subjects.isEmpty() || getChildFragmentManager().findFragmentByTag(ScheduleEditorDialog.TAG) != null) return;
        ScheduleEditorDialog.create(student.getId(), subjects, schedule, selected).show(getChildFragmentManager(), ScheduleEditorDialog.TAG);
    }
    private void actions(StudySchedule schedule) {
        if (busy) return;
        boolean cancelled = ScheduleValidator.CANCELLED.equals(schedule.getStatus());
        new MaterialAlertDialogBuilder(requireContext()).setTitle(subjectLabel(schedule.getSubjectId()))
                .setItems(cancelled ? new String[]{"Xóa lịch"} : new String[]{"Sửa lịch (gửi duyệt lại)", "Hủy lịch", "Xóa lịch"}, (dialog, which) -> {
                    int action = cancelled ? 2 : which;
                    if (action == 0) editor(schedule);
                    else new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(action == 1 ? "Hủy lịch học?" : "Xóa lịch học?")
                            .setMessage("Buổi học " + display(LocalDate.parse(schedule.getStudyDate())) + " lúc " + schedule.getStartTime())
                            .setNegativeButton("Giữ lại", null).setPositiveButton("Đồng ý", (d,w) -> mutate(repo -> {
                                if (action == 1) repo.changeStatus(schedule.getId(), ScheduleValidator.CANCELLED);
                                else repo.delete(schedule.getId());
                                return schedule.getId();
                            }, action == 1 ? "Đã hủy lịch học." : "Đã xóa lịch học.", false)).show();
                }).show();
    }
    private void save(StudySchedule schedule) {
        mutate(repo -> repo.save(schedule), "Đã lưu lịch học.", true);
    }
    private interface Mutation { long run(ScheduleRepository repository); }
    private void mutate(Mutation mutation, String success, boolean fromEditor) {
        if (busy || root == null || mutationState.hasState()) { editorError("Đang tải dữ liệu. Vui lòng thử lưu lại."); return; }
        long account = loadedAccount;
        Context context = requireContext().getApplicationContext();
        mutationState.submit(() -> {
            try (ScheduleRepository repository = new ScheduleRepository(context, account)) {
                if (account <= 0 || StudentSession.accountId(context) != account)
                    throw new IllegalStateException("Phiên đã thay đổi. Vui lòng đăng nhập lại.");
                long id = mutation.run(repository);
                try {
                    if (StudentSession.accountId(context) == account) {
                        ScheduleNotifications.clearReminder(context, id);
                        ScheduleNotifications.reconcile(context, account, repository.schedules());
                        ScheduleNotifications.change(context, account, id, success);
                    }
                } catch (RuntimeException e) { return "Lịch đã lưu; chưa thể cập nhật thông báo."; }
                return success;
            }
        });
    }
    private void editorError(String message) {
        Fragment dialog = getChildFragmentManager().findFragmentByTag(ScheduleEditorDialog.TAG);
        if (dialog instanceof ScheduleEditorDialog) ((ScheduleEditorDialog)dialog).showError(message);
    }
    private void notificationSettings() {
        if (!ScheduleNotifications.configured(requireContext())) {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Chưa cấu hình nhắc lịch")
                    .setMessage("TV1 cần ghép quyền thông báo và hai receiver theo docs/TV3_INTEGRATION.md. Chức năng đặt lịch vẫn hoạt động.")
                    .setPositiveButton("Đã hiểu", null).show(); return;
        }
        if (Build.VERSION.SDK_INT >= 33 && requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS); return;
        }
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName()));
    }
    private void setBusy(boolean value) {
        if (root == null) return;
        root.findViewById(R.id.schedule_loading).setVisibility(value ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.schedule_refresh).setEnabled(!value);
        root.findViewById(R.id.schedule_create).setEnabled(!value && student != null && !subjects.isEmpty());
    }
    private void clearData() { student = null; schedules.clear(); subjects.clear(); subjectById.clear(); }
    private String subjectLabel(long id) {
        Subject s = subjectById.get(id); return s == null ? "Môn #" + id : s.getSubjectCode() + " · " + s.getSubjectName();
    }
    private static String display(LocalDate date) { return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('đ', 'd');
    }
    private void text(int id, String value) { ((TextView)root.findViewById(id)).setText(value); }
    private void toast(String value) { Toast.makeText(requireContext(), value, Toast.LENGTH_LONG).show(); }
    private ArrayAdapter<String> adapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); return adapter;
    }
    private interface Selected { void accept(int index); }
    private AdapterView.OnItemSelectedListener selection(Selected callback) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { callback.accept(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); out.putString("selected", selected.toString()); out.putString("mode", mode.name());
        out.putString("search", search); out.putInt("statusFilter", statusFilter);
    }
    @Override public void onDestroyView() { generation++; root = null; busy = false; clearData(); super.onDestroyView(); }
    @Override public void onDestroy() { io.shutdown(); super.onDestroy(); }
}
