package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.coordination.*
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskDispatcherTest {

    private PlatformTaskRepository taskRepo;
    private PlatformJobRepository jobRepo;
    private PlatformTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        var cs = mock(PlatformCoordinationService.class);
        var hr = mock(TaskHandlerRegistry.class);
        taskRepo = mock(PlatformTaskRepository.class);
        jobRepo = mock(PlatformJobRepository.class);
        dispatcher = new PlatformTaskDispatcher(cs, hr, taskRepo, jobRepo);
    }

    @Test
    void shouldSkipWhenNoPendingTasks() {
        when(taskRepo.listPendingByCapability(any(), anyInt())).thenReturn(List.of());
        dispatcher.dispatch();
        verify(taskRepo, never()).lease(any());
    }

    @Test
    void shouldRecoverStaleLeases() {
        when(taskRepo.resetStaleLeases(anyInt())).thenReturn(3);
        dispatcher.recoverStaleLeases();
        verify(taskRepo).resetStaleLeases(anyInt());
    }
}
