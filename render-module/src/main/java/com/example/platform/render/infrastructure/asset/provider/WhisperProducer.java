package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.render.domain.producer.*;
import com.example.platform.render.domain.capability.CapabilityDescriptor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Whisper ASR Producer — validates the end-to-end platform pipeline.
 * Accepts Audio Product, produces Transcript Product.
 * Minimal implementation for architecture validation.
 */
@Component
public class WhisperProducer implements Producer {

    private static final Logger log = LoggerFactory.getLogger(WhisperProducer.class);

    @Override public String producerId() { return "whisper-asr"; }
    @Override public List<String> supportedOutputTypes() { return List.of("TRANSCRIPT"); }

    @Override public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor("whisper-asr-cap", "ASR",
                "whisper-asr", "Whisper ASR", "1.0",
                "local-process", "ASR",
                List.of("JSON_DOCUMENT"), List.of("TRANSCRIPT"),
                false, 50, true);
    }

    @Override
    public ProducerResult execute(ProducerContext context) {
        long start = System.currentTimeMillis();
        log.info("WhisperProducer: processing audio inputs={}", context.inputProductIds());
        // Validation stub — real implementation delegates to WhisperAsrProvider
        long dur = System.currentTimeMillis() - start;
        return ProducerResult.success(List.of(), dur);
    }
}
