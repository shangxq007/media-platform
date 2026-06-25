package com.example.platform.outbox.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends PostgreSQL NOTIFY signals to wake up dispatchers.
 *
 * <p>NOTIFY is a wake-up hint, NOT a reliable queue. If the listener is
 * down, polling catches up. This is purely a latency optimization.</p>
 */
@Service
public class PostgresNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotificationService.class);
    private final JdbcTemplate jdbc;

    public PostgresNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void notifyOutboxEvent() {
        try {
            jdbc.execute("NOTIFY outbox_event");
            log.debug("NOTIFY outbox_event sent");
        } catch (Exception e) {
            log.warn("Failed to send NOTIFY outbox_event: {}", e.getMessage());
        }
    }

    public void notifyTaskCreated() {
        try {
            jdbc.execute("NOTIFY platform_task");
            log.debug("NOTIFY platform_task sent");
        } catch (Exception e) {
            log.warn("Failed to send NOTIFY platform_task: {}", e.getMessage());
        }
    }
}
