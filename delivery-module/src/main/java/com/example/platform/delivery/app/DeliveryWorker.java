package com.example.platform.delivery.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryJobService deliveryJobService;
    private final boolean workerEnabled;

    public DeliveryWorker(DeliveryJobService deliveryJobService,
                          @Value("${delivery.worker-enabled:true}") boolean workerEnabled) {
        this.deliveryJobService = deliveryJobService;
        this.workerEnabled = workerEnabled;
    }

    @Scheduled(fixedDelayString = "${delivery.poll-interval-ms:5000}")
    public void poll() {
        if (!workerEnabled) {
            return;
        }
        int n = deliveryJobService.processQueued(16);
        if (n > 0) {
            log.debug("Processed {} delivery jobs", n);
        }
    }
}
