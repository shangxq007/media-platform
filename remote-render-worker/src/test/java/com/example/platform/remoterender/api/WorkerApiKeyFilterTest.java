package com.example.platform.remoterender.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class WorkerApiKeyFilterTest {

    @Test
    void getAndHeadHealthBypassAuthenticationEvenWhenKeyIsBlank() throws Exception {
        WorkerApiKeyFilter filter = new WorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "  ");

        for (String method : java.util.List.of("GET", "HEAD")) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/healthz");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void blankConfiguredKeyRejectsAllRequests() throws Exception {
        WorkerApiKeyFilter filter = new WorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "  ");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/remote-worker/register"), response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void configuredKeyIsRequiredAndAllowsOnlyExactMatch() throws Exception {
        WorkerApiKeyFilter filter = new WorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "worker-secret");

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        FilterChain rejectedChain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/remote-worker/workers"), rejected, rejectedChain);
        assertEquals(401, rejected.getStatus());
        verifyNoInteractions(rejectedChain);

        MockHttpServletRequest accepted = new MockHttpServletRequest("GET", "/api/remote-worker/workers");
        accepted.addHeader("X-Worker-Api-Key", "worker-secret");
        MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
        FilterChain acceptedChain = mock(FilterChain.class);
        filter.doFilter(accepted, acceptedResponse, acceptedChain);
        verify(acceptedChain).doFilter(accepted, acceptedResponse);
    }
}
