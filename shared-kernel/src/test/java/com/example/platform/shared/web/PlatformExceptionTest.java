package com.example.platform.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformExceptionTest {

    @BeforeEach
    void setUp() {
        // Registry loads from error-codes.json in test resources
    }

    @Test
    void platformExceptionHasErrorCode() {
        ErrorCode code = new ConfigurableErrorCode("SUBTITLE-400-001", 400201,
                Map.of("en", "Subtitle parsing failed", "zh", "字幕解析失败"),
                "subtitle", 400);
        PlatformException ex = new PlatformException(code, "Failed to parse SRT");

        assertEquals("SUBTITLE-400-001", ex.getErrorCode().code());
        assertEquals(400, ex.getErrorCode().status());
    }

    @Test
    void configurableErrorCodeReturnsLocalizedMessage() {
        ConfigurableErrorCode code = new ConfigurableErrorCode("SUBTITLE-400-001", 400201,
                Map.of("en", "Subtitle parsing failed", "zh", "字幕解析失败"),
                "subtitle", 400);

        assertEquals("Subtitle parsing failed", code.message("en"));
        assertEquals("字幕解析失败", code.message("zh"));
        assertEquals("Subtitle parsing failed", code.message()); // default en
    }

    @Test
    void platformExceptionReturnsLocalizedMessage() {
        ConfigurableErrorCode code = new ConfigurableErrorCode("SUBTITLE-400-001", 400201,
                Map.of("en", "Subtitle parsing failed", "zh", "字幕解析失败"),
                "subtitle", 400);
        PlatformException ex = new PlatformException(code, "detail", null, "zh");

        assertEquals("字幕解析失败", ex.getLocalizedMessage());
    }

    @Test
    void platformExceptionHasDetails() {
        ConfigurableErrorCode code = new ConfigurableErrorCode("RENDER-500-001", 500101,
                Map.of("en", "Render failed"), "render", 500);
        Map<String, Object> details = Map.of("jobId", "job-123", "reason", "provider unavailable");
        PlatformException ex = new PlatformException(code, "Render failed", details, "en");

        assertNotNull(ex.getDetails());
        assertEquals("job-123", ex.getDetails().get("jobId"));
    }

    @Test
    void commonErrorCodesWork() {
        assertEquals("COMMON-400-001", CommonErrorCode.INVALID_REQUEST.code());
        assertEquals(400, CommonErrorCode.INVALID_REQUEST.status());
        assertEquals("COMMON-404-001", CommonErrorCode.RESOURCE_NOT_FOUND.code());
        assertEquals(404, CommonErrorCode.RESOURCE_NOT_FOUND.status());
    }

    @Test
    void errorCodeRegistryLoadsFromConfig() {
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();

        assertTrue(registry.getErrorCode("SUBTITLE-400-001").isPresent());
        assertTrue(registry.getErrorCode("COMMON-400-001").isPresent());
        assertTrue(registry.getErrorCode("RENDER-500-001").isPresent());
        assertTrue(registry.getErrorCode("nonexistent").isEmpty());

        ConfigurableErrorCode code = registry.getRequiredErrorCode("SUBTITLE-400-001");
        assertEquals("SUBTITLE-400-001", code.code());
        assertEquals("subtitle", code.module());
    }
}
