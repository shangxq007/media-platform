package com.example.platform.render.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Service
public class RenderOrchestratorService implements RenderOrchestratorPort {
    private static final Logger log = LoggerFactory.getLogger(RenderOrchestratorService.class);

    private final DSLContext dsl;
    private final RenderQuotaService quotaService;
    private final AiGatewayPort aiGatewayPort;
    private final RenderProviderRouter renderProviderRouter;
    private final NotificationEventPublisher notificationEventPublisher;
    private final StorageCatalogPort storageCatalogPort;
    private final BlobStorage blobStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;

    public RenderOrchestratorService(DSLContext dsl, RenderQuotaService quotaService,
            AiGatewayPort aiGatewayPort, RenderProviderRouter renderProviderRouter,
            NotificationEventPublisher notificationEventPublisher,
            StorageCatalogPort storageCatalogPort,
            BlobStorage blobStorage, ApplicationEventPublisher eventPublisher,
            RenderJobStatusHistoryRepository historyRepository) {
        this.dsl = dsl;
        this.quotaService = quotaService;
        this.aiGatewayPort = aiGatewayPort;
        this.renderProviderRouter = renderProviderRouter;
        this.notificationEventPublisher = notificationEventPublisher;
        this.storageCatalogPort = storageCatalogPort;
        this.blobStorage = blobStorage;
        this.eventPublisher = eventPublisher;
        this.historyRepository = historyRepository;
        this.stateMachine = new RenderJobStateMachine();
    }

    @Override
    @Transactional
    public String submitRenderJob(SubmitRenderJobRequest request) {
        log.info("Submitting render job: tenant={}, project={}, profile={}",
                request.tenantId(), request.projectId(), request.profileOrDefault());

        assertTenantAccess(request.tenantId());
        assertProjectBelongsToTenant(request.tenantId(), request.projectId());

        if (!quotaService.checkQuota(request.tenantId(), "render", 1)) {
            String rejectedJobId = Ids.newId("rj");
            String profile = request.profileOrDefault();
            dsl.insertInto(table("render_job"))
                    .columns(field("id"), field("project_id"), field("tenant_id"),
                            field("timeline_snapshot_id"),
                            field("profile"), field("status"), field("created_at"), field("error_message"))
                    .values(rejectedJobId, request.projectId(), request.tenantId(),
                            "snap_" + rejectedJobId, profile,
                            RenderJobStatus.REJECTED.name(), OffsetDateTime.now(),
                            "Quota exceeded")
                    .execute();
            historyRepository.record(rejectedJobId, null, RenderJobStatus.REJECTED.name(),
                    "Quota exceeded", "QUOTA_EXCEEDED");
            eventPublisher.publishEvent(new RenderJobFailedEvent(
                    rejectedJobId, request.projectId(), "Quota exceeded", Instant.now()));
            throw new IllegalStateException("Quota exceeded for tenant: " + request.tenantId());
        }

        String jobId = Ids.newId("rj");
        String profile = request.profileOrDefault();

        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"),
                        field("profile"), field("status"), field("created_at"))
                .values(jobId, request.projectId(), request.tenantId(),
                        "snap_" + jobId, profile,
                        RenderJobStatus.QUEUED.name(), OffsetDateTime.now())
                .execute();
        historyRepository.record(jobId, null, RenderJobStatus.QUEUED.name(), "Job created", null);

        notificationEventPublisher.publish(
                new RenderJobCreatedEvent(jobId, request.projectId(), "snap_" + jobId, profile, "ffmpeg"));

        updateStatus(jobId, RenderJobStatus.QUEUED, RenderJobStatus.AI_PROCESSING, null);

        String aiScript;
        try {
            assertJobNotInTerminalState(jobId);
            var chatResult = aiGatewayPort.chat("script-generation", request.prompt());
            aiScript = chatResult.content();
        } catch (Exception e) {
            log.error("AI script generation failed for job {}", jobId, e);
            updateStatus(jobId, RenderJobStatus.AI_PROCESSING, RenderJobStatus.FAILED, "AI_GENERATION_FAILED");
            dsl.update(table("render_job"))
                    .set(field("error_message"), "AI generation failed: " + e.getMessage())
                    .where(field("id").eq(jobId))
                    .execute();
            eventPublisher.publishEvent(new RenderJobFailedEvent(
                    jobId, request.projectId(), "AI generation failed", Instant.now()));
            throw new IllegalStateException("AI script generation failed", e);
        }

        dsl.update(table("render_job"))
                .set(field("ai_script"), aiScript)
                .where(field("id").eq(jobId))
                .execute();

        updateStatus(jobId, RenderJobStatus.AI_PROCESSING, RenderJobStatus.RENDERING, null);

