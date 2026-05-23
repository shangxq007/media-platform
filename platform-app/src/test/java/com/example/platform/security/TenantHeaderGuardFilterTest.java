package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantHeaderGuardFilterTest {

    private final OAuth2SecurityProperties props =
            new OAuth2SecurityProperties(
                    true, "https://auth.example/", null, "tenantId", "roles", "platform_user_id",
                    false, true, true, "tenant-1");
    private final TenantHeaderGuardFilter filter = new TenantHeaderGuardFilter(props);

    @Test
    void rejectsMismatchedTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.tenantId", "tenant-1");
        request.addHeader("X-Tenant-ID", "tenant-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsMatchingOrMissingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.tenantId", "tenant-1");
        request.addHeader("X-Tenant-ID", "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
