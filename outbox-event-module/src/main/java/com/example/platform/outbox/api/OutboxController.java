package com.example.platform.outbox.api;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.OutboxEventDispatcher;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OutboxController {
    private final OutboxEventService service;
    private final OutboxEventDispatcher dispatcher;

    public OutboxController(OutboxEventService service, OutboxEventDispatcher dispatcher) {
        this.service = service;
        this.dispatcher = dispatcher;
    }

    // -------------------------------------------------------------------------
    // Internal outbox endpoints (Prompt 13)
    // -------------------------------------------------------------------------

    @PostMapping("/internal/outbox/process-once")
    public Map<String, Object> processOnceInternal() {
        int processed = dispatcher.processBatch(1);
        return Map.of("processed", processed);
    }

    @GetMapping("/internal/outbox/events")
    public List<Map<String, Object>> getOutboxEvents(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(Math.max(1, Math.min(limit, 200)));
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @GetMapping("/outbox/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @GetMapping("/outbox/recent")
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(Math.max(1, Math.min(limit, 200)));
    }

    @PostMapping("/outbox/dispatch")
    public Map<String, Object> dispatch(@RequestParam(defaultValue = "100") int limit) {
        int processed = dispatcher.processBatch(Math.max(1, Math.min(limit, 500)));
        return Map.of("processed", processed);
    }

    @PostMapping("/outbox/process-once/{outboxId}")
    public Map<String, Object> processOnce(@PathVariable String outboxId) {
        boolean success = dispatcher.processOnce(outboxId);
        return Map.of("outboxId", outboxId, "dispatched", success);
    }

    @PostMapping("/outbox/process-batch")
    public Map<String, Object> processBatch(@RequestParam(defaultValue = "100") int limit) {
        int processed = dispatcher.processBatch(Math.max(1, Math.min(limit, 500)));
        return Map.of("processed", processed);
    }

    @GetMapping("/outbox/failed")
    public List<Map<String, Object>> failedEvents(@RequestParam(defaultValue = "50") int limit) {
        return service.failedEvents(Math.max(1, Math.min(limit, 200)));
    }

    @PostMapping("/outbox/retry/{outboxId}")
    public Map<String, Object> retry(@PathVariable String outboxId) {
        boolean success = dispatcher.processOnce(outboxId);
        return Map.of("outboxId", outboxId, "retried", success);
    }

    @PostMapping("/outbox/dead-letter/{outboxId}")
    public Map<String, Object> deadLetter(@PathVariable String outboxId,
            @RequestParam(defaultValue = "Manual dead-letter via API") String reason) {
        dispatcher.deadLetter(outboxId, reason);
        return Map.of("outboxId", outboxId, "status", "DEAD_LETTER", "reason", reason);
    }

    @GetMapping("/outbox/dead-letter")
    public List<Map<String, Object>> deadLetterEvents(@RequestParam(defaultValue = "50") int limit) {
        return service.deadLetterEvents(Math.max(1, Math.min(limit, 200)));
    }

    @PostMapping("/outbox/retry-due")
    public Map<String, Object> retryDue(@RequestParam(defaultValue = "100") int limit) {
        int processed = dispatcher.retryDueEvents();
        return Map.of("processed", processed, "limit", Math.max(1, Math.min(limit, 500)));
    }
}
