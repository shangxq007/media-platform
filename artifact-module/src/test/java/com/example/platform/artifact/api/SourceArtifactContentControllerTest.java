package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactApplicationService;
import com.example.platform.artifact.app.ArtifactContentRead;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.artifact.app.ArtifactProjectScope;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceArtifactContentControllerTest {
    final ArtifactProjectScope scope = new ArtifactProjectScope("tenant-a", "project-a");
    final ArtifactId id = new ArtifactId("source-a");
    final byte[] bytes = HexFormat.of().parseHex("000000186674797069736f6d0000020069736f6d6d703432");
    final ContentDigest digest = digest(bytes);
    final ArtifactContentRead contents = mock(ArtifactContentRead.class);
    final ArtifactProjectAuthorizationPort authorization = mock(ArtifactProjectAuthorizationPort.class);
    final SourceArtifactContentController controller = new SourceArtifactContentController(contents, authorization);

    @Test void authorizedSourceReturnsExactBytesAndNoStorageCoordinates() {
        when(contents.readProjectSourceArtifact(scope, id))
                .thenReturn(new ArtifactContentRead.Content(bytes, "video/mp4", "output.mp4", digest));
        var response = controller.content("tenant-a", "project-a", "source-a");
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(bytes, (byte[]) response.getBody());
        assertEquals("video/mp4", response.getHeaders().getContentType().toString());
        assertEquals(bytes.length, response.getHeaders().getContentLength());
        assertFalse(response.getHeaders().toString().contains("so-"));
        assertFalse(response.getHeaders().toString().contains("local-storage"));
        verify(authorization).requireRead("tenant-a", "project-a");
    }

    @Test void missingArtifactIsNotFound() {
        when(contents.readProjectSourceArtifact(scope, id)).thenThrow(new ArtifactApplicationService.ArtifactAccessException(
                ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND, "Artifact not found"));
        assertEquals(404, controller.content("tenant-a", "project-a", "source-a").getStatusCode().value());
    }

    @Test void unavailableSourceIs503() {
        when(contents.readProjectSourceArtifact(scope, id)).thenThrow(new ArtifactApplicationService.ArtifactAccessException(
                ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE, "Artifact content unavailable"));
        var response = controller.content("tenant-a", "project-a", "source-a");
        assertEquals(503, response.getStatusCode().value());
        var body = (ArtifactApplicationController.ArtifactAccessError) response.getBody();
        assertEquals("NOT_AVAILABLE", body.status());
        assertEquals("Artifact content unavailable", body.message());
    }

    @Test void authorizationDenialPrecedesLookup() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(authorization)
                .requireRead("tenant-a", "project-a");
        assertThrows(ResponseStatusException.class,
                () -> controller.content("tenant-a", "project-a", "source-a"));
        verifyNoInteractions(contents);
    }

    private static ContentDigest digest(byte[] bytes) {
        try {
            return ContentDigest.sha256(HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
