package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SimpleModelRouterTest {

    @Test
    void routeReturnsDefaultProvider() {
        SimpleModelRouter router = new SimpleModelRouter("stubChatProvider");
        String result = router.route("summarize");
        assertEquals("stubChatProvider", result);
    }

    @Test
    void routeIgnoresCapability() {
        SimpleModelRouter router = new SimpleModelRouter("openAiChatProvider");
        assertEquals("openAiChatProvider", router.route("summarize"));
        assertEquals("openAiChatProvider", router.route("translate"));
        assertEquals("openAiChatProvider", router.route("any-capability"));
    }

    @Test
    void routeWithCustomDefault() {
        SimpleModelRouter router = new SimpleModelRouter("customProvider");
        assertEquals("customProvider", router.route("capability"));
    }

    @Test
    void routeReturnsNonNull() {
        SimpleModelRouter router = new SimpleModelRouter("stubChatProvider");
        assertNotNull(router.route("test"));
    }
}
