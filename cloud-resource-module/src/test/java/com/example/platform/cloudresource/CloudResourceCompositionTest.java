package com.example.platform.cloudresource;

import com.example.platform.cloudresource.app.CloudResourceCatalogService;
import com.example.platform.cloudresource.api.CloudResourceController;
import com.example.platform.cloudresource.domain.CloudResourceProvider;
import com.example.platform.cloudresource.infrastructure.StubCloudResourceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudResourceCompositionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CloudResourceConfiguration.class);
    @Test void defaultsAndExplicitDisabledHaveNoSurfaceOrProviderCalls() {
        for (String value : new String[]{"", "app.cloud-resource.enabled=false"}) {
            CloudResourceProvider provider = mock(CloudResourceProvider.class);
            runner.withPropertyValues(value).withBean(CloudResourceProvider.class, () -> provider).run(c -> {
                assertThat(c).doesNotHaveBean(CloudResourceCatalogService.class).doesNotHaveBean(CloudResourceController.class);
                verifyNoInteractions(provider);
            });
        }
    }
    @Test void enabledWithoutProviderFailsClearly() {
        runner.withPropertyValues("app.cloud-resource.enabled=true").run(c ->
                assertThat(c.getStartupFailure()).hasRootCauseMessage(
                        "app.cloud-resource.enabled requires exactly one valid CloudResourceProvider"));
    }
    @Test void explicitDevelopmentCannotProvisionOrClaimActive() {
        runner.withPropertyValues("app.cloud-resource.enabled=true", "spring.profiles.active=dev").run(c -> {
            assertThat(c).hasSingleBean(CloudResourceProvider.class).hasSingleBean(CloudResourceCatalogService.class);
            assertThat(c.getBean(CloudResourceController.class).providers()).containsExactly("stub");
            assertThatThrownBy(() -> c.getBean(CloudResourceProvider.class).ensureBucket("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(c.getBean(CloudResourceCatalogService.class).createBucket("x", "r", "stub").status())
                    .isEqualTo("CATALOGUED");
        });
    }
    @Test void productionCannotSelectDevelopmentStub() {
        runner.withPropertyValues("app.cloud-resource.enabled=true", "spring.profiles.active=dev,prod")
                .run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("app.cloud-resource.enabled=true")
                .withBean(CloudResourceProvider.class, StubCloudResourceProvider::new)
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void validOwnerAssemblesWithoutProvisioning() {
        CloudResourceProvider provider = mock(CloudResourceProvider.class);
        when(provider.code()).thenReturn("owner");
        runner.withPropertyValues("app.cloud-resource.enabled=true").withBean(CloudResourceProvider.class, () -> provider)
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(CloudResourceController.class).providers()).containsExactly("owner");
                    verify(provider, never()).ensureBucket(anyString());
                });
    }
    @Test void conflictingAndInvalidProvidersFail() {
        runner.withPropertyValues("app.cloud-resource.enabled=true", "spring.profiles.active=dev")
                .withBean("other", CloudResourceProvider.class, () -> mock(CloudResourceProvider.class))
                .run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("app.cloud-resource.enabled=true")
                .withBean(CloudResourceProvider.class, () -> mock(CloudResourceProvider.class))
                .run(c -> assertThat(c).hasFailed());
    }
}
