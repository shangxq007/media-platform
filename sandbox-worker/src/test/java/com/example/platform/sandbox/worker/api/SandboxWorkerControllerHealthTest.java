package com.example.platform.sandbox.worker.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.sandbox.worker.app.SandboxExecutionService;
import org.junit.jupiter.api.Test;

class SandboxWorkerControllerHealthTest {

    @Test
    void healthProbeIsSideEffectFree() {
        SandboxExecutionService executionService = mock(SandboxExecutionService.class);
        SandboxWorkerController controller = new SandboxWorkerController(executionService);

        var response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
        verifyNoInteractions(executionService);
    }
}
