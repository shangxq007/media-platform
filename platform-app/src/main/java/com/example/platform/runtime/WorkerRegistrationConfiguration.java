package com.example.platform.runtime;

import com.example.platform.workerfabric.domain.DefaultHostResourceProbe;
import com.example.platform.workerfabric.domain.LocalHostRegistrationLoop;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * P2-5b-2a-1-2-R3b-R2: worker-scoped bounded local host/runtime registration.
 *
 * <p>Owner decisions applied: the configuration is worker-profile only, lives in the worker runtime
 * package ({@code com.example.platform.runtime}, added to the worker component scan with explicit
 * owner authorization), and does <b>not</b> assemble {@code HostResourceAgent} — the registration
 * loop is the sole publisher and the snapshot travels inside {@code HostRegistration}.
 *
 * <p>The loop runs on a worker-local timer (10 s fixed delay) and re-registers with a 30 s validity
 * window. A failed tick is logged and never swallowed silently: the previous registration then
 * expires on its own window and the next tick retries the observation.
 */
@Configuration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@EnableScheduling
public class WorkerRegistrationConfiguration {

    /** Bounded V1 single-host identity; exported so the execution half reuses the same host scope. */
    public static final String LOCAL_HOST_ID = "local";

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistrationConfiguration.class);

    @Bean
    public PhysicalHostId localPhysicalHostId() {
        return PhysicalHostId.of(LOCAL_HOST_ID);
    }

    @Bean
    public DefaultHostResourceProbe defaultHostResourceProbe(
            @Value("${platform.ffmpeg-worker.work-root:/tmp/media-platform-ffmpeg-worker}")
            String workRoot) {
        return DefaultHostResourceProbe.forLocalHost(Path.of(workRoot));
    }

    @Bean
    public LocalHostRegistrationLoop localHostRegistrationLoop(
            DefaultHostResourceProbe probe,
            WorkerFabricRegistrationBoundary registrationBoundary,
            Clock clock,
            PhysicalHostId localPhysicalHostId) {
        return new LocalHostRegistrationLoop(
                probe, registrationBoundary, clock, localPhysicalHostId);
    }

    @Bean
    public LocalHostRegistrationScheduler localHostRegistrationScheduler(
            LocalHostRegistrationLoop localHostRegistrationLoop) {
        return new LocalHostRegistrationScheduler(localHostRegistrationLoop);
    }

    /** Worker-local timer; one registration tick every 10 s. */
    public static final class LocalHostRegistrationScheduler {

        private final LocalHostRegistrationLoop loop;

        LocalHostRegistrationScheduler(LocalHostRegistrationLoop loop) {
            this.loop = loop;
        }

        @Scheduled(fixedDelay = 10_000L)
        public void scheduledRegister() {
            try {
                loop.registerOnce();
            } catch (RuntimeException failure) {
                // Not swallowed: the previous registration expires on its own window and the next
                // tick retries the observation. Nothing is registered from failed evidence.
                log.error("bounded local host registration tick failed", failure);
            }
        }
    }
}
