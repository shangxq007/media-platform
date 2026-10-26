package com.example.platform.storage.app;

import com.example.platform.storage.api.VerifiedStorageRead;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.read.*;
import com.example.platform.shared.digest.ContentDigest;
import java.util.*;
import java.io.*;
import org.springframework.stereotype.Service;

@Service
public class VerifiedStorageReadService implements VerifiedStorageRead {
    private final Map<StorageProviderId,StorageProvider> providers;
    public VerifiedStorageReadService(List<StorageProvider> supplied) {
        var map=new HashMap<StorageProviderId,StorageProvider>();
        for(var provider:supplied)if(map.put(provider.providerId(),provider)!=null)throw new IllegalStateException("duplicate storage provider identity");
        providers=Map.copyOf(map);
    }
    @Override public boolean supports(StorageProviderId id) { return providers.containsKey(id); }
    public byte[] read(StorageProviderId id,StorageObjectId object,ContentDigest expected,long length,long limit) {
        if(length<0||length>limit||limit>Integer.MAX_VALUE-1)throw new IllegalArgumentException("content exceeds bounded download limit");
        StorageProvider provider=Optional.ofNullable(providers.get(id)).orElseThrow(()->new IllegalStateException("content unavailable"));
        var stat=provider.stat(object).orElseThrow(()->new IllegalStateException("content unavailable"));
        if(stat.length()!=length||!stat.digest().matches(expected))throw new IllegalStateException("content integrity mismatch");
        try(InputStream in=provider.openRead(new StorageReadRequest(object,Optional.empty(),IntegrityRequirement.VERIFY_DIGEST)).orElseThrow(()->new IllegalStateException("content unavailable"))) {
            byte[] bytes=in.readNBytes(Math.toIntExact(length+1));
            var actual=ContentDigest.sha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            if(bytes.length!=length||!actual.matches(expected))throw new IllegalStateException("content integrity mismatch");
            return bytes;
        }catch(IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("content unavailable");}
    }
}
