package com.example.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.identity.app.BuiltinDataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

/**
 * Tests for BuiltinDataBootstrapRunner.
 */
class BuiltinDataBootstrapRunnerTest {

    @Test
    void runCallsInitOnBuiltinDataInitializer() {
        BuiltinDataInitializer initializer = mock(BuiltinDataInitializer.class);
        BuiltinDataBootstrapRunner runner = new BuiltinDataBootstrapRunner(initializer);

        runner.run(mock(ApplicationArguments.class));

        verify(initializer).init();
    }

    @Test
    void runThrowsIllegalStateExceptionWhenInitFails() {
        BuiltinDataInitializer initializer = mock(BuiltinDataInitializer.class);
        doThrow(new RuntimeException("DB error")).when(initializer).init();
        BuiltinDataBootstrapRunner runner = new BuiltinDataBootstrapRunner(initializer);

        assertThrows(IllegalStateException.class,
                () -> runner.run(mock(ApplicationArguments.class)));
    }

    @Test
    void runnerIsConditionalOnProperty() {
        // Verify the annotation is present
        assertTrue(BuiltinDataBootstrapRunner.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class),
                "BuiltinDataBootstrapRunner should be conditional on identity.builtin-data.enabled");
    }
}
