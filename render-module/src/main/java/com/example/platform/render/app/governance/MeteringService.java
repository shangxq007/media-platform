package com.example.platform.render.app.governance;

import com.example.platform.render.domain.governance.MeterEvent;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Metering Service — single platform entry for recording consumption facts.
 * Never prices. Never bills. Records facts only.
 */
@Service
public class MeteringService {

    private static final Logger log = LoggerFactory.getLogger(MeteringService.class);
    private final Deque<MeterEvent> events = new ConcurrentLinkedDeque<>();

    public void record(MeterEvent event) {
        events.addFirst(event);
        log.debug("Meter recorded: {} = {} {} (attribution={})",
                event.meterName(), event.quantity(), event.unit(), event.attribution().producerId());
    }

    public void recordAll(List<MeterEvent> batch) {
        batch.forEach(this::record);
    }

    public List<MeterEvent> recent(int limit) {
        return events.stream().limit(limit).toList();
    }

    public int count() {
        return events.size();
    }
}
