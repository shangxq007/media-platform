package com.example.platform.notification.infrastructure;

import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import com.example.platform.notification.domain.NotificationProvider;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.notification.novu", name = "enabled", havingValue = "true", matchIfMissing = false)
public class NovuNotificationProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(NovuNotificationProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final boolean enabled;
    private final RestClient restClient;

    public NovuNotificationProvider(
            @Value("${app.notification.novu.api-key:}") String apiKey,
            @Value("${app.notification.novu.base-url:https://api.novu.co/v1}") String baseUrl,
            RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String channel() {
        return "NOVU";
    }

    @Override
    public String providerCode() {
        return "novu";
    }

    @Override
    public DeliveryResult send(DeliveryCommand command) {
        if (!enabled) {
            log.error("NovuNotificationProvider: Novu is not configured (app.notification.novu.api-key is missing)");
            throw new PlatformException(
                    new ConfigurableErrorCode("NOTIFICATION-NOVU-503-001", 5032001,
                            Map.of("en", "Novu notification provider is not configured", "zh", "Novu 通知提供者未配置"),
                            "notification", 503),
                    "Novu API key not configured");
        }

        String workflowId = command.metadata() != null ? (String) command.metadata().get("novuWorkflowId") : null;
        if (workflowId == null || workflowId.isBlank()) {
            log.error("NovuNotificationProvider: no novuWorkflowId in metadata for eventId={}", command.eventId());
            throw new PlatformException(
                    new ConfigurableErrorCode("NOTIFICATION-NOVU-400-001", 4002001,
                            Map.of("en", "Missing novuWorkflowId in delivery metadata", "zh", "投递元数据中缺少 novuWorkflowId"),
                            "notification", 400),
                    "Missing novuWorkflowId in delivery metadata");
        }

        String subscriberId = command.metadata() != null ? (String) command.metadata().get("subscriberId") : null;
        if (subscriberId == null || subscriberId.isBlank()) {
            subscriberId = command.subject();
        }

        Map<String, Object> payload = Map.of(
                "workflowId", workflowId,
                "to", Map.of("subscriberId", subscriberId),
                "payload", Map.of("subject", command.subject(), "body", command.body())
        );

        try {
            String response = restClient.post()
                    .uri("/events/trigger")
                    .header(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("NovuNotificationProvider: triggered workflow={}, eventId={}", workflowId, command.eventId());
            return new DeliveryResult("SENT", response);
        } catch (RestClientException e) {
            log.error("NovuNotificationProvider: failed to trigger workflow={}, eventId={}, error={}",
                    workflowId, command.eventId(), e.getMessage());
            return new DeliveryResult("FAILED", "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
