package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.coordination.TaskExecutionContext;
import com.example.platform.outbox.coordination.*;
import com.example.platform.render.domain.asset.marketplace.*;
import com.example.platform.render.infrastructure.asset.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketplaceTaskHandlerTest {

    @Test
    void validateHandlerShouldReportCapability() {
        var h = new MarketplaceValidateTaskHandler(mock(AssetRepository.class), mock(SearchProjectionRepository.class));
        assertEquals(TaskCapability.VALIDATE, h.capability());
    }

    @Test
    void packageHandlerShouldReportCapability() {
        var h = new MarketplacePackageTaskHandler(mock(MarketplaceListingBuilder.class), mock(MarketplaceListingRepository.class));
        assertEquals(TaskCapability.PACKAGE, h.capability());
    }
}
