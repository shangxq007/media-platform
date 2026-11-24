package com.example.platform.render.app.operation;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompositionAspectOperationContractTest {
    @Test
    void genericBatchAdmitsAnExactNonPresetAspect() {
        var advance = new TimelineBatchCommand.Operation("timeline.composition.aspect.set", null,
                Map.of("aspectRatio", Map.of("format", "composition-aspect-rational-v1",
                        "numerator", 13, "denominator", 11)));
        var body = new TimelineBatchCommand("aspect-command", "base-revision", "base-hash", null, List.of(advance));
        assertThatCode(() -> new TimelineBatchCommandMapper().toBatchRequest("tenant", "project", body))
                .doesNotThrowAnyException();
    }
}
