package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Complete mechanical zero-guard matrix for Roadmap #22 Epoch 3 Task G.
 *
 * <p>Correction 3 repository-wide structural scan evidence:
 *
 * <ul>
 *   <li>{@code EXECUTION_LIFECYCLE_MUTATION_OUTSIDE_WORKER_FABRIC_COUNT}: active Spring authority
 *       annotations plus lease/task field types and lifecycle-state mutation structure.
 *   <li>{@code RENDER_SPECIFIC_RUNTIME_AUTHORITY_COUNT}: active Spring authority annotations in
 *       render farm code plus registration/heartbeat/claim endpoint or persistence structure.
 *   <li>{@code WORKER_RUNTIME_HOST_CAPACITY_AUTHORITY_COUNT}: record components, field declarations,
 *       and maps whose types bind host capacity to a WorkerRuntime identity.
 *   <li>{@code ROADMAP_23_GLOBAL_OPTIMIZER_IMPLEMENTATION_COUNT}: every production Java type and
 *       method definition naming global/DRF/fairness/cost/deadline/locality/GPU-packing execution
 *       optimization.
 * </ul>
 *
 * <p>The expected manifested count for each scan is zero. Existing worker-fabric-local reflection
 * and source guards remain below as independent, narrower checks.
 */
class TaskGArchitectureGuardTest {

    private static final Path REPO_ROOT = repoRoot();
    private static final Path WORKER_MAIN = REPO_ROOT.resolve("worker-fabric-module/src/main/java");
    private static final Path WORKER_DOMAIN =
            WORKER_MAIN.resolve("com/example/platform/workerfabric/domain");
    private static final Path WORKER_INFRASTRUCTURE =
            WORKER_MAIN.resolve("com/example/platform/workerfabric/infrastructure");
    private static final Path STATIC_MAIN = REPO_ROOT.resolve("media-execution-plan-module/src/main/java");

    private static final Pattern TYPE_DEFINITION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern WORKER_RUNTIME_SCOPED_CAPACITY_DECLARATION = Pattern.compile(
            "(?i)\\b(?:HostResourceSnapshot|SchedulableCapacity|CapacitySnapshot|ReservedResources)"
                    + "\\s+(?:workerRuntime(?:Host)?(?:Capacity|ResourceSnapshot)|"
                    + "runtimeScoped(?:Host)?Capacity|capacityByWorkerRuntime|"
                    + "hostCapacityByWorkerRuntime)\\b");
    private static final Pattern WORKER_RUNTIME_CAPACITY_MAP = Pattern.compile(
            "(?s)\\bMap\\s*<\\s*WorkerRuntime(?:Id|IncarnationId)\\s*,\\s*"
                    + "(?:HostResourceSnapshot|SchedulableCapacity|CapacitySnapshot|ReservedResources)\\b");
    private static final Pattern ACTIVE_SPRING_AUTHORITY = Pattern.compile(
            "(?m)^\\s*@(RestController|Controller|Service|Repository|Component)\\b");
    private static final Pattern EXECUTION_AUTHORITY_FIELD_TYPE = Pattern.compile(
            "(?s)\\b(?:PlatformTask|RenderJobLeaseRecord|JobLeaseRepository(?:\\.JobLease)?|"
                    + "RenderJobQueue|RenderWorkerService|TaskLease|ExecutionAttempt|"
                    + "ExecutionAssignment|Reservation)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\b"
                    + "|\\b(?:RENDER_JOB_LEASE|PLATFORM_TASK)\\.(?:STATUS|STARTED_AT|COMPLETED_AT)\\b");
    private static final Pattern EXECUTION_LIFECYCLE_MUTATION = Pattern.compile(
            "(?s)(?:\\.set\\([^\\n]*(?:STATUS|STATE)[^\\n]*"
                    + "(?:COMPLETED|FAILED|LEASED|EXPIRED|RELEASED|RECOVERY_HOLD|QUEUED)"
                    + "|\\b(?:completeTask|failTask|expireLease|expireStaleLeases|renewLease|"
                    + "claimNextJob|leaseAndRun|resetStaleLeases|markCompleted|markFailed)\\s*\\()");
    private static final Pattern RENDER_RUNTIME_ENDPOINT_STRUCTURE = Pattern.compile(
            "(?s)@RequestMapping\\([^)]*render-workers[^)]*\\).*"
                    + "@PostMapping\\([^)]*(?:register|heartbeat|claim|leases)[^)]*\\)");
    private static final Pattern RENDER_RUNTIME_PERSISTENCE_STRUCTURE = Pattern.compile(
            "(?s)\\b(?:RENDER_WORKER|RENDER_JOB_LEASE)\\.(?:STATUS|WORKER_ID|LEASE_ID)\\b.*"
                    + "(?:\\.update\\(|\\.insertInto\\()");
    private static final Pattern RENDER_RUNTIME_DEPENDENCY_STRUCTURE = Pattern.compile(
            "(?s)private\\s+final\\s+(?:RenderWorkerService|RenderWorkerRegistryService|"
                    + "RenderWorkerRepository|RenderJobLeaseService|RenderJobLeaseRepository|"
                    + "JobLeaseRepository|RenderJobQueue)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\b");
    private static final Pattern ROADMAP_23_OPTIMIZER_TYPE = Pattern.compile(
            "(?i)^(?:(?:Roadmap23|Global|CrossProvider|DominantResourceFairness|DominantResource|"
                    + "Drf|FairShare|Fairness|Fair|Cost|Deadline|Locality|GpuPacking|GpuPack)"
                    + "[A-Za-z0-9_$]*(?:Optimizer|Scheduler|Matcher|Planner)"
                    + "|(?:Execution|Work|Placement)[A-Za-z0-9_$]*(?:DominantResourceFairness|"
                    + "DominantResource|Drf|FairShare|Fairness|Fair|Cost|Deadline|Locality|"
                    + "GpuPacking|GpuPack)"
                    + "[A-Za-z0-9_$]*(?:Optimizer|Scheduler|Matcher|Planner))$");
    private static final Pattern ROADMAP_23_OPTIMIZER_METHOD = Pattern.compile(
            "\\b(?:optimizeGlobalPlacement|optimizeGlobalSchedule|applyDominantResourceFairness|"
                    + "applyDrf|optimizeFairness|optimizeCostPlacement|optimizeDeadlinePlacement|"
                    + "optimizeLocality|scheduleByDeadline|rankByCost|selectForLocality|"
                    + "selectByFairShare|packGpus|packGpuDevices)\\s*\\(");

