package com.example.platform.app;

import com.example.platform.identity.app.IdentityProperties;
import com.example.platform.shared.web.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final IdentityProperties identityProperties;
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimits = new ConcurrentHashMap<>();

    public RateLimitFilter(IdentityProperties identityProperties) {
        this.identityProperties = identityProperties;
    }

    @Bean
    @Order(2)
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(this);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(2);
        registration.setEnabled(identityProperties.isRateLimitEnabled());
        return registration;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !identityProperties.isRateLimitEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        List<String> whitelist = identityProperties.getIpWhitelist();

        if (whitelist != null && !whitelist.isEmpty() && whitelist.contains(clientIp)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isRateLimited(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Max " + identityProperties.getRateLimitRequestsPerMinute()
                            + " requests per minute.");
            pd.setTitle(CommonErrorCode.RATE_LIMIT_EXCEEDED.title());
            pd.setProperty("code", CommonErrorCode.RATE_LIMIT_EXCEEDED.code());
            pd.setProperty("retryAfter", "60");
            response.setStatus(429);
            response.setContentType("application/problem+json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + pd.getTitle()
                    + "\",\"status\":429,\"detail\":\"" + pd.getDetail()
                    + "\",\"code\":\"" + CommonErrorCode.RATE_LIMIT_EXCEEDED.code() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String clientIp) {
        int maxRequests = identityProperties.getRateLimitRequestsPerMinute();
        long now = Instant.now().getEpochSecond();
        long windowStart = now - (now % 60);

        pruneStaleEntries(windowStart);

        RateLimitEntry entry = rateLimits.compute(clientIp, (ip, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new RateLimitEntry(windowStart, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() > maxRequests;
    }

    /** Drop entries older than two minute-windows to avoid unbounded map growth. */
    private void pruneStaleEntries(long currentWindowStart) {
        long staleBefore = currentWindowStart - 120;
        rateLimits.entrySet().removeIf(e -> e.getValue().windowStart < staleBefore);
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String rightmost = xForwardedFor.split(",")[xForwardedFor.split(",").length - 1].trim();
            if (!rightmost.isEmpty()) {
                return remoteAddr + ":" + rightmost;
            }
        }
        return remoteAddr;
    }

    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count;
        RateLimitEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
