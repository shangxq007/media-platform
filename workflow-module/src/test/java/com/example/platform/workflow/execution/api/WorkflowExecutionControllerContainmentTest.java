package com.example.platform.workflow.execution.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.shared.authorization.AuthorizationDeniedException;
import com.example.platform.workflow.execution.app.WorkflowExecutionService;
import org.junit.jupiter.api.Test;

class WorkflowExecutionControllerContainmentTest {

    private final WorkflowExecutionService service = mock(WorkflowExecutionService.class);
    private final WorkflowExecutionController controller = new WorkflowExecutionController(service);

    @Test
    void requestActorsAndMissingCancellationBodyCannotEstablishMutationAuthority() {
        var start = new WorkflowExecutionController.StartWorkflowExecutionRequest(
                "request-actor", "SYSTEM", "definition", 1, null, "{}", "idempotency");
        var approval = new WorkflowExecutionController.ApprovalRequest(
                true, "request-approver", "approve");

        assertUnavailable(() -> controller.start("tenant", start));
        assertUnavailable(() -> controller.cancel("tenant", "execution", null));
        assertUnavailable(() -> controller.cancel(
                "tenant", "execution",
                new WorkflowExecutionController.CancelRequest("request-actor", "USER", "cancel")));
        assertUnavailable(() -> controller.approve("tenant", "execution", approval));

        verifyNoInteractions(service);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
