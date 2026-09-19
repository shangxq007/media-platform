package com.example.platform.workflow;

import static com.example.platform.workflow.plan.WorkflowPlan.*;

import static org.assertj.core.api.Assertions.*;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.identity.app.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.RoleRepository;
import com.example.platform.operation.invocation.*;
import com.example.platform.operation.operation.*;
import com.example.platform.outbox.app.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.workflow.definition.app.UserWorkflowDefinitionService;
import com.example.platform.workflow.definition.domain.*;
import com.example.platform.workflow.plan.*;
import com.example.platform.workflow.run.*;
import com.example.platform.workflow.temporal.*;

import io.temporal.client.*;
import io.temporal.testing.TestWorkflowEnvironment;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Full Spring/Identity/Outbox/PostgreSQL assembly with real SDK runtime and an explicitly
 * controlled Operation owner.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview", "ep07-workflow-test"})
@TestPropertySource(
        properties = {
            "app.security.enabled=true",
            "app.security.oauth2.enabled=false",
            "app.identity.api-key-auth-enabled=false",
            "app.outbox.dispatcher-enabled=false",
            "storage.s3.enabled=false",
            "app.security.jwt.secret-key=ep07-workflow-http-key-at-least-256-bits"
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(WorkflowFoundationIntegrationTest.Runtime.class)
class WorkflowFoundationIntegrationTest extends PostgresTestContainerSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired WorkflowRunService runs;
    @Autowired WorkflowRunStore store;
    @Autowired WorkflowDispatch dispatch;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserWorkflowDefinitionService definitions;
    @Autowired TestWorkflowEnvironment temporal;
    @Autowired ControlledOperation operation;
    @Autowired FaultActivities faults;
    @LocalServerPort int port;
    String tenant, user, workspace, project, outsider;

    @TestConfiguration
    @Profile("ep07-workflow-test")
    static class Runtime {
        @Bean(destroyMethod = "close")
        TestWorkflowEnvironment workflowTestEnvironment() {
            return TestWorkflowEnvironment.newInstance(
                    io.temporal.testing.TestEnvironmentOptions.newBuilder()
                            .setUseExternalService(true)
                            .setTarget(
                                    System.getenv()
                                            .getOrDefault(
                                                    "EP07_TEMPORAL_TARGET", "127.0.0.1:17233"))
                            .build());
        }

        @Bean
        WorkflowClient workflowClient(TestWorkflowEnvironment env) {
            return env.getWorkflowClient();
        }

        @Bean
        @Primary
        ControlledOperation controlledOperation(JdbcTemplate jdbc) {
            return new ControlledOperation(jdbc);
        }

        @Bean
        FaultActivities faultActivities(WorkflowActivities activities) {
            return new FaultActivities(activities);
        }

        @Bean
        org.springframework.context.ApplicationListener<
                        org.springframework.boot.context.event.ApplicationReadyEvent>
                startWorker(TestWorkflowEnvironment env, FaultActivities activities) {
            return event -> {
                var worker = env.newWorker("workflow-process");
                worker.registerWorkflowImplementationTypes(PlanWalkWorkflowImpl.class);
                worker.registerActivitiesImplementations(activities);
                env.start();
            };
        }
    }

    /** Faults occur outside the production transactional proxy, after its database commit. */
    public static class FaultActivities implements PlanWalkActivities {
        final WorkflowActivities delegate;
        final java.util.concurrent.atomic.AtomicBoolean failAcknowledgement =
                new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean failProjection =
                new java.util.concurrent.atomic.AtomicBoolean();
        final Map<String, CountDownLatch> waitSignals = new ConcurrentHashMap<>();
        final Map<String, CountDownLatch[]> invocationGates = new ConcurrentHashMap<>();

        FaultActivities(WorkflowActivities delegate) {
            this.delegate = delegate;
        }

        public String invoke(
                String run, String step, String plan, String node, Map<String, String> bindings) {
            String result = delegate.invoke(run, step, plan, node, bindings);
            var gate = invocationGates.get(bindings.getOrDefault("baseRevisionId", ""));
            if (gate != null) {
                gate[0].countDown();
                try {
                    if (!gate[1].await(30, TimeUnit.SECONDS))
                        throw new IllegalStateException("Correction test gate timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            if (failAcknowledgement.compareAndSet(true, false))
                throw new IllegalStateException(
                        "Injected acknowledgement loss after committed effect/receipt");
            return result;
        }

        public void waiting(String run, String step, String kind, long deadline) {
            delegate.waiting(run, step, kind, deadline);
            waitSignals.computeIfAbsent(run, k -> new CountDownLatch(1)).countDown();
        }

        public void stepCompleted(String run, String step, String result) {
            delegate.stepCompleted(run, step, result);
            if (failProjection.compareAndSet(true, false))
                throw new IllegalStateException("Injected projection acknowledgement loss");
        }

        public String terminal(String run, String status, String code, String step) {
            return delegate.terminal(run, status, code, step);
        }
    }

    static class ControlledOperation implements OperationInvocationPort {
        final JdbcTemplate jdbc;

        ControlledOperation(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void validate(
                OperationRequest request, OperationInvocationContext context, String project) {
            if (!request.definitionId().value().equals("test.echo")
                    || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest t)
                    || !project.equals(t.timelineId()))
                throw new IllegalArgumentException("Unsupported controlled operation/scope");
        }

        public OperationInvocationResult invoke(
                OperationRequest request, OperationInvocationContext context) {
            jdbc.update(
                    "insert into ep07_test_effect(invocation_id,base_revision) values (?,?) on"
                            + " conflict (invocation_id) do update set"
                            + " invocation_count=ep07_test_effect.invocation_count+1",
                    context.invocationId(),
                    request.baseRevisionId());
            return new OperationInvocationResult.Applied(
                    request.definitionId(),
                    request.version(),
                    "digest",
                    request.baseRevisionId(),
                    "revision-" + context.invocationId(),
                    "hash",
                    context.invocationId(),
                    context.provenance().correlationId());
        }
    }

    @BeforeEach
    void fixture() {
        tenant = "wf-" + UUID.randomUUID();
        jdbc.update(
                "insert into tenant(id,name,status,created_at) values (?,?,'ACTIVE',now())",
                tenant,
                "workflow test");
        user = member();
        outsider = member();
        as(
                user,
                () -> {
                    workspace =
                            context.getBean(WorkspaceService.class)
                                    .createWorkspace(
                                            tenant,
                                            new CreateWorkspaceRequest("workflow", null, null))
                                    .id();
                });
        var roles = context.getBean(RoleRepository.class);
        var permissionService = context.getBean(PermissionService.class);
        var role =
                context.getBean(RoleService.class)
                        .createRole(
                                "workflow-" + UUID.randomUUID(),
                                "Workflow test",
                                null,
                                com.example.platform.identity.domain.Role.RoleScope.WORKSPACE);
        for (String key :
                List.of(
                        "CREATE",
                        "READ",
                        "WRITE",
                        "workflow.execution.start",
                        "workflow.execution.read",
                        "workflow.execution.cancel",
                        "workflow.execution.approve",
                        "workflow-definition.edit",
                        "workflow-definition.read",
                        "workflow-definition.publish",
                        "workflow-definition.archive")) {
            var permission =
                    roles.findAllPermissions().stream()
                            .filter(p -> p.permissionKey().equals(key))
                            .findFirst()
                            .orElseGet(
                                    () ->
                                            permissionService.createPermission(
                                                    key, key, null, "PROJECT"));
            roles.saveRolePermission(
                    new RolePermission(
                            UUID.randomUUID().toString(),
                            role.id(),
                            permission.id(),
                            Instant.now()));
        }
        String membership =
                jdbc.queryForObject(
                        "select id from workspace_member where workspace_id=? and user_id=?",
                        String.class,
                        workspace,
                        user);
        as(
                user,
                () ->
                        context.getBean(WorkspaceService.class)
                                .assignRoleToMember(
                                        workspace,
                                        membership,
                                        new AssignRoleRequest(role.roleKey(), user)));
        as(
                user,
                () ->
                        project =
                                context.getBean(TenantProjectService.class)
                                        .createProject(
                                                tenant,
                                                new CreateProjectRequest(
                                                        "workflow", null, workspace))
                                        .id());
        jdbc.execute(
                "create table if not exists ep07_test_effect(invocation_id text primary"
                    + " key,base_revision text not null, invocation_count integer not null default"
                    + " 1)");
    }

    String member() {
        String id = "member-" + UUID.randomUUID();
        jdbc.update(
                "insert into \"user\"(id,tenant_id,username,email,role,status,created_at) values"
                        + " (?,?,?,?,'MEMBER','ACTIVE',now())",
                id,
                tenant,
                id,
                id + "@test.invalid");
        var accounts = context.getBean(AccountMembershipService.class);
        var actor = CanonicalActor.system("system:identity-provisioning", tenant);
        var account = accounts.provisionVerifiedAccount(actor, "urn:media-platform:local-hmac", id);
        accounts.linkMembership(actor, account, tenant, id);
        return id;
    }

    void as(String subject, Runnable action) {
        var request = new MockHttpServletRequest();
        request.setAttribute("auth.subject", subject);
        request.setAttribute("jwt.issuer", "urn:media-platform:local-hmac");
        request.setAttribute("jwt.tenantId", tenant);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.set(tenant);
        try {
            action.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            TenantContext.clear();
        }
    }

    Node effect(String id, Map<String, ValueRef> bindings) {
        var request =
                new OperationRequest(
                        new OperationDefinitionId("test.echo"),
                        OperationDefinitionVersion.V1_0,
                        new OperationTargetRequest.TimelineTargetRequest(project),
                        new OperationParameters.NoParameters(),
                        "base",
                        "base-hash",
                        null);
        return new Node(
                id,
                Kind.OPERATION_INVOCATION,
                null,
                null,
                0,
                0,
                null,
                request,
                List.of(),
                bindings,
                new Retry(3, 1),
                null);
    }

    String publish(List<Node> nodes, List<ControlEdge> edges) {
        List<UserWorkflowDefinitionNode> declarations =
                nodes.stream()
                        .map(
                                n ->
                                        new UserWorkflowDefinitionNode(
                                                n.id(),
                                                WorkflowNodeType.valueOf(n.kind().name()),
                                                n.id(),
                                                "workflow.node.v2",
                                                new UserWorkflowDefinitionNode
                                                        .VersionedJsonDocument(2, RunJson.write(n)),
                                                List.of(),
                                                List.of(),
                                                n.retry().maximumAttempts() > 1
                                                        ? UserWorkflowDefinitionNode.ErrorPolicy
                                                                .RETRY
                                                        : UserWorkflowDefinitionNode.ErrorPolicy
                                                                .FAIL))
                        .toList();
        // Use the plan codec's explicit owner variants for node JSON.
        var plan =
                new WorkflowPlan(
                        1, "temporary", 1, tenant, project, nodes.getFirst().id(), nodes, edges);
        var tree =
                RunJson.read(
                        new WorkflowPlanCodec().encode(plan),
                        com.fasterxml.jackson.databind.JsonNode.class);
        Map<String, String> serialized = new HashMap<>();
        tree.path("nodes").forEach(n -> serialized.put(n.path("id").asText(), n.toString()));
        declarations =
                declarations.stream()
                        .map(
                                n ->
                                        new UserWorkflowDefinitionNode(
                                                n.nodeId(),
                                                n.nodeType(),
                                                n.name(),
                                                n.configSchemaRef(),
                                                new UserWorkflowDefinitionNode
                                                        .VersionedJsonDocument(
                                                        2, serialized.get(n.nodeId())),
                                                n.inputDeclarations(),
                                                n.outputDeclarations(),
                                                n.errorPolicy()))
                        .toList();
        var def =
                definitions.create(
                        tenant,
                        project,
                        "workflow",
                        null,
                        declarations,
                        edges.stream()
                                .map(
                                        e ->
                                                UserWorkflowDefinitionEdge.unconditional(
                                                        UUID.randomUUID().toString(),
                                                        e.parentId(),
                                                        e.childId(),
                                                        e.order()))
                                .toList(),
                        List.of(),
                        UserWorkflowTriggerBinding.manual(),
                        2,
                        user);
        assertThat(definitions.validate(tenant, def.definitionId(), def.version(), user).valid())
                .isTrue();
        definitions.publish(tenant, def.definitionId(), def.version(), 2, user);
        return def.definitionId().value();
    }

    WorkflowRunService.View start(String definition, String key) {
        WorkflowRunService.View[] result = new WorkflowRunService.View[1];
        as(
                user,
                () ->
                        result[0] =
                                runs.start(
                                        tenant,
                                        new WorkflowRunService.Start(
                                                definition, 1, project, key, "{}")));
        return result[0];
    }

    String event(String run) {
        return jdbc.queryForObject(
                "select id from outbox_events where aggregate_id=? and"
                        + " event_type='workflow.run.start'",
                String.class,
                run);
    }

    OutboxEventDispatcher dispatcher() {
        return new OutboxEventDispatcher(
                context.getBean(OutboxEventService.class),
                context,
                context.getBean(OutboxEventRouter.class),
                3,
                context.getBean(io.micrometer.core.instrument.MeterRegistry.class));
    }

    void complete(String id) {
        try {
            temporal.getWorkflowClient()
                    .newUntypedWorkflowStub(WorkflowDispatch.workflowId(id))
                    .getResult(30, TimeUnit.SECONDS, Map.class);
        } catch (java.util.concurrent.TimeoutException e) {
            try {
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of("build/reports/ep07-timeout-history.json"),
                        temporal.getWorkflowClient()
                                .fetchHistory(WorkflowDispatch.workflowId(id))
                                .toJson(true));
            } catch (Exception ignored) {
            }
            throw new AssertionError(e);
        }
    }

    @Test
    @Order(1)
    void assembledMultiStepDataAndDurableEffects() {
        String def =
                publish(
                        List.of(
                                Node.control("root", Kind.SEQUENCE),
                                effect("first", Map.of()),
                                effect(
                                        "second",
                                        Map.of(
                                                "baseRevisionId",
                                                new ValueRef(Source.RESULT, "first.revisionId"),
                                                "baseContentHash",
                                                new ValueRef(Source.RESULT, "first.contentHash")))),
                        List.of(
                                new ControlEdge("root", "first", 0),
                                new ControlEdge("root", "second", 1)));
        var run = start(def, "same");
        assertThat(store.require(run.id()).workspaceId()).isEqualTo(workspace);
        assertThat(start(def, "same").id()).isEqualTo(run.id());
        try (var dispatcher = dispatcher()) {
            assertThat(dispatcher.processOnce(event(run.id()))).isTrue();
        }
        complete(run.id());
        assertThat(store.require(run.id()).status()).isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_operation_receipt where run_id=?",
                                Integer.class,
                                run.id()))
                .isEqualTo(2);
        var bases =
                jdbc.queryForList(
                        "select base_revision from ep07_test_effect where invocation_id like ?",
                        String.class,
                        "workflow:" + run.id() + ":%");
        assertThat(bases).hasSize(2).contains("base");
        assertThat(bases.stream().anyMatch(s -> s.startsWith("revision-workflow:" + run.id())))
                .isTrue();
        // Reconstructed dispatcher observes the same logical execution after acknowledgement loss.
        dispatch.ensureStarted(store.require(run.id()));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ep07_test_effect where invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isEqualTo(2);
    }

    @Test
    @Order(1)
    void rollbackAndConflictingConcurrentRequest() throws Exception {
        String def = publish(List.of(effect("root", Map.of())), List.of());
        as(
                user,
                () ->
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(
                                        tx -> {
                                            runs.start(
                                                    tenant,
                                                    new WorkflowRunService.Start(
                                                            def, 1, project, "rollback", "{}"));
                                            tx.setRollbackOnly();
                                        }));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_run where tenant_id=?",
                                Integer.class,
                                tenant))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_events where payload like ?",
                                Integer.class,
                                "%" + tenant + "%"))
                .isZero();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a =
                    executor.submit(
                            () -> {
                                barrier.await();
                                return start(def, "concurrent").id();
                            });
            var b =
                    executor.submit(
                            () -> {
                                barrier.await();
                                return start(def, "concurrent").id();
                            });
            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(b.get(30, TimeUnit.SECONDS));
        }
        as(
                user,
                () ->
                        assertThatThrownBy(
                                        () ->
                                                runs.start(
                                                        tenant,
                                                        new WorkflowRunService.Start(
                                                                def,
                                                                1,
                                                                project,
                                                                "concurrent",
                                                                "{\"different\":true}")))
                                .isInstanceOf(IllegalArgumentException.class));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_run where tenant_id=?",
                                Integer.class,
                                tenant))
                .isEqualTo(1);
    }

    @Test
    @Order(1)
    void missingScopeAndUnauthorizedCommandsFailWithoutEffects() {
        String def = publish(List.of(effect("root", Map.of())), List.of());
        as(
                outsider,
                () ->
                        assertThatThrownBy(
                                        () ->
                                                runs.start(
                                                        tenant,
                                                        new WorkflowRunService.Start(
                                                                def, 1, project, "denied", "{}")))
                                .isInstanceOf(RuntimeException.class));
        as(
                user,
                () ->
                        assertThatThrownBy(
                                        () ->
                                                runs.start(
                                                        tenant,
                                                        new WorkflowRunService.Start(
                                                                def, 1, "missing", "missing",
                                                                "{}")))
                                .isInstanceOf(RuntimeException.class));
        var run = start(def, "allowed");
        as(
                outsider,
                () -> {
                    assertThatThrownBy(() -> runs.get(tenant, run.id()))
                            .isInstanceOf(RuntimeException.class);
                    assertThatThrownBy(() -> runs.cancel(tenant, run.id()))
                            .isInstanceOf(RuntimeException.class);
                    assertThatThrownBy(
                                    () -> runs.release(tenant, run.id(), "step", "release", true))
                            .isInstanceOf(RuntimeException.class);
                });
        assertThat(store.require(run.id()).cancelRequested()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ep07_test_effect where invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isZero();
    }

    @Test
    @Order(1)
    void committedEffectAcknowledgementFailureDoesNotRepeatEffect() {
        String def = publish(List.of(effect("root", Map.of())), List.of());
        var run = start(def, "ack-loss");
        faults.failAcknowledgement.set(true);
        faults.failProjection.set(true);
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run.id()))).isTrue();
        }
        complete(run.id());
        assertThat(faults.failAcknowledgement.get()).isFalse();
        assertThat(store.require(run.id()).status()).isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ep07_test_effect where invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_operation_receipt where run_id=?",
                                Integer.class,
                                run.id()))
                .isEqualTo(1);
    }

    @Test
    @Order(1)
    void acceptedDefinitionIsPinnedAcrossArchiveAndDispatchFailure() {
        String def = publish(List.of(effect("root", Map.of())), List.of());
        var run = start(def, "dispatch-failure");
        var provider =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                        .getBeanProvider(WorkflowClient.class);
        assertThatThrownBy(
                        () ->
                                new WorkflowDispatch(store, provider)
                                        .ensureStarted(store.require(run.id())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.require(run.id()).status()).isEqualTo("ACCEPTED");
        definitions.archive(
                tenant,
                new UserWorkflowDefinitionId(def),
                new UserWorkflowDefinitionVersion(1),
                3,
                user);
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run.id()))).isTrue();
        }
        complete(run.id());
        assertThat(store.require(run.id()).status()).isEqualTo("SUCCEEDED");
    }

    @Test
    @Order(1)
    void cancellationBeforeDispatchAndCompletionCannotFabricateSuccess() {
        String def = publish(List.of(effect("root", Map.of())), List.of());
        var run = start(def, "cancel");
        assertThatThrownBy(
                        () ->
                                context.getBean(WorkflowActivities.class)
                                        .terminal(run.id(), "SUCCEEDED", null, null))
                .isInstanceOf(RuntimeException.class);
        assertThat(store.require(run.id()).status()).isEqualTo("ACCEPTED");
        as(user, () -> runs.cancel(tenant, run.id()));
        String control =
                jdbc.queryForObject(
                        "select id from outbox_events where aggregate_id=? and"
                                + " event_type='workflow.run.control'",
                        String.class,
                        run.id());
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(control)).isTrue();
        }
        assertThatThrownBy(() -> complete(run.id())).isInstanceOf(WorkflowFailedException.class);
        assertThat(store.require(run.id()).status()).isEqualTo("CANCELLED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ep07_test_effect where invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isZero();
        as(user, () -> assertThat(runs.cancel(tenant, run.id()).status()).isEqualTo("CANCELLED"));
    }

    @Test
    @Order(1)
    void invalidOperationBindingCapabilityAndChildFailBeforeAcceptance() {
        Node good = effect("root", Map.of());
        var badRequest =
                new OperationRequest(
                        new OperationDefinitionId("unsupported"),
                        OperationDefinitionVersion.V1_0,
                        good.operation().target(),
                        good.operation().parameters(),
                        "base",
                        "hash",
                        null);
        var unsupported =
                new Node(
                        "root",
                        Kind.OPERATION_INVOCATION,
                        null,
                        null,
                        0,
                        0,
                        null,
                        badRequest,
                        List.of(),
                        Map.of(),
                        null,
                        null);
        String bad = publish(List.of(unsupported), List.of());
        assertThatThrownBy(() -> start(bad, "bad-operation")).isInstanceOf(RuntimeException.class);
        var badBinding =
                effect(
                        "root",
                        Map.of("baseRevisionId", new ValueRef(Source.RESULT, "future.revisionId")));
        String invalid = publish(List.of(badBinding), List.of());
        assertThatThrownBy(() -> start(invalid, "bad-binding"))
                .isInstanceOf(IllegalArgumentException.class);
        var capability =
                com.example.platform.extension.domain.CapabilityRequirement.of(
                        new com.example.platform.extension.domain.CapabilityId(
                                "missing.capability"),
                        com.example.platform.extension.domain.ContractVersionRange.exactly(
                                new com.example.platform.extension.domain.ContractVersion(1, 0)));
        var missing =
                new Node(
                        "root",
                        Kind.OPERATION_INVOCATION,
                        null,
                        null,
                        0,
                        0,
                        null,
                        good.operation(),
                        List.of(capability),
                        Map.of(),
                        null,
                        null);
        String cap = publish(List.of(missing), List.of());
        assertThatThrownBy(() -> start(cap, "missing-capability"))
                .isInstanceOf(IllegalArgumentException.class);
        var child =
                new WorkflowPlan(
                        1,
                        "never-published",
                        1,
                        tenant,
                        project,
                        "leaf",
                        List.of(effect("leaf", Map.of())),
                        List.of());
        var sub =
                new Node(
                        "root",
                        Kind.SUBWORKFLOW,
                        null,
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ChildPin(new WorkflowPlanCodec().digest(child), child));
        String subId = publish(List.of(sub), List.of());
        assertThatThrownBy(() -> start(subId, "unpublished-child"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_run where tenant_id=?",
                                Integer.class,
                                tenant))
                .isZero();
        var optional =
                new Node(
                        "root",
                        Kind.OPERATION_INVOCATION,
                        null,
                        null,
                        0,
                        0,
                        null,
                        good.operation(),
                        List.of(
                                com.example.platform.extension.domain.CapabilityRequirement
                                        .optional(
                                                capability.capabilityId(),
                                                capability.contractRange())),
                        Map.of(),
                        null,
                        null);
        String optionalId = publish(List.of(optional), List.of());
        var accepted = start(optionalId, "optional-absence");
        assertThat(store.require(accepted.id()).bindingsJson()).contains("null");
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(accepted.id()))).isTrue();
        }
        complete(accepted.id());
        assertThat(store.require(accepted.id()).status()).isEqualTo("SUCCEEDED");
    }

    @Test
    @Order(1)
    void publishedChildAndTerminatingLoopUsePinnedOperations() {
        String childId = publish(List.of(effect("leaf", Map.of())), List.of());
        var child =
                context.getBean(WorkflowAdmission.class)
                        .resolve(
                                definitions.getVersion(
                                        tenant,
                                        new UserWorkflowDefinitionId(childId),
                                        new UserWorkflowDefinitionVersion(1)));
        var sub =
                new Node(
                        "child",
                        Kind.SUBWORKFLOW,
                        null,
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ChildPin(new WorkflowPlanCodec().digest(child), child));
        var loop =
                new Node(
                        "loop",
                        Kind.LOOP,
                        new Predicate(
                                Comparison.IS_EMPTY, new ValueRef(Source.RESULT, "body"), null),
                        null,
                        3,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        String id =
                publish(
                        List.of(
                                Node.control("root", Kind.SEQUENCE),
                                sub,
                                loop,
                                effect("body", Map.of())),
                        List.of(
                                new ControlEdge("root", "child", 0),
                                new ControlEdge("root", "loop", 1),
                                new ControlEdge("loop", "body", 0)));
        var run = start(id, "child-loop");
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run.id()))).isTrue();
        }
        complete(run.id());
        assertThat(store.require(run.id()).status()).isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_operation_receipt where run_id=?",
                                Integer.class,
                                run.id()))
                .isEqualTo(2);
    }

    java.net.http.HttpResponse<String> http(String subject, String method, String path, Object body)
            throws Exception {
        var request =
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/json");
        if (subject != null) {
            String token =
                    io.jsonwebtoken.Jwts.builder()
                            .subject(subject)
                            .claim("tenantId", tenant)
                            .expiration(new java.util.Date(System.currentTimeMillis() + 600000))
                            .signWith(
                                    io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                            "ep07-workflow-http-key-at-least-256-bits"
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)))
                            .compact();
            request.header("Authorization", "Bearer " + token);
        }
        return java.net.http.HttpClient.newHttpClient()
                .send(
                        request.method(
                                        method,
                                        body == null
                                                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                                                : java.net.http.HttpRequest.BodyPublishers.ofString(
                                                        RunJson.write(body)))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @Order(1)
    void authenticatedPublicationAndStartRejectForgedBodyIdentity() throws Exception {
        var node = effect("root", Map.of());
        var plan =
                new WorkflowPlan(1, "draft", 1, tenant, project, "root", List.of(node), List.of());
        var config =
                RunJson.read(
                                new WorkflowPlanCodec().encode(plan),
                                com.fasterxml.jackson.databind.JsonNode.class)
                        .path("nodes")
                        .get(0);
        var body =
                Map.of(
                        "name",
                        "HTTP workflow",
                        "projectId",
                        project,
                        "schemaVersion",
                        2,
                        "nodes",
                        List.of(
                                Map.of(
                                        "nodeId",
                                        "root",
                                        "nodeType",
                                        "OPERATION_INVOCATION",
                                        "name",
                                        "root",
                                        "configSchemaRef",
                                        "workflow.node.v2",
                                        "configValues",
                                        config,
                                        "inputDeclarations",
                                        List.of(),
                                        "outputDeclarations",
                                        List.of(),
                                        "errorPolicy",
                                        "RETRY")),
                        "edges",
                        List.of(),
                        "parameters",
                        List.of(),
                        "trigger",
                        Map.of("triggerType", "MANUAL"));
        String base = "/api/tenants/" + tenant;
        assertThat(http(null, "POST", base + "/workflow-definitions", body).statusCode())
                .isIn(401, 403);
        var created = http(user, "POST", base + "/workflow-definitions", body);
        assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);
        String id =
                RunJson.read(created.body(), com.fasterxml.jackson.databind.JsonNode.class)
                        .path("definitionId")
                        .asText();
        assertThat(
                        http(
                                        user,
                                        "POST",
                                        base
                                                + "/workflow-definitions/"
                                                + id
                                                + "/versions/1/validate",
                                        Map.of("optimisticVersion", 1))
                                .statusCode())
                .isEqualTo(200);
        var published =
                http(
                        user,
                        "POST",
                        base + "/workflow-definitions/" + id + "/versions/1/publish",
                        Map.of("optimisticVersion", 2));
        assertThat(published.statusCode()).withFailMessage(published.body()).isEqualTo(200);
        Map<String, Object> start =
                new HashMap<>(
                        Map.of(
                                "definitionId",
                                id,
                                "definitionVersion",
                                1,
                                "projectId",
                                project,
                                "idempotencyKey",
                                "http",
                                "inputsJson",
                                "{}"));
        start.put("actorId", outsider);
        assertThat(http(user, "POST", base + "/workflow-executions", start).statusCode())
                .isEqualTo(400);
        start.remove("actorId");
        start.put("workspaceId", tenant);
        assertThat(http(user, "POST", base + "/workflow-executions", start).statusCode())
                .isEqualTo(400);
        start.remove("workspaceId");
        assertThat(http(outsider, "POST", base + "/workflow-executions", start).statusCode())
                .isIn(403, 404);
        var accepted = http(user, "POST", base + "/workflow-executions", start);
        assertThat(accepted.statusCode()).withFailMessage(accepted.body()).isEqualTo(201);
        String run =
                RunJson.read(accepted.body(), com.fasterxml.jackson.databind.JsonNode.class)
                        .path("id")
                        .asText();
        assertThat(http(user, "GET", base + "/workflow-executions/" + run, null).statusCode())
                .isEqualTo(200);
        assertThat(http(outsider, "GET", base + "/workflow-executions/" + run, null).statusCode())
                .isIn(403, 404);
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run))).isTrue();
        }
        complete(run);
        assertThat(store.require(run).status()).isEqualTo("SUCCEEDED");
    }

    @Test
    @Order(1)
    void temporalAcceptanceThenOutboxAcknowledgementLossReconcilesSameRun() throws Exception {
        var wait =
                new Node(
                        "wait",
                        Kind.WAIT,
                        null,
                        new Wait(WaitKind.APPROVAL, 86400000),
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        String def =
                publish(
                        List.of(
                                Node.control("root", Kind.SEQUENCE),
                                wait,
                                effect("effect", Map.of())),
                        List.of(
                                new ControlEdge("root", "wait", 0),
                                new ControlEdge("root", "effect", 1)));
        var run = start(def, "start-ack");
        var latch = new CountDownLatch(1);
        faults.waitSignals.put(run.id(), latch);
        try (var failing =
                new OutboxEventDispatcher(
                        context.getBean(OutboxEventService.class),
                        payload -> {
                            context.publishEvent(payload);
                            throw new IllegalStateException(
                                    "Injected dispatch acknowledgement loss");
                        },
                        context.getBean(OutboxEventRouter.class),
                        3,
                        context.getBean(io.micrometer.core.instrument.MeterRegistry.class))) {
            assertThat(failing.processOnce(event(run.id()))).isFalse();
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        var before =
                temporal.getWorkflowClient()
                        .newUntypedWorkflowStub(WorkflowDispatch.workflowId(run.id()))
                        .describe()
                        .getFirstRunId();
        assertThat(
                        jdbc.queryForObject(
                                "select status from outbox_events where id=?",
                                String.class,
                                event(run.id())))
                .isEqualTo("FAILED");
        jdbc.update("update outbox_events set next_attempt_at=now() where id=?", event(run.id()));
        context.getBean(OutboxEventService.class).resetDueFailedEvents();
        try (var restarted = dispatcher()) {
            assertThat(restarted.processOnce(event(run.id()))).isTrue();
        }
        assertThat(
                        temporal.getWorkflowClient()
                                .newUntypedWorkflowStub(WorkflowDispatch.workflowId(run.id()))
                                .describe()
                                .getFirstRunId())
                .isEqualTo(before);
        as(
                user,
                () ->
                        runs.release(
                                tenant,
                                run.id(),
                                WorkflowStepIdentity.child(
                                        WorkflowStepIdentity.root("root"), "wait"),
                                "approved",
                                true));
        String control =
                jdbc.queryForObject(
                        "select id from outbox_events where aggregate_id=? and"
                                + " event_type='workflow.run.control'",
                        String.class,
                        run.id());
        try (var restarted = dispatcher()) {
            assertThat(restarted.processOnce(control)).isTrue();
        }
        complete(run.id());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ep07_test_effect where invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isEqualTo(1);
    }

    @Test
    @Order(1)
    void realContinueAsNewPreservesInvocationIdentityAndDatabaseEffects() {
        var each =
                new Node(
                        "root",
                        Kind.FOREACH,
                        null,
                        null,
                        100,
                        4,
                        new ValueRef(Source.INPUT, "items"),
                        null,
                        null,
                        null,
                        null,
                        null);
        String def =
                publish(
                        List.of(each, effect("body", Map.of())),
                        List.of(new ControlEdge("root", "body", 0)));
        String items =
                java.util.stream.IntStream.range(0, 70)
                        .mapToObj(Integer::toString)
                        .collect(java.util.stream.Collectors.joining(",", "{\"items\":[", "]}"));
        WorkflowRunService.View[] accepted = new WorkflowRunService.View[1];
        as(
                user,
                () ->
                        accepted[0] =
                                runs.start(
                                        tenant,
                                        new WorkflowRunService.Start(
                                                def, 1, project, "continue", items)));
        var run = accepted[0];
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run.id()))).isTrue();
        }
        String first =
                temporal.getWorkflowClient()
                        .newUntypedWorkflowStub(WorkflowDispatch.workflowId(run.id()))
                        .describe()
                        .getFirstRunId();
        complete(run.id());
        var history =
                temporal.getWorkflowClient()
                        .fetchHistory(WorkflowDispatch.workflowId(run.id()), first);
        assertThat(history.getEvents())
                .anyMatch(
                        e ->
                                e.getEventType()
                                        == io.temporal.api.enums.v1.EventType
                                                .EVENT_TYPE_WORKFLOW_EXECUTION_CONTINUED_AS_NEW);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_operation_receipt where run_id=?",
                                Integer.class,
                                run.id()))
                .isEqualTo(70);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ep07_test_effect where invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isEqualTo(70);
        assertThat(
                        jdbc.queryForObject(
                                "select max(invocation_count) from ep07_test_effect where"
                                        + " invocation_id like ?",
                                Integer.class,
                                "workflow:" + run.id() + ":%"))
                .isEqualTo(1);
    }

    @Test
    @Order(1)
    void foreachItemBindingsAreValidatedBeforeAcceptanceAndTransferredToOwner() {
        var each =
                new Node(
                        "root",
                        Kind.FOREACH,
                        null,
                        null,
                        3,
                        2,
                        new ValueRef(Source.INPUT, "items"),
                        null,
                        null,
                        null,
                        null,
                        null);
        var body =
                effect(
                        "body",
                        Map.of(
                                "baseRevisionId",
                                new ValueRef(Source.ITEM, "revision"),
                                "baseContentHash",
                                new ValueRef(Source.ITEM, "hash")));
        String def = publish(List.of(each, body), List.of(new ControlEdge("root", "body", 0)));
        as(
                user,
                () ->
                        assertThatThrownBy(
                                        () ->
                                                runs.start(
                                                        tenant,
                                                        new WorkflowRunService.Start(
                                                                def,
                                                                1,
                                                                project,
                                                                "invalid-item",
                                                                "{\"items\":[{\"revision\":17,\"hash\":\"h\"}]}")))
                                .isInstanceOf(IllegalArgumentException.class));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_run where tenant_id=?",
                                Integer.class,
                                tenant))
                .isZero();
        WorkflowRunService.View[] accepted = new WorkflowRunService.View[1];
        as(
                user,
                () ->
                        accepted[0] =
                                runs.start(
                                        tenant,
                                        new WorkflowRunService.Start(
                                                def,
                                                1,
                                                project,
                                                "valid-items",
                                                "{\"items\":[{\"revision\":\"base-a\",\"hash\":\"ha\"},{\"revision\":\"base-b\",\"hash\":\"hb\"}]}")));
        var run = accepted[0];
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run.id()))).isTrue();
        }
        complete(run.id());
        assertThat(
                        jdbc.queryForList(
                                "select base_revision from ep07_test_effect where invocation_id"
                                        + " like ?",
                                String.class,
                                "workflow:" + run.id() + ":%"))
                .containsExactlyInAnyOrder("base-a", "base-b");
    }

    @Test
    @Order(100)
    void durableWaitDuplicateReleaseAndRestart() throws Exception {
        var wait =
                new Node(
                        "wait",
                        Kind.WAIT,
                        null,
                        new Wait(WaitKind.APPROVAL, 86400000),
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        String def =
                publish(
                        List.of(
                                Node.control("root", Kind.SEQUENCE),
                                wait,
                                effect("effect", Map.of())),
                        List.of(
                                new ControlEdge("root", "wait", 0),
                                new ControlEdge("root", "effect", 1)));
        var run = start(def, "wait");
        CountDownLatch registered = new CountDownLatch(1);
        faults.waitSignals.put(run.id(), registered);
        try (var worker = dispatcher()) {
            assertThat(worker.processOnce(event(run.id()))).isTrue();
        }
        assertThat(registered.await(10, TimeUnit.SECONDS)).isTrue();
        String step = WorkflowStepIdentity.child(WorkflowStepIdentity.root("root"), "wait");
        assertThat(
                        jdbc.queryForObject(
                                "select status from workflow_run_step where run_id=? and step_id=?",
                                String.class,
                                run.id(),
                                step))
                .isEqualTo("WAITING");
        // Stop the worker factory; persisted Temporal history and PostgreSQL wait outlive worker
        // instances.
        temporal.getWorkerFactory().shutdown();
        temporal.getWorkerFactory().awaitTermination(10, TimeUnit.SECONDS);
        as(
                user,
                () -> {
                    runs.release(tenant, run.id(), step, "approved", true);
                    runs.release(tenant, run.id(), step, "approved", true);
                });
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_events where aggregate_id=? and"
                                        + " event_type='workflow.run.control'",
                                Integer.class,
                                run.id()))
                .isEqualTo(1);
        var restartClient =
                WorkflowClient.newInstance(
                        temporal.getWorkflowServiceStubs(),
                        WorkflowClientOptions.newBuilder()
                                .setNamespace("default")
                                .setIdentity("ep07-restarted-worker")
                                .build());
        var replacement = io.temporal.worker.WorkerFactory.newInstance(restartClient);
        var sdkWorker = replacement.newWorker("workflow-process");
        sdkWorker.registerWorkflowImplementationTypes(PlanWalkWorkflowImpl.class);
        sdkWorker.registerActivitiesImplementations(faults);
        replacement.start();
        try {
            String control =
                    jdbc.queryForObject(
                            "select id from outbox_events where aggregate_id=? and"
                                    + " event_type='workflow.run.control'",
                            String.class,
                            run.id());
            try (var worker = dispatcher()) {
                assertThat(worker.processOnce(control)).isTrue();
            }
            complete(run.id());
            as(
                    user,
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    runs.release(
                                                            tenant, run.id(), step, "late", true))
                                    .isInstanceOf(IllegalArgumentException.class));
            assertThat(store.require(run.id()).status()).isEqualTo("SUCCEEDED");
        } finally {
            // Keep a replacement worker available for subsequent methods, and close it with the
            // test context.
            replacement.shutdown();
            replacement.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(1)
    void approvalAfterContinuationUsesDurableDispatchAndSeventyDistinctEffects() throws Exception {
        continuedControl(WaitKind.APPROVAL, 70, false, false);
    }

    @Test
    @Order(1)
    void signalAndRetriedStartRaceRepeatedContinuationWithoutLosingIdentity() throws Exception {
        continuedControl(WaitKind.SIGNAL, 210, false, true);
    }

    @Test
    @Order(1)
    void cancellationAfterRepeatedContinuationFencesTerminalProjection() throws Exception {
        continuedControl(WaitKind.APPROVAL, 210, true, false);
    }

    private void continuedControl(WaitKind kind, int count, boolean cancel, boolean race)
            throws Exception {
        var wait = new Node("wait", Kind.WAIT, null, new Wait(kind, 120000),
                0, 0, null, null, null, null, null, null);
        var each = new Node("each", Kind.FOREACH, null, null, 300, 4,
                new ValueRef(Source.INPUT, "items"), null, null, null, null, null);
        String definition = publish(
                List.of(Node.control("root", Kind.PARALLEL), wait, each,
                        effect("effect", Map.of("baseRevisionId", new ValueRef(Source.ITEM, "item")))),
                List.of(new ControlEdge("root", "wait", 0), new ControlEdge("root", "each", 1),
                        new ControlEdge("each", "effect", 0)));
        String inputs = RunJson.write(Map.of("items", java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "base-" + i).toList()));
        var response = http(user, "POST", "/api/tenants/" + tenant + "/workflow-executions",
                Map.of("definitionId", definition, "definitionVersion", 1, "projectId", project,
                        "idempotencyKey", "continued-control", "inputsJson", inputs));
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
        String id = RunJson.read(response.body(), com.fasterxml.jackson.databind.JsonNode.class)
                .path("id").asText();
        var client = temporal.getWorkflowClient();
        var gate = new CountDownLatch[] {new CountDownLatch(1), new CountDownLatch(1)};
        if (race) faults.invocationGates.put("\"base-100\"", gate);
        try {
            try (var worker = dispatcher()) { assertThat(worker.processOnce(event(id))).isTrue(); }
            String first = client.newUntypedWorkflowStub(WorkflowDispatch.workflowId(id))
                    .describe().getFirstRunId();
            if (race) assertThat(gate[0].await(30, TimeUnit.SECONDS)).isTrue();
            else org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(effectCount(id)).isEqualTo(count));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(continuationsWithPins(id, first)).isGreaterThanOrEqualTo(1));
            String step = WorkflowStepIdentity.child(WorkflowStepIdentity.root("root"), "wait");
            assertThat(store.waitProjection(id, step).getFirst().get("status")).isEqualTo("WAITING");
            var release = Map.of("stepId", step, "releaseId", "continued-command", "approved", true);
            String base = "/api/tenants/" + tenant + "/workflow-executions/" + id;
            String path = base + (cancel ? "/cancel" : "/release");
            Object body = cancel ? Map.of() : release;
            assertThat(http(outsider, "POST", path, body).statusCode()).isIn(401, 403);
            assertThat(http(user, "POST", path, body).statusCode()).isEqualTo(200);
            assertThat(http(user, "POST", path, body).statusCode()).isEqualTo(200);
            String control = jdbc.queryForObject("select id from outbox_events where aggregate_id=?"
                    + " and event_type='workflow.run.control'", String.class, id);
            Runnable deliver = () -> {
                replayStart(id); // Same accepted intent, through the existing typed Outbox router.
                try (var worker = dispatcher()) { assertThat(worker.processOnce(control)).isTrue(); }
                // Re-deliver the persisted envelope; do not reset published Outbox rows.
                replayControl(id, step, cancel);
            };
            if (race) {
                var barrier = new CyclicBarrier(2);
                try (var executor = Executors.newSingleThreadExecutor()) {
                    var delivery = executor.submit(() -> { barrier.await(); deliver.run(); return true; });
                    barrier.await();
                    gate[1].countDown();
                    assertThat(delivery.get(30, TimeUnit.SECONDS)).isTrue();
                }
            } else deliver.run();
            if (cancel) assertThatThrownBy(() -> complete(id)).isInstanceOf(WorkflowFailedException.class);
            else complete(id);
            assertThat(store.require(id).status()).isEqualTo(cancel ? "CANCELLED" : "SUCCEEDED");
            assertThat(effectCount(id)).isEqualTo(count);
            assertThat(jdbc.queryForObject("select sum(invocation_count) from ep07_test_effect where invocation_id like ?",
                    Integer.class, "workflow:" + id + ":%" )).isEqualTo(count);
            assertThat(jdbc.queryForObject("select count(distinct base_revision) from ep07_test_effect where invocation_id like ?",
                    Integer.class, "workflow:" + id + ":%" )).isEqualTo(count);
            assertThat(jdbc.queryForObject("select count(*) from workflow_operation_receipt where run_id=?",
                    Integer.class, id)).isEqualTo(count);
            assertThat(jdbc.queryForObject("select status from outbox_events where id=?",
                    String.class, control)).isEqualTo("PUBLISHED");
            int transitions = continuationsWithPins(id, first);
            assertThat(transitions).isGreaterThanOrEqualTo(count == 70 ? 1 : 3);
            assertThat(http(user, "POST", base + "/release", release).statusCode()).isEqualTo(400);
            String terminal = store.require(id).status();
            assertThat(context.getBean(WorkflowActivities.class).terminal(id, "SUCCEEDED", null, null))
                    .isEqualTo(terminal);
            replayStart(id);
            replayControl(id, step, cancel);
            assertThat(store.require(id).status()).isEqualTo(terminal);
            System.out.println("EP07_CORRECTION_CONTROL run=" + id + " kind=" + kind + " cancel=" + cancel
                    + " continuations=" + transitions + " effects=" + count + " status=" + terminal);
        } finally {
            gate[1].countDown();
            faults.invocationGates.remove("\"base-100\"");
        }
    }

    private int effectCount(String id) {
        return jdbc.queryForObject("select count(*) from ep07_test_effect where invocation_id like ?",
                Integer.class, "workflow:" + id + ":%");
    }

    private int continuationsWithPins(String id, String first) {
        var client = temporal.getWorkflowClient();
        String execution = first;
        int count = 0;
        for (;;) {
            var described = client.newUntypedWorkflowStub(WorkflowDispatch.workflowId(id),
                    Optional.of(execution), Optional.empty()).describe();
            assertThat(described.getMemo("workflowRunId", String.class)).isEqualTo(id);
            assertThat(described.getMemo("planDigest", String.class)).isEqualTo(store.require(id).workflowPlanDigest());
            var next = client.fetchHistory(WorkflowDispatch.workflowId(id), execution).getEvents().stream()
                    .filter(e -> e.hasWorkflowExecutionContinuedAsNewEventAttributes()).findFirst();
            if (next.isEmpty()) return count;
            execution = next.get().getWorkflowExecutionContinuedAsNewEventAttributes().getNewExecutionRunId();
            count++;
            assertThat(count).isLessThan(20);
        }
    }

    private void replayStart(String id) {
        var run = store.require(id);
        com.example.platform.outbox.api.event.OutboxDeliveryContext.run(
                new com.example.platform.outbox.api.event.OutboxDeliveryContext.Delivery(event(id), tenant),
                () -> context.publishEvent(new RunEvents.Start(tenant, id, run.workflowPlanDigest())));
    }

    private void replayControl(String id, String step, boolean cancel) {
        String event = jdbc.queryForObject("select id from outbox_events where aggregate_id=?"
                + " and event_type='workflow.run.control'", String.class, id);
        com.example.platform.outbox.api.event.OutboxDeliveryContext.run(
                new com.example.platform.outbox.api.event.OutboxDeliveryContext.Delivery(event, tenant),
                () -> context.publishEvent(new RunEvents.Control(tenant, id, cancel ? null : step,
                        cancel ? "cancel" : "continued-command", !cancel, cancel)));
    }

    @Test
    @Order(1)
    void mismatchedOrMissingRuntimeIdentityFailsClosedWithoutEffects() {
        for (var memo : List.of(Map.of("workflowRunId", "wrong", "planDigest", "wrong"),
                Map.of("workflowRunId", "wrong"), Map.<String, String>of())) {
            var run = start(publish(List.of(effect("root", Map.of())), List.of()), UUID.randomUUID().toString());
            var persisted = store.require(run.id());
            var client = temporal.getWorkflowClient();
            var workflow = client.newWorkflowStub(PlanWalkWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId(WorkflowDispatch.workflowId(run.id()))
                            .setTaskQueue("ep07-correction-unpolled").setMemo(new HashMap<>(memo)).build());
            WorkflowClient.start(workflow::execute, run.id(), persisted.planJson(), Map.of(), null);
            try {
                assertThatThrownBy(() -> replayStart(run.id())).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("does not match");
                assertThat(effectCount(run.id())).isZero();
                assertThat(store.steps(run.id())).isEmpty();
            } finally { client.newUntypedWorkflowStub(WorkflowDispatch.workflowId(run.id())).terminate("test complete"); }
        }
    }

}
