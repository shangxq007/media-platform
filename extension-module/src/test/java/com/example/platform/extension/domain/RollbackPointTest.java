package com.example.platform.extension.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RollbackPointTest {

    @Test
    void shouldCreateValidRollbackPoint() {
        RollbackPoint point = new RollbackPoint("rb-1", "ext-1", "1.0.0",
                "s3://bucket/ext-1-1.0.0.jar", null, null,
                OffsetDateTime.now(), "admin", true);

        assertEquals("rb-1", point.id());
        assertEquals("ext-1", point.extensionCode());
        assertEquals("1.0.0", point.version());
        assertTrue(point.active());
    }

    @Test
    void shouldThrowOnBlankExtensionCode() {
        assertThrows(IllegalArgumentException.class, () ->
                new RollbackPoint("rb-1", "", "1.0.0", null, null, null,
                        OffsetDateTime.now(), "admin", true));
    }

    @Test
    void shouldThrowOnBlankVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                new RollbackPoint("rb-1", "ext-1", "", null, null, null,
                        OffsetDateTime.now(), "admin", true));
    }
}
