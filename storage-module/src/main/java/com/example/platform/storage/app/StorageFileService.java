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
    private final BlobStorage backend; private final StorageReferenceStore references; private final Path root;
    public StorageFileService(BlobStorage backend,StorageReferenceStore references,
            @Value("${app.storage.local-root:./.data/storage}") String root){this.backend=backend;this.references=references;this.root=Path.of(root).toAbsolutePath().normalize();}
    public StorageReference uploadPreview(StorageOwnershipScope scope,byte[] bytes,String contentType){
        TenantGuard.assertSameTenant(scope.tenantId());
        if(bytes==null || bytes.length==0 || bytes.length>20*1024*1024 || !"video/mp4".equals(contentType))throw new IllegalArgumentException("invalid preview media");
        String key=UUID.randomUUID()+"/input.mp4";
        var expected=new StorageObjectRef(backend.code(),"preview-media",key);
        var written=backend.put(new PutObjectCommand(expected.bucket(),expected.objectKey(),bytes,contentType));
        if(!expected.equals(written))throw new IllegalStateException("preview placement mismatch");
        byte[] actual=backend.get(expected.bucket(),key).orElseThrow(()->new IllegalStateException("preview write unavailable"));
        if(!Arrays.equals(bytes,actual))throw new IllegalStateException("preview write integrity mismatch");
        return register(expected,actual,contentType);
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