    private static final List<String> RUNTIME_STATE_TOKENS = List.of(
            "WorkerRuntimeId",
            "WorkerRuntimeIncarnationId",
            "PhysicalHostId",
            "PhysicalHostIncarnationId",
            "HostResourceSnapshot",
            "SchedulableCapacity",
            "DeviceId",
            "ExecutionBackend",
            "ExecutionAssignment",
            "Reservation",
            "TaskLease",
            "ExecutionAttempt",
            "ExecutionOwnershipGeneration",
            "ProviderProbeResult",
            "ObservedUsage");

    @Test
    void renderSpecificRuntimeAuthorityCountIsZero() {
        assertThat(activeRenderRuntimeAuthorities())
                .as("RENDER_SPECIFIC_RUNTIME_AUTHORITY_COUNT=0; structural scan="
                        + "**/src/main/java/**/*.java for Spring authority annotations plus "
                        + "render-worker endpoint/persistence behavior")
                .isEmpty();

        String source = javaSources(WORKER_MAIN);
        assertThat(source)
                .doesNotContain(
                        "com.example.platform.render",
                        "RenderJobLease",
                        "RenderFarmWorkerController",
                        "RenderProvider");
    }

    @Test
    void executionLifecycleStateMutationOutsideWorkerFabricCountIsZero() {
        assertThat(activeExecutionLifecycleAuthoritiesOutsideWorkerFabric())
                .as("EXECUTION_LIFECYCLE_MUTATION_OUTSIDE_WORKER_FABRIC_COUNT=0; "
                        + "structural scan=**/src/main/java/**/*.java excluding worker-fabric "
                        + "for Spring authority annotations + lease/task field types + "
                        + "completion/failure/expiry state mutation")
                .isEmpty();
    }

