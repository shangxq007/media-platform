package com.example.platform.federation.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FederationQueryServiceTest {

    private FederationQueryService service;

    @BeforeEach
    void setUp() {
        service = new FederationQueryService();
    }

    @Test
    void overviewReturnsStubStatus() {
        Map<String, Object> overview = service.overview();
        assertEquals("federation-query-module", overview.get("module"));
        assertEquals("stub", overview.get("status"));
        assertNotNull(overview.get("description"));
    }

    @Test
    void executeReturnsStubResponse() {
        Map<String, Object> result = service.execute("SELECT * FROM events", List.of("pg", "es"));
        assertEquals("stub", result.get("status"));
        assertTrue(result.get("message").toString().contains("not implemented"));
    }

    @Test
    void executePreservesQueryInResponse() {
        Map<String, Object> result = service.execute("SELECT 1", List.of("source1"));
        assertEquals("SELECT 1", result.get("query"));
    }

    @Test
    void executePreservesSourcesInResponse() {
        Map<String, Object> result = service.execute("SELECT 1", List.of("pg", "es"));
        assertEquals(List.of("pg", "es"), result.get("sources"));
    }

    @Test
    void executeWithNullQueryReturnsEmptyString() {
        Map<String, Object> result = service.execute(null, List.of("s1"));
        assertEquals("", result.get("query"));
    }

    @Test
    void executeWithNullSourcesReturnsEmptyList() {
        Map<String, Object> result = service.execute("SELECT 1", null);
        assertEquals(List.of(), result.get("sources"));
    }

    @Test
    void isStubReturnsTrue() {
        assertTrue(service.isStub());
    }

    @Test
    void overviewContainsModuleDescription() {
        Map<String, Object> overview = service.overview();
        String description = (String) overview.get("description");
        assertNotNull(description);
        assertFalse(description.isBlank());
    }
}
