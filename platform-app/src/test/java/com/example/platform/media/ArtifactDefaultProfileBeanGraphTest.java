package com.example.platform.media;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Runtime bean-definition graph proof for the default production profile.
 *
 * The scanner is the same Spring component registration mechanism used by the
 * application. The context is intentionally left unrefreshed so bean creation
 * does not require PostgreSQL, Temporal, or object storage; its BeanDefinition
 * registry is the runtime registration/reachability graph under review.
 */
class ArtifactDefaultProfileBeanGraphTest {
    private static final Set<String> DELETED = Set.of(
            "MediaAuthorization", "MediaAssetService", "MediaProbeService",
            "MediaProbes", "MediaProbePort", "MediaProbePortAdapter");

    @Test
    void defaultProfile_hasNoDeletedLegacyAuthorityAndHasArtifactAuthority() {
        try (AnnotationConfigApplicationContext context = scanDefaultProductionGraph()) {
            assertNoDeletedAuthorities(context);
            Set<String> classes = beanClassNames(context);
            assertTrue(classes.stream().anyMatch(n -> n.endsWith("ArtifactApplicationService")),
                    "Artifact application authority must be registered");
            assertTrue(classes.stream().anyMatch(n -> n.endsWith("ArtifactOutputReadService")
                            || n.endsWith("ArtifactQueryService")),
                    "Artifact retrieval authority must be registered");
        }
    }

    @Test
    void reintroducedDeletedAuthority_isRejectedByRuntimeGraphProof() {
        try (AnnotationConfigApplicationContext context = scanDefaultProductionGraph()) {
            context.registerBean("reintroducedMediaProbePortAdapter",
                    MediaProbePortAdapter.class,
                    MediaProbePortAdapter::new);
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> assertNoDeletedAuthorities(context));
            assertTrue(failure.getMessage().contains("MediaProbePortAdapter"));
        }
    }

    private static AnnotationConfigApplicationContext scanDefaultProductionGraph() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context);
        scanner.scan("com.example.platform");
        return context;
    }

    private static void assertNoDeletedAuthorities(BeanDefinitionRegistry context) {
        for (String name : context.getBeanDefinitionNames()) {
            String className = context.getBeanDefinition(name).getBeanClassName();
            if (className == null) continue;
            for (String deleted : DELETED) {
                assertFalse(className.endsWith("." + deleted) || className.endsWith("$" + deleted),
                        "deleted authority reachable as bean " + name + " -> " + className);
            }
        }
    }

    private static Set<String> beanClassNames(BeanDefinitionRegistry context) {
        return Arrays.stream(context.getBeanDefinitionNames())
                .map(name -> context.getBeanDefinition(name).getBeanClassName())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    static final class MediaProbePortAdapter {
    }
}
