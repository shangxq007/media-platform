package com.example.platform.outbox.api;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.OutboxEventDispatcher;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "app.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
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
        throw FailClosedAuthorization.unavailable("internal outbox processing");
    }

    @GetMapping("/internal/outbox/events")
    public List<Map<String, Object>> getOutboxEvents(@RequestParam(defaultValue = "20") int limit) {
        throw FailClosedAuthorization.unavailable("internal outbox event inspection");
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @GetMapping("/outbox/overview")
    public Map<String, Object> overview() {
        throw FailClosedAuthorization.unavailable("outbox overview inspection");
    }

    @GetMapping("/outbox/recent")
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "20") int limit) {
        throw FailClosedAuthorization.unavailable("outbox event inspection");
    }

    @PostMapping("/outbox/dispatch")
    public Map<String, Object> dispatch(@RequestParam(defaultValue = "100") int limit) {
        throw FailClosedAuthorization.unavailable("outbox dispatch");
    }

    @PostMapping("/outbox/process-once/{outboxId}")
    public Map<String, Object> processOnce(@PathVariable String outboxId) {
        throw FailClosedAuthorization.unavailable("single outbox processing");
    }

    @PostMapping("/outbox/process-batch")
    public Map<String, Object> processBatch(@RequestParam(defaultValue = "100") int limit) {
        throw FailClosedAuthorization.unavailable("outbox batch processing");
    }

    @GetMapping("/outbox/failed")
    public List<Map<String, Object>> failedEvents(@RequestParam(defaultValue = "50") int limit) {
        throw FailClosedAuthorization.unavailable("failed outbox event inspection");
    }

    @PostMapping("/outbox/retry/{outboxId}")
    public Map<String, Object> retry(@PathVariable String outboxId) {
        throw FailClosedAuthorization.unavailable("outbox retry");
    }

    @PostMapping("/outbox/dead-letter/{outboxId}")
    public Map<String, Object> deadLetter(@PathVariable String outboxId,
            @RequestParam(defaultValue = "Manual dead-letter via API") String reason) {
        throw FailClosedAuthorization.unavailable("outbox dead-letter mutation");
    }

    @GetMapping("/outbox/dead-letter")
    public List<Map<String, Object>> deadLetterEvents(@RequestParam(defaultValue = "50") int limit) {
        throw FailClosedAuthorization.unavailable("dead-letter event inspection");
    }

    @PostMapping("/outbox/retry-due")
    public Map<String, Object> retryDue(@RequestParam(defaultValue = "100") int limit) {
        throw FailClosedAuthorization.unavailable("global outbox retry");
    }
}
