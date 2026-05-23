package com.example.platform.render.infrastructure.natron;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Resolves the absolute path to {@code natron/poc-render.sh} for {@link ProcessToolRunner}.
 */
@Component
public class NatronPocScriptResolver {

    private static final Logger log = LoggerFactory.getLogger(NatronPocScriptResolver.class);
    private static final String CLASSPATH_SCRIPT = "natron/poc-render.sh";

    public String resolve(NatronRenderProviderProperties properties, String storageRoot) throws Exception {
        if (properties.getPocScriptPath() != null && !properties.getPocScriptPath().isBlank()) {
            Path configured = Path.of(properties.getPocScriptPath());
            if (!Files.isRegularFile(configured)) {
                throw new IllegalStateException("Natron POC script not found: " + configured);
            }
            return configured.toAbsolutePath().toString();
        }

        Path targetDir = Path.of(storageRoot, "natron", "bin");
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve("poc-render.sh");
        ClassPathResource resource = new ClassPathResource(CLASSPATH_SCRIPT);
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.setPosixFilePermissions(target, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException e) {
            log.debug("Could not set POSIX permissions on POC script: {}", e.getMessage());
            target.toFile().setExecutable(true);
        }
        log.info("Synced Natron POC script to {}", target);
        return target.toAbsolutePath().toString();
    }
}
