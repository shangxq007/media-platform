package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.render.domain.producer.*;
import com.example.platform.render.domain.capability.CapabilityDescriptor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Remotion Producer — validates rendering architecture.
 * Accepts Timeline Product, produces Preview Product.
 * Never executes rendering directly. Validation only.
 */
@Component
public class RemotionProducer implements Producer {

    static final String UNAVAILABLE_REASON =
            "Remotion producer is unavailable until a governed execution provider is configured";

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
        return ProducerResult.failure(UNAVAILABLE_REASON, 0);
    }
}
