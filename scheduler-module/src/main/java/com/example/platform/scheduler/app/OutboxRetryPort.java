package com.example.platform.scheduler.app;

public interface OutboxRetryPort {

    int retryPendingOutboxEvents(int batchSize);
}
