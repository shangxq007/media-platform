package com.example.platform.social;

import com.example.platform.social.app.*;
import com.example.platform.social.api.SocialPublishController;
import com.example.platform.social.domain.*;
import com.example.platform.social.infrastructure.persistence.*;
import com.example.platform.social.infrastructure.platform.*;
import com.example.platform.identity.api.authorization.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialCompositionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ComponentScan(basePackages = "com.example.platform.social", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Repository.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Configuration.class)})
    static class Assembly {}

    private ApplicationContextRunner runner(SocialPostRepository posts, ConnectedPlatformRepository accounts) {
        return new ApplicationContextRunner().withUserConfiguration(Assembly.class)
                .withBean(SocialPostRepository.class, () -> posts)
                .withBean(ConnectedPlatformRepository.class, () -> accounts)
                .withBean(PostAnalyticsRepository.class, () -> mock(PostAnalyticsRepository.class))
                .withBean(CanonicalActorResolver.class, () -> mock(CanonicalActorResolver.class))
                .withBean(AuthorizationDecisionPort.class, () -> mock(AuthorizationDecisionPort.class))
                .withBean(SocialProjectScopePort.class, () -> mock(SocialProjectScopePort.class))
                .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class));
    }
    @Test void disabledKeepsReadsAndRejectsMutationWithoutWork() {
        for (String setting : new String[]{"", "app.social-publish.enabled=false"}) {
            var posts = mock(SocialPostRepository.class);
            var accounts = mock(ConnectedPlatformRepository.class);
            runner(posts, accounts).withPropertyValues(setting, "app.social-publish.scheduler.enabled=true").run(c -> {
                assertThat(c).hasNotFailed().hasSingleBean(SocialPostReadService.class).hasSingleBean(SocialAccountReadService.class)
                        .doesNotHaveBean(SocialPublishService.class).doesNotHaveBean(PlatformAdapter.class);
                assertThat(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                com.example.platform.shared.web.TenantContext.set("tenant");
                try {
                    assertThatThrownBy(() -> c.getBean(SocialPublishController.class).connectPlatform("actor", "TWITTER", null))
                            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                            .hasMessageContaining("503");
                } finally { com.example.platform.shared.web.TenantContext.clear(); }
                verifyNoInteractions(posts, accounts);
            });
        }
    }
    @Test void missingAndConflictingProvidersRejectEnabledAssembly() {
        var runner = runner(mock(SocialPostRepository.class), mock(ConnectedPlatformRepository.class))
                .withPropertyValues("app.social-publish.enabled=true");
        runner.run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("spring.profiles.active=dev,prod").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("spring.profiles.active=dev")
                .withBean("other", PlatformAdapter.class, () -> adapter()).run(c -> assertThat(c).hasFailed());
    }
    @Test void devAdapterCannotConnectPublishOrFabricateAnalytics() {
        var posts = mock(SocialPostRepository.class);
        var accounts = mock(ConnectedPlatformRepository.class);
        runner(posts, accounts).withPropertyValues("app.social-publish.enabled=true", "spring.profiles.active=dev").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
            var provider = c.getBean(PlatformAdapter.class);
            var account = account();
            assertThat(provider.validateCredentials(account)).isFalse();
            assertThat(provider.publish(post(), account).success()).isFalse();
            assertThatThrownBy(() -> provider.fetchAnalytics(post(), account)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> c.getBean(PlatformAuthService.class).connectPlatform("tenant", "actor", "twitter", "code"))
                    .isInstanceOf(UnsupportedOperationException.class);
            verifyNoInteractions(posts, accounts);
        });
    }
    @Test void schedulerRegisteredOnceExecutesOwnerPublishingAndPersistsFailureThenRecovery() {
        var posts = mock(SocialPostRepository.class);
        var accounts = mock(ConnectedPlatformRepository.class);
        var provider = adapter();
        when(posts.findScheduledBefore(any())).thenReturn(List.of(post()));
        when(posts.findById("post")).thenReturn(Optional.of(post()));
        when(accounts.findByTenantUserAndPlatform("tenant", "actor", "TWITTER")).thenReturn(Optional.of(account()));
        when(provider.validateCredentials(any())).thenReturn(true);
        when(provider.publish(any(), any())).thenReturn(new PublishResult(false, null, null, "RETRY", "retry"),
                new PublishResult(true, "external", "https://example.test/post", null, null));
        runner(posts, accounts).withPropertyValues("app.social-publish.enabled=true", "app.social-publish.scheduler.enabled=true")
                .withBean(PlatformAdapter.class, () -> provider).run(c -> {
                    assertThat(c).hasNotFailed();
                    var tasks = c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                    assertThat(tasks).hasSize(1);
                    tasks.iterator().next().getTask().getRunnable().run();
                    verify(posts).save(argThat(p -> p.status() == PostStatus.FAILED && p.retryCount() == 1));
                    tasks.iterator().next().getTask().getRunnable().run();
                    verify(posts).save(argThat(p -> p.status() == PostStatus.PUBLISHED));
                    verify(provider, times(2)).publish(any(), any());
                });
    }
    @Test void schedulerDisabledMakesNoRepositoryCallsAndMissingCredentialsNeverPublish() {
        var posts = mock(SocialPostRepository.class);
        var accounts = mock(ConnectedPlatformRepository.class);
        var provider = adapter();
        runner(posts, accounts).withPropertyValues("app.social-publish.enabled=true", "app.social-publish.scheduler.enabled=false")
                .withBean(PlatformAdapter.class, () -> provider).run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
                    verifyNoInteractions(posts, accounts);
                    when(posts.findById("post")).thenReturn(Optional.of(post()));
                    assertThatThrownBy(() -> c.getBean(SocialPublishService.class).publishNow("tenant", "actor", "post"))
                            .hasMessageContaining("valid provider credentials");
                    verify(provider, never()).publish(any(), any());
                    verify(posts, never()).save(any());
                });
    }
    private static PlatformAdapter adapter() {
        var adapter = mock(PlatformAdapter.class);
        when(adapter.platform()).thenReturn(PlatformType.TWITTER);
        return adapter;
    }
    private static ConnectedPlatform account() {
        return new ConnectedPlatform("account", "tenant", "actor", "TWITTER", "external", "name", "ACTIVE", 1L, Instant.now(), Instant.now());
    }
    private static SocialPost post() {
        var now = Instant.now();
        return new SocialPost("post", "tenant", "actor", null, null, null, null, "text", List.of(), PlatformType.TWITTER,
                PostStatus.SCHEDULED, null, null, now.minusSeconds(60), null, null, null, null, 0, now, now);
    }
}
