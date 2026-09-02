package com.example.platform.operation.invocation;

import com.example.platform.operation.operation.OperationDefinitionId;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationInvocationContractTest {

    private static final Path PRODUCTION_SOURCE = Path.of("src/main/java");
    private static final OperationDefinitionId DEFINITION_ID = OperationDefinitionId.of("timeline.move");
    private static final OperationDefinitionVersion DEFINITION_VERSION = OperationDefinitionVersion.V1_0;
    private static final CanonicalActor ACTOR = CanonicalActor.user(
            "actor-1", "tenant-1", Set.of("editor"), "test");

    @Test
    void portAcceptsTheExistingOperationRequestAndHasExactlyOneMethod() throws Exception {
        Method invoke = OperationInvocationPort.class.getDeclaredMethod(
                "invoke", OperationRequest.class, OperationInvocationContext.class);

        assertEquals(OperationInvocationResult.class, invoke.getReturnType());
        assertTrue(Modifier.isPublic(invoke.getModifiers()));
        assertTrue(Modifier.isAbstract(invoke.getModifiers()));
        assertArrayEquals(new Method[]{invoke}, OperationInvocationPort.class.getDeclaredMethods());
    }

    @Test
    void productionSourceContainsNoPeerInvocationRequestType() throws IOException {
        Set<String> forbiddenNames = Set.of(
                "OperationInvocationRequest",
                "WorkflowOperationRequest",
                "CanonicalOperationRequest",
                "ExecutableOperationRequest");

        try (Stream<Path> paths = Files.walk(PRODUCTION_SOURCE)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                forbiddenNames.forEach(name -> assertFalse(source.contains(name), path + ": " + name));
            }
        }
    }

    @Test
    void contextCarriesOneCanonicalActorOneIdentityAndNonAuthoritativeProvenance() {
        var provenance = new OperationInvocationContext.Provenance("correlation-1", "workflow");
        var context = new OperationInvocationContext(ACTOR, "invocation-1", provenance);

        assertSame(ACTOR, context.actor());
        assertEquals("invocation-1", context.invocationId());
        assertEquals("correlation-1", context.provenance().correlationId());
        assertEquals("workflow", context.provenance().origin());
        assertEquals(1, Arrays.stream(OperationInvocationContext.class.getRecordComponents())
                .filter(component -> component.getType() == CanonicalActor.class)
                .count());
        assertArrayEquals(
                new String[]{"actor", "invocationId", "provenance"},
                Arrays.stream(OperationInvocationContext.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toArray(String[]::new));
        assertArrayEquals(
                new String[]{"correlationId", "origin"},
                Arrays.stream(OperationInvocationContext.Provenance.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toArray(String[]::new));

        Set<String> forbiddenAuthorityNames = Set.of(
                "tenantid", "projectid", "actorid", "actortype", "admin", "system",
                "authorization", "expectedhead", "provider", "worker", "device", "storage");
        Stream.concat(
                        Arrays.stream(OperationInvocationContext.class.getRecordComponents()),
                        Arrays.stream(OperationInvocationContext.Provenance.class.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .forEach(name -> assertTrue(forbiddenAuthorityNames.stream().noneMatch(name::contains), name));

        assertThrows(NullPointerException.class,
                () -> new OperationInvocationContext(null, "invocation-1", provenance));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationInvocationContext(ACTOR, " ", provenance));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationInvocationContext.Provenance("x".repeat(257), "workflow"));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationInvocationContext.Provenance("correlation-1", "x".repeat(129)));
    }

    @Test
    void appliedAndNoOpAreImmutableTypedResultsWithDistinctInvariants() {
        OperationInvocationResult.Applied applied = new OperationInvocationResult.Applied(
                DEFINITION_ID, DEFINITION_VERSION, "plan-digest", "revision-1", "revision-2",
                "result-hash", "invocation-1", "correlation-1");
        OperationInvocationResult.NoOp noOp = new OperationInvocationResult.NoOp(
                DEFINITION_ID, DEFINITION_VERSION, "plan-digest", "revision-1",
                "base-hash", "invocation-1", "correlation-1");

        assertTrue(OperationInvocationResult.class.isSealed());
        assertTrue(OperationInvocationResult.Applied.class.isRecord());
        assertTrue(OperationInvocationResult.NoOp.class.isRecord());
        assertEquals(DEFINITION_ID, applied.definitionId());
        assertEquals(DEFINITION_VERSION, applied.definitionVersion());
        assertEquals("revision-2", applied.newRevisionId());
        assertEquals("result-hash", applied.resultContentHash());
        assertEquals("revision-1", noOp.baseRevisionId());
        assertEquals("base-hash", noOp.unchangedContentHash());
        assertEquals("invocation-1", noOp.invocationId());
        assertEquals("correlation-1", noOp.correlationId());
        assertThrows(IllegalArgumentException.class, () -> new OperationInvocationResult.Applied(
                DEFINITION_ID, DEFINITION_VERSION, "plan", "same", "same",
                "hash", "invocation", null));
        assertThrows(IllegalArgumentException.class, () -> new OperationInvocationResult.NoOp(
                DEFINITION_ID, DEFINITION_VERSION, " ", "revision", "hash", "invocation", null));
    }

    @Test
    void failureTaxonomyIsExactAndDiagnosticsAreImmutableAndBounded() {
        assertEquals(Set.of(
                        "UNSUPPORTED_OPERATION",
                        "INVALID_REQUEST",
                        "INVALID_SCOPE",
                        "INVALID_PARAMETER",
                        "BASE_REVISION_NOT_FOUND",
                        "STALE_BASE_REVISION",
                        "SOURCE_REFERENCE_INVALID",
                        "CANDIDATE_INVALID",
                        "PLAN_CHANGED",
                        "AUTHORIZATION_DENIED",
                        "AUTHORIZATION_CONTEXT_MISMATCH",
                        "IDEMPOTENCY_CONFLICT",
                        "TARGET_MISSING",
                        "PLACEMENT_CONFLICT",
                        "CANONICAL_INVARIANT_VIOLATION",
                        "PERSISTENCE_FAILURE",
                        "APPLY_FAILURE"),
                Arrays.stream(OperationInvocationFailureCode.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(17, OperationInvocationFailureCode.values().length);

        Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("field", "baseRevisionId");
        var failure = new OperationInvocationException(OperationInvocationFailureCode.INVALID_REQUEST, mutable);
        mutable.put("leaked", "later mutation");

        assertEquals(OperationInvocationFailureCode.INVALID_REQUEST, failure.code());
        assertEquals(Map.of("field", "baseRevisionId"), failure.diagnostics());
        assertThrows(UnsupportedOperationException.class,
                () -> failure.diagnostics().put("another", "value"));
        assertNull(failure.getCause());
        assertThrows(IllegalStateException.class,
                () -> failure.initCause(new RuntimeException("lower-layer mechanics")));
        assertTrue(Arrays.stream(OperationInvocationException.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .noneMatch(Throwable.class::isAssignableFrom));
        assertThrows(IllegalArgumentException.class, () -> new OperationInvocationException(
                OperationInvocationFailureCode.INVALID_REQUEST,
                Map.of("diagnostic", "x".repeat(513))));
    }

    @Test
    void invocationNamedInterfaceIsTypeLevelOnlyAndCoversTheRequiredGraph() throws IOException {
        Stream.of(
                        OperationRequest.class,
                        OperationDefinitionId.class,
                        OperationDefinitionVersion.class,
                        OperationTargetRequest.class,
                        OperationParameters.class,
                        OperationInvocationPort.class,
                        OperationInvocationContext.class,
                        OperationInvocationContext.Provenance.class,
                        OperationInvocationResult.class,
                        OperationInvocationResult.Applied.class,
                        OperationInvocationResult.NoOp.class,
                        OperationInvocationFailureCode.class,
                        OperationInvocationException.class)
                .forEach(OperationInvocationContractTest::assertInvocationNamedInterface);
        Arrays.stream(OperationTargetRequest.class.getPermittedSubclasses())
                .forEach(OperationInvocationContractTest::assertInvocationNamedInterface);
        Arrays.stream(OperationParameters.class.getPermittedSubclasses())
                .forEach(OperationInvocationContractTest::assertInvocationNamedInterface);

        assertFalse(Files.exists(PRODUCTION_SOURCE.resolve("com/example/platform/operation/operation/package-info.java")));
        assertFalse(Files.exists(PRODUCTION_SOURCE.resolve("com/example/platform/operation/plan/package-info.java")));
        assertFalse(Files.exists(PRODUCTION_SOURCE.resolve("com/example/platform/operation/invocation/package-info.java")));
    }

    @Test
    void publicInvocationSignaturesDoNotExposeLowerLayerMechanics() {
        Set<String> forbidden = Set.of(
                "OperationPlan", "TimelineDocument", "TimelinePatch", "Repository", "jooq",
                "Provider", "Transaction", "AuthorizationDecision", "ApplyContext", "ApplyResult",
                "ApplyService", "Persistence", "Runtime");

        Stream.of(
                        OperationInvocationPort.class,
                        OperationInvocationContext.class,
                        OperationInvocationContext.Provenance.class,
                        OperationInvocationResult.class,
                        OperationInvocationResult.Applied.class,
                        OperationInvocationResult.NoOp.class,
                        OperationInvocationFailureCode.class,
                        OperationInvocationException.class)
                .flatMap(OperationInvocationContractTest::publicSignatureTypes)
                .map(Type::getTypeName)
                .forEach(signature -> forbidden.forEach(fragment ->
                        assertFalse(signature.contains(fragment), signature)));
    }

    @Test
    void operationRequestRequiresAnExactImmutableBaseContentHash() {
        OperationTargetRequest target = new OperationTargetRequest.TimelineTargetRequest("timeline-1");
        OperationParameters parameters = new OperationParameters.NoParameters();

        assertThrows(IllegalArgumentException.class, () -> new OperationRequest(
                DEFINITION_ID, DEFINITION_VERSION, target, parameters,
                "revision-1", null, null));
        assertThrows(IllegalArgumentException.class, () -> new OperationRequest(
                DEFINITION_ID, DEFINITION_VERSION, target, parameters,
                "revision-1", " ", null));
        OperationRequest request = new OperationRequest(
                DEFINITION_ID, DEFINITION_VERSION, target, parameters,
                "revision-1", "content-hash", "metadata");
        assertEquals("content-hash", request.baseContentHash());
    }

    private static void assertInvocationNamedInterface(Class<?> type) {
        Path source = PRODUCTION_SOURCE.resolve(type.getName().split("\\$")[0].replace('.', '/') + ".java");
        String declarationKind = type.isRecord() ? "record"
                : type.isEnum() ? "enum"
                : type.isInterface() ? "interface"
                : "class";
        String expected = "@org.springframework.modulith.NamedInterface(\"invocation\")";
        try {
            String content = Files.readString(source);
            int declaration = content.indexOf(declarationKind + " " + type.getSimpleName());
            int annotation = content.lastIndexOf(expected, declaration);
            assertTrue(declaration >= 0, type.getName());
            assertTrue(annotation >= 0, type.getName());
            assertTrue(content.substring(annotation + expected.length(), declaration).isBlank()
                            || content.substring(annotation + expected.length(), declaration)
                            .matches("(?s)\\s*(public\\s+)?(final\\s+|sealed\\s+)?"),
                    type.getName());
        } catch (IOException exception) {
            throw new AssertionError(type.getName(), exception);
        }
    }

    private static Stream<Type> publicSignatureTypes(Class<?> type) {
        Stream<Type> methods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .flatMap(method -> Stream.concat(
                        Stream.of(method.getGenericReturnType()),
                        Arrays.stream(method.getGenericParameterTypes())));
        Stream<Type> constructors = Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .flatMap(constructor -> Arrays.stream(constructor.getGenericParameterTypes()));
        return Stream.concat(methods, constructors);
    }
}
