package com.brainbyte.easy_maintenance.kernel.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSetAndGetTenantId() {
        TenantContext.set("org-abc");
        Optional<String> result = TenantContext.get();
        assertTrue(result.isPresent());
        assertEquals("org-abc", result.get());
    }

    @Test
    void shouldReturnEmptyWhenNotSet() {
        Optional<String> result = TenantContext.get();
        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmptyAfterClear() {
        TenantContext.set("org-xyz");
        TenantContext.clear();
        assertFalse(TenantContext.get().isPresent());
    }

    @Test
    void shouldOverwritePreviousValue() {
        TenantContext.set("org-first");
        TenantContext.set("org-second");
        assertEquals("org-second", TenantContext.get().orElse(null));
    }

    @Test
    void shouldSetAndReturnTrueForSystemContext() {
        TenantContext.setSystemContext();
        assertTrue(TenantContext.isSystemContext());
    }

    @Test
    void shouldReturnFalseForSystemContextWhenNotSet() {
        assertFalse(TenantContext.isSystemContext());
    }

    @Test
    void shouldClearSystemContextOnClear() {
        TenantContext.setSystemContext();
        TenantContext.clear();
        assertFalse(TenantContext.isSystemContext());
    }

    @Test
    void shouldAllowBothTenantAndSystemContextIndependently() {
        TenantContext.set("org-abc");
        TenantContext.setSystemContext();
        assertTrue(TenantContext.get().isPresent());
        assertTrue(TenantContext.isSystemContext());
    }

    @Test
    void shouldIsolateTenantByThread() throws InterruptedException {
        TenantContext.set("main-thread-org");

        AtomicReference<String> otherThreadTenant = new AtomicReference<>();
        Thread other = new Thread(() -> {
            // Thread diferente não deve ver o valor definido na thread principal
            otherThreadTenant.set(TenantContext.get().orElse(null));
        });
        other.start();
        other.join();

        // Thread principal ainda tem seu valor
        assertEquals("main-thread-org", TenantContext.get().orElse(null));
        // Thread filha não herdou o valor
        assertNull(otherThreadTenant.get(), "ThreadLocal não deve ser herdado por thread filha");
    }
}
