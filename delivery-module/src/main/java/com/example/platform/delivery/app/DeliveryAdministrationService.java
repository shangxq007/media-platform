package com.example.platform.delivery.app;

import static com.example.platform.typedschema.jooq.generated.tables.DeliveryDestination.DELIVERY_DESTINATION;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryJob.DELIVERY_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.DeliveryPolicy.DELIVERY_POLICY;
import static org.jooq.impl.DSL.noCondition;

import com.example.platform.delivery.api.dto.AdminDeliveryJobResponse;
import com.example.platform.delivery.api.dto.CreateDeliveryDestinationRequest;
import com.example.platform.delivery.api.dto.CreateDeliveryPolicyRequest;
import com.example.platform.delivery.api.dto.DeliveryDestinationResponse;
import com.example.platform.delivery.api.dto.DeliveryJobResponse;
import com.example.platform.delivery.api.dto.DeliveryPolicyResponse;
import com.example.platform.delivery.api.dto.UpdateDeliveryDestinationRequest;
import com.example.platform.delivery.infrastructure.DeliveryConfigParser;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.secrets.api.port.CredentialBundlePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application boundary for delivery's administrative transport configuration and runtime operations.
 * These operations are delivery-local: they do not alter canonical render semantics.
 */
@Service
public class DeliveryAdministrationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final DeliveryAccess access;
    private final DeliveryJobService deliveryJobService;
    private final DeliveryDestinationCredentialService destinationCredentialService;
    private final CredentialBundlePort credentialBundlePort;

    public DeliveryAdministrationService(
            DSLContext dsl,
            DeliveryJobService deliveryJobService,
            DeliveryDestinationCredentialService destinationCredentialService,
            CredentialBundlePort credentialBundlePort, DeliveryAccess access) {
        this.access = access;
        this.dsl = dsl;
        this.deliveryJobService = deliveryJobService;
        this.destinationCredentialService = destinationCredentialService;
        this.credentialBundlePort = credentialBundlePort;
    }

    @Transactional
    public DeliveryDestinationResponse createDestination(
            String tenantId, CreateDeliveryDestinationRequest request) {
        access.require(tenantId, null, true);
        String id = ("dst_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        var stored = destinationCredentialService.persist(
                tenantId, id, request.credentialRef(), request.credentials());
        boolean enabled = request.enabled() == null || request.enabled();
        dsl.insertInto(DELIVERY_DESTINATION)
                .columns(DELIVERY_DESTINATION.ID, DELIVERY_DESTINATION.TENANT_ID, DELIVERY_DESTINATION.NAME,
                        DELIVERY_DESTINATION.PROTOCOL, DELIVERY_DESTINATION.CONFIG_JSON,
                        DELIVERY_DESTINATION.CREDENTIAL_REF, DELIVERY_DESTINATION.CREDENTIAL_JSON,
                        DELIVERY_DESTINATION.ENABLED, DELIVERY_DESTINATION.CREATED_AT)
                .values(id, tenantId, request.name(), request.protocol(), toJson(request.config()),
                        stored.credentialRef(), stored.credentialJson(), enabled, LocalDateTime.now())
                .execute();
        return toDestinationResponse(id, tenantId, request.name(), request.protocol(), enabled,
                stored.credentialRef(), stored.credentialJson());
    }

    @Transactional
    public DeliveryDestinationResponse updateDestination(
            String tenantId, String destinationId, UpdateDeliveryDestinationRequest request) {
        access.require(tenantId, null, true);
        Record previous = requireDestination(tenantId, destinationId);
        if (request.name() != null && !request.name().isBlank()) {
            dsl.update(DELIVERY_DESTINATION).set(DELIVERY_DESTINATION.NAME, request.name().trim())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId)).execute();
        }
        if (request.enabled() != null) {
            dsl.update(DELIVERY_DESTINATION).set(DELIVERY_DESTINATION.ENABLED, request.enabled())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId)).execute();
        }
        if (request.config() != null) {
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.CONFIG_JSON, DeliveryConfigParser.toJson(request.config()))
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId)).execute();
        }
        if (request.credentialRef() != null || (request.credentials() != null && !request.credentials().isEmpty())) {
            var stored = destinationCredentialService.persist(
                    tenantId, destinationId, request.credentialRef(), request.credentials());
            dsl.update(DELIVERY_DESTINATION)
                    .set(DELIVERY_DESTINATION.CREDENTIAL_REF, stored.credentialRef())
                    .set(DELIVERY_DESTINATION.CREDENTIAL_JSON, stored.credentialJson())
                    .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                    .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                    .execute();
            if (!java.util.Objects.equals(previous.get(DELIVERY_DESTINATION.CREDENTIAL_REF), stored.credentialRef())) {
                destinationCredentialService.revokeAfterCommit(tenantId, destinationId, previous.get(DELIVERY_DESTINATION.CREDENTIAL_REF));
            }
        }
        return mapDestinationRow(tenantId, requireDestination(tenantId, destinationId));
    }

    @Transactional
    public void deleteDestination(String tenantId, String destinationId) {
        access.require(tenantId, null, true);
        Record destination = requireDestination(tenantId, destinationId);
        int policies = dsl.fetchCount(
                dsl.select().from(DELIVERY_POLICY).where(DELIVERY_POLICY.DESTINATION_ID.eq(destinationId)));
        int jobs = dsl.fetchCount(dsl.select().from(DELIVERY_JOB).where(DELIVERY_JOB.DESTINATION_ID.eq(destinationId)));
        if (policies > 0 || jobs > 0) {
            throw new IllegalStateException("Destination is referenced by " + policies + " policies");
        }
        dsl.deleteFrom(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .execute();
        destinationCredentialService.revokeAfterCommit(tenantId, destinationId, destination.get(DELIVERY_DESTINATION.CREDENTIAL_REF));
    }

    public List<DeliveryDestinationResponse> listDestinations(String tenantId) {
        access.require(tenantId, null, false);
        return dsl.select().from(DELIVERY_DESTINATION)
                .where(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId))
                .fetch(row -> mapDestinationRow(tenantId, row));
    }

    public List<DeliveryDestinationResponse> listAdministrativeDestinations(String tenantId) {
        access.requireAdministrator();
        var condition = noCondition();
        if (tenantId != null && !tenantId.isBlank()) {
            condition = condition.and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId));
        }
        return dsl.select().from(DELIVERY_DESTINATION).where(condition)
                .orderBy(DELIVERY_DESTINATION.CREATED_AT.desc())
                .fetch(row -> mapDestinationRow(row.get(DELIVERY_DESTINATION.TENANT_ID), row));
    }

    public List<DeliveryPolicyResponse> listPolicies(String tenantId, String projectId) {
        access.require(tenantId, projectId, false);
        return dsl.select().from(DELIVERY_POLICY)
                .where(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.eq(projectId))
                .fetch(row -> new DeliveryPolicyResponse(
                        row.get(DELIVERY_POLICY.ID), tenantId, projectId,
                        row.get(DELIVERY_POLICY.DESTINATION_ID), row.get(DELIVERY_POLICY.ARTIFACT_SELECTOR),
                        row.get(DELIVERY_POLICY.PATH_TEMPLATE), row.get(DELIVERY_POLICY.TRIGGER_MODE),
                        Boolean.TRUE.equals(row.get(DELIVERY_POLICY.ENABLED))));
    }

    @Transactional
    public String createPolicy(String tenantId, String projectId, CreateDeliveryPolicyRequest request) {
        access.require(tenantId, projectId, true);
        requireDestination(tenantId, request.destinationId());
        String id = ("dlp_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        dsl.insertInto(DELIVERY_POLICY)
                .columns(DELIVERY_POLICY.ID, DELIVERY_POLICY.TENANT_ID, DELIVERY_POLICY.PROJECT_ID,
                        DELIVERY_POLICY.DESTINATION_ID, DELIVERY_POLICY.ARTIFACT_SELECTOR,
                        DELIVERY_POLICY.PATH_TEMPLATE, DELIVERY_POLICY.TRIGGER_MODE,
                        DELIVERY_POLICY.ENABLED, DELIVERY_POLICY.CREATED_AT)
                .values(id, tenantId, projectId, request.destinationId(), request.artifactSelectorOrDefault(),
                        request.pathTemplateOrDefault(), request.triggerModeOrDefault(), true, LocalDateTime.now())
                .execute();
        return id;
    }

    @Transactional
    public void updatePolicyEnabled(String tenantId, String projectId, String policyId, boolean enabled) {
        access.require(tenantId, projectId, true);
        int updated = dsl.update(DELIVERY_POLICY).set(DELIVERY_POLICY.ENABLED, enabled)
                .where(DELIVERY_POLICY.ID.eq(policyId)).and(DELIVERY_POLICY.TENANT_ID.eq(tenantId))
                .and(DELIVERY_POLICY.PROJECT_ID.eq(projectId)).execute();
        if (updated == 0) {
            throw new IllegalArgumentException("Policy not found");
        }
    }

    @Transactional
    public void deletePolicy(String tenantId, String projectId, String policyId) {
        access.require(tenantId, projectId, true);
        int deleted = dsl.deleteFrom(DELIVERY_POLICY).where(DELIVERY_POLICY.ID.eq(policyId))
                .and(DELIVERY_POLICY.TENANT_ID.eq(tenantId)).and(DELIVERY_POLICY.PROJECT_ID.eq(projectId)).execute();
        if (deleted == 0) {
            throw new IllegalArgumentException("Policy not found");
        }
    }

    public List<DeliveryJobResponse> listDeliveries(String tenantId, String projectId, String renderJobId) {
        access.require(tenantId, projectId, false);
        return dsl.select().from(DELIVERY_JOB).where(DELIVERY_JOB.TENANT_ID.eq(tenantId))
                .and(DELIVERY_JOB.PROJECT_ID.eq(projectId)).and(DELIVERY_JOB.RENDER_JOB_ID.eq(renderJobId))
                .fetch(this::mapJob);
    }

    public boolean retryDelivery(String tenantId, String projectId, String renderJobId, String deliveryJobId) {
        access.require(tenantId, projectId, true);
        if (!deliveryJobService.retryDelivery(tenantId, projectId, renderJobId, deliveryJobId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "Delivery retry failed; inspect persisted status");
        }
        return true;
    }

    public String triggerDelivery(String tenantId, String projectId, String renderJobId, String destinationId) {
        access.require(tenantId, projectId, true);
        requireDestination(tenantId, destinationId);
        String deliveryJobId = deliveryJobService.triggerManual(tenantId, projectId, renderJobId, destinationId);
        if (!deliveryJobService.runJob(deliveryJobId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "Delivery failed; inspect the persisted job status");
        }
        return deliveryJobId;
    }

    public DeliveryAdapter.ProbeResult probeDestination(String tenantId, String destinationId) {
        access.require(tenantId, null, true);
        return deliveryJobService.probeDestination(tenantId, destinationId);
    }

    public List<AdminDeliveryJobResponse> listAdministrativeJobs(
            String tenantId, String status, int page, int size) {
        access.requireAdministrator();
        int limit = Math.min(Math.max(size, 1), 200);
        int offset = Math.max(page, 0) * limit;
        var condition = noCondition();
        if (tenantId != null && !tenantId.isBlank()) {
            condition = condition.and(DELIVERY_JOB.TENANT_ID.eq(tenantId));
        }
        if (status != null && !status.isBlank()) {
            condition = condition.and(DELIVERY_JOB.STATUS.eq(status));
        }
        return dsl.select().from(DELIVERY_JOB).where(condition).orderBy(DELIVERY_JOB.CREATED_AT.desc())
                .limit(limit).offset(offset).fetch(this::mapAdministrativeJob);
    }

    public AdminDeliveryJobResponse retryAdministrativeJob(String deliveryJobId) {
        access.requireAdministrator();
        Record job = requireJob(deliveryJobId);
        if (!deliveryJobService.retryDelivery(job.get(DELIVERY_JOB.TENANT_ID), job.get(DELIVERY_JOB.PROJECT_ID),
                job.get(DELIVERY_JOB.RENDER_JOB_ID), deliveryJobId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "Delivery retry failed; inspect persisted status");
        }
        return mapAdministrativeJob(requireJob(deliveryJobId));
    }

    private Record requireDestination(String tenantId, String destinationId) {
        Record destination = dsl.select().from(DELIVERY_DESTINATION).where(DELIVERY_DESTINATION.ID.eq(destinationId))
                .and(DELIVERY_DESTINATION.TENANT_ID.eq(tenantId)).forUpdate().fetchOne();
        if (destination == null) {
            throw new IllegalArgumentException("Destination not found");
        }
        return destination;
    }

    private Record requireJob(String deliveryJobId) {
        Record job = dsl.select().from(DELIVERY_JOB).where(DELIVERY_JOB.ID.eq(deliveryJobId)).fetchOne();
        if (job == null) {
            throw new IllegalArgumentException("Delivery job not found");
        }
        return job;
    }

    private DeliveryJobResponse mapJob(Record job) {
        return new DeliveryJobResponse(job.get(DELIVERY_JOB.ID), job.get(DELIVERY_JOB.RENDER_JOB_ID),
                job.get(DELIVERY_JOB.DESTINATION_ID), job.get(DELIVERY_JOB.STATUS),
                job.get(DELIVERY_JOB.SOURCE_URI), job.get(DELIVERY_JOB.REMOTE_URI),
                job.get(DELIVERY_JOB.BYTES_TRANSFERRED), job.get(DELIVERY_JOB.ERROR_MESSAGE));
    }

    private AdminDeliveryJobResponse mapAdministrativeJob(Record job) {
        return new AdminDeliveryJobResponse(job.get(DELIVERY_JOB.ID), job.get(DELIVERY_JOB.TENANT_ID),
                job.get(DELIVERY_JOB.PROJECT_ID), job.get(DELIVERY_JOB.RENDER_JOB_ID),
                job.get(DELIVERY_JOB.DESTINATION_ID), job.get(DELIVERY_JOB.STATUS), job.get(DELIVERY_JOB.SOURCE_URI),
                job.get(DELIVERY_JOB.REMOTE_URI), job.get(DELIVERY_JOB.BYTES_TRANSFERRED),
                job.get(DELIVERY_JOB.ATTEMPT_COUNT), job.get(DELIVERY_JOB.ERROR_CODE),
                job.get(DELIVERY_JOB.ERROR_MESSAGE), job.get(DELIVERY_JOB.CREATED_AT), job.get(DELIVERY_JOB.COMPLETED_AT));
    }

    private DeliveryDestinationResponse mapDestinationRow(String tenantId, Record destination) {
        return toDestinationResponse(destination.get(DELIVERY_DESTINATION.ID), tenantId,
                destination.get(DELIVERY_DESTINATION.NAME), destination.get(DELIVERY_DESTINATION.PROTOCOL),
                Boolean.TRUE.equals(destination.get(DELIVERY_DESTINATION.ENABLED)),
                destination.get(DELIVERY_DESTINATION.CREDENTIAL_REF), destination.get(DELIVERY_DESTINATION.CREDENTIAL_JSON));
    }

    private DeliveryDestinationResponse toDestinationResponse(String id, String tenantId, String name,
            String protocol, boolean enabled, String credentialRef, String credentialJson) {
        return new DeliveryDestinationResponse(id, tenantId, name, protocol, enabled, credentialRef,
                credentialBundlePort.hasCredentials(credentialRef, credentialJson));
    }

    private static String toJson(Map<String, ?> map) {
        try {
            return MAPPER.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON field");
        }
    }
}
