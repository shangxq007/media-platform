package com.example.platform.render.infrastructure.asset.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.producer.ProducerContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemotionProducerFailClosedTest {

    @Test
    void missingGovernedProviderReturnsTypedFailureWithoutFabricatedOutput() {
        var result = new RemotionProducer().execute(ProducerContext.of(
                "execution-1", "tenant-1", "project-1", List.of("input-1"), List.of("PREVIEW")));

        assertFalse(result.success());
        assertTrue(result.producedProductIds().isEmpty());
        assertEquals(0, result.executionDurationMs());
        assertEquals(RemotionProducer.UNAVAILABLE_REASON, result.error());
    }
}
