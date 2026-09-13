package com.example.platform.storage.app;
import com.example.platform.storage.api.*;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.domain.*;
import com.example.platform.shared.web.TenantGuard;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageFileService implements StorageFilePort {
    private final BlobStorage backend;
    private final StorageReferenceStore references;
    private final Path root;
    private final StorageWriteIntentRecovery recovery;
    private final com.example.platform.storage.app.identity.StorageObjectAuthorityRepository receipts;
    private final com.example.platform.storage.infrastructure.StorageS3Properties s3;
    public StorageFileService(BlobStorage backend, StorageReferenceStore references,
            @Value("${app.storage.local-root:./.data/storage}") String root,
            StorageWriteIntentRecovery recovery,
            com.example.platform.storage.app.identity.StorageObjectAuthorityRepository receipts,
            com.example.platform.storage.infrastructure.StorageS3Properties s3) {
        this.backend=backend; this.references=references; this.root=Path.of(root).toAbsolutePath().normalize();
        this.recovery=recovery; this.receipts=receipts; this.s3=s3;
    }
    /** External bytes and their recovery evidence survive downstream Product transaction rollback. */
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public StorageReference uploadPreview(StorageOwnershipScope scope, IssuanceIdempotencyKey requestKey, byte[] bytes, String contentType) {
        TenantGuard.assertSameTenant(scope.tenantId());
        if(scope.projectId()!=null)throw new IllegalArgumentException("preview upload requires tenant-only scope");
        Objects.requireNonNull(requestKey,"preview request key");
        if(bytes==null || bytes.length==0 || bytes.length>20*1024*1024 || !"video/mp4".equals(contentType))
            throw new IllegalArgumentException("invalid preview media");
        String region=switch(backend.code()) {
            case "localFsStorageProvider" -> "local";
            case "s3StorageProvider" -> com.example.platform.storage.infrastructure.S3ClientSettingsResolver.resolve(s3).region();
            default -> throw new IllegalArgumentException("unsupported preview backend");
        };
        var digest=contentDigest(bytes);
        String fingerprint=com.example.platform.storage.domain.identity.StableStorageFingerprint.sha256(List.of(
                "preview-v1",scope.tenantId(),requestKey.value(),contentType,digest.canonicalValue(),
                Long.toString(bytes.length),backend.code(),"preview-media",region));
        var intent=recovery.beginOrResume(new StorageWriteIntentRecovery.BeginWriteIntentCommand(scope,requestKey,fingerprint,null));
        String key="previews/"+intent.objectId().value()+"/input.mp4";
        var expected=new StorageObjectRef(backend.code(),"preview-media",key);
        if(intent.state()==com.example.platform.storage.domain.identity.StorageWriteIntent.State.PENDING_PROVIDER) {
            var written=backend.put(new PutObjectCommand(expected.bucket(),key,bytes,contentType));
            if(!expected.equals(written))throw new IllegalStateException("preview placement mismatch; retry with the same request key");
        }
        byte[] actual=backend.get(expected.bucket(),key).orElseThrow(()->new IllegalStateException("preview write unavailable; retain intent for retry"));
        if(actual.length!=bytes.length || !digest.matches(contentDigest(actual)))
            throw new IllegalStateException("preview write integrity mismatch; retain intent for retry");
        var namespace=new com.example.platform.storage.contract.namespace.StorageNamespace(scope.tenantId(),null,
                com.example.platform.storage.contract.namespace.NamespaceClass.TEMPORARY,
                com.example.platform.storage.contract.namespace.RegionPolicy.SINGLE_REGION,
                com.example.platform.storage.contract.namespace.DataClassification.INTERNAL);
        var placement=new StorageObjectIssuance.BackendPlacementResult(new StorageReplicaId("rep-"+intent.objectId().value()),
                new com.example.platform.storage.contract.identity.StorageObjectLocation(new StorageProviderId(backend.code()),namespace,
                        expected.toStorageUri(),null,region),com.example.platform.storage.contract.replica.ReplicaState.AVAILABLE,
                digest,actual.length,intent.writeIntentId());
        recovery.recordProviderCompleted(intent.writeIntentId(),placement);
        var issued=recovery.complete(new StorageWriteIntentRecovery.CompleteWriteIntentCommand(intent.writeIntentId(),placement));
        receipts.findPlacement(scope,issued.objectId(),issued.placement().replicaId())
                .orElseThrow(()->new IllegalStateException("preview placement is no longer available"));
        return register(expected,actual,contentType);
    }
    private static com.example.platform.shared.digest.ContentDigest contentDigest(byte[] bytes) {
        try {return com.example.platform.shared.digest.ContentDigest.sha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public StorageReference registerUpload(StorageOwnershipScope scope,String uri,String expectedKey,String contentType){
        TenantGuard.assertSameTenant(scope.tenantId());
        var ref=BlobStorage.parseUri(uri).orElseThrow(()->new IllegalArgumentException("RAW_MEDIA storage reference is invalid"));
        if(!backend.code().equals(ref.provider()) || !"uploads".equals(ref.bucket()) || !Objects.equals(expectedKey,ref.objectKey()))
            throw new IllegalArgumentException("RAW_MEDIA storage key mismatch or backend placement mismatch");
        byte[] bytes=backend.get(ref.bucket(),ref.objectKey()).orElseThrow(()->new IllegalArgumentException("RAW_MEDIA uploaded object not found"));
        if(bytes.length==0)throw new IllegalArgumentException("RAW_MEDIA uploaded object is empty");
        return register(ref,bytes,contentType);
    }
    private StorageReference register(StorageObjectRef ref,byte[] bytes,String contentType){
        String provider=switch(ref.provider()) {case "localFsStorageProvider" -> "LOCAL";case "s3StorageProvider" -> "S3_COMPATIBLE";default -> throw new IllegalArgumentException("unsupported file backend");};
        String location="LOCAL".equals(provider)?root.resolve(ref.bucket()).toString():ref.bucket();
        try{
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return references.save(new StorageReference(null,provider,StorageClass.STANDARD,location,ref.objectKey(),digest,digest,bytes.length,contentType,Instant.now(),Instant.now()));
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
