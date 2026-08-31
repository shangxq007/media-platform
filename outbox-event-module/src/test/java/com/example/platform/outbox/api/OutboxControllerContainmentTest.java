package com.example.platform.outbox.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.outbox.app.OutboxEventDispatcher;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import org.junit.jupiter.api.Test;

class OutboxControllerContainmentTest {

    @Test
    void globalHttpControlPlaneDeniesEveryReadAndMutationWithoutDispatch() {
        OutboxEventService service = mock(OutboxEventService.class);
        OutboxEventDispatcher dispatcher = mock(OutboxEventDispatcher.class);
        OutboxController controller = new OutboxController(service, dispatcher);

        assertUnavailable(controller::processOnceInternal);
        assertUnavailable(() -> controller.getOutboxEvents(20));
        assertUnavailable(controller::overview);
        assertUnavailable(() -> controller.recent(20));
        assertUnavailable(() -> controller.dispatch(100));
        assertUnavailable(() -> controller.processOnce("outbox"));
        assertUnavailable(() -> controller.processBatch(100));
        assertUnavailable(() -> controller.failedEvents(20));
        assertUnavailable(() -> controller.retry("outbox"));
        assertUnavailable(() -> controller.deadLetter("outbox", "request reason"));
        assertUnavailable(() -> controller.deadLetterEvents(20));
        assertUnavailable(() -> controller.retryDue(100));

        verifyNoInteractions(service, dispatcher);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
