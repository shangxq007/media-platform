package com.example.platform.outbox.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PlatformTaskTest {

    @Test
    void platformTaskIsImmutableDeliveryDataWithoutLifecycleMutationMethods() {
        assertThat(Arrays.stream(PlatformTask.class.getDeclaredMethods())
                        .map(Method::getName))
                .doesNotContain(
                        "markLeased",
                        "markCompleted",
                        "markFailed",
                        "complete",
                        "fail",
                        "expire",
                        "retry");
    }
}
