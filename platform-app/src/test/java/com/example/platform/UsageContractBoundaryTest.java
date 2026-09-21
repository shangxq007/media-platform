package com.example.platform;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UsageContractBoundaryTest {
    static boolean privateOrSharedContract(String source) {
        return source.contains("com.example.platform.shared.usage")
                || source.contains("com.example.platform.usage.app.")
                || source.contains("com.example.platform.usage.infrastructure.");
    }
    @Test void producersUseOnlyOwnerPublishedObservationContracts() throws Exception {
        Path root=Path.of("").toAbsolutePath();
        while(!Files.exists(root.resolve("AGENTS.md"))) root=root.getParent();
        for(String module:new String[]{"ai-module","extension-module","render-module","storage-module","usage-contract-module"}) {
            try(var files=Files.walk(root.resolve(module+"/src/main/java"))) {
                for(Path file:files.filter(p->p.toString().endsWith(".java")).toList())
                    assertFalse(privateOrSharedContract(Files.readString(file)),file.toString());
            }
        }
        assertFalse(Files.exists(root.resolve("shared-kernel/src/main/java/com/example/platform/shared/usage")));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.example.platform.shared.usage.ObservedRuntimeUsageEmissionPort"));
    }
    @Test void retiredImportsAndPrivateIngestionPathsAreRejected() {
        assertTrue(privateOrSharedContract("import com.example.platform.shared.usage.UsageUnit;"));
        assertTrue(privateOrSharedContract("import com.example.platform.usage.app.ObservedRuntimeUsageEmissionService;"));
        assertTrue(privateOrSharedContract("import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;"));
        assertFalse(privateOrSharedContract("import com.example.platform.usage.api.ObservedRuntimeUsageEmissionPort;"));
    }
}
