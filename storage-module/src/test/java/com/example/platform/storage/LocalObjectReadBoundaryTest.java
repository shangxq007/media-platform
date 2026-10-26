package com.example.platform.storage;

import com.example.platform.storage.infrastructure.LocalObjectStoreStorageProvider;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.read.*;
import java.nio.file.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalObjectReadBoundaryTest {
    @TempDir Path dir;
    @Test void objectsDirectoryCannotBeReplacedByAnEscapingSymlink() throws Exception {
        var provider=new LocalObjectStoreStorageProvider("local",dir.resolve("store").toString());
        Path outside=dir.resolve("outside");Files.createDirectory(outside);Files.writeString(outside.resolve("object"),"private synthetic bytes");
        Files.delete(dir.resolve("store/objects"));Files.createSymbolicLink(dir.resolve("store/objects"),outside);
        assertThrows(IllegalStateException.class,()->provider.openRead(new StorageReadRequest(new StorageObjectId("object"),Optional.empty(),IntegrityRequirement.VERIFY_DIGEST)));
        assertEquals("private synthetic bytes",Files.readString(outside.resolve("object")));
    }
    @Test void traversalAbsoluteIdentityAndSymlinkAreRejectedBeforeRead() throws Exception {
        var provider=new LocalObjectStoreStorageProvider("local",dir.resolve("store").toString());
        Path secret=dir.resolve("unrelated");Files.writeString(secret,"synthetic-unrelated-bytes");
        for(String invalid:new String[]{"../unrelated",secret.toString(),"objects/other",".."})assertThrows(IllegalArgumentException.class,()->provider.openRead(new StorageReadRequest(new StorageObjectId(invalid),Optional.empty(),IntegrityRequirement.VERIFY_DIGEST)));
        Files.createSymbolicLink(dir.resolve("store/objects/escape"),secret);
        assertThrows(IllegalStateException.class,()->provider.openRead(new StorageReadRequest(new StorageObjectId("escape"),Optional.empty(),IntegrityRequirement.VERIFY_DIGEST)));
        assertThrows(IllegalStateException.class,()->provider.stat(new StorageObjectId("escape")));
        assertEquals("synthetic-unrelated-bytes",Files.readString(secret));
    }
}
