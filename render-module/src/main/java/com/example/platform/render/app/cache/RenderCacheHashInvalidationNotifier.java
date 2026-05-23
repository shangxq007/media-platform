package com.example.platform.render.app.cache;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.shared.events.RenderCacheHashInvalidatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link RenderCacheHashInvalidatedEvent} and optionally POSTs to a configured outbound webhook URL.
 */
@Component
public class RenderCacheHashInvalidationNotifier {

    private static final Logger log = LoggerFactory.getLogger(RenderCacheHashInvalidationNotifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ApplicationEventPublisher eventPublisher;
    private final RenderCacheProperties cacheProperties;

    public RenderCacheHashInvalidationNotifier(ApplicationEventPublisher eventPublisher,
                                               RenderCacheProperties cacheProperties) {
        this.eventPublisher = eventPublisher;
        this.cacheProperties = cacheProperties;
    }

    public void notifyIfNeeded(String tenantId,
                               String projectId,
                               String renderJobId,
                               String baseJobId,
                               Set<String> invalidatedTaskIds) {
        if (invalidatedTaskIds == null || invalidatedTaskIds.isEmpty()) {
            return;
        }
        List<String> taskIds = List.copyOf(invalidatedTaskIds);
        var event = new RenderCacheHashInvalidatedEvent(
                renderJobId,
                projectId,
                tenantId,
                baseJobId,
                taskIds,
                taskIds.size(),
                Instant.now());
        eventPublisher.publishEvent(event);
        log.info("Published RenderCacheHashInvalidatedEvent job={} baseJob={} tasks={}",
                renderJobId, baseJobId, taskIds);
        postOutboundWebhook(event);
    }

    public static Set<String> parseInvalidatedTaskIds(Map<String, String> planMetadata) {
        if (planMetadata == null || planMetadata.isEmpty()) {
            return Set.of();
        }
        String raw = planMetadata.get("hashInvalidatedTaskIds");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Set.of(raw.split(","));
    }

    private void postOutboundWebhook(RenderCacheHashInvalidatedEvent event) {
        if (!cacheProperties.isWebhookEnabled()) {
            return;
        }
        String url = cacheProperties.getWebhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventType", "render.cache.hash_invalidated");
            body.put("renderJobId", event.renderJobId());
            body.put("projectId", event.projectId());
            body.put("tenantId", event.tenantId());
            body.put("baseJobId", event.baseJobId());
            body.put("invalidatedTaskIds", event.invalidatedTaskIds());
            body.put("invalidatedCount", event.invalidatedCount());
            body.put("detectedAt", event.detectedAt().toString());

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            String secret = cacheProperties.getWebhookSecret();
            if (secret != null && !secret.isBlank()) {
                builder.header("X-Render-Cache-Signature", secret);
            }
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Render cache webhook returned status={} url={}", response.statusCode(), url);
            } else {
                log.debug("Render cache webhook delivered job={}", event.renderJobId());
            }
        } catch (Exception e) {
            log.warn("Render cache webhook delivery failed job={}: {}", event.renderJobId(), e.getMessage());
        }
    }
}
