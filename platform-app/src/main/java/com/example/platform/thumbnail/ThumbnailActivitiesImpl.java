package com.example.platform.thumbnail;

import com.example.platform.artifact.domain.*;
import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.frameextract.FrameExtractExecutionAdapter;
import com.example.platform.frameextract.FrameExtractResult;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.*;
import io.temporal.spring.boot.ActivityImpl;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Temporal Activity adapter; all FFmpeg mechanics remain in the registered Provider. */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@ActivityImpl(taskQueues = "media-platform-tasks")
public class ThumbnailActivitiesImpl implements ThumbnailActivities {
    private final ThumbnailTaskStore tasks; private final ArtifactQueryService artifacts; private final ArtifactCatalogService catalog;
    private final StoragePlacementQuery storage;
    private final ThumbnailCommitService commitService; private final FrameExtractExecutionAdapter capabilities; private final Path root;
    public ThumbnailActivitiesImpl(ThumbnailTaskStore tasks, ArtifactQueryService artifacts,
            ArtifactCatalogService catalog, StoragePlacementQuery storage,
            ThumbnailCommitService commitService, FrameExtractExecutionAdapter capabilities,
            @Value("${app.storage.local-root:./.data/storage}") String root) {
        this.tasks=tasks; this.artifacts=artifacts; this.catalog=catalog; this.storage=storage;
        this.commitService=commitService; this.capabilities=capabilities;
        this.root=Path.of(root).toAbsolutePath().normalize();
    }
    @Override public String extractAndCommit(String taskId, String tenant, String project) {
        // Worker-role tenant scope (mirrors CoverImageActivitiesImpl): the canonical Storage
        // read/publication path asserts the ambient tenant, which only the API request thread
        // normally establishes. The activity is handed the tenant explicitly, so it establishes the
        // same scope for its own execution and restores the previous value to avoid leaking scope
        // across pooled activity threads.
        String previousTenant=TenantContext.get();
        try {
            TenantContext.set(tenant);
            return extractAndCommitScoped(taskId, tenant, project);
        } finally {
            if (previousTenant==null) TenantContext.clear(); else TenantContext.set(previousTenant);
        }
    }
    private String extractAndCommitScoped(String taskId, String tenant, String project) {
        if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.RUNNING, null, null)) return null;
        var req=tasks.request(tenant,project,taskId).orElseThrow(()->new IllegalArgumentException("thumbnail task not found"));
        // Retired media authority replaced by the owner-published Artifact contract: the
        // project-bound catalog lookup is fail-closed for a cross-project or out-of-tenant id
        // (AUTH-ARTIFACT-BOUNDARY-FIX-002), which is exactly what the previous owner-side read scope
        // plus project-equality filter enforced.
        catalog.findArtifact(tenant, project, req.sourceAssetId())
                .orElseThrow(()->fail(taskId,"SOURCE_MEDIA_UNAVAILABLE", "source media unavailable in scope"));
        Artifact source=artifacts.getArtifact(tenant,new ArtifactId(req.sourceAssetId()))
                .filter(a->a.state()==ArtifactState.AVAILABLE)
                .orElseThrow(()->fail(taskId,"SOURCE_MEDIA_UNAVAILABLE", "source media unavailable in scope"));
        if(source.mediaType()!=ArtifactMediaType.VIDEO) throw fail(taskId,"UNSUPPORTED_MEDIA", "source media is not a video");
        byte[] input=readSourceBytes(taskId,tenant,project,source);
        if (input.length == 0 || input.length > 512L * 1024L * 1024L) throw fail(taskId,"INPUT_TOO_LARGE", "source exceeds thumbnail input limit");
        FrameExtractResult result;
        try { result=capabilities.invoke(req,input,()->Thread.currentThread().isInterrupted()||tasks.isCancelled(tenant,project,taskId)); }
        catch (RuntimeException failure) { tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, "PROVIDER_FAILED"); throw failure; }
        if (!result.succeeded()) { tasks.statusIfActive(taskId, "CANCELLED".equals(result.failureCode())?ThumbnailContracts.Status.CANCELLED:ThumbnailContracts.Status.FAILED, null, result.failureCode()); return null; }
        if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.COMMITTING, null, null)) return null;
        Path output=root.resolve("thumbnail-work").resolve(req.idempotencyKey()).resolve("provider-output").normalize();
        try { return commitService.commit(tenant, project, taskId, req.sourceAssetId(), result.contentType(), result.bytes(), output, root.toString()); }
        catch (Exception failure) { tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, "OUTPUT_COMMIT_FAILED"); throw new IllegalStateException(failure); }
    }
    /** Digest-verified read of the source Artifact bytes through the canonical Storage receipt path. */
    private byte[] readSourceBytes(String taskId,String tenant,String project,Artifact source) {
        ArtifactReplicaBinding binding=artifacts.listReplicas(tenant,source.artifactId()).stream().findFirst()
                .orElseThrow(()->fail(taskId,"SOURCE_STORAGE_UNAVAILABLE", "source object unavailable"));
        byte[] bytes;
        try { bytes=storage.read(new StorageOwnershipScope(tenant,project),binding.storageObjectId(),binding.storageReplicaId()); }
        catch (RuntimeException failure) { throw fail(taskId,"SOURCE_STORAGE_UNAVAILABLE", "source object unavailable"); }
        if (bytes.length!=source.byteLength() || !sha256(bytes).matches(source.contentDigest()))
            throw fail(taskId,"SOURCE_STORAGE_UNAVAILABLE", "source object integrity mismatch");
        return bytes;
    }
    private static ContentDigest sha256(byte[] bytes) {
        try { return ContentDigest.sha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private RuntimeException fail(String taskId, String code, String message) { tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, code); return new IllegalArgumentException(message); }
}
