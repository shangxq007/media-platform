package com.example.platform.federation.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class FederationQueryServiceTest {

    private final FederationQueryService service = new FederationQueryService();

    @Test
    void overviewAndExecutionAreTypedUnavailable() {
        FederationQueryUnavailableException overview = assertThrows(
                FederationQueryUnavailableException.class, service::overview);
        assertEquals(503, overview.getErrorCode().status());

        assertThrows(FederationQueryUnavailableException.class,
                () -> service.execute("SELECT 1", List.of("source")));
    }
}
