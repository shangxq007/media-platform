package com.example.platform.media;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaAuthorityBoundaryTest {
    static boolean prohibitedRenderSource(String source) {
        return source.contains("com.example.platform.media.app.")
                || source.contains("com.example.platform.media.infrastructure.")
                || source.matches("(?s).*(?:insertInto|update|deleteFrom)\\s*\\(\\s*MEDIA_ASSET\\b.*")
                || source.matches("(?is).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+media_asset\\b.*");
    }
    @Test void renderUsesPublicMediaAndHasNoMediaAssetWriter() throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if(!Files.isDirectory(root.resolve("render-module")))root=root.getParent();
        Path source=root.resolve("render-module/src/main/java");
        try(var files=Files.walk(source)) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList())
                assertFalse(prohibitedRenderSource(Files.readString(file)),file.toString());
        }
        assertFalse(Files.exists(source.resolve("com/example/platform/render/infrastructure/asset/AssetRepository.java")));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.example.platform.render.domain.asset.Asset"));
    }
    @Test void guardsRejectReintroducedForeignWritesAndImplementationImports() {
        for(String source:List.of("dsl.insertInto(MEDIA_ASSET)","dsl.update(MEDIA_ASSET)","dsl.deleteFrom(MEDIA_ASSET)",
                "jdbc.update(\"delete from media_asset where id=?\")", "import com.example.platform.media.app.MediaAssetRepository;",
                "import com.example.platform.media.infrastructure.persistence.JooqMediaAssetRepository;"))
            assertTrue(prohibitedRenderSource(source),source);
        assertFalse(prohibitedRenderSource("import com.example.platform.media.api.MediaAssets;"));
    }
}
