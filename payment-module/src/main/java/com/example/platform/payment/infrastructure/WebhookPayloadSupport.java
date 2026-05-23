package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.WebhookParseResult;
import com.example.platform.shared.Jsons;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

public final class WebhookPayloadSupport {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private WebhookPayloadSupport() {}

    public static WebhookParseResult parseCommerceWebhook(String body, String defaultReference) {
        if (body == null || body.isBlank()) {
            return new WebhookParseResult("payment.succeeded", 1, defaultReference, false);
        }
        try {
            Map<String, Object> payload = Jsons.fromJson(body, MAP);
            String eventType = stringOr(payload.get("type"), payload.get("eventType"), "payment.succeeded");
            String checkoutSessionId = stringOr(payload.get("checkoutSessionId"), payload.get("checkout_session_id"));
            String tenantId = stringOr(payload.get("tenantId"), payload.get("tenant_id"));
            String userId = stringOr(payload.get("userId"), payload.get("user_id"));
            String reference = stringOr(payload.get("providerReference"), payload.get("externalReference"), defaultReference);
            String status = stringOr(payload.get("status"), payload.get("canonicalStatus"), "paid");
            return new WebhookParseResult(
                    eventType, 1, reference, false, status, checkoutSessionId, tenantId, userId);
        } catch (Exception ignored) {
            return new WebhookParseResult("payment.succeeded", 1, defaultReference, false);
        }
    }

    private static String stringOr(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
