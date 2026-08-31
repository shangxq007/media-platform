package com.example.platform.federation.nlq.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.federation.nlq.api.dto.ReportCreateRequest;
import com.example.platform.federation.nlq.api.dto.ReportUpdateRequest;
import com.example.platform.federation.nlq.app.ReportDefinitionService;
import com.example.platform.federation.nlq.app.ReportExecutionService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportControllerContainmentTest {

    @Test
    void reportMutationsAndRequestAdminExecutionDenyBeforeServicesAreUsed() {
        ReportDefinitionService definitions = mock(ReportDefinitionService.class);
        ReportExecutionService executions = mock(ReportExecutionService.class);
        AdminAuditPublisher audit = mock(AdminAuditPublisher.class);
        ReportController controller = new ReportController(definitions, executions, audit);

        assertUnavailable(() -> controller.createReport(new ReportCreateRequest(
                "request-tenant", "workspace", "report", null,
                List.of(), List.of(), "request-actor", "PRIVATE", null)));
        assertUnavailable(() -> controller.updateReport(
                "report", new ReportUpdateRequest("report", null, List.of(), List.of(), "PRIVATE", null)));
        assertUnavailable(() -> controller.executeReport(
                "report", "request-user", "request-tenant", "workspace", true));
        assertUnavailable(() -> controller.archiveReport("report"));

        verifyNoInteractions(definitions, executions, audit);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
