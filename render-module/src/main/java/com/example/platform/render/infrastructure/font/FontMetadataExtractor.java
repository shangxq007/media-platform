package com.example.platform.render.infrastructure.font;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public interface FontMetadataExtractor {

    String extractorName();

    default boolean enabled() { return true; }

    FontMetadata extract(Path fontFile);

    FontMetadata extract(InputStream fontData, String fileName);
}
