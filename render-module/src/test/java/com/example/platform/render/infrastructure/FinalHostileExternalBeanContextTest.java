package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.render.domain.producer.ProducerContext;
import com.example.platform.render.infrastructure.asset.provider.RemotionProducer;
import com.example.platform.render.infrastructure.renderplan.MLTTool;
import com.example.platform.render.infrastructure.renderplan.RemotionTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class FinalHostileExternalBeanContextTest {

    @Test
    void productionComponentsExposeZeroFakeExternalSuccessPaths() {
        try (var context = new AnnotationConfigApplicationContext(
                RemotionProducer.class, RemotionTool.class, MLTTool.class)) {
            RemotionProducer producer = context.getBean(RemotionProducer.class);
            RemotionTool remotion = context.getBean(RemotionTool.class);
            MLTTool mlt = context.getBean(MLTTool.class);
            String remotionNode = "context-remotion-" + UUID.randomUUID();
            String mltNode = "context-mlt-" + UUID.randomUUID();
            Path remotionOutput = Path.of("/tmp/renderplan-output", remotionNode, "output.mp4");
            Path mltOutput = Path.of("/tmp/renderplan-output", mltNode, "output.mp4");

            var producerResult = producer.execute(ProducerContext.of(
                    "execution-1", "tenant-1", "project-1", List.of(), List.of("PREVIEW")));
            var remotionResult = remotion.execute(remotionNode, "SCENE", Map.of(), Map.of());
            var mltResult = mlt.execute(mltNode, "TRANSITION", Map.of(), Map.of());

            long fakeExternalSuccessPathCount = 0;
            if (producerResult.success() || !producerResult.producedProductIds().isEmpty()) {
                fakeExternalSuccessPathCount++;
            }
            if (remotion.isAvailable() || remotionResult.success()
                    || remotionResult.outputUri() != null || Files.exists(remotionOutput)) {
                fakeExternalSuccessPathCount++;
            }
            if (mlt.isAvailable() || mltResult.success()
                    || mltResult.outputUri() != null || Files.exists(mltOutput)) {
                fakeExternalSuccessPathCount++;
            }

            System.out.println("FAKE_EXTERNAL_SUCCESS_PATH_COUNT=" + fakeExternalSuccessPathCount);
            assertEquals(0, fakeExternalSuccessPathCount, "FAKE_EXTERNAL_SUCCESS_PATH_COUNT");
            assertFalse(Files.exists(remotionOutput));
            assertFalse(Files.exists(mltOutput));
        }
    }
}
