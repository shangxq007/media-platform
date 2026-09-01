package com.example.platform.delivery.app;

import com.example.platform.delivery.api.dto.UpdateDeliveryDestinationRequest;
import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.delivery.domain.DeliveryJobStatus;
import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.infrastructure.DeliveryAdapterRegistry;
import com.example.platform.delivery.infrastructure.DeliveryConfigParser;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.example.platform.delivery.spi.DeliveryContext;
import com.example.platform.shared.Ids;
import com.example.platform.shared.events.RenderDeliveryCompletedEvent;
import com.example.platform.shared.events.RenderDeliveryFailedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryDestination.DELIVERY_DESTINATION;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryPolicy.DELIVERY_POLICY;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;


@Service
public class DeliveryJobService implements DeliveryAfterRenderPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryJobService.class);
    private final DSLContext dsl;
    private final DeliveryAdapterRegistry adapterRegistry;
    private final DeliverySourceResolver sourceResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean enabled;
    private final int maxAttempts;
    private final CredentialBundlePort credentialBundlePort;
    private final DeliveryDestinationCredentialService destinationCredentialService;

    public DeliveryJobService(DSLContext dsl,
                              DeliveryAdapterRegistry adapterRegistry,
                              DeliverySourceResolver sourceResolver,
                              ApplicationEventPublisher eventPublisher,
                              CredentialBundlePort credentialBundlePort,
                              DeliveryDestinationCredentialService destinationCredentialService,
                              @Value("${delivery.enabled:true}") boolean enabled,
                              @Value("${delivery.max-attempts:3}") int maxAttempts) {
        this.dsl = dsl;
        this.adapterRegistry = adapterRegistry;
        this.sourceResolver = sourceResolver;
        this.eventPublisher = eventPublisher;
        this.credentialBundlePort = credentialBundlePort;
        this.destinationCredentialService = destinationCredentialService;
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
        String sourceUri = requireEventText(event.storageUri(), "storageUri");
        List<Record> policies = resolvePolicies(tenantId, projectId);
        for (Record policy : policies) {
            enqueueFromPolicy(tenantId, projectId, renderJobId, sourceUri, policy);
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
                                   String sourceUri, Record policy) {
        String destinationId = policy.get(DELIVERY_JOB.DESTINATION_ID);
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
        String jobId = Ids.newId("dlv");
        dsl.insertInto(DELIVERY_JOB)
                .columns(DELIVERY_JOB.ID, DELIVERY_JOB.TENANT_ID, DELIVERY_JOB.PROJECT_ID, DELIVERY_JOB.RENDER_JOB_ID,
                        DELIVERY_JOB.DESTINATION_ID, DELIVERY_JOB.STATUS, DELIVERY_JOB.SOURCE_URI, DELIVERY_JOB.REMOTE_PATH,
                        DELIVERY_JOB.ATTEMPT_COUNT, DELIVERY_JOB.CREATED_AT)
                .values(jobId, tenantId, projectId, renderJobId, destinationId,
                        DeliveryJobStatus.QUEUED.name(), sourceUri, remotePath, 0, LocalDateTime.now())
                .execute();
        log.info("Queued delivery job {} renderJob={} destination={}", jobId, renderJobId, destinationId);
    }

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

    @Transactional
    public boolean runJob(String deliveryJobId) {
        Record row = dsl.select()
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .fetchOne();
        if (row == null) {
            return false;
        }
        String status = row.get(DELIVERY_JOB.STATUS);
        int attempts = row.get(DELIVERY_JOB.ATTEMPT_COUNT);
        if (!DeliveryJobStatus.QUEUED.name().equals(status)
                && !(DeliveryJobStatus.FAILED.name().equals(status) && attempts < maxAttempts)) {
            return false;
        }
        dsl.update(DELIVERY_JOB)
                .set(DELIVERY_JOB.STATUS, DeliveryJobStatus.RUNNING.name())
                .set(DELIVERY_JOB.ATTEMPT_COUNT, row.get(DELIVERY_JOB.ATTEMPT_COUNT) + 1)
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .execute();

        String tenantId = row.get(DELIVERY_JOB.TENANT_ID);
        String projectId = row.get(DELIVERY_JOB.PROJECT_ID);
        String renderJobId = row.get(DELIVERY_JOB.RENDER_JOB_ID);
        String sourceUri = row.get(DELIVERY_JOB.SOURCE_URI);
        String remotePath = row.get(DELIVERY_JOB.REMOTE_PATH);
        String destinationId = row.get(DELIVERY_JOB.DESTINATION_ID);

        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .fetchOne();
        if (dest == null) {
            markFailed(deliveryJobId, tenantId, projectId, renderJobId, destinationId, "UNKNOWN", "DESTINATION_NOT_FOUND", "Destination missing");
            return false;
        }
        DeliveryProtocol protocol = DeliveryProtocol.fromString(dest.get(DELIVERY_DESTINATION.PROTOCOL));
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
            Map<String, Object> config = DeliveryConfigParser.parseConfig(dest.get(DELIVERY_DESTINATION.CONFIG_JSON));
            Map<String, String> credentials = resolveDestinationCredentials(dest);
            DeliveryContext ctx = new DeliveryContext(
                    deliveryJobId, tenantId, projectId, renderJobId, sourceUri,
                    file.fileName(), file.contentType(), file.length(), file.stream(),
                    remotePath, protocol.name(), config, credentials);
            DeliveryAdapter.DeliveryResult result = adapter.get().deliver(ctx);
            if (result.success()) {
                dsl.update(DELIVERY_JOB)
                        .set(DELIVERY_JOB.STATUS, DeliveryJobStatus.COMPLETED.name())
                        .set(DELIVERY_JOB.REMOTE_URI, result.remoteUri())
                        .set(DELIVERY_JOB.BYTES_TRANSFERRED, result.bytesTransferred())
                        .set(DELIVERY_JOB.COMPLETED_AT, LocalDateTime.now())
                        .where(DELIVERY_JOB.ID.eq(deliveryJobId))
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
        Record job = dsl.select(RENDER_JOB.ARTIFACT_URI, DELIVERY_DESTINATION.TENANT_ID)
                .from(RENDER_JOB)
                .where(DELIVERY_DESTINATION.ID.eq(renderJobId))
                .and(RENDER_JOB.PROJECT_ID.eq(projectId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (job == null) {
            throw new IllegalArgumentException("Render job not found");
        }
        Record dest = dsl.select()
                .from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (dest == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        String sourceUri = job.get(RENDER_JOB.ARTIFACT_URI);
        String pathTemplate = "{tenantId}/{projectId}/{jobId}/output.mp4";
        String remotePath = DeliveryPathRenderer.render(
                pathTemplate, DeliveryPathRenderer.vars(tenantId, projectId, renderJobId, "output.mp4"));
        String dlvId = Ids.newId("dlv");
        dsl.insertInto(DELIVERY_JOB)
                .columns(DELIVERY_JOB.ID, DELIVERY_JOB.TENANT_ID, DELIVERY_JOB.PROJECT_ID, DELIVERY_JOB.RENDER_JOB_ID,
                        DELIVERY_JOB.DESTINATION_ID, DELIVERY_JOB.STATUS, DELIVERY_JOB.SOURCE_URI, DELIVERY_JOB.REMOTE_PATH,
                        DELIVERY_JOB.ATTEMPT_COUNT, DELIVERY_JOB.CREATED_AT)
                .values(dlvId, tenantId, projectId, renderJobId, destinationId,
                        DeliveryJobStatus.QUEUED.name(), sourceUri, remotePath, 0, LocalDateTime.now())
                .execute();
        return dlvId;
    }

    private void markFailed(String deliveryJobId, String tenantId, String projectId, String renderJobId,
                            String destinationId, String protocol, String code, String message) {
        dsl.update(DELIVERY_JOB)
                .set(DELIVERY_JOB.STATUS, DeliveryJobStatus.FAILED.name())
                .set(DELIVERY_JOB.ERROR_CODE, code)
                .set(DELIVERY_JOB.ERROR_MESSAGE, message != null && message.length() > 2000 ? message.substring(0, 2000) : message)
                .set(DELIVERY_JOB.COMPLETED_AT, LocalDateTime.now())
                .where(DELIVERY_JOB.ID.eq(deliveryJobId))
                .execute();
        eventPublisher.publishEvent(new RenderDeliveryFailedEvent(
                deliveryJobId, renderJobId, projectId, tenantId, destinationId, protocol, message, Instant.now()));
    }

    @Transactional
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
                "probe", tenantId, null, null, "", "", "application/octet-stream", 0,
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

    @Transactional
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
    @Transactional
    public int finalizeDeliveriesForRenderJob(String renderJobId) {
        String requiredRenderJobId = requireEventText(renderJobId, "renderJobId");
        List<String> deliveryJobIds = dsl.select(DELIVERY_JOB.ID)
                .from(DELIVERY_JOB)
                .where(DELIVERY_JOB.RENDER_JOB_ID.eq(requiredRenderJobId))
                .and(DELIVERY_JOB.STATUS.eq(DeliveryJobStatus.QUEUED.name())
                        .or(DELIVERY_JOB.STATUS.eq(DeliveryJobStatus.FAILED.name())
                                .and(DELIVERY_JOB.ATTEMPT_COUNT.lt(maxAttempts))))
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

    @Transactional
    public void updateDestination(String tenantId, String destinationId, UpdateDeliveryDestinationRequest request) {
        requireDestination(tenantId, destinationId);
        if (request.name() != null && !request.name().isBlank()) {
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.NAME, request.name().trim())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                    .execute();
        }
        if (request.enabled() != null) {
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.ENABLED, request.enabled())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                    .execute();
        }
        if (request.config() != null) {
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.CONFIG_JSON, DeliveryConfigParser.toJson(request.config()))
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                    .execute();
        }
    }

    @Transactional
    public void deleteDestination(String tenantId, String destinationId) {
        Record dest = requireDestination(tenantId, destinationId);
        String credentialRef = dest.get(DELIVERY_DESTINATION.CREDENTIAL_REF);
        int policies = dsl.fetchCount(
                dsl.selectFrom(DELIVERY_POLICY).where(DELIVERY_POLICY.DESTINATION_ID.eq(destinationId)));
        if (policies > 0) {
            throw new IllegalStateException("Destination is referenced by " + policies + " policies");
        }
        dsl.deleteFrom(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .execute();
        destinationCredentialService.revoke(credentialRef);
    }

    @Transactional
    public void updatePolicyEnabled(String tenantId, String projectId, String policyId, boolean enabled) {
        int updated = dsl.update(DELIVERY_POLICY)
                .set(DELIVERY_POLICY.ENABLED, enabled)
                .where(DELIVERY_POLICY.ID.eq(policyId))
                .and(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.eq(projectId))
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("Policy not found");
        }
    }

    @Transactional
    public void deletePolicy(String tenantId, String projectId, String policyId) {
        int deleted = dsl.deleteFrom(DELIVERY_POLICY)
                .where(DELIVERY_POLICY.ID.eq(policyId))
                .and(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.eq(projectId))
                .execute();
        if (deleted == 0) {
            throw new IllegalArgumentException("Policy not found");
        }
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
