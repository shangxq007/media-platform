package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.infrastructure.DeliveryAdapterRegistry;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.api.event.RenderJobCompletedEvent;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DeliveryCompletionOwnerBoundaryTest extends PostgresTestContainerSupport {

    private static final String SCHEMA = isolatedSchemaName();
    private static Connection connection;
    private static DSLContext dsl;
    private DeliveryCompletionListener listener;
    private DeliveryJobService service;
    private boolean uncertainTransport;
    private boolean failedProbe;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        var settings = new Settings()
                .withRenderNameCase(RenderNameCase.LOWER)
                .withRenderMapping(new RenderMapping()
                        .withSchemata(new MappedSchema().withInput("public").withOutput(SCHEMA)));
        dsl = DSL.using(connection, SQLDialect.POSTGRES, settings);
        DeliveryTestSchema.migrate(jdbcUrl(),username(),password(),SCHEMA);
        dsl.execute("set search_path to " + SCHEMA);

    }

    @AfterAll
    static void tearDownDatabase() throws Exception {
        if (dsl != null) {
            dsl.execute("drop schema " + SCHEMA + " cascade");
        }
        if (connection != null) {
            connection.close();
        }
    }

    @BeforeEach
    void setUp() {
        uncertainTransport = false;
        failedProbe = false;
        dsl.execute("truncate table delivery_job, delivery_policy, delivery_destination");
        dsl.execute("""
                insert into delivery_destination
                    (id, tenant_id, name, protocol, config_json, enabled, created_at)
                values ('destination-1', 'tenant-1', 'SFTP destination', 'SFTP', '{}', true, current_timestamp)
                """);
        dsl.execute("""
                insert into delivery_policy
                    (id, tenant_id, project_id, destination_id, path_template, trigger_mode, enabled, created_at)
                values ('policy-1', 'tenant-1', 'project-1', 'destination-1',
                        '{tenantId}/{projectId}/{jobId}/{filename}', 'AUTO', true, current_timestamp)
                """);

        DeliveryAdapter adapter = new DeliveryAdapter() {
            @Override
            public DeliveryProtocol protocol() {
                return DeliveryProtocol.SFTP;
            }

            @Override
            public ProbeResult probe(com.example.platform.delivery.spi.DeliveryContext context) {
                if (failedProbe) return ProbeResult.failure("test probe failed");
                return ProbeResult.success();
            }

            @Override
            public DeliveryResult deliver(com.example.platform.delivery.spi.DeliveryContext context) {
                if (uncertainTransport) throw new IllegalStateException("connection lost after write");
                return DeliveryResult.ok(
                        context.remotePath(),
                        "sftp://delivered/" + context.deliveryJobId(),
                        context.contentLength());
            }
        };
        var sourceResolver = mock(DeliverySourceResolver.class);
        when(sourceResolver.open(any(com.example.platform.artifact.app.ArtifactOutputReference.class))).thenAnswer(invocation -> Optional.of(
                new DeliverySourceResolver.SourceFile("output.mp4", "video/mp4", 4, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}))));
        when(sourceResolver.find(any(com.example.platform.artifact.app.ArtifactScope.class))).thenAnswer(i->{
            var scope=i.getArgument(0,com.example.platform.artifact.app.ArtifactScope.class);
            return scope.equals(new com.example.platform.artifact.app.ArtifactScope("tenant-1","project-1","render-1"))
                ? Optional.of(new com.example.platform.artifact.app.ArtifactOutputReference(scope,new com.example.platform.shared.identity.ArtifactId("artifact-1"))) : Optional.empty();
        });
        var credentialBundlePort = mock(CredentialBundlePort.class);
        when(credentialBundlePort.resolve(any(), any())).thenReturn(Map.of());
        service = new DeliveryJobService(
                dsl,
                new DeliveryAdapterRegistry(List.of(adapter)),
                sourceResolver,
                new DeliveryOutcomeService(dsl,mock(com.example.platform.outbox.app.OutboxEventService.class)),
                credentialBundlePort,
                true,
                3);
        listener = new DeliveryCompletionListener(service);
    }

    @Test
    void uncertainTransportIsPersistedAndCannotBeRetried() {
        listener.onRenderJobCompleted(new RenderJobCompletedEvent(new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope((RenderInitiator.restore(ActorType.USER, "user-1", "tenant-1")).tenantId(),"project-1","render-1"),new com.example.platform.shared.identity.ArtifactId("artifact-1")), Instant.now(), RenderInitiator.restore(ActorType.USER, "user-1", "tenant-1")));
        String id = (String) dsl.fetchValue("select id from delivery_job");
        uncertainTransport = true;
        org.junit.jupiter.api.Assertions.assertFalse(service.runJob(id));
        assertEquals("UNCERTAIN", dsl.fetchValue("select status from delivery_job where id = ?", id));
        com.example.platform.shared.web.TenantContext.set("tenant-1");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> service.retryDelivery("tenant-1", "project-1", "render-1", id));
        } finally {com.example.platform.shared.web.TenantContext.clear();}
        assertEquals(0, service.processQueued(10));
    }

    @Test
    void manualTriggerBindsRenderTenantProjectAndDestination() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.triggerManual("tenant-2", "project-1", "render-1", "destination-1"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.triggerManual("tenant-1", "project-2", "render-1", "destination-1"));
        String id = service.triggerManual("tenant-1", "project-1", "render-1", "destination-1");
        org.junit.jupiter.api.Assertions.assertTrue(service.runJob(id));
        org.junit.jupiter.api.Assertions.assertFalse(service.runJob(id));
        assertEquals("COMPLETED", dsl.fetchValue("select status from delivery_job where id = ?", id));
    }

    @Test
    void failedProbeDoesNotMarkDestinationVerified() {
        failedProbe = true;
        org.junit.jupiter.api.Assertions.assertFalse(service.probeDestination("tenant-1", "destination-1").ok());
        assertNull(dsl.fetchValue("select verified_at from delivery_destination where id = 'destination-1'"));
    }

    @Test
    void completionAndFinalizationUseArtifactFactsWithoutReadingRenderRows() {
        assertEquals(0,((Number)dsl.fetchValue("select count(*) from render_job")).intValue());

        listener.onRenderJobCompleted(new RenderJobCompletedEvent(new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope((RenderInitiator.restore(ActorType.USER, "user-1", "tenant-1")).tenantId(),"project-1","render-1"),new com.example.platform.shared.identity.ArtifactId("artifact-1")), Instant.parse("2026-09-01T00:00:00Z"), RenderInitiator.restore(ActorType.USER, "user-1", "tenant-1")));

        assertEquals(1, dsl.fetchCount(DSL.table("delivery_job"),
                DSL.field("render_job_id").eq("render-1")));
        assertEquals("tenant-1", dsl.fetchValue(
                "select tenant_id from delivery_job where render_job_id = 'render-1'"));
        assertEquals("project-1", dsl.fetchValue(
                "select project_id from delivery_job where render_job_id = 'render-1'"));
        assertEquals("artifact-1", dsl.fetchValue(
                "select artifact_id from delivery_job where render_job_id = 'render-1'"));

        dsl.execute("""
                insert into delivery_job
                    (id, tenant_id, project_id, render_job_id, destination_id, status,
                     artifact_id, remote_path, attempt_count, created_at)
                values ('delivery-unrelated', 'tenant-1', 'project-1', 'render-2', 'destination-1',
                        'QUEUED', 'artifact-2', 'unrelated/output.mp4', 0, current_timestamp)
                """);
        dsl.execute("""
                insert into delivery_job
                    (id, tenant_id, project_id, render_job_id, destination_id, status,
                     artifact_id, remote_path, attempt_count, created_at)
                values ('delivery-failed', 'tenant-1', 'project-1', 'render-1', 'destination-1',
                        'FAILED', 'artifact-1', 'failed/output.mp4', 1, current_timestamp)
                """);

        assertEquals(1, service.finalizeDeliveriesForRenderJob("render-1"));
        assertEquals("COMPLETED", dsl.fetchValue(
                "select status from delivery_job where id <> 'delivery-failed' and render_job_id = 'render-1'"));
        assertEquals("QUEUED", dsl.fetchValue(
                "select status from delivery_job where render_job_id = 'render-2'"));
        assertEquals("FAILED", dsl.fetchValue(
                "select status from delivery_job where id = 'delivery-failed'"));
        assertEquals(1, dsl.fetchValue(
                "select attempt_count from delivery_job where id = 'delivery-failed'"));
        assertNull(dsl.fetchValue(
                "select remote_uri from delivery_job where id = 'delivery-failed'"));
    }
}
