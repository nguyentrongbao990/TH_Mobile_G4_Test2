package com.example.studyschedule.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps one accepted write and its result across configuration changes. Tasks use application context. */
public final class OperationViewModel extends ViewModel {
    public static final class State {
        public final boolean running;
        public final Object value;
        public final Exception error;
        private State(boolean running, Object value, Exception error) {
            this.running = running; this.value = value; this.error = error;
        }
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    public LiveData<State> state() { return state; }
    public boolean isRunning() { return state.getValue() != null && state.getValue().running; }
    public boolean hasState() { return state.getValue() != null; }
    public boolean submit(Callable<?> task) {
        if (hasState()) return false; // Consume the previous result before accepting another write.
        state.setValue(new State(true, null, null));
        worker.execute(() -> {
            try { state.postValue(new State(false, task.call(), null)); }
            catch (Exception e) { state.postValue(new State(false, null, e)); }
        });
        return true;
    }
    public void consume() { if (!isRunning()) state.setValue(null); }
    @Override protected void onCleared() { worker.shutdown(); }
}
