package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import org.junit.jupiter.api.Test;

class ProjectDashboardControllerContainmentTest {

    @Test
    void projectAliasesDenyBeforeAnyRepositoryOrGlobalOutboxRead() {
        AssetRepository assets = mock(AssetRepository.class);
        MarketplaceListingRepository marketplace = mock(MarketplaceListingRepository.class);
        SearchProjectionRepository search = mock(SearchProjectionRepository.class);
        TimelineRevisionRepository revisions = mock(TimelineRevisionRepository.class);
        TimelineReviewRepository reviews = mock(TimelineReviewRepository.class);
        OutboxEventService outbox = mock(OutboxEventService.class);
        ProjectDashboardController controller = new ProjectDashboardController(
                assets, marketplace, search, revisions, reviews, outbox);

        assertUnavailable(() -> controller.dashboard("request-project", "request-tenant"));
        assertUnavailable(() -> controller.activity("request-project", 20));
        assertUnavailable(() -> controller.pending("request-project"));
        assertUnavailable(() -> controller.health("request-project"));

        verifyNoInteractions(assets, marketplace, search, revisions, reviews, outbox);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
