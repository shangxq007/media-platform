package com.example.platform.workflow.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OperationInvocationBoundaryArchitectureTest {

    private static final Path WORKFLOW_PRODUCTION =
            Path.of("src/main/java/com/example/platform/workflow");
    private static final Path WORKFLOW_PACKAGE_INFO =
            Path.of("src/main/java/com/example/platform/workflow/package-info.java");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:interface|class|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern IMPORT_DECLARATION = Pattern.compile(
            "(?m)^\\s*import\\s+([^;]+);");

    @Test
    void workflowAllowsExactlyTheOperationInvocationNamedInterface() throws IOException {
        String source = Files.readString(WORKFLOW_PACKAGE_INFO);

        assertEquals(1, quotedMemberCount(source, "operation :: invocation"),
                "Workflow allowedDependencies must contain operation :: invocation exactly once");
        assertEquals(0, quotedMemberCount(source, "operation"),
                "Workflow allowedDependencies must not contain the broad operation module");
    }

    @Test
    void workflowPeerInvocationPortCountIsZero() throws IOException {
        Set<String> legacyAndKnownPeerNames = Set.of(
                "CanonicalOperationInvocationPort",
                "OperationInvocationPort",
                "WorkflowOperationInvocation",
                "WorkflowOperationNodeExecutor");

        assertZero("WORKFLOW_PEER_INVOCATION_PORT_COUNT", declaredTypeHits(name ->
                legacyAndKnownPeerNames.contains(name)
                        || name.matches("(?i)(?=.*operation)(?=.*invocation).*(port|gateway|authority|executor)$")));
    }

    @Test
    void workflowPeerOperationResultAuthorityCountIsZero() throws IOException {
        Set<String> knownPeerNames = Set.of(
                "CanonicalOperationResult",
                "CanonicalOperationException",
                "OperationInvocationResult",
                "OperationInvocationException",
                "OperationInvocationFailureCode");

        assertZero("WORKFLOW_PEER_OPERATION_RESULT_AUTHORITY_COUNT", declaredTypeHits(name ->
                knownPeerNames.contains(name)
                        || name.matches("(?i)(?=.*operation).*(result|exception|failure|failurecode|error)$")));
    }

    @Test
    void legacyWorkflowInvocationPortDefinitionCountIsZero() throws IOException {
        Set<String> legacyNames = Set.of(
                "CanonicalOperationInvocationPort",
                "OperationInvocationPort");

        assertZero("LEGACY_WORKFLOW_INVOCATION_PORT_DEFINITION_COUNT",
                declaredTypeHits(legacyNames::contains));
    }

    @Test
    void legacyWorkflowOperationResultDefinitionCountIsZero() throws IOException {
        Set<String> legacyNames = Set.of(
                "CanonicalOperationResult",
                "CanonicalOperationException");

        assertZero("LEGACY_WORKFLOW_OPERATION_RESULT_DEFINITION_COUNT",
                declaredTypeHits(legacyNames::contains));
    }

    @Test
    void workflowOperationPlanImportCountIsZero() throws IOException {
        assertZero("WORKFLOW_OPERATION_PLAN_IMPORT_COUNT",
                importHits(importedType -> importedType.endsWith(".OperationPlan")));
    }

    @Test
    void workflowOperationPlannerImportCountIsZero() throws IOException {
        Set<String> forbiddenSimpleNames = Set.of(
                "OperationRequestResolver",
                "OperationInstance",
                "OperationPlanner",
                "TextOperationPlanner");

        assertZero("WORKFLOW_OPERATION_PLANNER_IMPORT_COUNT", importHits(importedType ->
                forbiddenSimpleNames.stream().anyMatch(name -> importedType.endsWith("." + name))));
    }

    @Test
    void workflowTimelineWriterImportCountIsZero() throws IOException {
        Pattern writerOrPersistenceType = Pattern.compile(
                ".*(?:TimelineRevisionSaveService|TimelineRevisionRefMutation|"
                        + "TimelineRevisionRepository|TimelineRevisionPersistencePort|"
                        + "TimelineRevisionRefHeadUpdateAdapter|HeadUpdatePort|"
                        + "Timeline.*Writer|Timeline.*Repository|Timeline.*Persistence).*");

        assertZero("WORKFLOW_TIMELINE_WRITER_IMPORT_COUNT", importHits(importedType ->
                importedType.startsWith("com.example.platform.timeline.")
                        && writerOrPersistenceType.matcher(importedType).matches()));
    }

    @Test
    void workflowGenericTimelinePatchUsageCountIsZero() throws IOException {
        Pattern genericPatchType = Pattern.compile(
                "\\b(?:TimelinePatchApplicationService|TimelinePatchEngine|TimelinePatch)\\b");

        assertZero("WORKFLOW_GENERIC_TIMELINE_PATCH_USAGE_COUNT", sourceHits(genericPatchType));
    }

    @Test
    void workflowJooqImportCountIsZero() throws IOException {
        assertZero("WORKFLOW_JOOQ_IMPORT_COUNT",
                importHits(importedType -> importedType.startsWith("org.jooq.")));
    }

    @Test
    void workflowCanonicalMediaWriterCountIsZero() throws IOException {
        Pattern canonicalWriter = Pattern.compile(
                "\\b(?:saveRevision|advanceHead|advanceTimelineHead|updateHead|"
                        + "OperationPlanApplyService|TimelineRevisionSaveService|"
                        + "TimelineRevisionRefMutation|TimelineRevisionRefHeadUpdateAdapter|"
                        + "TimelinePatchApplicationService|TimelinePatchEngine|TimelinePatch)\\b");

        assertZero("WORKFLOW_CANONICAL_MEDIA_WRITER_COUNT", sourceHits(canonicalWriter));
    }

    @Test
    void workflowOperationResultReauthoringCountIsZero() throws IOException {
        Set<String> canonicalTruthNames = Set.of(
                "CanonicalOperationResult",
                "CanonicalOperationException",
                "OperationInvocationResult",
                "OperationInvocationException",
                "OperationInvocationFailureCode");

        assertZero("WORKFLOW_OPERATION_RESULT_REAUTHORING_COUNT", declaredTypeHits(name ->
                canonicalTruthNames.contains(name)
                        || name.matches("(?i)(?=.*operation).*(result|exception|failure|failurecode|error)$")));
    }

    @Test
    void newOperationRequestPeerTypeCountIsZero() throws IOException {
        assertZero("NEW_OPERATION_REQUEST_PEER_TYPE_COUNT", declaredTypeHits(name ->
                name.matches("(?i)(?=.*operation)(?=.*request).*")
                        || Set.of(
                                "OperationInvocationRequest",
                                "WorkflowOperationRequest",
                                "CanonicalOperationRequest",
                                "ExecutableOperationRequest")
                        .contains(name)));
    }

    @Test
    void workflowOperationImportsStayWithinTheInvocationNamedInterface() throws IOException {
        Set<String> exactInvocationMembers = Set.of(
                "com.example.platform.operation.invocation.OperationInvocationPort",
                "com.example.platform.operation.invocation.OperationInvocationContext",
                "com.example.platform.operation.invocation.OperationInvocationResult",
                "com.example.platform.operation.invocation.OperationInvocationFailureCode",
                "com.example.platform.operation.invocation.OperationInvocationException",
                "com.example.platform.operation.operation.OperationRequest",
                "com.example.platform.operation.operation.OperationDefinitionId",
                "com.example.platform.operation.operation.OperationDefinitionVersion",
                "com.example.platform.operation.operation.OperationTargetRequest",
                "com.example.platform.operation.operation.OperationParameters");

        assertZero("WORKFLOW_UNNAMED_OPERATION_IMPORT_COUNT", importHits(importedType ->
                importedType.startsWith("com.example.platform.operation.")
                        && !importedType.equals("com.example.platform.operation.invocation.*")
                        && !exactInvocationMembers.contains(importedType)));
    }

    private static int quotedMemberCount(String source, String member) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(member) + "\\\"").matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static List<Path> productionJavaFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(WORKFLOW_PRODUCTION)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<String> declaredTypeHits(Predicate<String> forbiddenName) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path path : productionJavaFiles()) {
            Matcher matcher = TYPE_DECLARATION.matcher(withoutComments(Files.readString(path)));
            while (matcher.find()) {
                if (forbiddenName.test(matcher.group(1))) {
                    hits.add(WORKFLOW_PRODUCTION.relativize(path) + ":" + matcher.group(1));
                }
            }
        }
        return hits;
    }

    private static List<String> importHits(Predicate<String> forbiddenImport) throws IOException {
        return importHits(path -> true, forbiddenImport);
    }

    private static List<String> importHits(
            Predicate<Path> includedPath, Predicate<String> forbiddenImport) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path path : productionJavaFiles()) {
            if (!includedPath.test(path)) {
                continue;
            }
            Matcher matcher = IMPORT_DECLARATION.matcher(withoutComments(Files.readString(path)));
            while (matcher.find()) {
                String importedType = matcher.group(1).trim();
                if (forbiddenImport.test(importedType)) {
                    hits.add(WORKFLOW_PRODUCTION.relativize(path) + ":" + importedType);
                }
            }
        }
        return hits;
    }

    private static List<String> sourceHits(Pattern forbiddenUsage) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path path : productionJavaFiles()) {
            Matcher matcher = forbiddenUsage.matcher(withoutComments(Files.readString(path)));
            while (matcher.find()) {
                hits.add(WORKFLOW_PRODUCTION.relativize(path) + ":" + matcher.group());
            }
        }
        return hits;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static void assertZero(String counter, List<String> hits) {
        assertTrue(hits.isEmpty(), counter + "=" + hits.size() + " " + hits);
    }
}
