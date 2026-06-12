package com.example.platform.render.infrastructure.farm;

import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.Ids;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages render job leases: claim, renew, release, fail, expire.
 *
 * <p>A lease is a time-limited grant for a worker to execute a render job.
 * Only one active lease per job is allowed. Workers must renew their lease
 * before it expires, or the lease will be reclaimed by the stale compensation service.
 */
@Service
public class RenderJobLeaseService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobLeaseService.class);

    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** Providers that can never be claimed regardless of configuration. */
    private static final Set<String> INELIGIBLE_PROVIDER_STATUSES = Set.of(
            ProviderStatus.STUB.name(),
            ProviderStatus.SKELETON.name(),
            ProviderStatus.DEPRECATED.name(),
            ProviderStatus.MOCK.name()
    );

    private final RenderJobLeaseRepository leaseRepository;
    private final RenderJobRepository jobRepository;
    private final RenderWorkerRegistryService workerRegistry;

    public RenderJobLeaseService(
            RenderJobLeaseRepository leaseRepository,
            RenderJobRepository jobRepository,
            RenderWorkerRegistryService workerRegistry) {
        this.leaseRepository = leaseRepository;
        this.jobRepository = jobRepository;
        this.workerRegistry = workerRegistry;
    }

    /**
     * Claim the next available job for a worker, with provider eligibility filtering.
     *
     * @param workerId the worker claiming the job
     * @param workerProviderIds the providers the worker supports
     * @param allowPoc whether to allow POC providers
     * @param mode the execution mode (PRODUCTION, EXPERIMENT, MANUAL)
     * @return the claim result with job details, or failure
     */
    @Transactional
    public RenderFarmClaimResult claimNextJob(String workerId, List<String> workerProviderIds,
            boolean allowPoc, String mode) {
        // Verify worker exists and can accept jobs
        Optional<RenderWorkerRecord> workerOpt = workerRegistry.findAvailableWorkers().stream()
                .filter(w -> w.workerId().equals(workerId))
                .findFirst();
        if (workerOpt.isEmpty()) {
            return RenderFarmClaimResult.noWorker("Worker not available: " + workerId);
        }

        // Find eligible QUEUED jobs
        Optional<String> queuedJobId = jobRepository.findNextQueuedJobId();
        if (queuedJobId.isEmpty()) {
            return RenderFarmClaimResult.noJob("No queued jobs available");
        }

        String jobId = queuedJobId.get();

        // Check if job already has an active lease (double-claim prevention)
        Optional<RenderJobLeaseRecord> existingLease = leaseRepository.findActiveLeaseByJobId(jobId);
        if (existingLease.isPresent()) {
            return RenderFarmClaimResult.noJob("Job already has active lease: " + jobId);
        }

        // Get job details
        Record jobRecord = jobRepository.requireJobRecord(jobId);
        String tenantId = jobRecord.get("tenant_id", String.class);
        String profile = jobRecord.get("profile", String.class);
        String aiScript = jobRecord.get("ai_script", String.class);

        // Provider eligibility filtering
        String eligibleProvider = findEligibleProvider(workerProviderIds, profile, allowPoc, mode);
        if (eligibleProvider == null) {
            return RenderFarmClaimResult.providerIneligible(
                    "No eligible provider found for profile: " + profile);
        }

        // Determine attempt number
        int previousAttempts = leaseRepository.getMaxAttemptForJob(jobId);
        int attempt = previousAttempts + 1;

        Instant now = Instant.now();
        Instant leaseUntil = now.plus(DEFAULT_LEASE_DURATION);

        // Create lease
        RenderJobLeaseRecord lease = new RenderJobLeaseRecord(
                Ids.newId("lease"),
                Ids.newId("lease"),
                jobId,
                tenantId,
                workerId,
                eligibleProvider,
                RenderJobLeaseStatus.CLAIMED,
                1,
                now,
                leaseUntil,
                null,
                null,
                attempt,
                DEFAULT_MAX_ATTEMPTS,
                null, // heartbeat token TODO
                null,
                null,
                "db-lease-scheduler",
                now,
                now
        );

        leaseRepository.create(lease);

        // Update job status to RENDERING
        jobRepository.updateStatus(jobId, RenderJobStatus.RENDERING.name());

        // Increment worker active jobs
        workerRegistry.incrementActiveJobs(workerId);

        log.info("Job claimed: jobId={}, workerId={}, providerId={}, attempt={}, leaseUntil={}",
                jobId, workerId, eligibleProvider, attempt, leaseUntil);

        return RenderFarmClaimResult.success(
                lease.leaseId(), jobId, tenantId, eligibleProvider,
                attempt, DEFAULT_MAX_ATTEMPTS, leaseUntil, profile, aiScript);
    }

    /**
     * Renew a lease. Extends the lease window.
     *
     * @return true if renewed successfully
     */
    @Transactional
    public boolean renewLease(String leaseId, String workerId) {
        Optional<RenderJobLeaseRecord> leaseOpt = leaseRepository.findByLeaseId(leaseId);
        if (leaseOpt.isEmpty()) {
            log.warn("Renew failed: lease not found: {}", leaseId);
            return false;
        }

        RenderJobLeaseRecord lease = leaseOpt.get();

        if (!lease.workerId().equals(workerId)) {
            log.warn("Renew failed: lease {} owned by {}, not {}", leaseId, lease.workerId(), workerId);
            return false;
        }

        Instant newLeaseUntil = Instant.now().plus(DEFAULT_LEASE_DURATION);
        boolean renewed = leaseRepository.renew(leaseId, workerId, lease.leaseVersion(), newLeaseUntil, Instant.now());

        if (renewed) {
            log.info("Lease renewed: leaseId={}, newLeaseUntil={}", leaseId, newLeaseUntil);
        } else {
            log.warn("Renew failed: version conflict or lease not active: {}", leaseId);
        }
        return renewed;
    }

    /**
     * Complete a lease (job completed successfully with artifact).
     *
     * @return the release result
     */
    @Transactional
    public LeaseReleaseResult completeLease(String leaseId, String workerId, String artifactUri,
            String checksum, Long durationMs) {
        Optional<RenderJobLeaseRecord> leaseOpt = leaseRepository.findByLeaseId(leaseId);
        if (leaseOpt.isEmpty()) {
            return LeaseReleaseResult.failure(leaseId, "Lease not found");
        }

        RenderJobLeaseRecord lease = leaseOpt.get();

        if (!lease.workerId().equals(workerId)) {
            return LeaseReleaseResult.failure(leaseId, "Lease owned by different worker");
        }

        Instant now = Instant.now();
        boolean released = leaseRepository.release(leaseId, workerId, lease.leaseVersion(), now);

        if (released) {
            // Update job: COMPLETED + artifact URI
            jobRepository.updateStatus(lease.jobId(), RenderJobStatus.COMPLETED.name());
            if (artifactUri != null && !artifactUri.isBlank()) {
                jobRepository.updateArtifactUri(lease.jobId(), artifactUri);
            }
            // Decrement worker active jobs
            workerRegistry.decrementActiveJobs(workerId);

            log.info("Lease completed: leaseId={}, jobId={}, workerId={}, artifactUri={}",
                    leaseId, lease.jobId(), workerId, artifactUri);
            return LeaseReleaseResult.success(leaseId, lease.jobId());
        }

        return LeaseReleaseResult.failure(leaseId, "Version conflict or lease not active");
    }

    /**
     * Fail a lease (job execution failed).
     *
     * @return true if marked failed
     */
    @Transactional
    public boolean failLease(String leaseId, String workerId, String failureReason,
            String errorCode, boolean retryable) {
        Optional<RenderJobLeaseRecord> leaseOpt = leaseRepository.findByLeaseId(leaseId);
        if (leaseOpt.isEmpty()) {
            log.warn("Fail lease: not found: {}", leaseId);
            return false;
        }

        RenderJobLeaseRecord lease = leaseOpt.get();

        if (!lease.workerId().equals(workerId)) {
            log.warn("Fail lease: lease {} owned by {}, not {}", leaseId, lease.workerId(), workerId);
            return false;
        }

        Instant now = Instant.now();
        boolean failed = leaseRepository.fail(leaseId, workerId, lease.leaseVersion(),
                failureReason, errorCode, now);

        if (failed) {
            workerRegistry.decrementActiveJobs(workerId);

            // Determine if job should be requeued or marked failed
            boolean canRetry = retryable && lease.attempt() < lease.maxAttempts();
            if (canRetry) {
                jobRepository.updateStatus(lease.jobId(), RenderJobStatus.QUEUED.name());
                log.info("Lease failed, job requeued: leaseId={}, jobId={}, attempt={}/{}",
                        leaseId, lease.jobId(), lease.attempt(), lease.maxAttempts());
            } else {
                jobRepository.updateStatus(lease.jobId(), RenderJobStatus.FAILED.name());
                jobRepository.updateErrorMessage(lease.jobId(), failureReason);
                log.warn("Lease failed, job marked FAILED: leaseId={}, jobId={}, attempt={}/{}, retryable={}",
                        leaseId, lease.jobId(), lease.attempt(), lease.maxAttempts(), retryable);
            }
        }

        return failed;
    }

    /**
     * Expire stale leases. Called by the compensation scheduler.
     *
     * @return number of expired leases
     */
    @Transactional
    public int expireStaleLeases() {
        Instant now = Instant.now();
        List<RenderJobLeaseRecord> expired = leaseRepository.expireStaleLeases(now);

        for (RenderJobLeaseRecord lease : expired) {
            workerRegistry.decrementActiveJobs(lease.workerId());

            if (lease.attempt() < lease.maxAttempts()) {
                jobRepository.updateStatus(lease.jobId(), RenderJobStatus.QUEUED.name());
                log.info("Expired lease, job requeued: leaseId={}, jobId={}, attempt={}/{}",
                        lease.leaseId(), lease.jobId(), lease.attempt(), lease.maxAttempts());
            } else {
                jobRepository.updateStatus(lease.jobId(), RenderJobStatus.FAILED.name());
                jobRepository.updateErrorMessage(lease.jobId(), "Lease expired after max attempts");
                log.warn("Expired lease, max attempts exhausted: leaseId={}, jobId={}",
                        lease.leaseId(), lease.jobId());
            }
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} stale leases", expired.size());
        }
        return expired.size();
    }

    /**
     * Find an eligible provider from the worker's provider list.
     *
     * <p>Filters by:
     * <ul>
     *   <li>Provider must be in the worker's providerIds</li>
     *   <li>Provider status must not be STUB/SKELETON/DEPRECATED/MOCK</li>
     *   <li>POC providers only allowed if allowPoc=true or mode=EXPERIMENT/MANUAL</li>
     * </ul>
     *
     * @return the eligible provider ID, or null if none found
     */
    private String findEligibleProvider(List<String> workerProviderIds, String profile,
            boolean allowPoc, String mode) {
        if (workerProviderIds == null || workerProviderIds.isEmpty()) {
            return null;
        }

        for (String providerId : workerProviderIds) {
            if (isProviderEligible(providerId, allowPoc, mode)) {
                return providerId;
            }
        }
        return null;
    }

    /**
     * Check if a provider is eligible for dispatch.
     *
     * <p>This is a simplified eligibility check. For full ProviderEligibility logic,
     * use {@link com.example.platform.render.infrastructure.ProviderEligibility}.
     */
    private boolean isProviderEligible(String providerId, boolean allowPoc, String mode) {
        // Map well-known provider IDs to their status
        ProviderStatus status = resolveProviderStatus(providerId);

        // Never dispatch STUB/SKELETON/DEPRECATED/MOCK
        if (INELIGIBLE_PROVIDER_STATUSES.contains(status.name())) {
            return false;
        }

        // PRODUCTION: always eligible
        if (status == ProviderStatus.PRODUCTION) {
            return true;
        }

        // POC: needs explicit allow
        if (status == ProviderStatus.POC) {
            return allowPoc || "EXPERIMENT".equals(mode) || "MANUAL".equals(mode);
        }

        // OPTIONAL: needs explicit enable (treat like POC for now)
        if (status == ProviderStatus.OPTIONAL) {
            return allowPoc || "EXPERIMENT".equals(mode) || "MANUAL".equals(mode);
        }

        // HOLD: only experiment/manual
        if (status == ProviderStatus.HOLD) {
            return "EXPERIMENT".equals(mode) || "MANUAL".equals(mode);
        }

        // SPIKE: only manual
        if (status == ProviderStatus.SPIKE) {
            return "MANUAL".equals(mode);
        }

        return false;
    }

    /**
     * Resolve the provider status for a given provider ID.
     * This maps well-known provider IDs to their status.
     */
    private ProviderStatus resolveProviderStatus(String providerId) {
        return switch (providerId.toLowerCase()) {
            case "ffmpeg", "remote-ffmpeg", "remote" -> ProviderStatus.PRODUCTION;
            case "mlt" -> ProviderStatus.POC;
            case "gpac" -> ProviderStatus.POC;
            case "libass" -> ProviderStatus.POC;
            case "skia" -> ProviderStatus.POC;
            case "bento4" -> ProviderStatus.POC;
            case "shaka" -> ProviderStatus.POC;
            case "gstreamer" -> ProviderStatus.HOLD;
            case "blender" -> ProviderStatus.STUB;
            case "remotion" -> ProviderStatus.STUB;
            case "shotstack" -> ProviderStatus.SKELETON;
            case "natron" -> ProviderStatus.SKELETON;
            case "vapoursynth" -> ProviderStatus.SKELETON;
            case "javacv" -> ProviderStatus.DEPRECATED;
            case "ofx" -> ProviderStatus.DEPRECATED;
            case "mock" -> ProviderStatus.MOCK;
            default -> ProviderStatus.SKELETON; // Unknown providers treated as skeleton
        };
    }
}
