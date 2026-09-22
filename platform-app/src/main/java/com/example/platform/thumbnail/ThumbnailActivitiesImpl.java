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
import org.springframework.stereotype.Component;

/** Temporal Activity adapter; all FFmpeg mechanics remain in the registered Provider. */
@Component
@ActivityImpl(taskQueues = "media-platform-tasks")
public class ThumbnailActivitiesImpl implements ThumbnailActivities {
    private final ThumbnailTaskStore tasks; private final MediaAssets assets; private final BlobStorage storage;
    private final StorageOutputPort outputs; private final ArtifactCommitService commits; private final ThumbnailCapabilityRegistry capabilities; private final Path root;
    public ThumbnailActivitiesImpl(ThumbnailTaskStore tasks, MediaAssets assets, BlobStorage storage, StorageOutputPort outputs,
            ArtifactCommitService commits, ThumbnailCapabilityRegistry capabilities,
            @Value("${app.storage.local-root:./.data/storage}") String root) {
        this.tasks=tasks; this.assets=assets; this.storage=storage; this.outputs=outputs; this.commits=commits; this.capabilities=capabilities;
        this.root=Path.of(root).toAbsolutePath().normalize();
    }
    @Override public String extractAndCommit(String taskId, String tenant, String project) {
        if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.RUNNING, null, null)) return null;
        var req=tasks.request(tenant,project,taskId).orElseThrow(()->new IllegalArgumentException("thumbnail task not found"));
        assets.requireReadScope(tenant,project);
        Asset source=assets.findById(tenant,req.sourceAssetId()).filter(a->project.equals(a.projectId())).orElseThrow(()->new IllegalArgumentException("source media unavailable in scope"));
        if(!source.isVideo()) throw new IllegalArgumentException("source media is not a video");
        byte[] input=storage.get("uploads",source.storageKey()).orElseThrow(()->new IllegalStateException("source object unavailable"));
        if (input.length == 0 || input.length > 512L * 1024L * 1024L) throw new IllegalArgumentException("source exceeds thumbnail input limit");
        var result=capabilities.invoke(req,input,()->Thread.currentThread().isInterrupted()||tasks.isCancelled(tenant,project,taskId));
        if (!result.succeeded()) { tasks.statusIfActive(taskId, "CANCELLED".equals(result.failureCode())?ThumbnailContracts.Status.CANCELLED:ThumbnailContracts.Status.FAILED, null, result.failureCode()); return null; }
        if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.COMMITTING, null, null)) return null;
        Path output=root.resolve("thumbnail-work").resolve(req.idempotencyKey()).resolve("provider-output").normalize();
        try {
            Files.createDirectories(output.getParent()); Files.write(output,result.bytes());
            String relative=root.relativize(output).toString().replace(java.io.File.separatorChar,'/');
            var written=outputs.write(new StorageOutputPort.OutputCommand(new StorageOwnershipScope(tenant,project),new IssuanceIdempotencyKey("thumbnail:"+taskId),relative,result.contentType()));
            if (tasks.isCancelled(tenant,project,taskId)) return null;
            var issue=written.issuance(); var aid=new com.example.platform.shared.identity.ArtifactId("art-"+java.util.UUID.nameUUIDFromBytes((tenant+"\0"+project+"\0"+taskId+"\0"+issue.objectId().value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var accepted=commits.commit(new ArtifactCommitRequest(aid,tenant,issue.placement().committedDigest(),issue.placement().committedLength(),ArtifactMediaType.IMAGE,ArtifactKind.THUMBNAIL,Artifact.CURRENT_SCHEMA_VERSION,issue.objectId(),issue.placement().replicaId(),issue.placement().location().providerId(),ReplicaRole.PRIMARY,issue.placement().location().region(),"thumbnail:"+taskId,java.util.List.of(),Instant.now(),Instant.now(),taskId,project));
            if (!tasks.statusIfActive(taskId, ThumbnailContracts.Status.COMPLETED, accepted.artifact().artifactId().value(), null)) return null;
            return accepted.artifact().artifactId().value();
        } catch (Exception failure) {
            tasks.statusIfActive(taskId, ThumbnailContracts.Status.FAILED, null, "OUTPUT_COMMIT_FAILED");
            throw new IllegalStateException(failure);
        } finally {
            try { Files.deleteIfExists(output); Files.deleteIfExists(output.getParent()); } catch (Exception ignored) {}
        }
    }
}
