package com.example.platform.thumbnail;

import com.example.platform.contract.media.ThumbnailContracts;

import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Artifact and task completion share the caller transaction. Storage owns its durable write intent:
 * published bytes/receipts survive downstream failure for exact retry, never blind physical discard. */
@Service
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public class ThumbnailCommitService {
    private final ThumbnailTaskStore tasks; private final StorageOutputPort outputs; private final ArtifactCommitService commits;
    public ThumbnailCommitService(ThumbnailTaskStore tasks, StorageOutputPort outputs, ArtifactCommitService commits) { this.tasks=tasks; this.outputs=outputs; this.commits=commits; }

    @Transactional
    public String commit(String tenant, String project, String taskId, String subjectArtifactId,
            String contentType, byte[] bytes, Path output, String root) {
        if (!tasks.lockForCommit(tenant, project, taskId)) return null;
        try {
            try { Files.createDirectories(output.getParent()); Files.write(output, bytes); }
            catch (java.io.IOException failure) { throw new IllegalStateException("thumbnail output staging failed", failure); }
            String relative=Path.of(root).toAbsolutePath().normalize().relativize(output).toString().replace(java.io.File.separatorChar,'/');
            var written=outputs.write(new StorageOutputPort.OutputCommand(new StorageOwnershipScope(tenant,project),new IssuanceIdempotencyKey("thumbnail:"+taskId),relative,contentType));
            var issue=written.issuance(); var aid=new ArtifactId("art-"+java.util.UUID.nameUUIDFromBytes((tenant+"\0"+project+"\0"+taskId+"\0"+issue.objectId().value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            // COVER-THUMBNAIL-REBUILD-001 (action 5): a thumbnail is an image Artifact that plays the
            // thumbnail role through a THUMBNAIL_OF provenance relation (the same relation-based model
            // the cover capability uses with COVER_OF), not through a dedicated ArtifactKind.
            String pin=issue.placement().committedDigest().canonicalValue();
            var accepted=commits.commit(new ArtifactCommitRequest(aid,tenant,issue.placement().committedDigest(),issue.placement().committedLength(),ArtifactMediaType.IMAGE,ArtifactKind.DERIVED_MEDIA,Artifact.CURRENT_SCHEMA_VERSION,issue.objectId(),issue.placement().replicaId(),issue.placement().location().providerId(),ReplicaRole.PRIMARY,issue.placement().location().region(),"thumbnail:"+taskId,List.of(new ArtifactCommitRequest.ProvenanceEdgeDeclaration(new ArtifactId(subjectArtifactId),ProvenanceRelationType.THUMBNAIL_OF,ThumbnailContracts.OPERATION_ID,1,taskId,pin,pin)),Instant.now(),Instant.now(),taskId,project));
            if (!tasks.completeLocked(tenant, project, taskId, accepted.artifact().artifactId().value()))
                throw new IllegalStateException("thumbnail commit lost its locked task ownership");
            return accepted.artifact().artifactId().value();
        } finally { try { Files.deleteIfExists(output); Files.deleteIfExists(output.getParent()); } catch (Exception ignored) {} }
    }
}
