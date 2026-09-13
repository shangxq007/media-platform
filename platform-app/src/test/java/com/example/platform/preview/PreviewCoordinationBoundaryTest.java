package com.example.platform.preview;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreviewCoordinationBoundaryTest {
    static boolean foreignMutation(String source){return source.contains("ProductRuntimeService") || source.contains("StorageFilePort")
            || source.contains("StorageReferenceRepository") || source.contains("ProductRepository") || source.contains("BlobStorage");}
    @Test void controllerDelegatesAndCannotReintroduceWriters() throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();if(!Files.isDirectory(root.resolve("render-module")))root=root.getParent();
        String source=Files.readString(root.resolve("render-module/src/main/java/com/example/platform/render/api/RenderController.java"));
        assertFalse(foreignMutation(source));assertTrue(source.contains("previewUploads.upload"));
        assertTrue(foreignMutation("private ProductRuntimeService products;"));
        assertTrue(foreignMutation("private StorageReferenceRepository references;"));
        assertTrue(foreignMutation("private StorageFilePort files;"));
        assertFalse(source.contains("Continue without Product"));
    }
}
