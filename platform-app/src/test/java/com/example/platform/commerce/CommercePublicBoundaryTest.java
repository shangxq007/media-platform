package com.example.platform.commerce;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommercePublicBoundaryTest {
    static boolean violation(String path, String source) {
        boolean payment = path.contains("/payment/") || path.startsWith("payment-module/");
        if (payment && (source.contains("com.example.platform.commerce.app.")
                || source.contains("com.example.platform.commerce.infrastructure."))) return true;
        boolean foreign = !path.startsWith("commerce-module/") && !path.startsWith("typed-schema-module/");
        return foreign && (source.matches("(?is).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+(?:commerce_product|commercial_offering|product_catalog_command)\\b.*")
                || source.matches("(?s).*\\.(?:insertInto|update|deleteFrom)\\((?:[\\w.]*)(?:COMMERCE_PRODUCT|COMMERCIAL_OFFERING|PRODUCT_CATALOG_COMMAND)\\b.*")
                || source.contains("com.example.platform.commerce.infrastructure.ProductCatalogJdbcRepository"));
    }
    @Test void foreignCatalogWritersAndPaymentInternalImportsAreAbsent() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(p->p.toString().contains("/src/main/java/")&&p.toString().endsWith(".java")).toList())
                assertFalse(violation(root.relativize(file).toString(),Files.readString(file)),file.toString());
        }
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.example.platform.commerce.app.CheckoutPaymentPort"));
    }
    @Test void negativeControlsDistinguishCommerceCatalogFromTechnicalRenderProducts() {
        assertTrue(violation("payment-module/src/main/java/X.java","import com.example.platform.commerce.app.CheckoutPaymentPort;"));
        assertTrue(violation("platform-app/src/main/java/com/example/platform/payment/X.java","com.example.platform.commerce.app.CheckoutOrchestrator"));
        assertTrue(violation("render-module/src/main/java/X.java","jdbc.update(\"UPDATE commerce_product SET lifecycle_state=?\");"));
        assertTrue(violation("render-module/src/main/java/X.java","dsl.insertInto(COMMERCE_PRODUCT).execute();"));
        assertFalse(violation("render-module/src/main/java/X.java","dsl.insertInto(PRODUCT).execute();"));
        assertFalse(violation("commerce-module/src/main/java/X.java","INSERT INTO commerce_product"));
    }
}
