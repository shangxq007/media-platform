package com.example.platform.render.app.clientexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ClientExportServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void uploadAndCompleteStoresFile() throws Exception {
        ClientExportService service = new ClientExportService(tempDir.toString(), null);
        var session = service.startSession("tenant-1", "proj-1", "snap-1", "client_720p_watermarked");

        byte[] payload = new byte[] { 1, 2, 3, 4 };
        var file = new MockMultipartFile("file", "out.mp4", "video/mp4", payload);
        var completed = service.uploadAndComplete(session.sessionId(), file, 10L, null, false);

        assertEquals("COMPLETED", completed.status());
        assertTrue(Files.exists(tempDir.resolve("artifacts/client-exports")
                .resolve(session.sessionId()).resolve("output.mp4")));
    }
}
