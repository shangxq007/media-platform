package com.example.platform.security;

import com.example.platform.shared.web.TenantContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtAuthFilter filter;
    private JwtProperties jwtProperties;
    private SecretKey key;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties("test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3600000);
        filter = new JwtAuthFilter(jwtProperties);
        key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
        filterChain = mock(FilterChain.class);
    }

    private String createToken(String subject, String tenantId, List<String> roles) {
        return Jwts.builder()
                .subject(subject)
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    void shouldSkipNonWebPaths() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/mcp/render/jobs");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotSkipWebPaths() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotSkipPromptPaths() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/prompts/templates");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldRejectMissingToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getHeader("Authorization")).thenReturn(null);

        StringWriter sw = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(sw.toString().contains("Missing or malformed JWT token"));
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getHeader("Authorization")).thenReturn("NotBearer token123");

        StringWriter sw = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldAcceptValidToken() throws Exception {
        String token = createToken("user-1", "tenant-1", List.of("USER"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        StringWriter sw = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).setAttribute("jwt.subject", "user-1");
        verify(request).setAttribute("jwt.tenantId", "tenant-1");
        verify(request).setAttribute(eq("jwt.roles"), any(List.class));
        verify(request).setAttribute("request.source", "WEB");
        assertNull(TenantContext.get());
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        String expiredToken = Jwts.builder()
                .subject("user-1")
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key)
                .compact();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        StringWriter sw = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(sw.toString().contains("Invalid or expired JWT token"));
    }

    @Test
    void shouldClearTenantContextAfterFilter() throws Exception {
        String token = createToken("user-1", "tenant-1", List.of("USER"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(TenantContext.get());
    }
}
