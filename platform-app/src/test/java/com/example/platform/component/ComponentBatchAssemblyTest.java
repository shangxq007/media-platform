package com.example.platform.component;

import com.example.platform.PlatformApplication;
import com.example.platform.BuiltinDataBootstrapRunner;
import com.example.platform.analytics.app.*;
import com.example.platform.analytics.infrastructure.*;
import com.example.platform.analytics.scheduler.AnalyticsSchedule;
import com.example.platform.billing.BillingCatalogBootstrap;
import com.example.platform.cloudresource.domain.CloudResourceProvider;
import com.example.platform.lifecycle.PlatformGracefulShutdownCoordinator;
import com.example.platform.observability.app.PlatformTraceCorrelationFilter;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.social.app.*;
import com.example.platform.social.infrastructure.platform.PlatformAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.cloud-resource.enabled=true", "app.social-publish.enabled=true", "app.social-publish.scheduler.enabled=true",
        "app.analytics.enabled=true", "app.analytics.persistence=jdbc", "app.analytics.scheduler.enabled=true",
        "app.analytics.scheduler.tenants=batch-tenant", "render.providers.natron.enabled=false"})
@ActiveProfiles({"dev", "test", "preview"})
class ComponentBatchAssemblyTest extends PostgresTestContainerSupport {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    @MockitoBean(name = "taskScheduler") TaskScheduler scheduler;

    @Test void allFourAssembleOnceWithoutBootstrapAndControlledJobsUseCanonicalOwners() throws Exception {
        assertThat(context.getBeansOfType(CloudResourceProvider.class)).hasSize(1);
        assertThat(context.getBeansOfType(PlatformAdapter.class)).hasSize(1);
        assertThat(context.getBeansOfType(PostSchedulerService.class)).hasSize(1);
        assertThat(context.getBeansOfType(SocialPostReadService.class)).hasSize(1);
        assertThat(context.getBeansOfType(UserProfileRepository.class)).hasSize(1);
        assertThat(context.getBean(UserProfileRepository.class)).isInstanceOf(JdbcUserProfileRepository.class);
        assertThat(context.getBeansOfType(PlatformGracefulShutdownCoordinator.class)).hasSize(1);
        assertThat(context.getBeansOfType(PlatformTraceCorrelationFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(BuiltinDataBootstrapRunner.class)).isEmpty();
        assertThat(context.getBeansOfType(BillingCatalogBootstrap.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_meter", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_behavior_event", Long.class)).isZero();
        var client = HttpClient.newHttpClient();
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/cloud-resources/providers"))
                .header("X-Request-Id", "batch-request").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("stub");
        var profiles = context.getBean(UserProfileService.class);
        profiles.getOrCreateProfile("batch-tenant", "actor");
        context.getBean(BehaviorEventService.class).ingestEvent("batch-tenant", "actor", "USE", "edit", null, null, Map.of());
        var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
        var selected = tasks.stream().map(task -> task.getTask().getRunnable())
                .filter(ScheduledMethodRunnable.class::isInstance).map(ScheduledMethodRunnable.class::cast)
                .filter(task -> AnalyticsSchedule.class.isInstance(task.getTarget()) || PostSchedulerService.class.isInstance(task.getTarget())).toList();
        assertThat(selected).as("Registered tasks: %s", tasks).hasSize(3);
        selected.forEach(Runnable::run);
        assertThat(profiles.getOrCreateProfile("batch-tenant", "actor").totalActions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_segment WHERE tenant_id='batch-tenant'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_post", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_meter", Long.class)).isZero();
    }
}
