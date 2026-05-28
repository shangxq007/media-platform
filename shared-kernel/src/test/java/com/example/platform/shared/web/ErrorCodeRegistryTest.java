package com.example.platform.shared.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests for ErrorCodeRegistry to ensure error code integrity:
 * - All codes are globally unique
 * - All numericCodes are globally unique
 * - HTTP status in code prefix matches actual status
 * - Required fields are present
 */
class ErrorCodeRegistryTest {

    private final ErrorCodeRegistry registry = new ErrorCodeRegistry();

    {
        registry.loadErrorCodes();
    }

    @Test
    void allCodesAreGloballyUnique() {
        Map<String, ConfigurableErrorCode> codes = registry.getAllErrorCodes();
        Set<String> seen = new HashSet<>();
        for (String code : codes.keySet()) {
            assertTrue(seen.add(code), "Duplicate error code: " + code);
        }
        assertTrue(codes.size() > 0, "Error codes should not be empty");
    }

    @Test
    void allNumericCodesAreGloballyUnique() {
        Map<String, ConfigurableErrorCode> codes = registry.getAllErrorCodes();
        Set<Integer> seen = new HashSet<>();
        for (ConfigurableErrorCode ec : codes.values()) {
            int nc = ec.numericCode();
            if (nc > 0) {
                assertTrue(seen.add(nc), "Duplicate numericCode " + nc + " for code: " + ec.code());
            }
        }
    }

    @Test
    void httpStatusInCodePrefixMatchesActualStatus() {
        Map<String, ConfigurableErrorCode> codes = registry.getAllErrorCodes();
        for (ConfigurableErrorCode ec : codes.values()) {
            String code = ec.code();
            String[] parts = code.split("-");
            if (parts.length == 3) {
                try {
                    int codeHttp = Integer.parseInt(parts[1]);
                    int actualStatus = ec.status();
                    assertEquals(codeHttp, actualStatus,
                            "HTTP status mismatch for " + code + ": code prefix says " + codeHttp + " but status=" + actualStatus);
                } catch (NumberFormatException ignored) {
                    // Skip codes with non-numeric HTTP segment
                }
            }
        }
    }

    @Test
    void allErrorCodesHaveRequiredFields() {
        Map<String, ConfigurableErrorCode> codes = registry.getAllErrorCodes();
        for (ConfigurableErrorCode ec : codes.values()) {
            assertNotNull(ec.code(), "Code should not be null");
            assertFalse(ec.code().isBlank(), "Code should not be blank");
            assertTrue(ec.numericCode() > 0, "numericCode should be positive for: " + ec.code());
            assertTrue(ec.status() >= 400 && ec.status() < 600,
                    "status should be 4xx or 5xx for: " + ec.code() + ", got: " + ec.status());
            assertNotNull(ec.messages(), "messages should not be null for: " + ec.code());
            assertFalse(ec.messages().isEmpty(), "messages should not be empty for: " + ec.code());
        }
    }

    @Test
    void storageErrorCodesExist() {
        assertNotNull(registry.getErrorCode("STORAGE-400-001"),
                "STORAGE-400-001 should exist");
        assertNotNull(registry.getErrorCode("STORAGE-404-001"),
                "STORAGE-404-001 should exist");
        assertNotNull(registry.getErrorCode("STORAGE-403-001"),
                "STORAGE-403-001 should exist");
    }

    @Test
    void commonErrorCodesExist() {
        assertNotNull(registry.getErrorCode("COMMON-400-001"));
        assertNotNull(registry.getErrorCode("COMMON-404-001"));
        assertNotNull(registry.getErrorCode("COMMON-500-001"));
    }

    @Test
    void totalErrorCodeCountIsReasonable() {
        Map<String, ConfigurableErrorCode> codes = registry.getAllErrorCodes();
        assertTrue(codes.size() >= 50, "Should have at least 50 error codes");
        assertTrue(codes.size() <= 500, "Should not have more than 500 error codes");
    }
}
