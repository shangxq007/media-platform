package com.example.platform.web.assets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.asset.marketplace.*;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository.SearchResult;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MarketplaceControllerTest {

    private MarketplaceListingRepository listingRepo;
    private MarketplaceController controller;

    @BeforeEach
    void setUp() {
        listingRepo = mock(MarketplaceListingRepository.class);
        controller = new MarketplaceController(listingRepo);
    }

    @Test
    void shouldSearchWithPagination() {
        SearchResult result = new SearchResult(42, 0, 20, List.of());
        when(listingRepo.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(result);

        var response = controller.search(null, null, "PUBLISHED", null, null, 0, 20);

        assertEquals(42, response.total());
        assertEquals(0, response.offset());
    }

    @Test
    void shouldRejectInvalidTransition() {
        var listing = MarketplaceListing.draft("ml_1", "a1", "t1", "p1",
                MarketplaceListingType.MEDIA, "test.mp4");
        when(listingRepo.findByAssetId(eq("ml_1"), any())).thenReturn(Optional.of(listing));

        var body = new MarketplaceController.StatusUpdateRequest("PUBLISHED");
        ResponseEntity<MarketplaceController.MarketplaceListingDto> response =
                controller.updateStatus("ml_1", body);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void shouldAllowDraftToReady() {
        var draft = MarketplaceListing.draft("ml_1", "a1", "t1", "p1",
                MarketplaceListingType.MEDIA, "test.mp4");
        var ready = draft.withStatus(MarketplaceListingStatus.READY);
        when(listingRepo.findByAssetId(eq("ml_1"), any()))
                .thenReturn(Optional.of(draft), Optional.of(ready));

        var body = new MarketplaceController.StatusUpdateRequest("READY");
        ResponseEntity<MarketplaceController.MarketplaceListingDto> response =
                controller.updateStatus("ml_1", body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("READY", response.getBody().status());
    }
}
