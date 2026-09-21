package com.example.platform.component;

import com.example.platform.BuiltinDataBootstrapRunner;
import com.example.platform.billing.BillingCatalogBootstrap;
import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.identity.app.BuiltinDataInitializer;
import com.example.platform.lifecycle.*;
import com.example.platform.observability.app.PlatformTraceCorrelationFilter;
import com.example.platform.outbox.app.OutboxEventDispatcher;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.workflow.temporal.*;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformCompositionTest {
    @Test void bootstrapDefaultDisabledAndExplicitOptInExecuteActualHooks() {
        for (String[] flags : new String[][]{{}, {"identity.builtin-data.enabled=false", "app.billing.catalog-bootstrap.enabled=false"},
                {"identity.builtin-data.enabled=true", "app.billing.catalog-bootstrap.enabled=true"}}) {
            var initializer = mock(BuiltinDataInitializer.class);
            var metering = mock(UsageMeteringService.class);
            new ApplicationContextRunner().withUserConfiguration(BuiltinDataBootstrapRunner.class, BillingCatalogBootstrap.class)
                    .withBean(BuiltinDataInitializer.class, () -> initializer).withBean(UsageMeteringService.class, () -> metering)
                    .withPropertyValues(flags).run(c -> {
                        assertThat(c).hasNotFailed();
                        for (ApplicationRunner runner : c.getBeansOfType(ApplicationRunner.class).values()) runner.run(new DefaultApplicationArguments());
                        c.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], c.getSourceApplicationContext(), Duration.ZERO));
                        if (flags.length > 0 && flags[0].endsWith("true")) {
                            verify(initializer).init();
                            verify(metering, times(4)).registerMeter(anyString(), anyString(), anyString(), anyString(), anyString());
                        } else { verifyNoInteractions(initializer, metering); }
                    });
        }
    }
    @Test void optedInBootstrapFailurePreservesOriginalCause() {
        var initializer = mock(BuiltinDataInitializer.class);
        var failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(initializer).init();
        new ApplicationContextRunner().withUserConfiguration(BuiltinDataBootstrapRunner.class)
                .withBean(BuiltinDataInitializer.class, () -> initializer)
                .withPropertyValues("identity.builtin-data.enabled=true").run(c ->
                    assertThatThrownBy(() -> c.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments())).hasCause(failure));
    }
    @Test void shutdownRunsOnceContinuesAfterDrainFailureAndReleasesScheduler() {
        var outbox = mock(OutboxEventDispatcher.class);
        var worker = mock(WorkerFactory.class);
        var original = new IllegalStateException("outbox unavailable");
        when(outbox.processBatch(50)).thenThrow(original);
        var scheduler = new ThreadPoolTaskScheduler();
        var released = new AtomicBoolean();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PlatformGracefulShutdownCoordinator.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start(); logger.addAppender(events);
        try {
            new ApplicationContextRunner().withUserConfiguration(PlatformGracefulShutdownCoordinator.class, TemporalWorkerGracefulShutdown.class)
                    .withPropertyValues("app.temporal.enabled=true")
                    .withBean(OutboxEventDispatcher.class, () -> outbox).withBean(WorkerFactory.class, () -> worker)
                    .withBean(AppTemporalProperties.class, AppTemporalProperties::new)
                    .withBean(ThreadPoolTaskScheduler.class, () -> scheduler)
                    .withBean("ownedResource", AutoCloseable.class, () -> () -> released.set(true))
                    .run(c -> {
                        assertThat(c).hasNotFailed().hasSingleBean(PlatformGracefulShutdownCoordinator.class);
                        scheduler.schedule(() -> {}, Instant.now().plusSeconds(3600));
                    });
            var order = inOrder(worker, outbox);
            order.verify(worker).shutdown();
            order.verify(worker).awaitTermination(anyLong(), any());
            order.verify(outbox).processBatch(50);
            verify(outbox, times(1)).processBatch(50);
            assertThat(released).isTrue();
            assertThatThrownBy(() -> scheduler.schedule(() -> {}, Instant.now())).isInstanceOf(RejectedExecutionException.class);
            assertThat(events.list).anySatisfy(event -> {
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("outbox unavailable");
            });
        } finally { logger.detachAppender(events); }
    }
    @Test void workerCleanupRetainsOriginalAndSuppressedErrorsAndDoesNotPreventOtherOwners() {
        var worker = mock(WorkerFactory.class);
        var outbox = mock(OutboxEventDispatcher.class);
        var original = new IllegalStateException("stop failed");
        var cleanup = new IllegalStateException("await failed");
        doThrow(original).when(worker).shutdown();
        doThrow(cleanup).when(worker).awaitTermination(anyLong(), any());
        new ApplicationContextRunner().withUserConfiguration(PlatformGracefulShutdownCoordinator.class, TemporalWorkerGracefulShutdown.class)
                .withPropertyValues("app.temporal.enabled=true").withBean(WorkerFactory.class, () -> worker)
                .withBean(AppTemporalProperties.class, AppTemporalProperties::new)
                .withBean(OutboxEventDispatcher.class, () -> outbox).run(c -> assertThat(c).hasNotFailed());
        verify(worker).shutdown();
        verify(worker).shutdownNow();
        verify(outbox).processBatch(50);
        assertThat(original.getSuppressed()).contains(cleanup);
    }

    @Test void workerHealthReflectsDependencyTransitions() {
        var worker = mock(WorkerFactory.class);
        new ApplicationContextRunner().withUserConfiguration(TemporalWorkerHealthIndicator.class)
                .withPropertyValues("app.temporal.enabled=true").withBean(WorkerFactory.class, () -> worker)
                .withBean(AppTemporalProperties.class, AppTemporalProperties::new).run(c -> {
                    var health = c.getBean(TemporalWorkerHealthIndicator.class);
                    assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
                    when(worker.isStarted()).thenReturn(true);
                    assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
                    when(worker.isShutdown()).thenReturn(true);
                    assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
                });
    }
    @Test void requestFilterIsSingleRegisteredOuterBoundaryAndReusedThreadCannotInheritContext() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            new ApplicationContextRunner().withUserConfiguration(PlatformTraceCorrelationFilter.class).run(c -> {
                assertThat(c).hasSingleBean(PlatformTraceCorrelationFilter.class).hasSingleBean(FilterRegistrationBean.class);
                var registration = c.getBean(FilterRegistrationBean.class);
                assertThat(registration.getOrder()).isLessThan(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
                var filter = c.getBean(PlatformTraceCorrelationFilter.class);
                for (boolean fail : new boolean[]{false, true, false}) {
                    executor.submit(() -> {
                        MDC.put("workspaceId", "previous-workspace");
                        MDC.put("principal", "previous-actor");
                        TenantContext.set("previous-tenant");
                        var request = new MockHttpServletRequest("GET", "/api/social/posts");
                        request.addHeader("X-Tenant-Id", "spoofed");
                        request.addHeader("X-Workspace-Id", "spoofed");
                        request.addHeader("X-Request-Id", "request");
                        try {
                            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                                assertThat(MDC.get("requestId")).isEqualTo("request");
                                assertThat(MDC.get("traceId")).isNotBlank();
                                assertThat(MDC.get("workspaceId")).isNull();
                                assertThat(MDC.get("principal")).isNull();
                                assertThat(TenantContext.get()).isNull();
                                assertThat(req.getAttribute("jwt.subject")).isNull();
                                TenantContext.set("authenticated-tenant");
                                MDC.put("principal", "actor"); MDC.put("workspaceId", "workspace");
                                if (fail) throw new jakarta.servlet.ServletException("controlled failure");
                            });
                            assertThat(fail).isFalse();
                        } catch (jakarta.servlet.ServletException e) { assertThat(fail).isTrue(); }
                        assertThat(TenantContext.get()).isNull();
                        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
                        return null;
                    }).get(10, TimeUnit.SECONDS);
                }
            });
        }
    }
}
