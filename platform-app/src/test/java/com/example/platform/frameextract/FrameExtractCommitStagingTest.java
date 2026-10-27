package com.example.platform.frameextract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.coverimage.*;
import com.example.platform.thumbnail.*;
import com.example.platform.storage.api.StorageOutputPort;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real staging I/O through each production commit service; storage stops before publication. */
class FrameExtractCommitStagingTest {
    @TempDir Path root;
    private static final byte[] BYTES = {1, 2, 3};
    private static final class ControlledStop extends RuntimeException {}

    @Test void freshStagingWritesExactBytesAndOnlyRemovesItsOwnDirectory() throws Exception {
        for (boolean cover : new boolean[]{false, true}) {
            var outputs = mock(StorageOutputPort.class);
            var artifacts = mock(ArtifactCommitService.class);
            Path output = root.resolve(cover ? "cover-owned" : "thumbnail-owned").resolve("output");
            Path unrelated = Files.writeString(root.resolve("keep"), "untouched");
            when(outputs.write(any())).thenAnswer(call -> {
                assertArrayEquals(BYTES, Files.readAllBytes(output));
                assertEquals(root.relativize(output).toString(),
                        call.<StorageOutputPort.OutputCommand>getArgument(0).relativePath());
                throw new ControlledStop();
            });
            assertThrows(ControlledStop.class, () -> commit(cover, outputs, artifacts, output, root));
            verify(outputs).write(any());
            verify(artifacts, never()).commit(any());
            assertFalse(Files.exists(output.getParent()));
            assertEquals("untouched", Files.readString(unrelated));
        }
    }

    @Test void preexistingStagingAndSymlinksAreNeitherOverwrittenNorRemoved() throws Exception {
        for (boolean cover : new boolean[]{false, true}) {
            Path directory = Files.createDirectory(root.resolve(cover ? "existing-cover" : "existing-thumb"));
            Path keep = Files.writeString(directory.resolve("output"), "not-owned");
            rejected(cover, keep, root);
            assertEquals("not-owned", Files.readString(keep));
            Path link = Files.createSymbolicLink(root.resolve(cover ? "link-cover" : "link-thumb"), directory);
            rejected(cover, link.resolve("output"), root);
            assertTrue(Files.isSymbolicLink(link));
            assertEquals("not-owned", Files.readString(keep));
        }
    }

    @Test void escapedOutputAndSymlinkedConfiguredRootFailBeforeStorage() throws Exception {
        Path configured = Files.createDirectory(root.resolve("configured"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path keep = Files.writeString(outside.resolve("keep"), "unchanged");
        Path link = Files.createSymbolicLink(root.resolve("configured-link"), outside);
        for (boolean cover : new boolean[]{false, true}) {
            rejected(cover, outside.resolve("unowned/output"), configured);
            rejected(cover, link.resolve("unowned/output"), link);
            assertFalse(Files.exists(outside.resolve("unowned")));
            assertEquals("unchanged", Files.readString(keep));
        }
    }
    private void rejected(boolean cover, Path output, Path configured) {
        var outputs = mock(StorageOutputPort.class);
        var artifacts = mock(ArtifactCommitService.class);
        assertThrows(IllegalStateException.class, () -> commit(cover, outputs, artifacts, output, configured));
        verifyNoInteractions(outputs);
        verify(artifacts, never()).commit(any());
    }
    private String commit(boolean cover, StorageOutputPort outputs, ArtifactCommitService artifacts,
            Path output, Path configured) {
        if (cover) {
            var tasks = mock(CoverImageTaskStore.class);
            when(tasks.lockForCommit("tenant", "project", "task")).thenReturn(true);
            return new CoverImageCommitService(tasks, outputs, artifacts)
                    .commit("tenant", "project", "task", "subject", "image/png", BYTES, output, configured.toString());
        }
        var tasks = mock(ThumbnailTaskStore.class);
        when(tasks.lockForCommit("tenant", "project", "task")).thenReturn(true);
        return new ThumbnailCommitService(tasks, outputs, artifacts)
                .commit("tenant", "project", "task", "subject", "image/png", BYTES, output, configured.toString());
    }
}
