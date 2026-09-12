package com.example.platform.outbox.app;

import com.example.platform.outbox.api.event.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import org.springframework.stereotype.Component;

/** Sole versioned codec registry. Unknown persisted contracts are quarantined, never coerced. */
@Component
public class OutboxEventRouter {
    private final Map<String, OutboxEventType<?>> routes = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    public OutboxEventRouter(List<OutboxEventCatalog> catalogs) {
        catalogs.forEach(catalog -> catalog.types().forEach(this::register));
    }
    private void register(OutboxEventType<?> type) {
        if (routes.putIfAbsent(key(type.name(), type.version()), type) != null)
            throw new IllegalArgumentException("Duplicate outbox type/version: " + type.name());
    }
    private static String key(String name, int version) { return name + ":" + version; }
    public int size() { return routes.size(); }
    public String encode(OutboxAppend<?> append) {
        if (routes.get(key(append.type().name(), append.type().version())) != append.type())
            throw new IllegalArgumentException("Event contract is not registered by its defining domain");
        try {
            return mapper.writeValueAsString(new StoredEnvelope(1, append.tenantId(), append.type().name(), append.type().version(),
                    append.type().aggregateType(), append.aggregateId(), mapper.valueToTree(append.payload())));
        } catch (Exception e) { throw new IllegalArgumentException("Cannot encode typed outbox event", e); }
    }
    public Decoded decode(String name, int version, String aggregateType, String aggregateId, String json) {
        OutboxEventType<?> type = routes.get(key(name, version));
        if (type == null) throw new InvalidEvent("UNSUPPORTED_EVENT_VERSION", "Unsupported event type/version");
        try {
            var stored = mapper.readValue(json, StoredEnvelope.class);
            if (stored.envelopeVersion() != 1 || !name.equals(stored.eventType()) || version != stored.eventVersion()
                    || !aggregateType.equals(stored.aggregateType()) || !aggregateId.equals(stored.aggregateId()))
                throw new InvalidEvent("INVALID_EVENT_ENVELOPE", "Envelope and persisted identity do not agree");
            return decodePayload(type, stored);
        } catch (InvalidEvent e) { throw e; }
        catch (Exception e) { throw new InvalidEvent("MALFORMED_EVENT_PAYLOAD", "Malformed typed event envelope or payload"); }
    }
    private <T extends Record> Decoded decodePayload(OutboxEventType<T> type, StoredEnvelope stored) throws Exception {
        T payload = mapper.treeToValue(stored.payload(), type.payloadType());
        var checked = type.append(stored.tenantId(), payload, null);
        if (!checked.aggregateId().equals(stored.aggregateId()) || !type.aggregateType().equals(stored.aggregateType()))
            throw new InvalidEvent("INVALID_EVENT_SCOPE", "Payload identity does not agree with envelope");
        return new Decoded(stored.tenantId(), payload);
    }
    public record StoredEnvelope(int envelopeVersion, String tenantId, String eventType, int eventVersion,
            String aggregateType, String aggregateId, JsonNode payload) {}
    public record Decoded(String tenantId, Record payload) {}
    public static final class InvalidEvent extends RuntimeException {
        private final String code;
        public InvalidEvent(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
