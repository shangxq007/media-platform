package com.example.platform.render.infrastructure.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RemoteRenderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteRenderDispatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<String, RemoteJobTracker> jobs = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void registerWorker(String workerId, String address, int maxConcurrentJobs) {
        workers.put(workerId, new WorkerInfo(workerId, address, maxConcurrentJobs, 0, "IDLE", System.currentTimeMillis()));
        log.info("Registered remote worker: {} at {}", workerId, address);
    }

    public void heartbeat(String workerId) {
        WorkerInfo w = workers.get(workerId);
        if (w != null) {
            workers.put(workerId, w.withHeartbeat());
        }
    }

    public void deregisterWorker(String workerId) {
        workers.remove(workerId);
        log.info("Deregistered remote worker: {}", workerId);
    }

    public Optional<WorkerInfo> selectWorker() {
        return workers.values().stream()
                .filter(WorkerInfo::isAvailable)
                .min(Comparator.comparingInt(WorkerInfo::activeJobs));
    }

    public RemoteJobTracker dispatch(String jobId, String profile, String timelineJson, String callbackBaseUrl) {
        WorkerInfo worker = selectWorker()
                .orElseThrow(() -> new IllegalStateException("No available remote workers"));

        RemoteJobTracker tracker = new RemoteJobTracker(jobId, worker.workerId(), "QUEUED", 0, null, null, null, null);
        jobs.put(jobId, tracker);

        workers.put(worker.workerId(), worker.withActiveJobs(worker.activeJobs() + 1));

        String body;
        try {
            body = MAPPER.writeValueAsString(Map.of(
                    "workerId", worker.workerId(),
                    "profile", profile,
                    "timelineJson", timelineJson != null ? timelineJson : "{}"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize job request", e);
        }

        String targetUrl = worker.address() + "/api/v1/remote-worker/workers/" + worker.workerId() + "/jobs";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        jobs.put(jobId, tracker.withStatus("DISPATCHED"));
                        log.info("Dispatched job {} to worker {} at {}", jobId, worker.workerId(), worker.address());
                    } else {
                        jobs.put(jobId, tracker.withStatus("FAILED").withError("DISPATCH-ERR", "HTTP " + resp.statusCode()));
                        workers.put(worker.workerId(), worker.withActiveJobs(Math.max(0, worker.activeJobs() - 1)));
                        log.error("Dispatch failed for job {}: HTTP {}", jobId, resp.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    jobs.put(jobId, tracker.withStatus("FAILED").withError("DISPATCH-ERR", ex.getMessage()));
                    workers.put(worker.workerId(), worker.withActiveJobs(Math.max(0, worker.activeJobs() - 1)));
                    log.error("Dispatch error for job {}: {}", jobId, ex.getMessage());
                    return null;
                });

        return tracker;
    }

    public void handleCallback(String jobId, String status, int progress,
                                String artifactId, String storageUri,
                                String errorCode, String errorMessage) {
        RemoteJobTracker tracker = jobs.get(jobId);
        if (tracker == null) {
            log.warn("Callback for unknown job: {}", jobId);
            return;
        }

        tracker = tracker.withStatus(status).withProgress(progress)
                .withResult(artifactId, storageUri)
                .withError(errorCode, errorMessage);
        jobs.put(jobId, tracker);

        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            WorkerInfo w = workers.get(tracker.workerId());
            if (w != null) {
                workers.put(w.workerId(), w.withActiveJobs(Math.max(0, w.activeJobs() - 1)));
            }
        }

        log.info("Job callback: {} status={} progress={}", jobId, status, progress);
    }

    public Optional<RemoteJobTracker> getJobStatus(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public List<WorkerInfo> listWorkers() {
        return List.copyOf(workers.values());
    }

    @Scheduled(fixedDelay = 30_000)
    public void pruneStaleWorkers() {
        long threshold = System.currentTimeMillis() - 120_000;
        workers.entrySet().removeIf(entry -> {
            if (entry.getValue().lastHeartbeat() < threshold) {
                log.warn("Pruning stale worker: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    public record WorkerInfo(
            String workerId, String address, int maxConcurrentJobs,
            int activeJobs, String status, long lastHeartbeat) {

        public boolean isAvailable() {
            return "IDLE".equals(status) && activeJobs < maxConcurrentJobs;
        }

        public WorkerInfo withHeartbeat() {
            return new WorkerInfo(workerId, address, maxConcurrentJobs, activeJobs, status, System.currentTimeMillis());
        }

        public WorkerInfo withActiveJobs(int count) {
            String newStatus = count > 0 ? "BUSY" : "IDLE";
            return new WorkerInfo(workerId, address, maxConcurrentJobs, count, newStatus, lastHeartbeat);
        }
    }

    public record RemoteJobTracker(
            String jobId, String workerId, String status, int progress,
            String artifactId, String storageUri, String errorCode, String errorMessage) {

        public RemoteJobTracker withStatus(String s) {
            return new RemoteJobTracker(jobId, workerId, s, progress, artifactId, storageUri, errorCode, errorMessage);
        }

        public RemoteJobTracker withProgress(int p) {
            return new RemoteJobTracker(jobId, workerId, status, p, artifactId, storageUri, errorCode, errorMessage);
        }

        public RemoteJobTracker withResult(String aid, String uri) {
            return new RemoteJobTracker(jobId, workerId, status, progress, aid, uri, errorCode, errorMessage);
        }

        public RemoteJobTracker withError(String code, String msg) {
            return new RemoteJobTracker(jobId, workerId, status, progress, artifactId, storageUri, code, msg);
        }
    }
}
