package com.example.platform.app;

import com.example.platform.identity.app.IdentityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private IdentityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IdentityProperties();
        properties.setRateLimitEnabled(true);
        properties.setRateLimitRequestsPerMinute(5);
        filter = new RateLimitFilter(properties);
    }

    @Test
    void shouldAllowRequestsWithinLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.1");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, (req, res) -> {});
            assertNotEquals(429, response.getStatus(), "Request " + (i + 1) + " should not be rate limited");
        }
    }

    @Test
    void shouldBlockRequestsOverLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Send 5 allowed requests
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.2");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, (req, res) -> {});
        }

        // 6th request should be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest();
        blockedRequest.setRemoteAddr("192.168.1.2");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blockedRequest, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
        assertNotNull(blockedResponse.getHeader("Retry-After"));
    }

    @Test
    void shouldBypassWhitelistedIps() throws Exception {
        properties.setIpWhitelist(List.of("10.0.0.1"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Send many requests from whitelisted IP
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, (req, res) -> {});
            assertNotEquals(429, response.getStatus());
        }
    }

    @Test
    void shouldExtractClientIpFromXForwardedFor() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.1");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, (req, res) -> {});
        }

        // 6th request from same X-Forwarded-For IP should be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest();
        blockedRequest.addHeader("X-Forwarded-For", "203.0.113.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blockedRequest, blockedResponse, (req, res) -> {});

        assertEquals(429, blockedResponse.getStatus());
    }

    @Test
    void shouldTrackDifferentIpsIndependently() throws Exception {
        // Fill up limit for IP 1
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.10");
            filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {});
        }

        // IP 1 should be blocked
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest();
        blockedRequest.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blockedRequest, blockedResponse, (req, res) -> {});
        assertEquals(429, blockedResponse.getStatus());

        // IP 2 should still be allowed
        MockHttpServletRequest allowedRequest = new MockHttpServletRequest();
        allowedRequest.setRemoteAddr("192.168.1.11");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(allowedRequest, allowedResponse, (req, res) -> {});
        assertNotEquals(429, allowedResponse.getStatus());
    }

    @Test
    void shouldReturnProblemDetailOnRateLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Exceed limit
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.20");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, (req, res) -> {});
        }

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("SECURITY-429-001"));
    }
}
