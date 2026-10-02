package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.platform.workerfabric.domain.DefaultHostResourceProbe;
import com.example.platform.workerfabric.domain.LocalHostRegistrationLoop;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import java.lang.reflect.Method;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * P2-5b-2a-1-2-R6: worker-scoped bounded host registration wiring loads only for the worker role and
 * assembles the expected beans with the owner-decided values.
 */
class WorkerRegistrationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WorkerRegistrationConfiguration.class)
            .withBean(WorkerFabricRegistrationBoundary.class,
                    () -> mock(WorkerFabricRegistrationBoundary.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void workerProfileLoadsRegistrationBeans() {
        runner.withPropertyValues("platform.runtime.role=WORKER").run(context -> {
            assertThat(context).hasSingleBean(PhysicalHostId.class);
            assertThat(context).hasSingleBean(DefaultHostResourceProbe.class);
            assertThat(context).hasSingleBean(LocalHostRegistrationLoop.class);
            assertThat(context).hasSingleBean(WorkerRegistrationConfiguration.LocalHostRegistrationScheduler.class);
            assertThat(context.getBean(PhysicalHostId.class).value())
                    .isEqualTo(WorkerRegistrationConfiguration.LOCAL_HOST_ID);
        });
    }

    @Test
    void nonWorkerProfileLoadsNoRegistrationBeans() {
        runner.withPropertyValues("platform.runtime.role=API").run(context -> {
            assertThat(context).doesNotHaveBean(PhysicalHostId.class);
            assertThat(context).doesNotHaveBean(DefaultHostResourceProbe.class);
            assertThat(context).doesNotHaveBean(LocalHostRegistrationLoop.class);
            assertThat(context).doesNotHaveBean(WorkerRegistrationConfiguration.LocalHostRegistrationScheduler.class);
        });
    }

    @Test
    void workerScratchRootIsConfigurable() {
        runner.withPropertyValues(
                        "platform.runtime.role=WORKER",
                        "platform.ffmpeg-worker.work-root=/tmp/r6-worker-scratch")
                .run(context -> assertThat(context).hasSingleBean(DefaultHostResourceProbe.class));
    }

    @Test
    void localHostIdConstantIsTheBoundedLocalIdentity() {
        assertThat(WorkerRegistrationConfiguration.LOCAL_HOST_ID).isEqualTo("local");
        assertThat(PhysicalHostId.of(WorkerRegistrationConfiguration.LOCAL_HOST_ID).value())
                .isEqualTo("local");
    }

    @Test
    void schedulerTickIsNoArgAndFixedDelayTenSeconds() throws Exception {
        Method tick = WorkerRegistrationConfiguration.LocalHostRegistrationScheduler.class
                .getMethod("scheduledRegister");

        assertThat(tick.getParameterCount()).isZero();
        Scheduled scheduled = tick.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(10_000L);
        assertThat(scheduled.fixedRate()).isEqualTo(-1L);
    }
}
