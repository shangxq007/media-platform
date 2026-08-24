package com.example.platform.outbox.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.outbox.app.DispatchBooster;
import com.example.platform.outbox.app.OutboxEventDispatcher;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Structural proof that the retired PlatformTask engine cannot regain lifecycle authority. */
class PlatformTaskDeliveryAuthorityTest {

    private static final Set<String> FORBIDDEN_LIFECYCLE_METHODS = Set.of(
            "lease",
            "leaseAndRun",
            "complete",
            "completeTask",
            "fail",
            "failTask",
            "resetStaleLeases",
            "recoverStaleLeases");

    @Test
    void dispatcherDefinitionIsRetiredAndBoosterCoordinatesOutboxDeliveryOnly() {
        assertThatThrownBy(() -> Class.forName(
                        "com.example.platform.outbox.coordination.PlatformTaskDispatcher"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(Arrays.stream(DispatchBooster.class.getDeclaredFields())
                        .map(Field::getType))
                .contains(OutboxEventDispatcher.class)
                .noneMatch(type -> type.getSimpleName().contains("TaskDispatcher"));
    }

    @Test
    void platformTaskRepositoryAndCoordinationServiceExposeNoLifecycleMutation() {
        assertThat(methodNames(PlatformTaskRepository.class))
                .doesNotContainAnyElementsOf(FORBIDDEN_LIFECYCLE_METHODS);
        assertThat(methodNames(PlatformCoordinationService.class))
                .doesNotContainAnyElementsOf(FORBIDDEN_LIFECYCLE_METHODS);
        assertThat(methodNames(PlatformTask.class))
                .doesNotContain("markLeased", "markCompleted", "markFailed");
    }

    private static Set<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toUnmodifiableSet());
    }
}
