package com.example.platform.workerfabric.reuse;
import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
/** Protect the replacement native staging path with the retired slice's filesystem invariants. */
class OutputStagingAreaIsolationTest {
    @TempDir Path temp;
    private long count(Path directory) throws IOException {try(var entries=Files.list(directory)){return entries.count();}}
    @Test void eachInvocationUsesExclusiveFreshFilesAndPreservesExistingBytes() throws Exception {
        Path root=Files.createDirectory(temp.resolve("staging"));Path existing=root.resolve("existing");Files.writeString(existing,"protected");
        var staging=new OutputStagingArea(root);
        var first=staging.stage(new ByteArrayInputStream(new byte[]{1,2,3}));var second=staging.stage(new ByteArrayInputStream(new byte[]{1,2,3}));
        assertNotEquals(first.path(),second.path());assertEquals(root,first.path().getParent());assertEquals(first.contentDigest(),second.contentDigest());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(first.path()));assertEquals("protected",Files.readString(existing));
    }
    @Test void symlinkedRootOrAncestorIsRejectedBeforeWritingOutsideIt() throws Exception {
        Path outside=Files.createDirectory(temp.resolve("outside"));Path link=temp.resolve("link");Files.createSymbolicLink(link,outside);
        assertThrows(IllegalArgumentException.class,()->new OutputStagingArea(link));
        assertThrows(IllegalArgumentException.class,()->new OutputStagingArea(link.resolve("new-child")));
        assertFalse(Files.exists(outside.resolve("new-child")));assertEquals(0,count(outside));
    }
    @Test void replacingTheConfiguredDirectoryDoesNotRedirectOutput() throws Exception {
        Path root=Files.createDirectory(temp.resolve("staging"));var staging=new OutputStagingArea(root);
        Path held=temp.resolve("original");Files.move(root,held);Path outside=Files.createDirectory(temp.resolve("outside"));Files.createSymbolicLink(root,outside);
        assertThrows(IOException.class,()->staging.stage(new ByteArrayInputStream(new byte[]{4,5})));
        assertEquals(0,count(held));assertEquals(0,count(outside));
    }
}
