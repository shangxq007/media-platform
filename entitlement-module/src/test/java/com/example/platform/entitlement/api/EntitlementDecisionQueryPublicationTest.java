package com.example.platform.entitlement.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.stereotype.Service;

class EntitlementDecisionQueryPublicationTest {

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)\\b");

    private static final String DECISION_SERVICE =
            "com.example.platform.entitlement.app.EntitlementDecisionService";

    @Test
    void compiledPublicationMetadataKeepsDecisionImplementationInternalWithoutCallers() throws Exception {
        Class<?> service = Class.forName(DECISION_SERVICE);
        Class<?> query = EntitlementDecisionQuery.class;

        // Read the actual annotation attributes without requiring the compileOnly Modulith API at runtime.
        assertEquals(List.of("app"), namedInterfaces(service.getPackageName() + ".package-info"));
        assertEquals(List.of("API"), namedInterfaces(query.getPackageName() + ".package-info"));
        assertTrue(namedInterfaces(DECISION_SERVICE).isEmpty(),
                "EP-19: the implementation must not declare a type-level named interface");
        assertTrue(query.isInterface() && Modifier.isPublic(query.getModifiers()));
        assertTrue(query.isAssignableFrom(service));
        assertEquals(1, query.getDeclaredMethods().length);
        assertEquals(EntitlementDecision.class,
                query.getDeclaredMethod("evaluate", AccessCheckRequest.class).getReturnType());
        assertAll(
                () -> assertEquals(0, service.getModifiers()
                                & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE),
                        "EP-19: the decision implementation must be package-private even with no callers"),
                () -> assertEquals(List.of(query), Stream.of(query, service)
                                .filter(type -> Modifier.isPublic(type.getModifiers())).toList(),
                        "EP-19: only the query may be public in these published named interfaces"));
    }

    @Test
    void springScansAndWiresTheInternalImplementationThroughThePublishedQuery() throws Exception {
        Class<?> service = Class.forName(DECISION_SERVICE);
        assertNotNull(service.getAnnotation(Service.class));
        EntitlementPolicyService policy = mock(EntitlementPolicyService.class);
        when(policy.getTier("tenant-1")).thenReturn("FREE");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EntitlementPolicyService.class, () -> policy);
            context.registerBean(EntitlementService.class, () -> mock(EntitlementService.class));
            ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter((metadata, factory) ->
                    metadata.getClassMetadata().getClassName().equals(DECISION_SERVICE));
            scanner.scan(service.getPackageName());
            context.refresh();

            assertEquals(1, context.getBeansOfType(EntitlementDecisionQuery.class).size());
            EntitlementDecisionQuery query = context.getBean(EntitlementDecisionQuery.class);
            assertEquals(service, query.getClass());
            EntitlementDecision decision = query.evaluate(new AccessCheckRequest(
                    "tenant-1", null, null, null, null, "read", null, null,
                    "feature-1", null, null, "api", null, null));
            assertFalse(decision.allowed());
            assertEquals("DEFAULT_DENY", decision.reasonCode());
            assertEquals("FREE", decision.currentTier());
            verify(policy).getTier("tenant-1");
        }
    }

    private static List<String> namedInterfaces(String className) throws IOException {
        List<String> names = new ArrayList<>();
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream input = EntitlementDecisionQueryPublicationTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, () -> "Missing compiled publication metadata: " + resource);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lorg/springframework/modulith/NamedInterface;")) {
                        return null;
                    }
                    assertTrue(visible, "NamedInterface must retain runtime-visible metadata");
                    // Also detect a marker annotation with no explicit name on the implementation.
                    names.add("<unnamed>");
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitArray(String attribute) {
                            if (!attribute.equals("value") && !attribute.equals("name")) {
                                return null;
                            }
                            names.remove("<unnamed>");
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String name, Object value) {
                                    names.add((String) value);
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return names;
    }

    @Test
    void publishesOnlyTheNarrowEntitlementDecisionQuery() {
        Path api = repositoryRoot().resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/api");

        String packageInfo = read(api.resolve("package-info.java"));
        String query = read(api.resolve("EntitlementDecisionQuery.java"));

        assertTrue(packageInfo.contains("NamedInterface(\"API\")"),
                "EP-19: the entitlement query must be a published module interface");
        assertTrue(query.contains("interface EntitlementDecisionQuery"),
                "EP-19: entitlement must publish a decision query, not its app service");
        assertTrue(query.contains("EntitlementDecision evaluate(AccessCheckRequest request)"),
                "EP-19: the query must preserve the existing entitlement-only decision input and result");
        for (String forbidden : new String[] {
                "AuthorizationDecision", "QuotaDecision", "Subscription", "proOnly",
                "enterpriseOnly", "UniversalAuthorizationPort", "EffectiveAccess" }) {
            assertFalse(query.contains(forbidden),
                    () -> "EP-19 query crosses a separate authority boundary: " + forbidden);
        }
    }

    @Test
    void crossModuleEntitlementDecisionAccessUsesOnlyThePublishedQuery() {
        Path root = repositoryRoot();
        Path entitlementProduction = root.resolve("entitlement-module/src/main/java");
        Path federationProduction = root.resolve("federation-query-module/src/main/java");
        List<Path> productionSources = productionJavaSources(root);

        assertTrue(productionSources.stream().anyMatch(path -> path.startsWith(federationProduction)),
                "EP-19: federation-query production must be included in the working-tree source scan");

        List<Path> externalServiceReferences = productionSources.stream()
                .filter(path -> !path.startsWith(entitlementProduction))
                .filter(path -> containsSymbol(read(path), "EntitlementDecisionService"))
                .toList();
        String federationPackageInfo = read(federationProduction.resolve(
                "com/example/platform/federation/package-info.java"));
        assertAll(
                () -> assertTrue(externalServiceReferences.isEmpty(),
                        () -> "EP-19: only entitlement may reference its internal EntitlementDecisionService: "
                                + relativePaths(root, externalServiceReferences)),
                () -> assertTrue(federationPackageInfo.contains("entitlement :: API"),
                        "EP-19: federation-query must depend on the published entitlement API"),
                () -> assertFalse(federationPackageInfo.contains("entitlement :: app"),
                        "EP-19: federation-query must not depend on entitlement's internal app package"));

        assertTrue(productionSources.stream()
                        .noneMatch(path -> containsSymbol(read(path), "EntitlementPort")),
                "EP-19: EntitlementPort must not reappear as a cross-module decision authority");
        assertTrue(productionSources.stream()
                        .noneMatch(path -> containsSymbol(read(path), "UniversalAuthorizationPort")),
                "EP-19: UniversalAuthorizationPort must not reappear as a cross-module decision authority");
        assertTrue(productionSources.stream()
                        .flatMap(path -> declaredTypes(read(path)).stream())
                        .noneMatch(EntitlementDecisionQueryPublicationTest::isCompatibilityQueryAuthority),
                "EP-19: compatibility, fallback, and alias decision-query authorities are forbidden");

        List<String> entitlementQueryAuthorities = productionSources.stream()
                .flatMap(path -> declaredTypes(read(path)).stream())
                .filter(EntitlementDecisionQueryPublicationTest::isEntitlementQueryAuthority)
                .toList();
        assertTrue(entitlementQueryAuthorities.equals(List.of("EntitlementDecisionQuery")),
                () -> "EP-19: EntitlementDecisionQuery must remain the sole entitlement query authority: "
                        + entitlementQueryAuthorities);

        String service = read(entitlementProduction.resolve(
                "com/example/platform/entitlement/app/EntitlementDecisionService.java"));
        assertTrue(service.contains("class EntitlementDecisionService implements EntitlementDecisionQuery"),
                "EP-19: EntitlementDecisionService remains entitlement's internal query implementation");
    }

    private static List<Path> productionJavaSources(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> isProductionJavaSource(root.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static boolean isProductionJavaSource(Path relativePath) {
        for (int index = 0; index + 2 < relativePath.getNameCount(); index++) {
            if (relativePath.getName(index).toString().equals("src")
                    && relativePath.getName(index + 1).toString().equals("main")
                    && relativePath.getName(index + 2).toString().equals("java")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSymbol(String source, String symbol) {
        return Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b").matcher(source).find();
    }

    private static List<String> declaredTypes(String source) {
        Matcher declarations = TYPE_DECLARATION.matcher(source);
        return declarations.results().map(match -> match.group(1)).toList();
    }

    private static boolean isEntitlementQueryAuthority(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.contains("entitlement")
                && normalized.contains("decision")
                && (normalized.contains("query") || normalized.contains("port"));
    }

    private static boolean isCompatibilityQueryAuthority(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        boolean compatibilityMarker = normalized.contains("compatibility")
                || normalized.contains("fallback")
                || normalized.contains("alias");
        boolean entitlementDecision = normalized.contains("entitlement") || normalized.contains("decision");
        return compatibilityMarker && entitlementDecision
                && (normalized.contains("query") || normalized.contains("port"));
    }

    private static List<Path> relativePaths(Path root, List<Path> paths) {
        return paths.stream().map(root::relativize).toList();
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts not found");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