        RenderProvider provider = renderProviderRouter.route(profile);
        if (provider == null) {
            updateStatus(jobId, RenderJobStatus.RENDERING, RenderJobStatus.FAILED, "NO_RENDER_PROVIDER");
            dsl.update(table("render_job"))
                    .set(field("error_message"), "No render provider for profile: " + profile)
                    .where(field("id").eq(jobId))
                    .execute();
            eventPublisher.publishEvent(new RenderJobFailedEvent(
                    jobId, request.projectId(), "No render provider", Instant.now()));
            throw new IllegalStateException("No render provider for profile: " + profile);
        }

        RenderProvider.RenderResult renderResult;
        try {
            assertJobNotInTerminalState(jobId);
            renderResult = provider.render(jobId, aiScript, profile);
        } catch (Exception e) {
            log.error("Render failed for job {}", jobId, e);
            updateStatus(jobId, RenderJobStatus.RENDERING, RenderJobStatus.FAILED, "RENDER_FAILED");
            dsl.update(table("render_job"))
                    .set(field("error_message"), "Render failed: " + e.getMessage())
                    .where(field("id").eq(jobId))
                    .execute();
            eventPublisher.publishEvent(new RenderJobFailedEvent(
                    jobId, request.projectId(), "Render failed", Instant.now()));
            throw new IllegalStateException("Render failed", e);
        }

        String artifactId = renderResult.artifactId();
        String storageUri = renderResult.storageUri();

        try {
            byte[] placeholderContent = ("ARTIFACT:" + artifactId).getBytes();
            PutObjectCommand putCmd = new PutObjectCommand("artifacts",
                    artifactId + "/output.mp4", placeholderContent, "video/mp4");
            StorageObjectRef storageRef = blobStorage.put(putCmd);
            storageCatalogPort.registerArtifact(jobId, request.projectId(), storageRef);
        } catch (Exception e) {
            log.error("Storage failed for job {}", jobId, e);
            updateStatus(jobId, RenderJobStatus.RENDERING, RenderJobStatus.FAILED, "STORAGE_FAILED");
            dsl.update(table("render_job"))
                    .set(field("error_message"), "Storage failed: " + e.getMessage())
                    .where(field("id").eq(jobId))
                    .execute();
            eventPublisher.publishEvent(new RenderJobFailedEvent(
                    jobId, request.projectId(), "Storage failed", Instant.now()));
            throw new IllegalStateException("Storage failed", e);
        }

        dsl.update(table("render_job"))
                .set(field("artifact_uri"), storageUri)
                .where(field("id").eq(jobId))
                .execute();

        updateStatus(jobId, RenderJobStatus.RENDERING, RenderJobStatus.COMPLETED, null);

        quotaService.consumeQuota(request.tenantId(), "render", 1);

        notificationEventPublisher.publish(
                new ArtifactCreatedEvent(artifactId, jobId, request.projectId(), storageUri, Instant.now()));

        eventPublisher.publishEvent(new RenderJobCompletedEvent(
                jobId, request.projectId(), artifactId, storageUri, Instant.now()));

        log.info("Render job {} completed successfully with artifact {}", jobId, artifactId);
        return jobId;
    }

    private void updateStatus(String jobId, RenderJobStatus oldStatus, RenderJobStatus newStatus, String errorCode) {
        stateMachine.validateTransition(oldStatus, newStatus);
        dsl.update(table("render_job"))
                .set(field("status"), newStatus.name())
                .where(field("id").eq(jobId))
                .execute();
        historyRepository.record(jobId, oldStatus.name(), newStatus.name(), null, errorCode);
        notificationEventPublisher.publish(
                new RenderJobStatusChangedEvent(jobId, null, oldStatus.name(), newStatus.name(), Instant.now()));
    }

    private void assertJobNotInTerminalState(String jobId) {
        Record record = dsl.select(field("status"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record != null) {
            String statusStr = record.get(field("status"), String.class);
            RenderJobStatus status = RenderJobStatus.valueOf(statusStr);
            if (status == RenderJobStatus.CANCELLED) {
                throw new IllegalStateException("Job has been cancelled: " + jobId);
            }
        }
    }

    public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
        Record jobRecord = dsl.select(field("tenant_id"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (jobRecord == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        String jobTenantId = jobRecord.get(field("tenant_id"), String.class);
        assertTenantAccess(jobTenantId);

        return storageCatalogPort.findArtifactsByJob(jobId).stream()
                .map(a -> new ArtifactInfoResponse(a.artifactId(), a.renderJobId(), a.projectId(),
                        a.storageUri(), a.format(), a.resolution(), a.duration(), a.createdAt()))
                .toList();
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }

    private void assertProjectBelongsToTenant(String tenantId, String projectId) {
        Record projectRecord = dsl.select(field("tenant_id"))
                .from(table("project"))
                .where(field("id").eq(projectId))
                .fetchOne();
        if (projectRecord == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        String projectTenantId = projectRecord.get(field("tenant_id"), String.class);
        if (!tenantId.equals(projectTenantId)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }
    }
}
