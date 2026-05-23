package com.example.platform.delivery.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.delivery.api.dto.UpdateDeliveryDestinationRequest;
import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.delivery.domain.DeliveryJobStatus;
import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.infrastructure.DeliveryAdapterRegistry;
import com.example.platform.delivery.infrastructure.DeliveryConfigParser;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.secrets.app.CredentialBundleResolver;
import com.example.platform.delivery.spi.DeliveryContext;
import com.example.platform.shared.Ids;
import com.example.platform.shared.events.RenderDeliveryCompletedEvent;
import com.example.platform.shared.events.RenderDeliveryFailedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryJobService implements DeliveryAfterRenderPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryJobService.class);
    private final DSLContext dsl;
    private final DeliveryAdapterRegistry adapterRegistry;
    private final DeliverySourceResolver sourceResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean enabled;
    private final int maxAttempts;
    private final CredentialBundleResolver credentialBundleResolver;
    private final DeliveryDestinationCredentialService destinationCredentialService;

    public DeliveryJobService(DSLContext dsl,
                              DeliveryAdapterRegistry adapterRegistry,
                              DeliverySourceResolver sourceResolver,
                              ApplicationEventPublisher eventPublisher,
                              CredentialBundleResolver credentialBundleResolver,
                              DeliveryDestinationCredentialService destinationCredentialService,
                              @Value("${delivery.enabled:true}") boolean enabled,
                              @Value("${delivery.max-attempts:3}") int maxAttempts) {
        this.dsl = dsl;
        this.adapterRegistry = adapterRegistry;
        this.sourceResolver = sourceResolver;
        this.eventPublisher = eventPublisher;
        this.credentialBundleResolver = credentialBundleResolver;
        this.destinationCredentialService = destinationCredentialService;
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public void onRenderJobCompleted(RenderJobCompletedEvent event) {
        if (!enabled) {
            return;
        }
        Record job = dsl.select(
                        field("tenant_id", String.class),
                        field("project_id", String.class),
                        field("artifact_uri", String.class))
                .from(table("render_job"))
                .where(field("id").eq(event.renderJobId()))
                .fetchOne();
        if (job == null) {
            return;
        }
        String tenantId = job.get(field("tenant_id", String.class));
        String projectId = job.get(field("project_id", String.class));
        String sourceUri = job.get(field("artifact_uri", String.class));
        if (sourceUri == null || sourceUri.isBlank()) {
            sourceUri = event.storageUri();
        }
        List<Record> policies = resolvePolicies(tenantId, projectId);
        for (Record policy : policies) {
            enqueueFromPolicy(tenantId, projectId, event.renderJobId(), sourceUri, policy);
        }
    }

    private List<Record> resolvePolicies(String tenantId, String projectId) {
        var projectPolicies = dsl.select()
                .from(table("delivery_policy"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .and(field("enabled").eq(true))
                .and(field("trigger_mode").eq("AUTO"))
                .fetch();
        if (!projectPolicies.isEmpty()) {
            return projectPolicies;
        }
        return dsl.select()
                .from(table("delivery_policy"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").isNull())
                .and(field("enabled").eq(true))
                .and(field("trigger_mode").eq("AUTO"))
                .fetch();
    }

    private void enqueueFromPolicy(String tenantId, String projectId, String renderJobId,
                                   String sourceUri, Record policy) {
        String destinationId = policy.get(field("destination_id", String.class));
        Record dest = dsl.select()
                .from(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .and(field("tenant_id").eq(tenantId))
                .and(field("enabled").eq(true))
                .fetchOne();
        if (dest == null) {
            return;
        }
        String pathTemplate = policy.get(field("path_template", String.class));
        String filename = "output.mp4";
        String remotePath = DeliveryPathRenderer.render(
                pathTemplate,
                DeliveryPathRenderer.vars(tenantId, projectId, renderJobId, filename));
        String jobId = Ids.newId("dlv");
        dsl.insertInto(table("delivery_job"))
                .columns(field("id"), field("tenant_id"), field("project_id"), field("render_job_id"),
                        field("destination_id"), field("status"), field("source_uri"), field("remote_path"),
                        field("attempt_count"), field("created_at"))
                .values(jobId, tenantId, projectId, renderJobId, destinationId,
                        DeliveryJobStatus.QUEUED.name(), sourceUri, remotePath, 0, OffsetDateTime.now())
                .execute();
        log.info("Queued delivery job {} renderJob={} destination={}", jobId, renderJobId, destinationId);
    }

    public int processQueued(int batchSize) {
        List<Record> queued = dsl.select()
                .from(table("delivery_job"))
                .where(field("status").eq(DeliveryJobStatus.QUEUED.name()))
                .orderBy(field("created_at").asc())
                .limit(batchSize > 0 ? batchSize : 16)
                .fetch();
        int processed = 0;
        for (Record row : queued) {
            if (runJob(row.get(field("id", String.class)))) {
                processed++;
            }
        }
        return processed;
    }

    @Transactional
    public boolean runJob(String deliveryJobId) {
        Record row = dsl.select()
                .from(table("delivery_job"))
                .where(field("id").eq(deliveryJobId))
                .fetchOne();
        if (row == null) {
            return false;
        }
        String status = row.get(field("status", String.class));
        int attempts = row.get(field("attempt_count", Integer.class));
        if (!DeliveryJobStatus.QUEUED.name().equals(status)
                && !(DeliveryJobStatus.FAILED.name().equals(status) && attempts < maxAttempts)) {
            return false;
        }
        dsl.update(table("delivery_job"))
                .set(field("status"), DeliveryJobStatus.RUNNING.name())
                .set(field("attempt_count"), row.get(field("attempt_count", Integer.class)) + 1)
                .where(field("id").eq(deliveryJobId))
                .execute();

        String tenantId = row.get(field("tenant_id", String.class));
        String projectId = row.get(field("project_id", String.class));
        String renderJobId = row.get(field("render_job_id", String.class));
        String sourceUri = row.get(field("source_uri", String.class));
        String remotePath = row.get(field("remote_path", String.class));
        String destinationId = row.get(field("destination_id", String.class));

        Record dest = dsl.select()
                .from(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .fetchOne();
        if (dest == null) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, destinationId, "UNKNOWN", "DESTINATION_NOT_FOUND", "Destination missing");
            return false;
        }
        DeliveryProtocol protocol = DeliveryProtocol.fromString(dest.get(field("protocol", String.class)));
        Optional<DeliveryAdapter> adapter = adapterRegistry.get(protocol);
        if (adapter.isEmpty()) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, destinationId, protocol.name(),
                    "ADAPTER_MISSING", "No adapter for " + protocol);
            return false;
        }

        Optional<DeliverySourceResolver.SourceFile> source = sourceResolver.open(sourceUri);
        if (source.isEmpty()) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, destinationId, protocol.name(),
                    "SOURCE_UNAVAILABLE", "Cannot read " + sourceUri);
            return false;
        }

        try (DeliverySourceResolver.SourceFile file = source.get()) {
            Map<String, Object> config = DeliveryConfigParser.parseConfig(dest.get(field("config_json", String.class)));
            Map<String, String> credentials = resolveDestinationCredentials(dest);
            DeliveryContext ctx = new DeliveryContext(
                    deliveryJobId, tenantId, projectId, renderJobId, sourceUri,
                    file.fileName(), file.contentType(), file.length(), file.stream(),
                    remotePath, protocol.name(), config, credentials);
            DeliveryAdapter.DeliveryResult result = adapter.get().deliver(ctx);
            if (result.success()) {
                dsl.update(table("delivery_job"))
                        .set(field("status"), DeliveryJobStatus.COMPLETED.name())
                        .set(field("remote_uri"), result.remoteUri())
                        .set(field("bytes_transferred"), result.bytesTransferred())
                        .set(field("completed_at"), OffsetDateTime.now())
                        .where(field("id").eq(deliveryJobId))
                        .execute();
                eventPublisher.publishEvent(new RenderDeliveryCompletedEvent(
                        deliveryJobId, renderJobId, projectId, tenantId, destinationId,
                        protocol.name(), result.remoteUri(), Instant.now()));
                return true;
            }
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, destinationId, protocol.name(),
                    "DELIVERY_FAILED", result.error());
            return false;
        } catch (Exception e) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, destinationId, protocol.name(),
                    "DELIVERY_ERROR", e.getMessage());
            return false;
        }
    }

    @Transactional
    public String triggerManual(String tenantId, String projectId, String renderJobId, String destinationId) {
        Record job = dsl.select(field("artifact_uri", String.class), field("tenant_id", String.class))
                .from(table("render_job"))
                .where(field("id").eq(renderJobId))
                .and(field("project_id").eq(projectId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (job == null) {
            throw new IllegalArgumentException("Render job not found");
        }
        Record dest = dsl.select()
                .from(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        String sourceUri = job.get(field("artifact_uri", String.class));
        String pathTemplate = "{tenantId}/{projectId}/{jobId}/output.mp4";
        String remotePath = DeliveryPathRenderer.render(
                pathTemplate, DeliveryPathRenderer.vars(tenantId, projectId, renderJobId, "output.mp4"));
        String dlvId = Ids.newId("dlv");
        dsl.insertInto(table("delivery_job"))
                .columns(field("id"), field("tenant_id"), field("project_id"), field("render_job_id"),
                        field("destination_id"), field("status"), field("source_uri"), field("remote_path"),
                        field("attempt_count"), field("created_at"))
                .values(dlvId, tenantId, projectId, renderJobId, destinationId,
                        DeliveryJobStatus.QUEUED.name(), sourceUri, remotePath, 0, OffsetDateTime.now())
                .execute();
        return dlvId;
    }

    private void markFailed(String deliveryJobId, String tenantId, String projectId, String renderJobId,
                            String destinationId, String protocol, String code, String message) {
        dsl.update(table("delivery_job"))
                .set(field("status"), DeliveryJobStatus.FAILED.name())
                .set(field("error_code"), code)
                .set(field("error_message"), message != null && message.length() > 2000 ? message.substring(0, 2000) : message)
                .set(field("completed_at"), OffsetDateTime.now())
                .where(field("id").eq(deliveryJobId))
                .execute();
        eventPublisher.publishEvent(new RenderDeliveryFailedEvent(
                deliveryJobId, renderJobId, projectId, tenantId, destinationId, protocol, message, Instant.now()));
    }

    @Transactional
    public DeliveryAdapter.ProbeResult probeDestination(String tenantId, String destinationId) {
        Record dest = dsl.select()
                .from(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        DeliveryProtocol protocol = DeliveryProtocol.fromString(dest.get(field("protocol", String.class)));
        DeliveryAdapter adapter = adapterRegistry.get(protocol)
                .orElseThrow(() -> new IllegalArgumentException("No adapter for " + protocol));
        Map<String, Object> config = DeliveryConfigParser.parseConfig(dest.get(field("config_json", String.class)));
        Map<String, String> credentials = resolveDestinationCredentials(dest);
        DeliveryContext ctx = new DeliveryContext(
                "probe", tenantId, null, null, "", "", "application/octet-stream", 0,
                new java.io.ByteArrayInputStream(new byte[0]), "probe.dat", protocol.name(), config, credentials);
        DeliveryAdapter.ProbeResult result = adapter.probe(ctx);
        if (result.ok()) {
            dsl.update(table("delivery_destination"))
                    .set(field("verified_at"), OffsetDateTime.now())
                    .where(field("id").eq(destinationId))
                    .execute();
        }
        return result;
    }

    @Transactional
    public boolean retryDelivery(String tenantId, String projectId, String renderJobId, String deliveryJobId) {
        Record row = dsl.select()
                .from(table("delivery_job"))
                .where(field("id").eq(deliveryJobId))
                .and(field("render_job_id").eq(renderJobId))
                .and(field("project_id").eq(projectId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException("Delivery job not found");
        }
        if (!DeliveryJobStatus.FAILED.name().equals(row.get(field("status", String.class)))) {
            throw new IllegalStateException("Only FAILED deliveries can be retried");
        }
        dsl.update(table("delivery_job"))
                .set(field("status"), DeliveryJobStatus.QUEUED.name())
                .set(field("error_code"), (String) null)
                .set(field("error_message"), (String) null)
                .set(field("completed_at"), (OffsetDateTime) null)
                .where(field("id").eq(deliveryJobId))
                .execute();
        return runJob(deliveryJobId);
    }

    @Override
    @Transactional
    public int finalizeDeliveriesForRenderJob(String renderJobId) {
        Record job = dsl.select(
                        field("tenant_id", String.class),
                        field("project_id", String.class),
                        field("artifact_uri", String.class))
                .from(table("render_job"))
                .where(field("id").eq(renderJobId))
                .fetchOne();
        if (job == null) {
            return 0;
        }
        String tenantId = job.get(field("tenant_id", String.class));
        String projectId = job.get(field("project_id", String.class));
        String artifactUri = job.get(field("artifact_uri", String.class));
        onRenderJobCompleted(new RenderJobCompletedEvent(
                renderJobId, projectId, null, artifactUri, Instant.now()));
        return processQueued(32);
    }

    @Transactional
    public void updateDestination(String tenantId, String destinationId, UpdateDeliveryDestinationRequest request) {
        requireDestination(tenantId, destinationId);
        if (request.name() != null && !request.name().isBlank()) {
            dsl.update(table("delivery_destination"))
                    .set(field("name"), request.name().trim())
                    .where(field("id").eq(destinationId))
                    .execute();
        }
        if (request.enabled() != null) {
            dsl.update(table("delivery_destination"))
                    .set(field("enabled"), request.enabled())
                    .where(field("id").eq(destinationId))
                    .execute();
        }
        if (request.config() != null) {
            dsl.update(table("delivery_destination"))
                    .set(field("config_json"), DeliveryConfigParser.toJson(request.config()))
                    .where(field("id").eq(destinationId))
                    .execute();
        }
    }

    @Transactional
    public void deleteDestination(String tenantId, String destinationId) {
        Record dest = requireDestination(tenantId, destinationId);
        String credentialRef = dest.get(field("credential_ref", String.class));
        int policies = dsl.fetchCount(
                dsl.selectFrom(table("delivery_policy")).where(field("destination_id").eq(destinationId)));
        if (policies > 0) {
            throw new IllegalStateException("Destination is referenced by " + policies + " policies");
        }
        dsl.deleteFrom(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .and(field("tenant_id").eq(tenantId))
                .execute();
        destinationCredentialService.revoke(credentialRef);
    }

    @Transactional
    public void updatePolicyEnabled(String tenantId, String projectId, String policyId, boolean enabled) {
        int updated = dsl.update(table("delivery_policy"))
                .set(field("enabled"), enabled)
                .where(field("id").eq(policyId))
                .and(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("Policy not found");
        }
    }

    @Transactional
    public void deletePolicy(String tenantId, String projectId, String policyId) {
        int deleted = dsl.deleteFrom(table("delivery_policy"))
                .where(field("id").eq(policyId))
                .and(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .execute();
        if (deleted == 0) {
            throw new IllegalArgumentException("Policy not found");
        }
    }

    private Map<String, String> resolveDestinationCredentials(Record dest) {
        return credentialBundleResolver.resolve(
                dest.get(field("credential_ref", String.class)),
                dest.get(field("credential_json", String.class)));
    }

    private Record requireDestination(String tenantId, String destinationId) {
        Record dest = dsl.select()
                .from(table("delivery_destination"))
                .where(field("id").eq(destinationId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        return dest;
    }
}
