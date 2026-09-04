package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.WebhookParseResult;
import com.example.platform.payment.domain.PaymentState;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.Map;

public final class WebhookPayloadSupport {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private WebhookPayloadSupport() {}

    public static WebhookParseResult parseCommerceWebhook(String body) {
        if (body == null || body.isBlank()) throw new IllegalArgumentException("Webhook body is required");
        try {
            Map<String, Object> payload = PaymentWebhookJson.fromJson(body, MAP);
            Map<String, Object> object = nestedObject(payload);
            Map<String, Object> metadata = nestedMap(object.get("metadata"));
            String eventId = stringOr(payload.get("eventId"), payload.get("event_id"), payload.get("id"));
            String eventType = required(stringOr(payload.get("type"), payload.get("eventType")), "event type");
            String reference = required(stringOr(payload.get("providerReference"),
                    payload.get("externalReference"), payload.get("payment_id"),
                    object.get("payment_id"), object.get("id")), "provider reference");
            eventId = required(eventId, "provider event ID");
            long cursor = longOr(payload.get("eventVersion"), payload.get("version"),
                    payload.get("created"), payload.get("timestamp"));
            String status = stringOr(payload.get("canonicalStatus"), payload.get("status"),
                    object.get("payment_status"), object.get("status"));
            PaymentState state = canonicalState(eventType, status);
            Instant occurredAt = cursor > 1_000_000_000L ? Instant.ofEpochSecond(cursor) : Instant.EPOCH;
            String checkoutSessionId = stringOr(payload.get("checkoutSessionId"),
                    payload.get("checkout_session_id"), object.get("client_reference_id"),
                    metadata.get("checkout_session_id"));
            String tenantId = stringOr(payload.get("tenantId"), payload.get("tenant_id"), metadata.get("tenant_id"));
            String userId = stringOr(payload.get("userId"), payload.get("user_id"), metadata.get("user_id"));
            return new WebhookParseResult(eventId, eventType, cursor, reference, state,
                    occurredAt, checkoutSessionId, tenantId, userId);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Malformed payment webhook", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedObject(Map<String, Object> payload) {
        Map<String, Object> data = nestedMap(payload.get("data"));
        Map<String, Object> object = nestedMap(data.get("object"));
        return object.isEmpty() ? payload : object;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static PaymentState canonicalState(String eventType, String status) {
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        if (java.util.Set.of("unpaid", "pending", "processing", "open", "requires_payment_method")
                .contains(normalizedStatus)) return PaymentState.PENDING;
        if (java.util.Set.of("paid", "succeeded", "charged", "complete", "completed")
                .contains(normalizedStatus)) return PaymentState.SETTLED;
        if (normalizedStatus.contains("authoriz")) return PaymentState.AUTHORIZED;
        if (normalizedStatus.contains("fail")) return PaymentState.FAILED;
        if (normalizedStatus.contains("cancel") || normalizedStatus.contains("expire")) return PaymentState.CANCELLED;
        String normalizedEvent = eventType.toLowerCase();
        if (normalizedEvent.contains("refund")) return PaymentState.REFUNDED;
        if (normalizedEvent.contains("succeed") || normalizedEvent.contains("paid")
                || normalizedEvent.contains("charged") || normalizedEvent.contains("complete")) return PaymentState.SETTLED;
        if (normalizedEvent.contains("authoriz")) return PaymentState.AUTHORIZED;
        if (normalizedEvent.contains("fail")) return PaymentState.FAILED;
        if (normalizedEvent.contains("cancel") || normalizedEvent.contains("expire")) return PaymentState.CANCELLED;
        return PaymentState.PENDING;
    }

    private static long longOr(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) return number.longValue();
            if (value != null) try { return Long.parseLong(value.toString()); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
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
