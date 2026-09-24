package com.example.platform.thumbnail;

import com.example.platform.artifact.domain.*;
import com.example.platform.media.api.Asset;
import com.example.platform.media.api.MediaAssets;
import com.example.platform.storage.api.*;
import com.example.platform.storage.domain.BlobStorage;
import io.temporal.spring.boot.ActivityImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Temporal Activity adapter; all FFmpeg mechanics remain in the registered Provider. */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@ActivityImpl(taskQueues = "media-platform-tasks")
public class ThumbnailActivitiesImpl implements ThumbnailActivities {
    private final ThumbnailTaskStore tasks; private final MediaAssets assets; private final BlobStorage storage;
    private final ThumbnailCommitService commitService; private final ThumbnailCapabilityRegistry capabilities; private final Path root;
    public ThumbnailActivitiesImpl(ThumbnailTaskStore tasks, MediaAssets assets, BlobStorage storage,
            ThumbnailCommitService commitService, ThumbnailCapabilityRegistry capabilities,
            @Value("${app.storage.local-root:./.data/storage}") String root) {
        this.tasks=tasks; this.assets=assets; this.storage=storage; this.commitService=commitService; this.capabilities=capabilities;
        this.root=Path.of(root).toAbsolutePath().normalize();
    }
    @Override public String extractAndCommit(String taskId, String tenant, String project) {
        if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.RUNNING, null, null)) return null;
        var req=tasks.request(tenant,project,taskId).orElseThrow(()->new IllegalArgumentException("thumbnail task not found"));
        assets.requireReadScope(tenant,project);
        Asset source=assets.findById(tenant,req.sourceAssetId()).filter(a->project.equals(a.projectId())).orElseThrow(()->fail(taskId,"SOURCE_MEDIA_UNAVAILABLE", "source media unavailable in scope"));
        if(!source.isVideo()) throw fail(taskId,"UNSUPPORTED_MEDIA", "source media is not a video");
        byte[] input=storage.get("uploads",source.storageKey()).orElseThrow(()->fail(taskId,"SOURCE_STORAGE_UNAVAILABLE", "source object unavailable"));
        if (input.length == 0 || input.length > 512L * 1024L * 1024L) throw fail(taskId,"INPUT_TOO_LARGE", "source exceeds thumbnail input limit");
        ThumbnailCapabilityProvider.Result result;
        try { result=capabilities.invoke(req,input,()->Thread.currentThread().isInterrupted()||tasks.isCancelled(tenant,project,taskId)); }
        catch (RuntimeException failure) { tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, "PROVIDER_FAILED"); throw failure; }
        if (!result.succeeded()) { tasks.statusIfActive(taskId, "CANCELLED".equals(result.failureCode())?ThumbnailContracts.Status.CANCELLED:ThumbnailContracts.Status.FAILED, null, result.failureCode()); return null; }
        if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.COMMITTING, null, null)) return null;
        Path output=root.resolve("thumbnail-work").resolve(req.idempotencyKey()).resolve("provider-output").normalize();
        try { return commitService.commit(tenant, project, taskId, result.contentType(), result.bytes(), output, root.toString()); }
        catch (Exception failure) { tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, "OUTPUT_COMMIT_FAILED"); throw new IllegalStateException(failure); }
    }
    private RuntimeException fail(String taskId, String code, String message) { tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, code); return new IllegalArgumentException(message); }
}
