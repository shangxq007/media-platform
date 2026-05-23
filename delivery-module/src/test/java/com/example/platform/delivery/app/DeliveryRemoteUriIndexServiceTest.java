package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

class DeliveryRemoteUriIndexServiceTest {

    @Test
    void findByAnyUriReturnsEmptyForBlank() {
        DeliveryRemoteUriIndexService service = new DeliveryRemoteUriIndexService(org.mockito.Mockito.mock(DSLContext.class));
        assertEquals(0, service.findByAnyUri("", null, 10).size());
        assertEquals(0, service.findByAnyUri("  ", "p1", 10).size());
        assertEquals(0, service.findByRemoteUri(null, null, 10).size());
    }
}
