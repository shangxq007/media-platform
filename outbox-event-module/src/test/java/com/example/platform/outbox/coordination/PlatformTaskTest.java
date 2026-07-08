package com.example.platform.outbox.coordination;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PlatformTaskTest {

    @Test
    void shouldAllowRetryWhenUnderMaxAttempts() {
        PlatformTask task = new PlatformTask("t1", "j1", "probe", TaskCapability.PROBE, null,
                TaskStatus.FAILED, 1, 3, null, null, "err", 0,
                null, null, null, null);
        assertTrue(task.canRetry());
    }

    @Test
    void shouldDisallowRetryWhenAtMaxAttempts() {
        PlatformTask task = new PlatformTask("t1", "j1", "probe", TaskCapability.PROBE, null,
                TaskStatus.FAILED, 3, 3, null, null, "err", 0,
                null, null, null, null);
        assertFalse(task.canRetry());
    }

    @Test
    void shouldBeLeasableWhenPending() {
        PlatformTask task = new PlatformTask("t1", "j1", "probe", TaskCapability.PROBE, null,
                TaskStatus.PENDING, 0, 3, null, null, null, 0,
                null, null, null, null);
        assertTrue(task.isLeasable());
    }
}
