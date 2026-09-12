package com.example.platform;

import com.example.platform.observability.app.MdcObservationContext;
import com.example.platform.observability.context.ObservationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded EP17 production dependency guard, with an executable violating control. */
class ObservationBoundaryTest {
    private static final Pattern INTERNAL = Pattern.compile(
            "\\bcom\\.example\\.platform\\.observability\\.app\\.[A-Za-z_$]");

    private static boolean dependsOnInternal(String source) {
        String code = source.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", " ");
        return INTERNAL.matcher(code).find();
    }

    @Test void identityAndAuditUsePublishedObservationBoundary() throws Exception {
        for (String module : List.of("identity-access-module", "audit-compliance-module")) {
            Path root = Path.of("..", module, "src/main/java");
            assertTrue(Files.isDirectory(root));
            try (var paths = Files.walk(root)) {
                var sources = paths.filter(p -> p.toString().endsWith(".java")).toList();
                assertFalse(sources.isEmpty());
                for (Path path : sources) assertFalse(dependsOnInternal(Files.readString(path)), path.toString());
            }
        }
        assertFalse(Files.exists(Path.of("../observability-module/src/main/java/com/example/platform/observability/app/TraceKeys.java")));
        assertFalse(Files.exists(Path.of("../identity-access-module/src/main/java/com/example/platform/identity/authorization/MdcCanonicalActorResolver.java")));
    }

    @Test void ruleRejectsImportsAndFullyQualifiedInternalReferences() {
        assertTrue(dependsOnInternal("import com.example.platform.observability.app.TraceKeys;"));
        assertTrue(dependsOnInternal("class X { Object x = com.example.platform.observability.app.TraceKeys.PRINCIPAL; }"));
        assertFalse(dependsOnInternal("import com.example.platform.observability.context.ObservationContext;"));
    }

    @Test void publishedPortHasOneScannedProductionImplementation() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var scanner = new org.springframework.context.annotation.ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AssignableTypeFilter(ObservationContext.class));
            scanner.scan("com.example.platform.observability");
            context.refresh();
            assertEquals(1, context.getBeansOfType(ObservationContext.class).size());
            assertInstanceOf(MdcObservationContext.class, context.getBean(ObservationContext.class));
        }
    }
}
