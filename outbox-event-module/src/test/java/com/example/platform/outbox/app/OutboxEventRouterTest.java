package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxEventRouterTest {

    private OutboxEventRouter router;

    @BeforeEach
    void setUp() {
        router = new OutboxEventRouter();
    }

    @Test
    void shouldRegisterAndResolveEventType() {
        router.register("test.event", String.class);
        assertEquals(String.class, router.resolve("test.event"));
    }

    @Test
    void shouldReturnNullForUnknownEventType() {
        assertNull(router.resolve("nonexistent.event"));
    }

    @Test
    void shouldTrackKnownEvents() {
        assertFalse(router.isKnown("test.event"));
        router.register("test.event", String.class);
        assertTrue(router.isKnown("test.event"));
    }

    @Test
    void shouldCountRegistrations() {
        assertEquals(0, router.size());
        router.register("event.a", String.class);
        router.register("event.b", Integer.class);
        assertEquals(2, router.size());
    }
}
