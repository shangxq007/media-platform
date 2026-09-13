package com.example.platform.storage.app;

import com.example.platform.storage.api.*;
import com.example.platform.storage.api.StorageObjectIssuance.*;
import com.example.platform.storage.app.identity.StorageObjectAuthorityRepository;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.*;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.domain.*;
import com.example.platform.storage.domain.identity.StableStorageFingerprint;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.web.TenantGuard;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Storage write-intent orchestration. External bytes survive downstream database rollback. */
@Service
public class StorageOutputService implements StorageOutputPort, StoragePlacementQuery {
    private final BlobStorage backend;
    private final StorageWriteIntentRecovery recovery;
    private final StorageObjectAuthorityRepository receipts;
    private final StorageReferenceStore references;
    private final Path root;
    private final OutputStorageProperties properties;
    private final com.example.platform.storage.infrastructure.StorageS3Properties s3;
    public StorageOutputService(BlobStorage backend, StorageWriteIntentRecovery recovery,
            StorageObjectAuthorityRepository receipts, StorageReferenceStore references,
            @Value("${app.storage.local-root:./.data/storage}") String root, OutputStorageProperties properties, com.example.platform.storage.infrastructure.StorageS3Properties s3) {
        this.backend=backend; this.recovery=recovery; this.receipts=receipts; this.references=references;
        this.root=Path.of(root).toAbsolutePath().normalize(); this.properties=properties; this.s3=s3;
    }

    @Override
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public WrittenOutput write(OutputCommand command) {
        TenantGuard.assertSameTenant(command.owner().tenantId());
        String providerType = providerType(); // reject unsupported configuration before any write
        try {
            Path relative = Path.of(command.relativePath());
            if (relative.isAbsolute() || relative.normalize().startsWith("..")) throw new IllegalArgumentException("output path traversal is forbidden");
            Path source=root.resolve(relative).toRealPath();
            if (!source.startsWith(root.toRealPath())) throw new IllegalArgumentException("output path traversal is forbidden");
            if (!Files.isRegularFile(source)) throw new IllegalArgumentException("output is not a regular file");
            byte[] bytes=Files.readAllBytes(source);
            if (bytes.length==0) throw new IllegalArgumentException("output has zero bytes");
            ContentDigest digest=digest(bytes);
            String bucket="local".equals(properties.getProvider()) ? "artifacts" : properties.getS3Bucket();
            if (bucket==null || bucket.isBlank()) bucket=s3.getDefaultBucket();
            if (bucket==null || bucket.isBlank()) throw new IllegalArgumentException("output bucket required");
            String region="local".equals(properties.getProvider()) ? "local" : com.example.platform.storage.infrastructure.S3ClientSettingsResolver.resolve(s3).region();
            String fingerprint=StableStorageFingerprint.sha256(List.of("output-v1", command.owner().tenantId(),
                    command.owner().projectId(), command.key().value(), command.contentType(), digest.canonicalValue(),
                    Long.toString(bytes.length), backend.code(), bucket, region));
            var intent=recovery.beginOrResume(new StorageWriteIntentRecovery.BeginWriteIntentCommand(
                    command.owner(), command.key(), fingerprint, null));
            String key="outputs/"+intent.objectId().value()+"/output";
            StorageObjectRef location=new StorageObjectRef(backend.code(),bucket,key);
            if (intent.state()==StorageWriteIntent.State.PENDING_PROVIDER) {
                StorageObjectRef written=backend.put(new PutObjectCommand(bucket,key,bytes,command.contentType()));
                if (!location.equals(written)) throw new IllegalStateException("backend placement differs from requested output");
            }
            byte[] actual=backend.get(bucket,key).orElseThrow(()->new IllegalStateException("written output is unavailable"));
            if (actual.length!=bytes.length || !digest.matches(digest(actual))) throw new IllegalStateException("written output length/digest mismatch");
            var namespace=new StorageNamespace(command.owner().tenantId(),command.owner().projectId(),
                    NamespaceClass.DERIVED,RegionPolicy.SINGLE_REGION,DataClassification.INTERNAL);
            var placement=new BackendPlacementResult(new StorageReplicaId("rep-"+intent.objectId().value()),
                    new StorageObjectLocation(new StorageProviderId(backend.code()),namespace,location.toStorageUri(),null,
                            region),
                    ReplicaState.AVAILABLE,digest,actual.length,intent.writeIntentId());
            recovery.recordProviderCompleted(intent.writeIntentId(),placement);
            recovery.complete(new StorageWriteIntentRecovery.CompleteWriteIntentCommand(intent.writeIntentId(),placement));
            var issued=receipts.findOriginalIssuance(command.owner(),command.key()).orElseThrow(()->new IllegalStateException("committed receipt unavailable"));
            String referenceRoot="LOCAL".equals(providerType) ? root.resolve(bucket).toString() : bucket;
            var reference=references.save(new StorageReference(null,providerType,StorageClass.STANDARD,referenceRoot,key,
                    digest.canonicalValue(),digest.canonicalValue(),(long)actual.length,command.contentType(),Instant.now(),Instant.now()));
            return new WrittenOutput(issued,reference);
        } catch (java.io.IOException e) { throw new IllegalStateException("output not found or write/read failed; retain write intent for retry",e); }
    }

    @Override public Optional<IssuanceResult> find(StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
        TenantGuard.assertSameTenant(owner.tenantId());
        return receipts.findOriginalIssuance(owner,key);
    }
    @Override public byte[] read(StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
        var issued=find(owner,key).orElseThrow(()->new IllegalArgumentException("output receipt not found"));
        var ref=BlobStorage.parseUri(issued.placement().location().opaqueLocator()).orElseThrow();
        if (!backend.code().equals(ref.provider())) throw new IllegalStateException("output backend unavailable");
        byte[] bytes=backend.get(ref.bucket(),ref.objectKey()).orElseThrow(()->new IllegalStateException("output bytes unavailable"));
        if (bytes.length!=issued.placement().committedLength() || !digest(bytes).matches(issued.placement().committedDigest()))
            throw new IllegalStateException("output integrity mismatch");
        return bytes;
    }
    private String providerType() {
        return switch (properties.getProvider()) {
            case "local" -> { if (!backend.code().equals("localFsStorageProvider")) throw new IllegalStateException("local output backend unavailable"); yield "LOCAL"; }
            case "s3-compatible" -> { if (!backend.code().equals("s3StorageProvider")) throw new IllegalStateException("S3 output backend unavailable"); yield "S3_COMPATIBLE"; }
            default -> throw new IllegalArgumentException("unsupported output provider");
        };
    }
    private static ContentDigest digest(byte[] bytes) {
        try { return ContentDigest.sha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
