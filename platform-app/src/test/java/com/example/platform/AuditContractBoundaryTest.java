package com.example.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source boundary complements the PostgreSQL application-assembly test. */
class AuditContractBoundaryTest {
    static boolean retiredReference(String code) {
        return code.contains("com.example.platform.shared.audit")
                || code.contains("com.example.platform.shared.asset")
                || code.contains("S3AssetDownloadUrlPort")
                || code.contains("AssetDownloadUrlPort");
    }

    @Test void allProductionCallersUsePublishedOwnerContracts() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("AGENTS.md"))) root = root.getParent();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().contains("/src/main/java/")
                    && p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);
                assertFalse(retiredReference(code), path.toString());
                if (!path.toString().contains("/audit-compliance-module/")) {
                    assertFalse(code.contains("import com.example.platform.audit.app.AuditPortAdapter"), path.toString());
                    assertFalse(code.contains("import com.example.platform.audit.app.AdminAuditPublisherImpl"), path.toString());
                }
            }
        }
    }

    @Test void reintroductionIsRejectedWithoutBanningCanonicalContracts() {
        assertTrue(retiredReference("package com.example.platform.shared.audit; interface AuditPort {}"));
        assertTrue(retiredReference("import com.example.platform.shared.asset.StorageUriReferenceHit;"));
        assertTrue(retiredReference("class S3AssetDownloadUrlPort {}"));
        assertTrue(retiredReference("interface AssetDownloadUrlPort {}"));
        assertFalse(retiredReference("import com.example.platform.auditcontract.api.AuditPort;"));
        assertFalse(retiredReference("import com.example.platform.storage.contract.StorageUriReferenceHit;"));
    }
}
