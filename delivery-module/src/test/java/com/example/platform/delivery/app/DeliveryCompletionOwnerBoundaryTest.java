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
import com.example.platform.shared.events.RenderInitiator;
import com.example.platform.shared.events.RenderJobCompletedEvent;
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

    @BeforeAll
    static void setUpDatabase() throws Exception {
        connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        var settings = new Settings()
                .withRenderNameCase(RenderNameCase.LOWER)
                .withRenderMapping(new RenderMapping()
                        .withSchemata(new MappedSchema().withInput("public").withOutput(SCHEMA)));
        dsl = DSL.using(connection, SQLDialect.POSTGRES, settings);
        dsl.execute("create schema " + SCHEMA);
        dsl.execute("set search_path to " + SCHEMA);
        dsl.execute("""
                create table delivery_destination (
                    id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    user_id varchar(64),
                    name varchar(255) not null,
                    protocol varchar(32) not null,
                    config_json text,
                    credential_json text,
                    enabled boolean default true,
                    verified_at timestamp,
                    created_at timestamp not null,
                    credential_ref varchar(512)
                )
                """);
        dsl.execute("""
                create table delivery_policy (
                    id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    project_id varchar(64),
                    destination_id varchar(64) not null,
                    artifact_selector varchar(32) not null default 'FINAL_ONLY',
                    path_template varchar(512) not null,
                    trigger_mode varchar(16) not null default 'AUTO',
                    enabled boolean default true,
                    created_at timestamp not null
                )
                """);
        dsl.execute("""
                create table delivery_job (
                    id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    project_id varchar(64) not null,
                    render_job_id varchar(64) not null,
                    destination_id varchar(64) not null,
                    status varchar(32) not null,
                    source_uri varchar(1024) not null,
                    remote_path varchar(1024),
                    remote_uri varchar(1024),
                    bytes_transferred bigint,
                    attempt_count int not null default 0,
                    error_code varchar(64),
                    error_message varchar(2048),
                    created_at timestamp not null,
                    completed_at timestamp
                )
                """);
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
                return ProbeResult.success();
            }

            @Override
            public DeliveryResult deliver(com.example.platform.delivery.spi.DeliveryContext context) {
                return DeliveryResult.ok(
                        context.remotePath(),
                        "sftp://delivered/" + context.deliveryJobId(),
                        context.contentLength());
            }
        };
        var sourceResolver = mock(DeliverySourceResolver.class);
        when(sourceResolver.open(anyString())).thenAnswer(invocation -> Optional.of(
                new DeliverySourceResolver.SourceFile(
                        invocation.getArgument(0), "output.mp4", "video/mp4", 4,
                        new ByteArrayInputStream(new byte[] {1, 2, 3, 4}))));
        var credentialBundlePort = mock(CredentialBundlePort.class);
        when(credentialBundlePort.resolve(any(), any())).thenReturn(Map.of());
        service = new DeliveryJobService(
                dsl,
                new DeliveryAdapterRegistry(List.of(adapter)),
                sourceResolver,
                mock(ApplicationEventPublisher.class),
                credentialBundlePort,
                mock(DeliveryDestinationCredentialService.class),
                true,
                3);
        listener = new DeliveryCompletionListener(service);
    }

    @Test
    void completionAndFinalizationUseEventFactsAndDeliveryOwnedRowsWithoutRenderTable() {
        assertNull(dsl.fetchValue("select to_regclass(?)", SCHEMA + ".render_job"));

        listener.onRenderJobCompleted(new RenderJobCompletedEvent(
                "render-1",
                "project-1",
                "artifact-1",
                "s3://render-output/render-1.mp4",
                Instant.parse("2026-09-01T00:00:00Z"),
                RenderInitiator.restore(ActorType.USER, "user-1", "tenant-1")));

        assertEquals(1, dsl.fetchCount(DSL.table("delivery_job"),
                DSL.field("render_job_id").eq("render-1")));
        assertEquals("tenant-1", dsl.fetchValue(
                "select tenant_id from delivery_job where render_job_id = 'render-1'"));
        assertEquals("project-1", dsl.fetchValue(
                "select project_id from delivery_job where render_job_id = 'render-1'"));
        assertEquals("s3://render-output/render-1.mp4", dsl.fetchValue(
                "select source_uri from delivery_job where render_job_id = 'render-1'"));

        dsl.execute("""
                insert into delivery_job
                    (id, tenant_id, project_id, render_job_id, destination_id, status,
                     source_uri, remote_path, attempt_count, created_at)
                values ('delivery-unrelated', 'tenant-1', 'project-1', 'render-2', 'destination-1',
                        'QUEUED', 's3://render-output/render-2.mp4', 'unrelated/output.mp4', 0, current_timestamp)
                """);
        dsl.execute("""
                insert into delivery_job
                    (id, tenant_id, project_id, render_job_id, destination_id, status,
                     source_uri, remote_path, attempt_count, created_at)
                values ('delivery-failed', 'tenant-1', 'project-1', 'render-1', 'destination-1',
                        'FAILED', 's3://render-output/render-1.mp4', 'failed/output.mp4', 1, current_timestamp)
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
