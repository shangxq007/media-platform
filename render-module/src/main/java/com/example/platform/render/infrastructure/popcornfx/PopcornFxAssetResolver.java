package com.example.platform.render.infrastructure.popcornfx;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves pre-baked PopcornFX overlay assets (transparent video / image sequence) on local storage.
 */
@Component
public class PopcornFxAssetResolver {

    private final TimelineScriptParser timelineScriptParser;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public PopcornFxAssetResolver(TimelineScriptParser timelineScriptParser) {
        this.timelineScriptParser = timelineScriptParser;
    }

    public Optional<Path> resolveOverlayPath(Map<String, Object> parameters) {
        if (parameters == null) {
            return Optional.empty();
        }
        Object asset = parameters.get("assetPath");
        if (asset == null || asset.toString().isBlank()) {
            return Optional.empty();
        }
        String uri = asset.toString();
        String local = timelineScriptParser.resolveLocalPath(uri, storageRoot);
        Path path = Path.of(local);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }
}
