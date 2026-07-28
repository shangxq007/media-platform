package com.example.platform.storage.opendal.conformance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal native smoke test — proves OpenDAL Java binding can initialize
 * before running full conformance suite.
 */
class OpenDalNativeSmokeTest {

    @Test void nativeLibraryLoads() {
        // This will throw if native library cannot be loaded
        assertDoesNotThrow(() -> {
            org.apache.opendal.NativeLibrary.loadLibrary();
        });
    }

    @Test void memoryOperatorCanBeCreated() {
        assertDoesNotThrow(() -> {
            var operator = org.apache.opendal.Operator.of("memory", java.util.Map.of());
            assertNotNull(operator);
            operator.close();
        });
    }
}
