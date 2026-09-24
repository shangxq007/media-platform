package com.example.platform.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.PlatformApplication;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/** Exercises the real PlatformApplication component scan and refreshes it. */
class ArtifactDefaultProfileBeanGraphTest {
    private static final Set<String> DELETED = Set.of(
            "MediaAuthorization", "MediaAssetService", "MediaProbeService",
            "MediaProbes", "MediaProbePort", "MediaProbePortAdapter");

    @Test
    void defaultProfile_refreshesRealApplicationWithoutDeletedAuthorities() {
        launch().run(context -> {
            assertNoDeletedAuthorities(context);
            Set<String> classes = beanClassNames(context);
            assertTrue(classes.stream().anyMatch(n -> n.endsWith("ArtifactApplicationService")),
                    "Artifact application authority must be registered");
            assertTrue(classes.stream().anyMatch(n -> n.endsWith("ArtifactOutputReadService")
                            || n.endsWith("ArtifactQueryService")),
                    "Artifact retrieval authority must be registered");
        });
    }

    @Test
    void realScannedNegativeFixture_isRejectedByTheSameRuntimeValidation() {
        launch("v26-negative").run(context -> {
            assertTrue(context.getBeanFactory().getBeanNamesForType(MediaProbePortAdapter.class, true, false).length == 1,
                    "negative fixture must be discovered by the real application scan");
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> assertNoDeletedAuthorities(context));
            assertTrue(failure.getMessage().contains("MediaProbePortAdapter"));
        });
    }

    private static ApplicationContextRunner launch(String... profiles) {
        // The production graph fences unfinished legacy consumers behind this
        // existing profile. The test profile suppresses startup side effects;
        // neither profile restores a deleted authority.
        StringBuilder active = new StringBuilder("test,legacy-media-disabled");
        for (String profile : profiles) active.append(',').append(profile);
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> ((GenericApplicationContext) context).addBeanFactoryPostProcessor(factory -> {
                    for (String name : factory.getBeanDefinitionNames()) {
                        factory.getBeanDefinition(name).setLazyInit(true);
                    }
                }))
                .withUserConfiguration(PlatformApplication.class)
                .withPropertyValues(
                        "spring.profiles.active=" + active,
                        "spring.main.web-application-type=none",
                        "spring.main.lazy-initialization=true",
                        "spring.flyway.enabled=false",
                        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/disabled",
                        "spring.datasource.username=disabled",
                        "spring.datasource.password=disabled",
                        "app.temporal.enabled=false",
                        "app.security.enabled=false",
                        "identity.builtin-data.enabled=false",
                        "app.outbox.dispatcher-enabled=false",
                        "storage.s3.enabled=false",
                        "server.port=0");
    }

    private static void assertNoDeletedAuthorities(ConfigurableApplicationContext context) {
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            String className = type == null ? context.getBeanFactory().getBeanDefinition(name).getBeanClassName() : type.getName();
            if (className == null) continue;
            for (String deleted : DELETED) {
                assertFalse(className.endsWith("." + deleted) || className.endsWith("$" + deleted),
                        "deleted authority reachable as bean " + name + " -> " + className);
            }
        }
    }

    private static Set<String> beanClassNames(ConfigurableApplicationContext context) {
        return Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(java.util.Objects::nonNull)
                .map(Class::getName)
                .collect(Collectors.toSet());
    }

}
