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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Storage write-intent orchestration. External bytes survive downstream database rollback. */
@Service
public class StorageOutputService implements StorageOutputPort, StoragePlacementQuery, StorageSourceUploadPort {
    private static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;
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
        return writeWithNamespace(command, NamespaceClass.DERIVED);
    }

    private WrittenOutput writeWithNamespace(OutputCommand command, NamespaceClass namespaceClass) {
        TenantGuard.assertSameTenant(command.owner().tenantId());
        String providerType = providerType(); // reject unsupported configuration before any write
        try {
            Path relative = Path.of(command.relativePath());
            if (relative.isAbsolute() || relative.normalize().startsWith("..")) throw new IllegalArgumentException("output path traversal is forbidden");
            Path source=root.resolve(relative).toRealPath();
            if (!source.startsWith(root.toRealPath())) throw new IllegalArgumentException("output path traversal is forbidden");
            if (!Files.isRegularFile(source)) throw new IllegalArgumentException("output is not a regular file");
            byte[] bytes=namespaceClass == NamespaceClass.SOURCE ? null : Files.readAllBytes(source);
            long length=bytes == null ? Files.size(source) : bytes.length;
            if (length==0 || (namespaceClass == NamespaceClass.SOURCE && length > MAX_SOURCE_BYTES))
                throw new IllegalArgumentException("output has invalid byte length");
            ContentDigest digest=bytes == null ? digest(source) : digest(bytes);
            String bucket="local".equals(properties.getProvider()) ? "artifacts" : properties.getS3Bucket();
            if (bucket==null || bucket.isBlank()) bucket=s3.getDefaultBucket();
            if (bucket==null || bucket.isBlank()) throw new IllegalArgumentException("output bucket required");
            String region="local".equals(properties.getProvider()) ? "local" : com.example.platform.storage.infrastructure.S3ClientSettingsResolver.resolve(s3).region();
            String fingerprint=StableStorageFingerprint.sha256(List.of(
                    namespaceClass == NamespaceClass.SOURCE ? "source-v1" : "output-v1", command.owner().tenantId(),
                    command.owner().projectId(), command.key().value(), command.contentType(), digest.canonicalValue(),
                    Long.toString(length), backend.code(), bucket, region));
            var intent=recovery.beginOrResume(new StorageWriteIntentRecovery.BeginWriteIntentCommand(
                    command.owner(), command.key(), fingerprint, null));
            String key=(namespaceClass == NamespaceClass.SOURCE ? "sources/" : "outputs/")
                    +intent.objectId().value()+"/output";
            StorageObjectRef location=new StorageObjectRef(backend.code(),bucket,key);
            if (intent.state()==StorageWriteIntent.State.PENDING_PROVIDER) {
                StorageObjectRef written=backend.put(bytes == null
                        ? PutObjectCommand.fromPath(bucket,key,source,command.contentType())
                        : new PutObjectCommand(bucket,key,bytes,command.contentType()));
                if (!location.equals(written)) throw new IllegalStateException("backend placement differs from requested output");
            }
            byte[] actual=bytes == null ? null : backend.get(bucket,key).orElseThrow(()->new IllegalStateException("written output is unavailable"));
            long actualLength=actual == null ? verifiedLength(bucket,key,digest,length) : actual.length;
            if (actual != null && (actualLength!=length || !digest.matches(digest(actual))))
                throw new IllegalStateException("written output length/digest mismatch");
            var namespace=new StorageNamespace(command.owner().tenantId(),command.owner().projectId(),
                    namespaceClass,RegionPolicy.SINGLE_REGION,DataClassification.INTERNAL);
            var placement=new BackendPlacementResult(new StorageReplicaId("rep-"+intent.objectId().value()),
                    new StorageObjectLocation(new StorageProviderId(backend.code()),namespace,location.toStorageUri(),null,
                            region),
                    ReplicaState.AVAILABLE,digest,actualLength,intent.writeIntentId());
            recovery.recordProviderCompleted(intent.writeIntentId(),placement);
            recovery.complete(new StorageWriteIntentRecovery.CompleteWriteIntentCommand(intent.writeIntentId(),placement));
            var issued=receipts.findOriginalIssuance(command.owner(),command.key()).orElseThrow(()->new IllegalStateException("committed receipt unavailable"));
            String referenceRoot="LOCAL".equals(providerType) ? root.resolve(bucket).toString() : bucket;
            var reference=references.save(new StorageReference(null,providerType,StorageClass.STANDARD,referenceRoot,key,
                    digest.canonicalValue(),digest.canonicalValue(),actualLength,command.contentType(),Instant.now(),Instant.now()));
            return new WrittenOutput(issued,reference);
        } catch (java.io.IOException e) { throw new IllegalStateException("output not found or write/read failed; retain write intent for retry",e); }
    }

    @Override
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public WrittenOutput writeSource(StorageOwnershipScope owner, IssuanceIdempotencyKey key,
            InputStream content, long declaredLength, String contentType) {
        TenantGuard.assertSameTenant(owner.tenantId());
        if (owner.projectId() == null || owner.projectId().isBlank()
                || content == null || declaredLength <= 0 || declaredLength > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("bounded project-scoped source bytes required");
        }
        try {
            Path stagingRoot = stagingDirectory(".source-upload-staging");
            Path staged = Files.createTempFile(stagingRoot, "upload-", ".bin");
            try {
                long copied=copyBounded(content,staged,MAX_SOURCE_BYTES);
                if (copied != declaredLength) throw new IllegalArgumentException("source length differs from declaration");
                return writeWithNamespace(new OutputCommand(owner, key,
                        root.relativize(staged).toString(), contentType), NamespaceClass.SOURCE);
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("source staging unavailable", failure);
        }
    }

    @Override
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public <T> T withVerifiedSource(StorageOwnershipScope owner, IssuanceIdempotencyKey key,
            java.util.function.Function<Path,T> reader) {
        TenantGuard.assertSameTenant(owner.tenantId());
        Objects.requireNonNull(reader);
        var issued=find(owner,key).orElseThrow(()->new IllegalArgumentException("source receipt unavailable"));
        if (issued.placement().location().namespace().namespaceClass() != NamespaceClass.SOURCE
                || issued.placement().state() != ReplicaState.AVAILABLE
                || issued.placement().committedLength() > MAX_SOURCE_BYTES)
            throw new IllegalArgumentException("available bounded source required");
        var location=BlobStorage.parseUri(issued.placement().location().opaqueLocator()).orElseThrow();
        if (!backend.code().equals(location.provider())) throw new IllegalStateException("source backend unavailable");
        Path staged=null;
        try {
            Path stagingRoot=stagingDirectory(".source-read-staging");
            staged=Files.createTempFile(stagingRoot,"verified-",".bin");
            try (InputStream input=backend.open(location.bucket(),location.objectKey())
                    .orElseThrow(()->new IllegalStateException("source bytes unavailable"))) {
                long copied=copyBounded(input,staged,MAX_SOURCE_BYTES);
                if (copied != issued.placement().committedLength()
                        || !digest(staged).matches(issued.placement().committedDigest()))
                    throw new IllegalStateException("source integrity mismatch");
            }
            return reader.apply(staged);
        } catch (IOException failure) {
            throw new IllegalStateException("verified source read unavailable",failure);
        } finally {
            if (staged != null) try { Files.deleteIfExists(staged); }
            catch (IOException cleanup) { throw new IllegalStateException("verified source cleanup failed",cleanup); }
        }
    }

    private long verifiedLength(String bucket,String key,ContentDigest expected,long length) throws IOException {
        try (InputStream input=backend.open(bucket,key)
                .orElseThrow(()->new IllegalStateException("written output is unavailable"))) {
            MessageDigest md=sha256();
            byte[] buffer=new byte[8192];long total=0;int count;
            while ((count=input.read(buffer))!=-1) {
                if (count == 0) {
                    int single=input.read();
                    if (single == -1) break;
                    buffer[0]=(byte)single;
                    count=1;
                }
                total=Math.addExact(total,count);
                if (total>length) throw new IllegalStateException("written output length mismatch");
                md.update(buffer,0,count);
            }
            if (total!=length || !expected.matches(ContentDigest.sha256(HexFormat.of().formatHex(md.digest()))))
                throw new IllegalStateException("written output length/digest mismatch");
            return total;
        }
    }

    private static long copyBounded(InputStream input,Path target,long max) throws IOException {
        try (OutputStream output=Files.newOutputStream(target,StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer=new byte[8192];long total=0;int count;
            while ((count=input.read(buffer))!=-1) {
                if (count == 0) {
                    int single=input.read();
                    if (single == -1) break;
                    buffer[0]=(byte)single;
                    count=1;
                }
                total=Math.addExact(total,count);
                if (total>max) throw new IllegalArgumentException("source exceeds 64 MiB");
                output.write(buffer,0,count);
            }
            return total;
        }
    }

    private static ContentDigest digest(Path source) throws IOException {
        MessageDigest md=sha256();
        try (InputStream input=new DigestInputStream(Files.newInputStream(source),md)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return ContentDigest.sha256(HexFormat.of().formatHex(md.digest()));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private Path stagingDirectory(String name) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("storage root symlink forbidden");
        Path stagingRoot=root.resolve(name);
        Files.createDirectories(stagingRoot);
        if (Files.isSymbolicLink(stagingRoot)
                || !stagingRoot.toRealPath().startsWith(root.toRealPath()))
            throw new IllegalArgumentException("source staging path unavailable");
        return stagingRoot;
    }

    @Override public List<IssuanceResult> references(String uri,String projectId,int limit){return receipts.references(uri,projectId,limit);}
    @Override public Optional<IssuanceResult> find(StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
        TenantGuard.assertSameTenant(owner.tenantId());
        return receipts.findOriginalIssuance(owner,key);
    }
    @Override public Optional<IssuanceResult> findPlacement(StorageOwnershipScope owner, StorageObjectId objectId, StorageReplicaId replicaId) {
        TenantGuard.assertSameTenant(owner.tenantId());
        return receipts.findPlacement(owner, objectId, replicaId);
    }
    @Override public byte[] read(StorageOwnershipScope owner, IssuanceIdempotencyKey key) {
        var issued=find(owner,key).orElseThrow(()->new IllegalArgumentException("output receipt not found"));
        receipts.findPlacement(owner,issued.objectId(),issued.placement().replicaId())
            .orElseThrow(()->new IllegalStateException("current output placement unavailable or changed"));
        var ref=BlobStorage.parseUri(issued.placement().location().opaqueLocator()).orElseThrow();
        if (!backend.code().equals(ref.provider())) throw new IllegalStateException("output backend unavailable");
        byte[] bytes=backend.get(ref.bucket(),ref.objectKey()).orElseThrow(()->new IllegalStateException("output bytes unavailable"));
        if (bytes.length!=issued.placement().committedLength() || !digest(bytes).matches(issued.placement().committedDigest()))
            throw new IllegalStateException("output integrity mismatch");
        return bytes;
    }
    @Override public byte[] read(StorageOwnershipScope owner,StorageObjectId objectId,StorageReplicaId replicaId) {
        TenantGuard.assertSameTenant(owner.tenantId());
        var placement=receipts.findPlacement(owner,objectId,replicaId).orElseThrow(()->new IllegalArgumentException("scoped placement not found"));
        if(placement.placement().state()!=ReplicaState.AVAILABLE)throw new IllegalStateException("placement unavailable");
        return read(owner,placement.receipt().idempotencyKey());
    }
    @Override public StorageReference reference(StorageOwnershipScope owner,StorageObjectId objectId,StorageReplicaId replicaId) {
        TenantGuard.assertSameTenant(owner.tenantId());
        var issued=receipts.findPlacement(owner,objectId,replicaId).orElseThrow(()->new IllegalArgumentException("scoped output placement unavailable"));
        var location=BlobStorage.parseUri(issued.placement().location().opaqueLocator()).orElseThrow();
        if(!backend.code().equals(location.provider()))throw new IllegalStateException("output backend unavailable");
        String type=providerType();String locationRoot="LOCAL".equals(type)?root.resolve(location.bucket()).toString():location.bucket();
        var reference=references.findByLocation(type,locationRoot,location.objectKey()).orElseThrow(()->new IllegalStateException("persisted output content metadata unavailable"));
        if(reference.fileSize()!=issued.placement().committedLength()
                || !issued.placement().committedDigest().canonicalValue().equals(reference.checksum())
                || !issued.placement().committedDigest().canonicalValue().equals(reference.contentHash()))
            throw new IllegalStateException("output content metadata differs from placement evidence");
        return reference;
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
