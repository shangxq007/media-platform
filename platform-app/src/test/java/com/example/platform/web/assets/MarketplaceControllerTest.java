package com.example.platform.web.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplaceControllerTest {

    private MarketplaceListingRepository listingRepo;
    private MarketplaceController controller;

    @BeforeEach
    void setUp() {
        listingRepo = mock(MarketplaceListingRepository.class);
        controller = new MarketplaceController(listingRepo);
    }

    @Test
    void marketplaceSearchIsContainedBeforeRepositoryDispatch() {
        assertContainedBeforeRepositoryDispatch(
                "marketplace listing search",
                () -> controller.search(null, null, null, null, null, 0, 20));
    }

    @Test
    void marketplaceListIsContainedBeforeRepositoryDispatch() {
        assertContainedBeforeRepositoryDispatch(
                "marketplace listing read", () -> controller.list(null, 50));
    }

    @Test
    void marketplaceListingReadIsContainedBeforeRepositoryDispatch() {
        assertContainedBeforeRepositoryDispatch(
                "marketplace listing read", () -> controller.get("listing-1"));
    }

    @Test
    void marketplaceAssetListingReadIsContainedBeforeRepositoryDispatch() {
        assertContainedBeforeRepositoryDispatch(
                "marketplace asset listing read",
                () -> controller.getByAsset("asset-1", "caller-tenant"));
    }

    @Test
    void marketplaceStatusMutationIsContainedBeforeRepositoryDispatch() {
        assertContainedBeforeRepositoryDispatch(
                "marketplace listing status mutation",
                () -> controller.updateStatus(
                        "listing-1", new MarketplaceController.StatusUpdateRequest("PUBLISHED")));
    }

    @Test
    void marketplaceDiscoveryIsContainedBeforeRepositoryDispatch() {
        assertContainedBeforeRepositoryDispatch(
                "marketplace discovery read", () -> controller.discovery(10));
    }

    private void assertContainedBeforeRepositoryDispatch(
            String operation,
            org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        assertEquals("FAIL_CLOSED_CONTAINMENT", failure.decision().ruleRef());
        assertEquals(
                operation + " is unavailable until canonical authorization is established",
                failure.decision().detail());
        verifyNoInteractions(listingRepo);
    }
}
