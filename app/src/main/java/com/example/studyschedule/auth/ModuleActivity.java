package com.example.studyschedule.auth;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

/** Worker and inset handling shared only by Member 1 screens. */
public abstract class ModuleActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    protected interface Result<T> { void accept(T result); }
    protected <T> void execute(Callable<T> task, Result<T> success, Result<Exception> failure) {
        worker.execute(() -> {
            try {
                T result = task.call();
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) success.accept(result); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) failure.accept(e); });
            }
        });
    }
    protected void fitSystemBars(int rootId) {
        View root = findViewById(rootId);
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
    @Override protected void onDestroy() {
        worker.shutdown(); // Allow an already accepted database transaction to finish.
        super.onDestroy();
    }
}
