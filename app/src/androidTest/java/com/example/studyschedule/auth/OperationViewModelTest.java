package com.example.studyschedule.auth;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OperationViewModelTest {
    @Test public void recreationSharesWriteAndRetainsOneResult() throws Exception {
        ViewModelStore store = new ViewModelStore();
        AtomicReference<OperationViewModel> original = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1), complete = new CountDownLatch(1);
        androidx.lifecycle.Observer<OperationViewModel.State> observer = value -> {
            if (value != null && !value.running) complete.countDown();
        };
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                OperationViewModel vm = new ViewModelProvider(store, new ViewModelProvider.NewInstanceFactory()).get(OperationViewModel.class);
                original.set(vm);
                vm.state().observeForever(observer);
                assertTrue(vm.submit(() -> { calls.incrementAndGet(); release.await(); return "saved"; }));
                // New host/provider after configuration change uses the existing ViewModelStore.
                OperationViewModel restored = new ViewModelProvider(store, new ViewModelProvider.NewInstanceFactory()).get(OperationViewModel.class);
                assertSame(vm, restored);
                assertTrue(restored.isRunning());
                assertFalse(restored.submit(() -> { calls.incrementAndGet(); return "duplicate"; }));
            });
            release.countDown();
            assertTrue(complete.await(5,TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                OperationViewModel restored = new ViewModelProvider(store, new ViewModelProvider.NewInstanceFactory()).get(OperationViewModel.class);
                assertEquals("saved",restored.state().getValue().value);
                assertNull(restored.state().getValue().error);
                assertEquals(1,calls.get());
                restored.consume();
                assertFalse(restored.hasState());
            });
        } finally {
            release.countDown();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (original.get() != null) original.get().state().removeObserver(observer);
                store.clear();
            });
        }
    }
}
