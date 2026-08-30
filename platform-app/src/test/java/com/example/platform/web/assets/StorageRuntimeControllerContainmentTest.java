package com.example.platform.web.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.storage.contract.StorageClass;
import com.example.platform.storage.contract.StorageReference;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageRuntimeControllerContainmentTest {

    @Test
    void safeReadDoesNotDisclosePhysicalPath() {
        StorageRuntimeService service = mock(StorageRuntimeService.class);
        when(service.find("storage-1")).thenReturn(Optional.of(new StorageReference(
                "storage-1", "local", StorageClass.STANDARD,
                "/secret/platform/root", "tenant-a/output.mp4",
                "checksum", "digest", 42L, "video/mp4", Instant.EPOCH, Instant.EPOCH)));

        Map<String, Object> body = new StorageRuntimeController(service).get("storage-1").getBody();

        assertNotNull(body);
        assertFalse(body.containsKey("path"));
        assertFalse(body.containsKey("rootPath"));
        assertFalse(body.containsKey("relativePath"));
        assertFalse(body.toString().contains("/secret/platform/root"));
    }
}
