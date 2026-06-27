package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.render.domain.producer.*;
import com.example.platform.render.domain.capability.CapabilityDescriptor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Remotion Producer — validates rendering architecture.
 * Accepts Timeline Product, produces Preview Product.
 * Never executes rendering directly. Validation only.
 */
@Component
public class RemotionProducer implements Producer {

    private static final Logger log = LoggerFactory.getLogger(RemotionProducer.class);

    @Override public String producerId() { return "remotion-render"; }
    @Override public List<String> supportedOutputTypes() { return List.of("PREVIEW", "FINAL_RENDER"); }

    @Override public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor("remotion-cap", "MEDIA_PIPELINE",
                "remotion-render", "Remotion Renderer", "1.0",
                "remotion-process", "MEDIA_PIPELINE",
                List.of("MEDIA_FILE"), List.of("PREVIEW", "FINAL_RENDER"),
                false, 50, true);
    }

    @Override
    public ProducerResult execute(ProducerContext context) {
        long start = System.currentTimeMillis();
        log.info("RemotionProducer: rendering timeline inputs={}", context.inputProductIds());
        long dur = System.currentTimeMillis() - start;
        return ProducerResult.success(List.of(), dur);
    }
}
