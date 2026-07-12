package com.example.platform.outbox.subscription;

/**
 * Reference to delivery destination configuration.
 * Format: type:id (e.g., "webhook_endpoint:wh_xxx")
 * Does NOT contain secrets or raw URLs.
 */
public record DestinationRef(String type, String id) {
    public DestinationRef {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type required");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
    }

    public static DestinationRef parse(String ref) {
        int idx = ref.indexOf(':');
        if (idx <= 0) throw new IllegalArgumentException("Invalid DestinationRef format: " + ref);
        return new DestinationRef(ref.substring(0, idx), ref.substring(idx + 1));
    }

    @Override
    public String toString() { return type + ":" + id; }
}
