package com.example.platform.render.domain.producer;

import com.example.platform.render.domain.capability.CapabilityDescriptor;
import java.util.List;

/**
 * SPI for Producers — the canonical execution entry.
 * All processing components implement this interface.
 */
public interface Producer {
    String producerId();
    List<String> supportedOutputTypes();
    ProducerResult execute(ProducerContext context);

    default List<String> requiredCapabilities() { return List.of(); }
    default String preferredBackend() { return null; }
    default List<String> supportedRepresentations() { return List.of("JSON_DOCUMENT"); }
    default CapabilityDescriptor descriptor() {
        return CapabilityDescriptor.of("unknown", producerId(), "local-process", "local-process");
    }
}
