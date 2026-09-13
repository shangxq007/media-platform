package com.example.platform.delivery.app;

import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.delivery.domain.DeliveryJobStatus;
import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.infrastructure.DeliveryAdapterRegistry;
import com.example.platform.delivery.infrastructure.DeliveryConfigParser;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.delivery.spi.DeliveryContext;
import com.example.platform.delivery.api.event.DeliveryCompletedEvent;
import com.example.platform.delivery.api.event.DeliveryFailedEvent;
import com.example.platform.render.api.event.RenderJobCompletedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryDestination.DELIVERY_DESTINATION;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryPolicy.DELIVERY_POLICY;
import com.example.platform.artifact.app.ArtifactOutputReference;
import com.example.platform.artifact.app.ArtifactScope;
import com.example.platform.shared.identity.ArtifactId;


@Service
public class DeliveryJobService implements DeliveryAfterRenderPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryJobService.class);
    private final DSLContext dsl;
    private final DeliveryAdapterRegistry adapterRegistry;
    private final DeliverySourceResolver sourceResolver;
    private final DeliveryOutcomeService outcomes;
    private final boolean enabled;
    private final int maxAttempts;
    private final CredentialBundlePort credentialBundlePort;

    public DeliveryJobService(DSLContext dsl,
                              DeliveryAdapterRegistry adapterRegistry,
                              DeliverySourceResolver sourceResolver,
                              DeliveryOutcomeService outcomes,
                              CredentialBundlePort credentialBundlePort,
                              @Value("${delivery.enabled:true}") boolean enabled,
                              @Value("${delivery.max-attempts:3}") int maxAttempts) {
        this.dsl = dsl;
        this.adapterRegistry = adapterRegistry;
        this.sourceResolver = sourceResolver;
        this.outcomes = outcomes;
        this.credentialBundlePort = credentialBundlePort;
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public void onRenderJobCompleted(RenderJobCompletedEvent event) {
        if (!enabled) {
            return;
        }
        String renderJobId = requireEventText(event.renderJobId(), "renderJobId");
        String tenantId = requireEventText(event.initiator().tenantId(), "initiator.tenantId");
        String projectId = requireEventText(event.projectId(), "projectId");
        ArtifactId artifactId = event.result().artifactId();
        List<Record> policies = resolvePolicies(tenantId, projectId);
        for (Record policy : policies) {
            enqueueFromPolicy(tenantId, projectId, renderJobId, artifactId, policy);
        }
    }

    private static String requireEventText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Render completion " + field + " must not be blank");
        }
        return value;
    }

    private List<Record> resolvePolicies(String tenantId, String projectId) {
        var projectPolicies = dsl.select()
                .from(DELIVERY_POLICY)
                .where(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.eq(projectId))
                .and(DELIVERY_POLICY.ENABLED.eq(true))
                .and(DELIVERY_POLICY.TRIGGER_MODE.eq("AUTO"))
                .fetch();
        if (!projectPolicies.isEmpty()) {
            return projectPolicies;
        }
        return dsl.select()
                .from(DELIVERY_POLICY)
                .where(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.isNull())
                .and(DELIVERY_POLICY.ENABLED.eq(true))
                .and(DELIVERY_POLICY.TRIGGER_MODE.eq("AUTO"))
                .fetch();
    }

    private void enqueueFromPolicy(String tenantId, String projectId, String renderJobId,
                                   ArtifactId artifactId, Record policy) {
        String destinationId = policy.get(DELIVERY_POLICY.DESTINATION_ID);
        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .and(DELIVERY_DESTINATION.ENABLED.eq(true))
                .fetchOne();
        if (dest == null) {
            return;
        }
        String pathTemplate = policy.get(DELIVERY_POLICY.PATH_TEMPLATE);
        String filename = "output.mp4";
        String remotePath = DeliveryPathRenderer.render(
                pathTemplate,
                DeliveryPathRenderer.vars(tenantId, projectId, renderJobId, filename));
        String jobId = "dlv_"+java.util.UUID.nameUUIDFromBytes((tenantId+"\0"+projectId+"\0"+renderJobId+"\0"+artifactId.value()+"\0"+policy.get(DELIVERY_POLICY.ID)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        dsl.insertInto(DELIVERY_JOB)
                .columns(DELIVERY_JOB.ID, DELIVERY_JOB.TENANT_ID, DELIVERY_JOB.PROJECT_ID, DELIVERY_JOB.RENDER_JOB_ID,
                        DELIVERY_JOB.DESTINATION_ID, DELIVERY_JOB.STATUS, DELIVERY_JOB.ARTIFACT_ID, DELIVERY_JOB.REMOTE_PATH,
                        DELIVERY_JOB.ATTEMPT_COUNT, DELIVERY_JOB.CREATED_AT)
                .values(jobId, tenantId, projectId, renderJobId, destinationId,
                        DeliveryJobStatus.QUEUED.name(), artifactId.value(), remotePath, 0, LocalDateTime.now())
                .onConflict(DELIVERY_JOB.ID).doNothing().execute();
        log.info("Queued delivery job {} renderJob={} destination={}", jobId, renderJobId, destinationId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int processQueued(int batchSize) {
        List<Record> queued = dsl.select()
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.STATUS.eq(DeliveryJobStatus.QUEUED.name()))
                .orderBy(DELIVERY_JOB.CREATED_AT.asc())
                .limit(batchSize > 0 ? batchSize : 16)
                .fetch();
        int processed = 0;
        for (Record row : queued) {
            if (runJob(row.get(DELIVERY_JOB.ID))) {
                processed++;
            }
        }
        return processed;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public boolean runJob(String deliveryJobId) {
        Record row = dsl.select()
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .fetchOne();
        if (row == null) {
            return false;
        }
        String previousTenant=com.example.platform.shared.web.TenantContext.get();
        String rowTenant=row.get(DELIVERY_JOB.TENANT_ID);
        if(previousTenant!=null && !previousTenant.equals(rowTenant))throw new IllegalArgumentException("Delivery tenant mismatch");
        try {
            com.example.platform.shared.web.TenantContext.set(rowTenant);
            return runScopedJob(deliveryJobId,row);
        } finally {
            if(previousTenant==null)com.example.platform.shared.web.TenantContext.clear();
            else com.example.platform.shared.web.TenantContext.set(previousTenant);
        }
    }

    private boolean runScopedJob(String deliveryJobId,Record row) {
        String status = row.get(DELIVERY_JOB.STATUS);
        int attempts = row.get(DELIVERY_JOB.ATTEMPT_COUNT);
        if (!DeliveryJobStatus.QUEUED.name().equals(status)
                && !(DeliveryJobStatus.FAILED.name().equals(status) && attempts < maxAttempts)) {
            return false;
        }
        int claimed = dsl.update(DELIVERY_JOB)
                .set(DELIVERY_JOB.STATUS, DeliveryJobStatus.RUNNING.name())
                .set(DELIVERY_JOB.ATTEMPT_COUNT, row.get(DELIVERY_JOB.ATTEMPT_COUNT) + 1)
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .and(DELIVERY_JOB.STATUS.eq(status))
                .and(DELIVERY_JOB.ATTEMPT_COUNT.eq(attempts))
                .execute();

        if (claimed != 1) return false;
        String tenantId = row.get(DELIVERY_JOB.TENANT_ID);
        String projectId = row.get(DELIVERY_JOB.PROJECT_ID);
        String renderJobId = row.get(DELIVERY_JOB.RENDER_JOB_ID);
        ArtifactId artifactId = new ArtifactId(row.get(DELIVERY_JOB.ARTIFACT_ID));
        String remotePath = row.get(DELIVERY_JOB.REMOTE_PATH);
        String destinationId = row.get(DELIVERY_JOB.DESTINATION_ID);

        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (dest == null) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, artifactId, attempts+1, destinationId, "DESTINATION_NOT_FOUND", "Destination missing");
            return false;
        }
        DeliveryProtocol protocol = DeliveryProtocol.fromString(dest.get(DELIVERY_DESTINATION.PROTOCOL));
        Optional<DeliveryAdapter> adapter = adapterRegistry.get(protocol);
        if (adapter.isEmpty()) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, artifactId, attempts+1, destinationId,
                    "ADAPTER_MISSING", "No adapter for " + protocol);
            return false;
        }

        Optional<DeliverySourceResolver.SourceFile> source = sourceResolver.open(new ArtifactOutputReference(new ArtifactScope(tenantId,projectId,renderJobId),artifactId));
        if (source.isEmpty()) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, artifactId, attempts+1, destinationId,
                    "SOURCE_UNAVAILABLE", "Cannot read Artifact " + artifactId.value());
            return false;
        }

        boolean transportInvoked = false;
        try (DeliverySourceResolver.SourceFile file = source.get()) {
            Map<String, Object> config = DeliveryConfigParser.parseConfig(dest.get(DELIVERY_DESTINATION.CONFIG_JSON));
            Map<String, String> credentials = resolveDestinationCredentials(dest);
            DeliveryContext ctx = new DeliveryContext(
                    deliveryJobId, tenantId, projectId, renderJobId, artifactId,
                    file.fileName(), file.contentType(), file.length(), file.stream(),
                    remotePath, protocol.name(), config, credentials);
            transportInvoked = true;
            DeliveryAdapter.DeliveryResult result = adapter.get().deliver(ctx);
            if (result.success()) {
                outcomes.completed(new DeliveryCompletedEvent(deliveryJobId,
                        new ArtifactOutputReference(new ArtifactScope(tenantId,projectId,renderJobId),artifactId),destinationId,
                        attempts+1,protocol,result.remoteUri(),result.bytesTransferred(),Instant.now()));
                return true;
            }
            throw new IllegalStateException("Transport did not confirm delivery");
        } catch (Exception e) {
            if (transportInvoked) {
                // A transport exception or failed completion write cannot establish that nothing was sent.
                // Persist uncertainty and prohibit automatic/manual retry until an operator reconciles it.
                dsl.update(DELIVERY_JOB).set(DELIVERY_JOB.STATUS, DeliveryJobStatus.UNCERTAIN.name())
                        .set(DELIVERY_JOB.ERROR_CODE, "DELIVERY_OUTCOME_UNCERTAIN")
                        .set(DELIVERY_JOB.ERROR_MESSAGE, "Transport or completion failed; reconciliation required")
                        .where(DELIVERY_JOB.ID.eq(deliveryJobId)).and(DELIVERY_JOB.TENANT_ID.eq(tenantId))
                        .and(DELIVERY_JOB.STATUS.eq("RUNNING")).and(DELIVERY_JOB.ATTEMPT_COUNT.eq(attempts+1)).execute();
                return false;
            }
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, artifactId, attempts+1, destinationId,
                    "DELIVERY_ERROR", e.getMessage());
            return false;
        }
    }

    @Transactional
    public String triggerManual(String tenantId, String projectId, String renderJobId, String destinationId) {
        ArtifactId artifactId=sourceResolver.find(new ArtifactScope(tenantId,projectId,renderJobId))
                .orElseThrow(()->new IllegalArgumentException("Accepted Render output not found")).artifactId();
        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        String pathTemplate = "{tenantId}/{projectId}/{jobId}/output.mp4";
        String remotePath = DeliveryPathRenderer.render(
                pathTemplate, DeliveryPathRenderer.vars(tenantId, projectId, renderJobId, "output.mp4"));
        String dlvId = ("dlv_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        dsl.insertInto(DELIVERY_JOB)
                .columns(DELIVERY_JOB.ID, DELIVERY_JOB.TENANT_ID, DELIVERY_JOB.PROJECT_ID, DELIVERY_JOB.RENDER_JOB_ID,
                        DELIVERY_JOB.DESTINATION_ID, DELIVERY_JOB.STATUS, DELIVERY_JOB.ARTIFACT_ID, DELIVERY_JOB.REMOTE_PATH,
                        DELIVERY_JOB.ATTEMPT_COUNT, DELIVERY_JOB.CREATED_AT)
                .values(dlvId, tenantId, projectId, renderJobId, destinationId,
                        DeliveryJobStatus.QUEUED.name(), artifactId.value(), remotePath, 0, LocalDateTime.now())
                .execute();
        return dlvId;
    }

    private void markFailed(String deliveryJobId,String tenantId,String projectId,String renderJobId,
                            ArtifactId artifactId,int attempt,String destinationId,String code,String message) {
        outcomes.failed(new DeliveryFailedEvent(deliveryJobId,
            new ArtifactOutputReference(new ArtifactScope(tenantId,projectId,renderJobId),artifactId),destinationId,attempt,code,message,Instant.now()));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public DeliveryAdapter.ProbeResult probeDestination(String tenantId, String destinationId) {
        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        DeliveryProtocol protocol = DeliveryProtocol.fromString(dest.get(DELIVERY_DESTINATION.PROTOCOL));
        DeliveryAdapter adapter = adapterRegistry.get(protocol)
                .orElseThrow(() -> new IllegalArgumentException("No adapter for " + protocol));
        Map<String, Object> config = DeliveryConfigParser.parseConfig(dest.get(DELIVERY_DESTINATION.CONFIG_JSON));
        Map<String, String> credentials = resolveDestinationCredentials(dest);
        DeliveryContext ctx = new DeliveryContext(
                "probe", tenantId, null, null, null, "", "application/octet-stream", 0,
                new java.io.ByteArrayInputStream(new byte[0]), "probe.dat", protocol.name(), config, credentials);
        DeliveryAdapter.ProbeResult result = adapter.probe(ctx);
        if (result.ok()) {
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.VERIFIED_AT, LocalDateTime.now())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                    .execute();
        }
        return result;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public boolean retryDelivery(String tenantId, String projectId, String renderJobId, String deliveryJobId) {
        Record row = dsl.select()
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .and(DELIVERY_JOB.RENDER_JOB_ID.eq(renderJobId))
                .and(DELIVERY_JOB.PROJECT_ID.eq(projectId))
                .and(DELIVERY_JOB.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (row == null) {
            throw new IllegalArgumentException("Delivery job not found");
        }
        if (!DeliveryJobStatus.FAILED.name().equals(row.get(DELIVERY_JOB.STATUS))) {
            throw new IllegalStateException("Only FAILED deliveries can be retried");
        }
        dsl.update(DELIVERY_JOB)
                .set(DELIVERY_JOB.STATUS, DeliveryJobStatus.QUEUED.name())
                .set(DELIVERY_JOB.ERROR_CODE, (String) null)
                .set(DELIVERY_JOB.ERROR_MESSAGE, (String) null)
                .set(DELIVERY_JOB.COMPLETED_AT, (LocalDateTime) null)
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .execute();
        return runJob(deliveryJobId);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int finalizeDeliveriesForRenderJob(String renderJobId) {
        String requiredRenderJobId = requireEventText(renderJobId, "renderJobId");
        List<String> deliveryJobIds = dsl.select(DELIVERY_JOB.ID)
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.RENDER_JOB_ID.eq(requiredRenderJobId))
                .and(DELIVERY_JOB.STATUS.eq(DeliveryJobStatus.QUEUED.name()))
                .orderBy(DELIVERY_JOB.CREATED_AT.asc())
                .fetch(DELIVERY_JOB.ID);
        int processed = 0;
        for (String deliveryJobId : deliveryJobIds) {
            if (runJob(deliveryJobId)) {
                processed++;
            }
        }
        return processed;
    }

    private Map<String, String> resolveDestinationCredentials(Record dest) {
        return credentialBundlePort.resolve(
                dest.get(DELIVERY_DESTINATION.CREDENTIAL_REF),
                dest.get(DELIVERY_DESTINATION.CREDENTIAL_JSON));
    }

    private Record requireDestination(String tenantId, String destinationId) {
        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        return dest;
    }
}
