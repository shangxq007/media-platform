package com.example.platform.scheduler.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.scheduler.domain.JobStatus;
import com.example.platform.scheduler.domain.ScheduledJobDefinition;
import com.example.platform.scheduler.domain.ScheduledJobRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleRegistryServiceTest {

    private ScheduleRegistryService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleRegistryService();
    }

    @Test
    void overviewReturnsExpectedKeys() {
        Map<String, Object> result = service.overview();
        assertNotNull(result);
        assertEquals("scheduler-module", result.get("module"));
        assertEquals("active", result.get("status"));
        assertNotNull(result.get("registeredJobs"));
        assertNotNull(result.get("activeJobs"));
        assertNotNull(result.get("totalRuns"));
        assertNotNull(result.get("pendingRuns"));
        assertNotNull(result.get("failedRuns"));
    }

    @Test
    void overviewReturnsZeroCountsWhenEmpty() {
        Map<String, Object> result = service.overview();
        assertEquals(0, result.get("registeredJobs"));
        assertEquals(0L, result.get("activeJobs"));
        assertEquals(0L, result.get("totalRuns"));
        assertEquals(0L, result.get("pendingRuns"));
        assertEquals(0L, result.get("failedRuns"));
    }

    @Test
    void registerJobWithDefinition() {
        ScheduledJobDefinition def = new ScheduledJobDefinition(
                "job-1", "test-job", "0 */5 * * * ?", true, 3, Instant.now());
        ScheduledJobDefinition result = service.registerJob(def);
        assertEquals(def, result);
        assertEquals(1, service.listJobs().size());
    }

    @Test
    void registerJobWithParams() {
        ScheduledJobDefinition result = service.registerJob("cleanup-job", "0 0 * * * ?", 5);
        assertNotNull(result);
        assertNotNull(result.id());
        assertTrue(result.id().startsWith("job_"));
        assertEquals("cleanup-job", result.name());
        assertEquals("0 0 * * * ?", result.cronExpression());
        assertTrue(result.enabled());
        assertEquals(5, result.maxRetries());
        assertNotNull(result.createdAt());
    }

    @Test
    void listJobsReturnsAllRegisteredJobs() {
        service.registerJob("job-1", "0 */5 * * * ?", 3);
        service.registerJob("job-2", "0 0 * * * ?", 5);
        List<ScheduledJobDefinition> jobs = service.listJobs();
        assertEquals(2, jobs.size());
    }

    @Test
    void findJobReturnsCorrectJob() {
        ScheduledJobDefinition def = service.registerJob("find-test", "0 */10 * * * ?", 2);
        ScheduledJobDefinition found = service.findJob(def.id());
        assertNotNull(found);
        assertEquals("find-test", found.name());
    }

    @Test
    void findJobReturnsNullForUnknownId() {
        assertNull(service.findJob("nonexistent"));
    }

    @Test
    void recordRunStoresRun() {
        ScheduledJobRun run = new ScheduledJobRun(
                "run-1", "job-1", JobStatus.PENDING, Instant.now(), null, 0, null);
        ScheduledJobRun result = service.recordRun(run);
        assertEquals(run, result);
        assertEquals(1, service.listRuns().size());
    }

    @Test
    void startRunCreatesRunningRun() {
        ScheduledJobDefinition def = service.registerJob("start-test", "0 */5 * * * ?", 3);
        ScheduledJobRun run = service.startRun(def.id());
        assertNotNull(run);
        assertEquals(JobStatus.RUNNING, run.status());
        assertEquals(def.id(), run.jobDefinitionId());
        assertNotNull(run.startedAt());
        assertNull(run.finishedAt());
        assertEquals(0, run.retryCount());
    }

    @Test
    void completeRunUpdatesStatusAndFinishedAt() {
        ScheduledJobDefinition def = service.registerJob("complete-test", "0 */5 * * * ?", 3);
        ScheduledJobRun run = service.startRun(def.id());
        ScheduledJobRun completed = service.completeRun(run.id());
        assertNotNull(completed);
        assertEquals(JobStatus.COMPLETED, completed.status());
        assertNotNull(completed.finishedAt());
    }

    @Test
    void completeRunReturnsNullForUnknownId() {
        assertNull(service.completeRun("nonexistent"));
    }

    @Test
    void failRunUpdatesStatusAndErrorMessage() {
        ScheduledJobDefinition def = service.registerJob("fail-test", "0 */5 * * * ?", 3);
        ScheduledJobRun run = service.startRun(def.id());
        ScheduledJobRun failed = service.failRun(run.id(), "something went wrong");
        assertNotNull(failed);
        assertEquals(JobStatus.FAILED, failed.status());
        assertNotNull(failed.finishedAt());
        assertEquals("something went wrong", failed.errorMessage());
    }

    @Test
    void failRunReturnsNullForUnknownId() {
        assertNull(service.failRun("nonexistent", "error"));
    }

    @Test
    void findPendingRunsReturnsOnlyPending() {
        ScheduledJobDefinition def = service.registerJob("pending-test", "0 */5 * * * ?", 3);
        ScheduledJobRun run1 = service.startRun(def.id());
        service.completeRun(run1.id());
        ScheduledJobRun run2 = new ScheduledJobRun(
                "run-pending", def.id(), JobStatus.PENDING, Instant.now(), null, 0, null);
        service.recordRun(run2);
        List<ScheduledJobRun> pending = service.findPendingRuns();
        assertEquals(1, pending.size());
        assertEquals(JobStatus.PENDING, pending.get(0).status());
    }

    @Test
    void findFailedRunsReturnsOnlyFailed() {
        ScheduledJobDefinition def = service.registerJob("failed-test", "0 */5 * * * ?", 3);
        ScheduledJobRun run1 = service.startRun(def.id());
        service.failRun(run1.id(), "error");
        ScheduledJobRun run2 = service.startRun(def.id());
        service.completeRun(run2.id());
        List<ScheduledJobRun> failed = service.findFailedRuns();
        assertEquals(1, failed.size());
        assertEquals(JobStatus.FAILED, failed.get(0).status());
    }

    @Test
    void findRunsByJobReturnsOnlyMatchingRuns() {
        ScheduledJobDefinition def1 = service.registerJob("job-a", "0 */5 * * * ?", 3);
        ScheduledJobDefinition def2 = service.registerJob("job-b", "0 */10 * * * ?", 3);
        service.startRun(def1.id());
        service.startRun(def1.id());
        service.startRun(def2.id());
        List<ScheduledJobRun> runsForDef1 = service.findRunsByJob(def1.id());
        assertEquals(2, runsForDef1.size());
        List<ScheduledJobRun> runsForDef2 = service.findRunsByJob(def2.id());
        assertEquals(1, runsForDef2.size());
    }

    @Test
    void retryFailedRunsRetriesWithinMaxRetries() {
        ScheduledJobDefinition def = service.registerJob("retry-test", "0 */5 * * * ?", 3);
        ScheduledJobRun run = service.startRun(def.id());
        service.failRun(run.id(), "transient error");
        int retried = service.retryFailedRuns(3);
        assertEquals(1, retried);
        List<ScheduledJobRun> pending = service.findPendingRuns();
        assertEquals(1, pending.size());
        assertEquals(1, pending.get(0).retryCount());
    }

    @Test
    void retryFailedRunsMovesToDeadLetterWhenExceedingMaxRetries() {
        ScheduledJobDefinition def = service.registerJob("deadletter-test", "0 */5 * * * ?", 2);
        ScheduledJobRun run = service.startRun(def.id());
        service.failRun(run.id(), "error 1");
        service.retryFailedRuns(2);
        ScheduledJobRun retriedRun = service.findPendingRuns().get(0);
        service.startRun(def.id());
        service.failRun(retriedRun.id(), "error 2");
        service.retryFailedRuns(2);
        ScheduledJobRun retriedRun2 = service.findPendingRuns().get(0);
        service.startRun(def.id());
        service.failRun(retriedRun2.id(), "error 3");
        int retried = service.retryFailedRuns(2);
        assertEquals(0, retried);
        List<ScheduledJobRun> deadLetters = service.listRuns().stream()
                .filter(r -> r.status() == JobStatus.DEAD_LETTER)
                .toList();
        assertEquals(1, deadLetters.size());
    }

    @Test
    void retryFailedRunsSkipsJobsWithNoDefinition() {
        ScheduledJobRun orphanRun = new ScheduledJobRun(
                "run-orphan", "nonexistent-job", JobStatus.FAILED,
                Instant.now(), Instant.now(), 0, "error");
        service.recordRun(orphanRun);
        int retried = service.retryFailedRuns(3);
        assertEquals(0, retried);
    }

    @Test
    void retryFailedRunsRespectsDefinitionMaxRetries() {
        ScheduledJobDefinition def = new ScheduledJobDefinition(
                "job-low-retry", "low-retry-job", "0 */5 * * * ?", true, 1, Instant.now());
        service.registerJob(def);
        ScheduledJobRun run = service.startRun(def.id());
        service.failRun(run.id(), "error");
        service.retryFailedRuns(10);
        ScheduledJobRun retried = service.findPendingRuns().get(0);
        service.startRun(def.id());
        service.failRun(retried.id(), "error again");
        int retried2 = service.retryFailedRuns(10);
        assertEquals(0, retried2);
        List<ScheduledJobRun> deadLetters = service.listRuns().stream()
                .filter(r -> r.status() == JobStatus.DEAD_LETTER)
                .toList();
        assertEquals(1, deadLetters.size());
    }

    @Test
    void registerOutboxRetryPortAndRetryPendingOutboxEvents() {
        OutboxRetryPort port1 = batchSize -> 5;
        OutboxRetryPort port2 = batchSize -> 3;
        service.registerOutboxRetryPort(port1);
        service.registerOutboxRetryPort(port2);
        int total = service.retryPendingOutboxEvents(10);
        assertEquals(8, total);
    }

    @Test
    void retryPendingOutboxEventsReturnsZeroWithNoPorts() {
        int total = service.retryPendingOutboxEvents(10);
        assertEquals(0, total);
    }

    @Test
    void scheduledJobRunIsPending() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.PENDING, Instant.now(), null, 0, null);
        assertTrue(run.isPending());
        assertFalse(run.isFailed());
        assertFalse(run.isDeadLetter());
        assertFalse(run.isTerminal());
    }

    @Test
    void scheduledJobRunIsFailed() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.FAILED, Instant.now(), Instant.now(), 0, "err");
        assertFalse(run.isPending());
        assertTrue(run.isFailed());
        assertFalse(run.isDeadLetter());
        assertFalse(run.isTerminal());
    }

    @Test
    void scheduledJobRunIsDeadLetter() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.DEAD_LETTER, Instant.now(), Instant.now(), 2, "err");
        assertFalse(run.isPending());
        assertFalse(run.isFailed());
        assertTrue(run.isDeadLetter());
        assertTrue(run.isTerminal());
    }

    @Test
    void scheduledJobRunIsTerminalWhenCompleted() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.COMPLETED, Instant.now(), Instant.now(), 0, null);
        assertTrue(run.isTerminal());
        assertFalse(run.isDeadLetter());
    }

    @Test
    void scheduledJobRunWithStatus() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.PENDING, Instant.now(), null, 0, null);
        ScheduledJobRun updated = run.withStatus(JobStatus.RUNNING);
        assertEquals(JobStatus.RUNNING, updated.status());
        assertEquals(run.id(), updated.id());
    }

    @Test
    void scheduledJobRunWithRetryCount() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.PENDING, Instant.now(), null, 0, null);
        ScheduledJobRun updated = run.withRetryCount(3);
        assertEquals(3, updated.retryCount());
    }

    @Test
    void scheduledJobRunWithErrorMessage() {
        ScheduledJobRun run = new ScheduledJobRun(
                "r1", "j1", JobStatus.FAILED, Instant.now(), Instant.now(), 0, null);
        ScheduledJobRun updated = run.withErrorMessage("timeout");
        assertEquals("timeout", updated.errorMessage());
    }

    @Test
    void scheduledJobDefinitionValidation() {
        ScheduledJobDefinition def = new ScheduledJobDefinition(
                "j1", "test", "0 */5 * * * ?", true, 3, Instant.now());
        assertEquals("j1", def.id());
        assertEquals("test", def.name());
        assertEquals("0 */5 * * * ?", def.cronExpression());
        assertTrue(def.enabled());
        assertEquals(3, def.maxRetries());
    }

    @Test
    void scheduledJobDefinitionRejectsNegativeMaxRetries() {
        try {
            new ScheduledJobDefinition("j1", "test", "0 */5 * * * ?", true, -1, Instant.now());
            throw new AssertionError("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxRetries"));
        }
    }

    @Test
    void scheduledJobRunRejectsNegativeRetryCount() {
        try {
            new ScheduledJobRun("r1", "j1", JobStatus.PENDING, Instant.now(), null, -1, null);
            throw new AssertionError("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("retryCount"));
        }
    }

    @Test
    void jobStatusEnumValues() {
        JobStatus[] values = JobStatus.values();
        assertEquals(5, values.length);
        assertEquals(JobStatus.PENDING, JobStatus.valueOf("PENDING"));
        assertEquals(JobStatus.RUNNING, JobStatus.valueOf("RUNNING"));
        assertEquals(JobStatus.COMPLETED, JobStatus.valueOf("COMPLETED"));
        assertEquals(JobStatus.FAILED, JobStatus.valueOf("FAILED"));
        assertEquals(JobStatus.DEAD_LETTER, JobStatus.valueOf("DEAD_LETTER"));
    }
}
