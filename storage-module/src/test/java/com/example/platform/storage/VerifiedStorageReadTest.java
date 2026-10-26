package com.example.platform.storage;

import com.example.platform.storage.app.VerifiedStorageReadService;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.shared.digest.ContentDigest;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VerifiedStorageReadTest {
    @Test void matchingBytesAreClosedAndRepeatedReadsAreIndependent() throws Exception {
        byte[] bytes={1,2,3};var digest=ContentDigest.sha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var provider=mock(StorageProvider.class);var id=new StorageProviderId("local");var object=new StorageObjectId("object");
        when(provider.providerId()).thenReturn(id);when(provider.stat(object)).thenReturn(Optional.of(new StorageObjectMetadata(object,digest,3)));
        var streams=new ArrayList<Tracked>();when(provider.openRead(any())).thenAnswer(call->{var in=new Tracked(bytes);streams.add(in);return Optional.of(in);});
        var service=new VerifiedStorageReadService(List.of(provider));
        assertArrayEquals(bytes,service.read(id,object,digest,3,10));assertArrayEquals(bytes,service.read(id,object,digest,3,10));
        assertEquals(2,streams.size());assertTrue(streams.stream().allMatch(s->s.closed));
    }
    @Test void wrongDigestTruncationAndTrailingDataFailClosedAndCloseStream() throws Exception {
        var id=new StorageProviderId("local");var object=new StorageObjectId("object");byte[] expected={1,2,3};
        var digest=ContentDigest.sha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(expected)));
        for(byte[] actual:List.of(new byte[]{1,2},new byte[]{1,2,3,4},new byte[]{3,2,1})) {
            var provider=mock(StorageProvider.class);when(provider.providerId()).thenReturn(id);when(provider.stat(object)).thenReturn(Optional.of(new StorageObjectMetadata(object,digest,3)));
            var in=new Tracked(actual);when(provider.openRead(any())).thenReturn(Optional.of(in));
            assertThrows(IllegalStateException.class,()->new VerifiedStorageReadService(List.of(provider)).read(id,object,digest,3,10));assertTrue(in.closed);
        }
    }
    @Test void oversizedContentNeverTouchesProvider(){var p=mock(StorageProvider.class);var id=new StorageProviderId("local");when(p.providerId()).thenReturn(id);var service=new VerifiedStorageReadService(List.of(p));clearInvocations(p);assertThrows(IllegalArgumentException.class,()->service.read(id,new StorageObjectId("object"),ContentDigest.sha256("a".repeat(64)),11,10));verifyNoInteractions(p);}
    @Test void supportedIdentityComesOnlyFromTheRegisteredProviderSet() {
        var provider=mock(StorageProvider.class);var id=new StorageProviderId("registered");when(provider.providerId()).thenReturn(id);
        var service=new VerifiedStorageReadService(List.of(provider));clearInvocations(provider);
        assertTrue(service.supports(id));assertFalse(service.supports(new StorageProviderId("not-registered")));
        verifyNoInteractions(provider);
    }
    static class Tracked extends ByteArrayInputStream { boolean closed;Tracked(byte[] b){super(b);}@Override public void close() throws IOException {closed=true;super.close();}}
}
