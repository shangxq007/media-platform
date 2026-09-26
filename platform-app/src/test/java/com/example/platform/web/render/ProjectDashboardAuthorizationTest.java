package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.marketplace.api.MarketplaceApi;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.api.review.ReviewQueries;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-UNPROTECTED-FIX-001 — {@code /api/projects/{projectId}/dashboard*} now authorizes a
 * project-scoped READ on the addressed project before aggregating any project data.
 */
class ProjectDashboardAuthorizationTest {

    private static final String TENANT = "tenant-1";

    private ProjectDashboardController controller;
    private OutboxEventService outbox;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        AuthorizationDecisionPort port = (AuthorizationRequest request) ->
                "prj_a".equals(request.resource().projectId())
                        ? AuthorizationDecision.allow("TEST_ALLOW")
                        : AuthorizationDecision.deny("TEST_DENY", "TEST", "project not authorized");
        TimelineProjectAuthorizationService authorization = new TimelineProjectAuthorizationService(
                port,
                () -> Optional.of(CanonicalActor.user("user-1", TENANT, Set.of("VIEWER"), "test")));
        outbox = mock(OutboxEventService.class);
        when(outbox.overview()).thenReturn(java.util.Map.of());
        controller = new ProjectDashboardController(
                mock(MarketplaceApi.class),
                mock(SearchProjectionRepository.class),
                mock(TimelineRevisionRepository.class),
                mock(ReviewQueries.class),
                outbox,
                authorization);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void healthAuthorizedForOwnedProject() {
        // A direct call with the owned project must clear the authorization gate (no exception).
        controller.health("prj_a");
    }

    @Test
    void healthDeniedForOtherProject() {
        assertThrows(AuthorizationDeniedException.class, () -> controller.health("prj_b"));
    }

    @Test
    void activityDeniedForOtherProject() {
        assertThrows(AuthorizationDeniedException.class, () -> controller.activity("prj_b", 20));
    }

    @Test
    void pendingDeniedForOtherProject() {
        assertThrows(AuthorizationDeniedException.class, () -> controller.pending("prj_b"));
    }

    @Test
    void dashboardDeniedForOtherProject() {
        assertThrows(AuthorizationDeniedException.class,
                () -> controller.dashboard("prj_b", TENANT));
    }

    @Test
    void missingAmbientTenantFailsClosed() {
        TenantContext.clear();
        assertThrows(ResponseStatusException.class, () -> controller.health("prj_a"));
    }
}
