package com.example.platform.workflow;

import static org.assertj.core.api.Assertions.*;

import com.example.platform.outbox.app.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workflow.definition.app.UserWorkflowDefinitionService;
import com.example.platform.workflow.definition.domain.*;
import com.example.platform.workflow.plan.*;
import com.example.platform.workflow.run.*;
import com.example.platform.workflow.temporal.*;

import io.temporal.client.*;
import io.temporal.worker.WorkerFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Uses the actual temporal profile and auto-discovered production workers, with no replacement
 * client/activity beans.
 */
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles({"test", "preview", "temporal"})
@TestPropertySource(
        properties = {
            "spring.temporal.connection.target=${EP07_TEMPORAL_TARGET:127.0.0.1:17233}",
            "spring.temporal.namespace=default",
            "app.temporal.namespace=default",
            "app.temporal.fail-on-missing-worker=true",
            "app.outbox.dispatcher-enabled=false",
            "storage.s3.enabled=false"
        })
class WorkflowProductionAssemblyTest extends PostgresTestContainerSupport {
    @Autowired WorkerFactory workers;
    @Autowired WorkflowClient client;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkflowRunStore store;
    @Autowired UserWorkflowDefinitionService definitions;
    @Autowired OutboxEventService outbox;
    @Autowired OutboxEventRouter router;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;

    @Test
    void productionProfileDiscoversAndExecutesItsPersistedPlan() throws Exception {
        assertThat(workers.getWorker("workflow-process")).isNotNull();
        assertThat(workers.isStarted()).isTrue();
        String tenant = "assembly-" + UUID.randomUUID(),
                workspace = "workspace-" + UUID.randomUUID(),
                project = "project-" + UUID.randomUUID();
        String runId = UUID.randomUUID().toString();
        String[] eventId = new String[1];
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        tx -> {
                            jdbc.update(
                                    "insert into tenant(id,name,status,created_at) values"
                                        + " (?,?,'ACTIVE',now())",
                                    tenant,
                                    "assembly");
                            jdbc.update(
                                    "insert into workspace(id,tenant_id,name,created_at,updated_at)"
                                        + " values (?,?,?,now(),now())",
                                    workspace,
                                    tenant,
                                    "assembly");
                            jdbc.update(
                                    "insert into project(id,tenant_id,workspace_id,name,created_at)"
                                        + " values (?,?,?,?,now())",
                                    project,
                                    tenant,
                                    workspace,
                                    "assembly");
                            var node =
                                    new WorkflowPlan.Node(
                                            "wait",
                                            WorkflowPlan.Kind.WAIT,
                                            null,
                                            new WorkflowPlan.Wait(WorkflowPlan.WaitKind.TIMER, 1),
                                            0,
                                            0,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null);
                            var declaration =
                                    new UserWorkflowDefinitionNode(
                                            "wait",
                                            WorkflowNodeType.WAIT,
                                            "wait",
                                            "workflow.node.v2",
                                            new UserWorkflowDefinitionNode.VersionedJsonDocument(
                                                    2, RunJson.write(node)),
                                            List.of(),
                                            List.of(),
                                            UserWorkflowDefinitionNode.ErrorPolicy.FAIL);
                            var definition =
                                    definitions.create(
                                            tenant,
                                            project,
                                            "assembly",
                                            null,
                                            List.of(declaration),
                                            List.of(),
                                            List.of(),
                                            UserWorkflowTriggerBinding.manual(),
                                            2,
                                            "system:assembly");
                            assertThat(
                                            definitions
                                                    .validate(
                                                            tenant,
                                                            definition.definitionId(),
                                                            definition.version(),
                                                            "system:assembly")
                                                    .valid())
                                    .isTrue();
                            definition =
                                    definitions.publish(
                                            tenant,
                                            definition.definitionId(),
                                            definition.version(),
                                            2,
                                            "system:assembly");
                            var plan = new WorkflowPlanCompiler().compile(definition);
                            var codec = new WorkflowPlanCodec();
                            // Trusted accepted-run fixture isolates production worker discovery.
                            // Authenticated admission has separate full HTTP coverage.
                            store.insert(
                                    new WorkflowRunStore.Run(
                                            runId,
                                            tenant,
                                            workspace,
                                            project,
                                            runId,
                                            "fixture",
                                            RunJson.write(
                                                    CanonicalActor.system(
                                                            "system:assembly", tenant)),
                                            definition.definitionId().value(),
                                            1,
                                            codec.digest(plan),
                                            codec.encode(plan),
                                            "{}",
                                            "{}",
                                            "ACCEPTED",
                                            false,
                                            null));
                            eventId[0] =
                                    outbox.append(
                                            RunEvents.START.append(
                                                    tenant,
                                                    new RunEvents.Start(
                                                            tenant, runId, codec.digest(plan)),
                                                    "workflow-start:" + runId));
                        });
        try (var dispatcher = new OutboxEventDispatcher(outbox, context, router, 3, metrics)) {
            assertThat(dispatcher.processOnce(eventId[0])).isTrue();
        }
        client.newUntypedWorkflowStub(WorkflowDispatch.workflowId(runId))
                .getResult(30, TimeUnit.SECONDS, Map.class);
        assertThat(store.require(runId).status()).isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from workflow_run_step where run_id=? and"
                                    + " status='COMPLETED'",
                                Integer.class,
                                runId))
                .isEqualTo(1);
    }
}
