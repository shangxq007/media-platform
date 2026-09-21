package com.example.platform.outbox.operations;
import java.util.Map;
/** Internal operational counters, not business authority. HTTP consumers must require platform-admin authority. Failures propagate. */
public interface OutboxOperationalQuery {
    Snapshot snapshot();
    record Snapshot(Map<String, Long> counts) {
        public Snapshot { counts = Map.copyOf(counts); }
        public long pendingCount() { return counts.getOrDefault("PENDING", 0L); }
    }
}