    @Test
    void hostResourceSnapshotGenerationRegressionAcceptanceCountIsZero() {
        String registration = source(WORKER_INFRASTRUCTURE.resolve(
                "JooqWorkerFabricRegistrationBoundary.java"));
        String migration = source(REPO_ROOT.resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        assertThat(registration)
                .contains(
                        "wf_host_snapshot_generation_authority.current_generation",
                        "< excluded.current_generation",
                        "strictly greater than durable authority");
        assertThat(migration)
                .contains(
                        "wf_reject_snapshot_generation_regression",
                        "new.current_generation <= old.current_generation",
                        "wf_host_snapshot_publication_immutable",
                        "before update or delete on wf_host_resource_snapshot",
                        "wf_snapshot_device_membership_immutable",
                        "before update or delete on wf_host_resource_snapshot_device",
                        "wf_snapshot_device_insert_during_publication",
                        "publication_transaction_id <> txid_current()");
    }

    @Test
    void workerRuntimeHostCapacityAuthorityCountIsZero() {
        assertThat(workerRuntimeHostCapacityAuthorityDefinitions())
                .as("WORKER_RUNTIME_HOST_CAPACITY_AUTHORITY_COUNT=0; "
                        + "scan=**/src/main/java/**/*.java")
                .isEmpty();

        assertRecordComponentsExclude(
                WorkerRuntimeDescriptor.class,
                CapacitySnapshot.class,
                HostResourceSnapshot.class,
                SchedulableCapacity.class,
                ReservedResources.class);
        assertRecordComponentsExclude(
                WorkerRuntimeAvailability.class,
                CapacitySnapshot.class,
                HostResourceSnapshot.class,
                SchedulableCapacity.class,
                ReservedResources.class);
    }

    @Test
    void workerReportedCapacityOverridesCentralReservationCountIsZero() {
        String source = source(WORKER_DOMAIN.resolve("SchedulableCapacity.java"));
        assertThat(source)
                .contains(
                        "STATIC_CAPACITY - ACTIVE_RESERVATIONS - RECOVERY_HOLD_RESERVATIONS",
                        "unavailable.add(active)",
                        "unavailable.add(recoveryHold)",
                        "observationContradictsStaticCapacity(snapshot)")
                .doesNotContain("observedUsage().cpu", "observedUsage().memory");
    }

    @Test
    void perRuntimeHostCapacityDuplicationCountIsZero() {
        assertRecordComponentsExclude(
                SchedulableCapacity.class,
                WorkerRuntimeId.class,
                WorkerRuntimeIncarnationId.class,
                WorkerRuntimeAvailability.class,
                WorkerRuntimeReporterRef.class);
    }

    @Test
    void deviceCapacityWorkerScopedDuplicationCountIsZero() {
        assertThat(componentTypes(DeviceResourceCapacity.class))
                .contains(DeviceId.class)
                .doesNotContain(WorkerRuntimeId.class, WorkerRuntimeIncarnationId.class);
        assertThat(componentTypes(CapacitySnapshot.class))
                .doesNotContain(WorkerRuntimeId.class, WorkerRuntimeIncarnationId.class);
    }

    @Test
    void hostResourceSnapshotWithoutHostIdentityCountIsZero() {
        assertThat(componentTypes(HostResourceSnapshot.class)).contains(PhysicalHostId.class);
    }

    @Test
    void hostResourceSnapshotWithoutHostIncarnationCountIsZero() {
        assertThat(componentTypes(HostResourceSnapshot.class))
                .contains(PhysicalHostIncarnationId.class);
    }

    @Test
    void schedulableCapacityCrossHostSnapshotAcceptanceCountIsZero() {
        assertThat(source(WORKER_DOMAIN.resolve("SchedulableCapacity.java")))
                .contains(
                        "validateHostSnapshot(snapshot, hostAvailability)",
                        "hostAvailability.matchesCurrentIncarnation(",
                        "reservation host scope does not match host resource snapshot");
    }

    @Test
    void staleHostIncarnationAssignmentAcceptanceCountIsZero() {
        assertThat(componentTypes(ExecutionAssignment.class))
                .contains(PhysicalHostId.class, PhysicalHostIncarnationId.class);
        assertThat(source(WORKER_DOMAIN.resolve("CentralWorkMatcher.java")))
                .contains(
                        "requestWork.physicalHostIncarnationId()",
                        "RequestWorkFailureReason.HOST_INCARNATION_MISMATCH");
    }

    @Test
    void staleRuntimeIncarnationAssignmentAcceptanceCountIsZero() {
        assertThat(componentTypes(ExecutionAssignment.class))
                .contains(WorkerRuntimeId.class, WorkerRuntimeIncarnationId.class);
        assertThat(source(WORKER_DOMAIN.resolve("CentralWorkMatcher.java")))
                .contains(
                        "requestWork.workerRuntimeIncarnationId()",
                        "RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH");
    }

    @Test
    void multipleActivePlacementAuthoritiesPerTaskCountIsZero() {
        assertThat(source(WORKER_DOMAIN.resolve("ExecutionBackendSelectionSet.java")))
                .contains(
                        "unique.putIfAbsent(selection.executableTaskId(), selection)",
                        "ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1");
    }

    @Test
    void openCuePlatformHostPlacementAuthorityCountIsZero() {
        assertRecordComponentsExclude(
                OpenCueFarmBackend.class,
                PhysicalHostId.class,
                PhysicalHostIncarnationId.class,
                WorkerRuntimeId.class,
                WorkerRuntimeIncarnationId.class,
                DeviceId.class);
        assertRecordComponentsExclude(
                OpenCueBackendExecutionHandle.class,
                PhysicalHostId.class,
                PhysicalHostIncarnationId.class,
                WorkerRuntimeId.class,
                WorkerRuntimeIncarnationId.class,
                DeviceId.class);
        assertThat(ExecutionBackend.OPEN_CUE_FARM.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.BACKEND_DELEGATED);
    }

    @Test
    void openCueRqdAsCanonicalWorkerRuntimeCountIsZero() {
        assertThat(javaSourcesWithFileNamePrefix(WORKER_DOMAIN, "OpenCue"))
                .doesNotContain("RQD", "Rqd", "WorkerRuntimeId", "WorkerRuntimeIncarnationId");
    }

    @Test
    void remoteProviderFakePhysicalHostCountIsZero() {
        remoteProviderRecords().forEach(type -> assertRecordComponentsExclude(
                type, PhysicalHostId.class, PhysicalHostIncarnationId.class));
    }

    @Test
    void remoteProviderFakeWorkerRuntimeCountIsZero() {
        remoteProviderRecords().forEach(type -> assertRecordComponentsExclude(
                type, WorkerRuntimeId.class, WorkerRuntimeIncarnationId.class));
    }

    @Test
    void remoteProviderNativeTaskLeaseCountIsZero() {
        remoteProviderRecords().forEach(type -> assertRecordComponentsExclude(
                type, TaskLease.class, LeaseId.class, ReservationId.class));
    }

    @Test
    void executionAssignmentProviderRebindCountIsZero() {
        assertRecordComponentsExclude(ExecutionAssignment.class, ProviderBindingPin.class);
        assertThat(componentNames(ExecutionAssignment.class))
                .noneMatch(name -> name.matches("(?i).*(provider|binding|implementation).*"));
    }

    @Test
    void duplicateActiveNativeLeaseCountIsZero() {
        assertThat(source(repoRoot().resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql")))
                .contains(
                        "create unique index ux_wf_one_active_native_lease_per_task",
                        "on wf_task_lease (task_id) where active");
    }

    @Test
    void leaseExpiryImmediateResourceReleaseCountIsZero() {
        String lifecycle = source(WORKER_INFRASTRUCTURE.resolve("JooqExecutionLifecycleBoundary.java"));
        String expiry = methodRegion(lifecycle, "expireLease", "disconnectWorker");
        assertThat(expiry)
                .contains("ReservationState.RECOVERY_HOLD")
                .doesNotContain("ReservationState.RELEASED");
    }

    @Test
    void recoveryHoldSchedulableCapacityCountIsZero() {
        assertThat(new Reservation(
                                ReservationId.of("task-g-reservation"),
                                PhysicalHostId.of("task-g-host"),
                                ReservationKind.TASK,
                                ReservedResources.none(),
                                ReservationState.RECOVERY_HOLD)
                        .keepsCapacityUnavailable())
                .isTrue();
        assertThat(source(WORKER_DOMAIN.resolve("SchedulableCapacity.java")))
                .contains("unavailable.add(recoveryHold)");
    }

    @Test
    void staleGenerationAuthoritativeCompletionCountIsZero() {
        assertThat(componentTypes(ExecutionObservation.class))
                .contains(ExecutionAttemptId.class, ExecutionOwnershipGeneration.class);
        assertThat(source(WORKER_INFRASTRUCTURE.resolve("JooqAtomicAssignmentGrantBoundary.java")))
                .contains(
                        "o.current_attempt_id = a.attempt_id",
                        "o.current_generation = a.generation",
                        "StaleOwnershipGenerationException");
    }

    @Test
    void backendSuccessEqualsTaskCompletedCountIsZero() {
        String source = source(WORKER_DOMAIN.resolve("CompletionFence.java"));
        assertThat(source)
                .contains(
                        "expectedOutputValidation().isValid()",
                        "artifactCommitEvidencePort.committedEvidenceFor(evidence)",
                        "completionAuthorityPort.completeIfCurrent(evidence");
        assertThat(source.indexOf("committedEvidenceFor(evidence)"))
                .isLessThan(source.indexOf("completeIfCurrent(evidence"));
    }

    @Test
    void messageQueueExecutionStateAuthorityCountIsZero() {
        assertRecordComponentsExclude(
                DurableDeliveryPort.DeliveryReceipt.class,
                ExecutionAttemptState.class,
                CompletionDecision.class,
                ExecutableTaskId.class);
        assertRecordComponentsExclude(
                OutboxDeliveryIntent.class,
                ExecutionAttemptState.class,
                CompletionDecision.class,
                ExecutableTaskId.class);
    }

    @Test
    void serverlessExecutionStateAuthorityCountIsZero() {
        assertThat(importCount(javaSources(WORKER_MAIN), "(?i)(cloudflare|aws\\.lambda|serverless)"))
                .isZero();
    }

    @Test
    void camelExecutionStateAuthorityCountIsZero() {
        assertThat(importCount(javaSources(WORKER_MAIN), "org\\.apache\\.camel"))
                .isZero();
    }

    @Test
    void executionObservationDirectDbAuthorityCountIsZero() {
        String observationSources = source(WORKER_DOMAIN.resolve("ExecutionObservation.java"))
                + source(WORKER_DOMAIN.resolve("ExecutionObservationIngestionPort.java"));
        assertThat(observationSources)
                .doesNotContain("org.jooq", "java.sql", "DSLContext", "JdbcTemplate");
    }

    @Test
    void etgRuntimeStateFieldCountIsZero() {
        assertStaticTypeHasNoRuntimeState(
                "com/example/platform/execution/taskgraph/ProviderBoundExecutableTaskGraph.java");
        assertStaticTypeHasNoRuntimeState(
                "com/example/platform/execution/taskgraph/ExecutableTask.java");
    }

    @Test
    void physicalExecutionPlanRuntimeStateFieldCountIsZero() {
        assertStaticTypeHasNoRuntimeState(
                "com/example/platform/execution/planning/PhysicalExecutionPlan.java");
    }

    @Test
    void providerCompatibilityGraphRuntimeStateFieldCountIsZero() {
        assertStaticTypeHasNoRuntimeState(
                "com/example/platform/execution/compatibility/ProviderCompatibilityGraph.java");
    }

    @Test
    void roadmap23GlobalOptimizerImplementationCountIsZero() {
        Pattern definition = Pattern.compile(
                "(?m)^(?:public\\s+)?(?:final\\s+)?(?:class|interface|record|enum)\\s+"
                        + "(?:GlobalOptimizer|Roadmap23\\w*Optimizer)\\b");
        assertThat(allProductionJavaFiles().stream()
                        .map(TaskGArchitectureGuardTest::source)
                        .filter(text -> definition.matcher(text).find()))
                .isEmpty();

        Set<String> semanticImplementations = new TreeSet<>(
                typeDefinitionsMatching(ROADMAP_23_OPTIMIZER_TYPE));
        allProductionJavaFiles().forEach(path -> {
            String executableSource = stripComments(source(path));
            if (ROADMAP_23_OPTIMIZER_METHOD.matcher(executableSource).find()) {
                semanticImplementations.add(relative(path) + "#optimizer-method");
            }
        });
        assertThat(semanticImplementations)
                .as("ROADMAP_23_GLOBAL_OPTIMIZER_IMPLEMENTATION_COUNT=0; "
                        + "scan=**/src/main/java/**/*.java")
                .isEmpty();
    }

    @Test
    void retiredRenderLeaseMechanicsDefinitionCountRemainsFive() {
        assertThat(retiredRenderLeaseMechanics())
                .as("RETIRED_RENDER_LEASE_MECHANICS_DEFINITION_COUNT=5; structural scan uses "
                        + "lease identity/time/status field types and repository/service dependencies; "
                        + "all five definitions are unregistered mechanics, not active authorities")
                .hasSize(5);
    }

    @Test
    void platformTaskIsDeliveryCoordinationWithoutExecutionLifecycleAuthority() {
        assertThat(platformTaskDeliveryCoordinationSurfaces())
                .as("PLATFORM_TASK_DELIVERY_COORDINATION_SURFACE_COUNT=4; structural scan uses "
                        + "PlatformTask field/parameter/return types, not filenames")
                .hasSize(4);
        assertThat(activePlatformTaskExecutionAuthorities())
                .as("LEGACY_PLATFORM_TASK_EXECUTION_AUTHORITY_COUNT=0; active annotated types "
                        + "contain no leasing/completion/failure/expiry mutation")
                .isEmpty();
    }

    @Test
    void nonOverlappingKeepDispositionSurfacesRemainPresent() {
        assertThat(Files.isRegularFile(REPO_ROOT.resolve(
                        "render-module/src/main/java/com/example/platform/render/app/"
                                + "MultiProviderPipelineService.java")))
                .isTrue();
        assertThat(Files.isRegularFile(REPO_ROOT.resolve(
                        "extension-module/src/main/java/com/example/platform/extension/spi/"
                                + "PlatformPluginPoints.java")))
                .isTrue();
        assertThat(Files.isRegularFile(REPO_ROOT.resolve(
                        "extension-module/src/main/java/com/example/platform/extension/examples/"
                                + "ThirdPartyRenderProviderExtension.java")))
                .isTrue();
    }

    @Test
    void legacyRenderWorkerRuntimeAuthorityCountIsZero() {
        assertThat(activeRenderRuntimeAuthorities())
                .as("LEGACY_RENDER_WORKER_RUNTIME_AUTHORITY_COUNT=0")
                .isEmpty();
    }

    @Test
    void legacyDuplicateExecutionAttemptAuthorityCountIsZero() {
        assertThat(structuralAttemptAuthoritiesOutsideWorkerFabric())
                .as("LEGACY_DUPLICATE_EXECUTION_ATTEMPT_AUTHORITY_COUNT=0; record/field types="
                        + "ExecutionAttemptId+ExecutionOwnershipGeneration+ExecutionAttemptState")
                .isEmpty();
    }

    @Test
    void legacyDuplicateReservationAuthorityCountIsZero() {
        assertThat(structuralReservationAuthoritiesOutsideWorkerFabric())
                .as("LEGACY_DUPLICATE_RESERVATION_AUTHORITY_COUNT=0; record/field types="
                        + "PhysicalHostId+ReservedResources+ReservationState")
                .isEmpty();
    }

    @Test
    void legacyGenericRuntimeProviderGodInterfaceCountIsZero() {
        assertThat(genericRuntimeGodInterfaces())
                .as("LEGACY_GENERIC_RUNTIME_PROVIDER_GOD_INTERFACE_COUNT=0; structural interface "
                        + "scan counts types combining three or more runtime authority field types")
                .isEmpty();
    }

    private static List<Class<?>> remoteProviderRecords() {
        return List.of(
                RemoteProviderBackend.class,
                RemoteProviderExecutionHandle.class,
                RemoteProviderTrustBoundary.IngressContext.class);
    }

    private static Set<Class<?>> componentTypes(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getType)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static void assertRecordComponentsExclude(Class<?> recordType, Class<?>... excluded) {
        assertThat(componentTypes(recordType)).doesNotContain(excluded);
    }

    private static void assertStaticTypeHasNoRuntimeState(String relativePath) {
        String source = source(STATIC_MAIN.resolve(relativePath));
        assertThat(RUNTIME_STATE_TOKENS)
                .allSatisfy(token -> assertThat(source).doesNotContain(token));
    }

    private static List<String> typeDefinitionsMatching(Pattern namePattern) {
        Set<String> definitions = new TreeSet<>();
        allProductionJavaFiles().forEach(path -> {
            var matcher = TYPE_DEFINITION.matcher(stripComments(source(path)));
            while (matcher.find()) {
                if (namePattern.matcher(matcher.group(1)).matches()) {
                    definitions.add(relative(path) + "#" + matcher.group(1));
                }
            }
        });
        return List.copyOf(definitions);
    }

    private static Set<String> workerRuntimeHostCapacityAuthorityDefinitions() {
        Set<String> definitions = new TreeSet<>();
        allProductionJavaFiles().forEach(path -> {
            String executableSource = stripComments(source(path));
            if (WORKER_RUNTIME_CAPACITY_MAP.matcher(executableSource).find()) {
                definitions.add(relative(path) + "#worker-runtime-capacity-map");
            }
            if (WORKER_RUNTIME_SCOPED_CAPACITY_DECLARATION.matcher(executableSource).find()) {
                definitions.add(relative(path) + "#worker-runtime-scoped-capacity-field");
            }
        });
        return definitions;
    }

    private static Set<String> activeRenderRuntimeAuthorities() {
        Set<String> authorities = new TreeSet<>();
        Path renderMain = REPO_ROOT.resolve("render-module/src/main/java");
        productionJavaFiles(renderMain).forEach(path -> {
            String executableSource = stripComments(source(path));
            if (ACTIVE_SPRING_AUTHORITY.matcher(executableSource).find()
                    && (RENDER_RUNTIME_ENDPOINT_STRUCTURE.matcher(executableSource).find()
                            || RENDER_RUNTIME_PERSISTENCE_STRUCTURE.matcher(executableSource)
                                    .find()
                            || RENDER_RUNTIME_DEPENDENCY_STRUCTURE.matcher(executableSource)
                                    .find())) {
                authorities.add(relative(path));
            }
        });
        return authorities;
    }

    private static Set<String> activeExecutionLifecycleAuthoritiesOutsideWorkerFabric() {
        Set<String> authorities = new TreeSet<>();
        allProductionJavaFiles().stream()
                .filter(path -> !path.startsWith(WORKER_MAIN))
                .forEach(path -> {
                    String executableSource = stripComments(source(path));
                    if (ACTIVE_SPRING_AUTHORITY.matcher(executableSource).find()
                            && EXECUTION_AUTHORITY_FIELD_TYPE.matcher(executableSource).find()
                            && EXECUTION_LIFECYCLE_MUTATION.matcher(executableSource).find()) {
                        authorities.add(relative(path));
                    }
                });
        return authorities;
    }

    private static Set<String> activePlatformTaskExecutionAuthorities() {
        Path coordination = REPO_ROOT.resolve(
                "outbox-event-module/src/main/java/com/example/platform/outbox/coordination");
        return productionJavaFiles(coordination).stream()
                .filter(path -> {
                    String executableSource = stripComments(source(path));
                    return ACTIVE_SPRING_AUTHORITY.matcher(executableSource).find()
                            && EXECUTION_AUTHORITY_FIELD_TYPE.matcher(executableSource).find()
                            && EXECUTION_LIFECYCLE_MUTATION.matcher(executableSource).find();
                })
                .map(TaskGArchitectureGuardTest::relative)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> retiredRenderLeaseMechanics() {
        Path farm = REPO_ROOT.resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/farm");
        Set<String> mechanics = new TreeSet<>();
        productionJavaFiles(farm).forEach(path -> {
            String executableSource = stripComments(source(path));
            boolean persistedLeaseRecord = executableSource.contains("long leaseVersion")
                    && executableSource.contains("String heartbeatTokenHash");
            boolean leaseRepositoryOrServiceDependency =
                    executableSource.matches("(?s).*\\bRenderJobLeaseRecord\\s+[A-Za-z_$].*")
                            && (executableSource.contains("DSLContext")
                                    || executableSource.matches(
                                            "(?s).*private final RenderJobLeaseRepository\\s+.*"));
            boolean compensationDependency = executableSource.matches(
                    "(?s).*private final RenderJobLeaseService\\s+.*expireStaleLeases\\s*\\(.*");
            boolean leaseStateAlgebra = executableSource.matches(
                    "(?s).*\\benum\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\{.*"
                            + "CLAIMED.*RENEWED.*RELEASED.*EXPIRED.*FAILED.*");
            if (persistedLeaseRecord
                    || leaseRepositoryOrServiceDependency
                    || compensationDependency
                    || leaseStateAlgebra) {
                mechanics.add(relative(path));
            }
        });
        return mechanics;
    }

    private static Set<String> platformTaskDeliveryCoordinationSurfaces() {
        Path coordination = REPO_ROOT.resolve(
                "outbox-event-module/src/main/java/com/example/platform/outbox/coordination");
        return productionJavaFiles(coordination).stream()
                .filter(path -> Pattern.compile(
                                "(?s)(?:\\brecord\\s+PlatformTask\\s*\\(|"
                                        + "\\bPlatformTask\\s+[A-Za-z_$][A-Za-z0-9_$]*\\b)")
                        .matcher(stripComments(source(path)))
                        .find())
                .map(TaskGArchitectureGuardTest::relative)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> structuralAttemptAuthoritiesOutsideWorkerFabric() {
        return structuralAuthoritiesOutsideWorkerFabric(
                List.of("ExecutionAttemptId", "ExecutionOwnershipGeneration", "ExecutionAttemptState"));
    }

    private static Set<String> structuralReservationAuthoritiesOutsideWorkerFabric() {
        return structuralAuthoritiesOutsideWorkerFabric(
                List.of("PhysicalHostId", "ReservedResources", "ReservationState"));
    }

    private static Set<String> structuralAuthoritiesOutsideWorkerFabric(List<String> fieldTypes) {
        return allProductionJavaFiles().stream()
                .filter(path -> !path.startsWith(WORKER_MAIN))
                .filter(path -> {
                    String executableSource = stripComments(source(path));
                    return executableSource.matches("(?s).*(?:record|class)\\s+.*")
                            && fieldTypes.stream().allMatch(type -> Pattern.compile(
                                            "\\b" + Pattern.quote(type)
                                                    + "\\s+[A-Za-z_$][A-Za-z0-9_$]*\\b")
                                    .matcher(executableSource)
                                    .find());
                })
                .map(TaskGArchitectureGuardTest::relative)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> genericRuntimeGodInterfaces() {
        List<String> authorityTypes = List.of(
                "ExecutionAssignment",
                "ExecutionAttempt",
                "TaskLease",
                "Reservation",
                "SchedulableCapacity",
                "WorkerRuntimeId");
        return allProductionJavaFiles().stream()
                .filter(path -> stripComments(source(path)).matches("(?s).*\\binterface\\s+.*"))
                .filter(path -> authorityTypes.stream()
                                .filter(type -> Pattern.compile("\\b" + type + "\\b")
                                        .matcher(stripComments(source(path)))
                                        .find())
                                .count()
                        >= 3)
                .map(TaskGArchitectureGuardTest::relative)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static int importCount(String source, String importedAuthorityPattern) {
        Pattern pattern = Pattern.compile("(?m)^import\\s+.*" + importedAuthorityPattern + ".*;");
        int count = 0;
        var matcher = pattern.matcher(source);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String methodRegion(String source, String startMethod, String nextMethod) {
        int startName = source.indexOf(startMethod);
        int endName = source.indexOf(nextMethod, startName + startMethod.length());
        int start = source.lastIndexOf("\n    ", startName);
        int end = source.lastIndexOf("\n    ", endName);
        if (start < 0 || end <= start) {
            throw new IllegalStateException("method source boundary not found");
        }
        return source.substring(start, end);
    }

    private static String javaSourcesWithFileNamePrefix(Path root, String prefix) {
        return productionJavaFiles(root).stream()
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .map(TaskGArchitectureGuardTest::source)
                .collect(Collectors.joining("\n"));
    }

    private static String javaSources(Path root) {
        return productionJavaFiles(root).stream()
                .map(TaskGArchitectureGuardTest::source)
                .collect(Collectors.joining("\n"));
    }

    private static List<Path> allProductionJavaFiles() {
        try (var files = Files.walk(REPO_ROOT)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot enumerate production Java sources", exception);
        }
    }

    private static List<Path> productionJavaFiles(Path root) {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot enumerate production Java sources: " + root, exception);
        }
    }

    private static String source(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read architecture guard source: " + path, exception);
        }
    }

    private static String stripComments(String value) {
        return value.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static String relative(Path path) {
        return REPO_ROOT.relativize(path).toString().replace('\\', '/');
    }

    private static Path testDomain() {
        return REPO_ROOT.resolve(
                "worker-fabric-module/src/test/java/com/example/platform/workerfabric/domain");
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

}
