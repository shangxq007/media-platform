package com.example.platform.render.infrastructure.natron;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NatronRendererBinaryResolver {

    public static final String TOOL_KEY = "natron-renderer";

    public Optional<String> resolveAbsolutePath(NatronRenderProviderProperties properties) {
        String configured = properties.getRendererBinary();
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(configured);
        if (path.isAbsolute() && Files.isExecutable(path)) {
            return Optional.of(path.toString());
        }
        for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, configured);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate.toAbsolutePath().toString());
            }
        }
        return Optional.empty();
    }
}
