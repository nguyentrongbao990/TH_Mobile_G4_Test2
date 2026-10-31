package com.example.studyschedule.schedule;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.example.studyschedule.R;
import com.example.studyschedule.model.StudySchedule;
import com.example.studyschedule.model.Subject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ScheduleEditorDialog extends DialogFragment {
    static final String REQUEST = "schedule_save", TAG = "schedule_editor";
    private View form;
    private long[] subjectIds;
    private boolean saving;

    static ScheduleEditorDialog create(long studentId, List<Subject> subjects, StudySchedule schedule, LocalDate selected) {
        Bundle args = new Bundle();
        long[] ids = new long[subjects.size()];
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            Subject s = subjects.get(i); ids[i] = s.getId();
            names.add(s.getSubjectCode() + " · " + s.getSubjectName());
        }
        args.putLongArray("subjectIds", ids); args.putStringArrayList("subjectNames", names);
        args.putLong("studentId", studentId);
        args.putLong("id", schedule == null ? 0 : schedule.getId());
        args.putLong("subjectId", schedule == null ? ids[0] : schedule.getSubjectId());
        args.putString("date", schedule == null ? selected.toString() : schedule.getStudyDate());
        args.putString("start", schedule == null ? "09:00" : schedule.getStartTime());
        args.putString("end", schedule == null ? "10:00" : schedule.getEndTime());
        args.putString("location", schedule == null ? "" : schedule.getLocation());
        args.putString("status", schedule == null ? ScheduleValidator.PENDING : schedule.getStatus());
        ScheduleEditorDialog dialog = new ScheduleEditorDialog(); dialog.setArguments(args); return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle state = savedInstanceState == null ? requireArguments() : savedInstanceState;
        form = LayoutInflater.from(requireContext()).inflate(R.layout.schedule_editor, null);
        subjectIds = requireArguments().getLongArray("subjectIds");
        Spinner subjects = form.findViewById(R.id.schedule_editor_subject);
        subjects.setAdapter(adapter(requireArguments().getStringArrayList("subjectNames")));
        for (int i=0; i<subjectIds.length; i++) if (subjectIds[i] == state.getLong("subjectId")) subjects.setSelection(i);
        Spinner status = form.findViewById(R.id.schedule_editor_status);
        List<String> labels = new ArrayList<>();
        for (String value : ScheduleValidator.STATUSES) labels.add(ScheduleValidator.statusLabel(value) + " (" + value + ")");
        status.setAdapter(adapter(labels));
        status.setSelection(Math.max(0, Arrays.asList(ScheduleValidator.STATUSES).indexOf(state.getString("status"))));
        Button date = form.findViewById(R.id.schedule_editor_date);
        date.setText(state.getString("date"));
        date.setOnClickListener(v -> {
            LocalDate value = LocalDate.parse(date.getText());
            new DatePickerDialog(requireContext(), (picker,y,m,d) -> date.setText(LocalDate.of(y,m+1,d).toString()),
                    value.getYear(), value.getMonthValue()-1, value.getDayOfMonth()).show();
        });
        setupTime(R.id.schedule_editor_start, state.getString("start"));
        setupTime(R.id.schedule_editor_end, state.getString("end"));
        ((EditText)form.findViewById(R.id.schedule_editor_location)).setText(state.getString("location"));
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(requireArguments().getLong("id") == 0 ? "Đặt lịch học" : "Sửa lịch học")
                .setView(form).setNegativeButton("Đóng", null).setPositiveButton("Lưu lịch", null).create();
    }

    private ArrayAdapter<String> adapter(List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); return adapter;
    }
    private void setupTime(int id, String initial) {
        Button button = form.findViewById(id); button.setText(initial);
        button.setOnClickListener(v -> {
            LocalTime time = LocalTime.parse(button.getText());
            new TimePickerDialog(requireContext(), (picker,h,m) -> button.setText(String.format(Locale.ROOT,"%02d:%02d",h,m)),
                    time.getHour(), time.getMinute(), true).show();
        });
    }

    @Override public void onStart() {
        super.onStart();
        ((AlertDialog)requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (saving) return;
            try {
                Bundle values = readForm();
                ScheduleValidator.validate(fromBundle(values));
                saving = true;
                ((AlertDialog)requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                ((AlertDialog)requireDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                setCancelable(false);
                getParentFragmentManager().setFragmentResult(REQUEST, values);
            } catch (IllegalArgumentException e) { showError(e.getMessage()); }
        });
    }
    private Bundle readForm() {
        Bundle values = new Bundle(requireArguments());
        int position = ((Spinner)form.findViewById(R.id.schedule_editor_subject)).getSelectedItemPosition();
        values.putLong("subjectId", subjectIds[position]);
        values.putString("date", ((Button)form.findViewById(R.id.schedule_editor_date)).getText().toString());
        values.putString("start", ((Button)form.findViewById(R.id.schedule_editor_start)).getText().toString());
        values.putString("end", ((Button)form.findViewById(R.id.schedule_editor_end)).getText().toString());
        values.putString("location", ((EditText)form.findViewById(R.id.schedule_editor_location)).getText().toString().trim());
        values.putString("status", ScheduleValidator.STATUSES[((Spinner)form.findViewById(R.id.schedule_editor_status)).getSelectedItemPosition()]);
        return values;
    }
    static StudySchedule fromBundle(Bundle b) {
        return new StudySchedule(b.getLong("id"), b.getLong("studentId"), b.getLong("subjectId"),
                b.getString("date"), b.getString("start"), b.getString("end"), b.getString("location"), b.getString("status"));
    }
    void showError(String message) {
        saving = false; setCancelable(true);
        if (form != null) ((TextView)form.findViewById(R.id.schedule_editor_error)).setText(message);
        if (getDialog() instanceof AlertDialog) {
            ((AlertDialog)getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            ((AlertDialog)getDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
        }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (form != null) out.putAll(readForm());
    }
    @Override public void onDestroyView() { form = null; super.onDestroyView(); }
}
