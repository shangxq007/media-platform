package com.example.platform.sandbox.worker.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class SandboxWorkerApiKeyFilterTest {

    @Test
    void kubernetesHealthProbeRemainsUnauthenticatedWhenKeyIsBlank() throws Exception {
        SandboxWorkerApiKeyFilter filter = new SandboxWorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "");

        for (String method : new String[] {"GET", "HEAD"}) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockHttpServletRequest request = new MockHttpServletRequest(
                    method, "/v1/sandbox/healthz");
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void blankConfiguredKeyRejectsExecutionFailClosed() throws Exception {
        SandboxWorkerApiKeyFilter filter = new SandboxWorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "");

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/v1/sandbox/execute"), response, chain);

        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void configuredKeyLeavesProbeOpenButRejectsMissingExecutionKey() throws Exception {
        SandboxWorkerApiKeyFilter filter = new SandboxWorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "sandbox-secret");

        MockHttpServletRequest probe = new MockHttpServletRequest("GET", "/v1/sandbox/healthz");
        MockHttpServletResponse probeResponse = new MockHttpServletResponse();
        FilterChain probeChain = mock(FilterChain.class);
        filter.doFilter(probe, probeResponse, probeChain);
        verify(probeChain).doFilter(probe, probeResponse);

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        FilterChain rejectedChain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("POST", "/v1/sandbox/execute"), rejected, rejectedChain);
        assertEquals(401, rejected.getStatus());
        verifyNoInteractions(rejectedChain);
    }

    @Test
    void configuredKeyAllowsExactExecutionKey() throws Exception {
        SandboxWorkerApiKeyFilter filter = new SandboxWorkerApiKeyFilter();
        ReflectionTestUtils.setField(filter, "configuredApiKey", "sandbox-secret");

        MockHttpServletRequest accepted = new MockHttpServletRequest("POST", "/v1/sandbox/execute");
        accepted.addHeader(SandboxWorkerApiKeyFilter.API_KEY_HEADER, "sandbox-secret");
        MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
        FilterChain acceptedChain = mock(FilterChain.class);
        filter.doFilter(accepted, acceptedResponse, acceptedChain);
        verify(acceptedChain).doFilter(accepted, acceptedResponse);
    }
}
