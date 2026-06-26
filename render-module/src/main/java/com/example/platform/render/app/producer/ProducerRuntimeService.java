package com.example.platform.render.app.producer;

import com.example.platform.render.domain.producer.*;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Producer Runtime — canonical execution entry for every Producer.
 *
 * Coordinates: ProductRuntime (inputs/outputs), StorageRuntime (materialization),
 * Producer SPI (execution), Product Graph (dependencies).
 *
 * Never accesses repositories directly. Never resolves storage paths directly.
 */
@Service
public class ProducerRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(ProducerRuntimeService.class);
    private final Map<String, Producer> producers = new LinkedHashMap<>();

    public ProducerRuntimeService(List<Producer> allProducers) {
        for (Producer p : allProducers) {
            producers.put(p.producerId(), p);
            log.info("Producer registered: id={} outputs={}", p.producerId(), p.supportedOutputTypes());
        }
    }

    public ProducerResult execute(String producerId, ProducerContext context) {
        long start = System.currentTimeMillis();
        Producer producer = producers.get(producerId);
        if (producer == null) {
            return ProducerResult.failure("Producer not found: " + producerId, System.currentTimeMillis() - start);
        }

        log.info("Producer execution started: id={} inputs={}", producerId, context.inputProductIds());
        ProducerResult result = producer.execute(context);
        long dur = System.currentTimeMillis() - start;

        log.info("Producer execution finished: id={} outputs={} dur={}ms success={}",
                producerId, result.producedProductIds(), dur, result.success());
        return result;
    }

    public List<String> listProducers() {
        return new ArrayList<>(producers.keySet());
    }
}
