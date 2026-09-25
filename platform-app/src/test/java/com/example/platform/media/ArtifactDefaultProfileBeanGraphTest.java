package com.example.platform.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.PlatformApplication;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Runtime reachability proof for the actual production application context.
 *
 * <p>No profile is active on the default test. In particular,
 * {@code legacy-media-disabled} is a positive Spring profile used by retained
 * legacy consumers; activating it would make those consumers reachable rather
 * than disable them. The context is therefore the real default profile and is
 * started through the repository's approved PostgreSQL test infrastructure.
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArtifactDefaultProfileBeanGraphTest extends PostgresTestContainerSupport {
    private static final Set<String> DELETED = Set.of(
            "MediaAuthorization", "MediaAssetService", "MediaProbeService",
            "MediaProbes", "MediaProbePort", "MediaProbePortAdapter");

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void actualDefaultProfile_refreshesRealApplicationWithoutDeletedAuthorities() {
        assertTrue(context.isActive(), "the production application context must be refreshed");
        assertFalse(context.getEnvironment().acceptsProfiles("legacy-media-disabled"),
                "default proof must not activate the positive legacy-media-disabled profile");
        assertNoDeletedAuthorities(context);
        Set<String> classes = beanClassNames(context);
        assertTrue(classes.stream().anyMatch(n -> n.endsWith("ArtifactApplicationService")),
                "Artifact application authority must be registered");
        assertTrue(classes.stream().anyMatch(n -> n.endsWith("ArtifactOutputReadService")
                        || n.endsWith("ArtifactQueryService")),
                "Artifact retrieval authority must be registered");
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

    static void assertRejected(ConfigurableApplicationContext context) {
        assertNotNull(context.getBeanFactory().getBeanNamesForType(MediaProbePortAdapter.class, true, false));
        assertTrue(context.getBeanFactory().getBeanNamesForType(MediaProbePortAdapter.class, true, false).length == 1,
                "negative fixture must be discovered by the real application scan");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertNoDeletedAuthorities(context));
        assertTrue(failure.getMessage().contains("MediaProbePortAdapter"));
    }
}
