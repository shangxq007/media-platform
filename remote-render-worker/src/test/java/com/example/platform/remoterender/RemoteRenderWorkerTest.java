package com.example.platform.remoterender;

import com.example.platform.remoterender.app.RemoteRenderService;
import com.example.platform.remoterender.app.WorkerRegistryService;
import com.example.platform.remoterender.domain.RemoteRenderJob;
import com.example.platform.remoterender.domain.WorkerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RemoteRenderWorkerTest {

    private WorkerRegistryService workerRegistry;
    private RemoteRenderService renderService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        workerRegistry = new WorkerRegistryService();
        com.example.platform.render.infrastructure.JavaCVRenderService javacvService =
                new com.example.platform.render.infrastructure.JavaCVRenderService(mock(com.example.platform.render.infrastructure.MediaProbeService.class));
        javacvService.setStorageRoot(tempDir.toString());
        renderService = new RemoteRenderService(javacvService, workerRegistry);
    }

    @Test
    void registerWorkerReturnsWorkerId() {
        String workerId = workerRegistry.registerWorker("http://localhost:8090", 4);
        assertNotNull(workerId);
        assertFalse(workerId.isEmpty());
    }

    @Test
    void getWorkerReturnsCorrectInfo() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 2);
        WorkerRegistryService.WorkerInfo info = workerRegistry.getWorker(workerId);
        assertNotNull(info);
        assertEquals(workerId, info.workerId());
        assertEquals("http://worker1:8090", info.workerAddress());
        assertEquals(WorkerStatus.IDLE, info.status());
        assertEquals(2, info.maxConcurrentJobs());
        assertTrue(info.isAvailable());
    }

    @Test
    void deregisterWorkerRemovesFromRegistry() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        assertNotNull(workerRegistry.getWorker(workerId));
        workerRegistry.deregisterWorker(workerId);
        assertNull(workerRegistry.getWorker(workerId));
    }

    @Test
    void updateWorkerStatusChangesStatus() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        workerRegistry.updateWorkerStatus(workerId, WorkerStatus.BUSY);
        assertEquals(WorkerStatus.BUSY, workerRegistry.getWorker(workerId).status());
        assertFalse(workerRegistry.getWorker(workerId).isAvailable());
    }

    @Test
    void heartbeatUpdatesLastHeartbeat() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        workerRegistry.heartbeat(workerId);
        assertNotNull(workerRegistry.getWorker(workerId).lastHeartbeat());
    }

    @Test
    void getAllWorkersReturnsAllRegistered() {
        workerRegistry.registerWorker("http://worker1:8090", 4);
        workerRegistry.registerWorker("http://worker2:8090", 2);
        assertEquals(2, workerRegistry.getAllWorkers().size());
    }

    @Test
    void submitJobCreatesJobWithSubmittedStatus() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        RemoteRenderJob job = renderService.submitJob(workerId, "default_1080p", "{}");
        assertNotNull(job);
        assertEquals("SUBMITTED", job.status());
        assertEquals(workerId, job.workerId());
        assertEquals("default_1080p", job.profile());
    }

    @Test
    void getJobStatusReturnsJob() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        RemoteRenderJob submitted = renderService.submitJob(workerId, "default_1080p", "{}");
        RemoteRenderJob status = renderService.getJobStatus(submitted.jobId());
        assertNotNull(status);
        assertEquals(submitted.jobId(), status.jobId());
    }

    @Test
    void getWorkerJobsReturnsJobsForWorker() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        renderService.submitJob(workerId, "default_1080p", "{}");
        renderService.submitJob(workerId, "preview_720p", "{}");

        List<RemoteRenderJob> jobs = renderService.getWorkerJobs(workerId);
        assertEquals(2, jobs.size());
    }

    @Test
    void cancelJobSetsStatusToCancelled() {
        String workerId = workerRegistry.registerWorker("http://worker1:8090", 4);
        RemoteRenderJob job = renderService.submitJob(workerId, "default_1080p", "{}");
        RemoteRenderJob cancelled = renderService.cancelJob(job.jobId());
        assertEquals("CANCELLED", cancelled.status());
    }

    @Test
    void cancelNonExistentJobThrowsException() {
        assertThrows(com.example.platform.shared.web.PlatformException.class, () -> {
            renderService.cancelJob("nonexistent-job-id");
        });
    }

    @Test
    void remoteRenderJobRecordCreatesCorrectly() {
        RemoteRenderJob job = RemoteRenderJob.create("worker-1", "default_1080p", "{}");
        assertNotNull(job.jobId());
        assertEquals("worker-1", job.workerId());
        assertEquals("SUBMITTED", job.status());
        assertEquals("default_1080p", job.profile());
        assertNotNull(job.submittedAt());
    }

    @Test
    void remoteRenderJobWithStatusTransitions() {
        RemoteRenderJob job = RemoteRenderJob.create("worker-1", "default_1080p", "{}");
        assertEquals("SUBMITTED", job.status());

        job = job.withStarted();
        assertEquals("RUNNING", job.status());
        assertNotNull(job.startedAt());

        job = job.withCompleted("art-123", "storage://output.mp4");
        assertEquals("COMPLETED", job.status());
        assertEquals("art-123", job.artifactId());
        assertEquals("storage://output.mp4", job.storageUri());
        assertNotNull(job.completedAt());

        // Test failure path
        RemoteRenderJob failed = RemoteRenderJob.create("worker-1", "default_1080p", "{}");
        failed = failed.withFailed("RENDER-500-001", "Render failed");
        assertEquals("FAILED", failed.status());
        assertEquals("RENDER-500-001", failed.errorCode());
        assertEquals("Render failed", failed.errorMessage());
    }

    @Test
    void workerInfoIsAvailableWhenIdle() {
        String workerId = workerRegistry.registerWorker("http://w:8090", 4);
        WorkerRegistryService.WorkerInfo fullInfo = workerRegistry.getWorker(workerId);
        assertTrue(fullInfo.isAvailable());
    }

    @Test
    void workerInfoNotAvailableWhenBusy() {
        String workerId = workerRegistry.registerWorker("http://w:8090", 4);
        workerRegistry.updateWorkerStatus(workerId, WorkerStatus.BUSY);
        assertFalse(workerRegistry.getWorker(workerId).isAvailable());
    }
}
