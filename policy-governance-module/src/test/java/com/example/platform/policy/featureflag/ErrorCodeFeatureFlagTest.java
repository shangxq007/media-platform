package com.example.platform.policy.featureflag;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeFeatureFlagTest {

    @Test
    void featureFlagNotFoundErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-404-001", 404701,
                Map.of("en", "Feature flag not found", "zh", "功能标志不存在"),
                "feature-flag", 404);
        assertEquals("FF-404-001", errorCode.code());
        assertEquals(404701, errorCode.numericCode());
        assertEquals(404, errorCode.status());
        assertEquals("Feature flag not found", errorCode.message("en"));
        assertEquals("功能标志不存在", errorCode.message("zh"));
    }

    @Test
    void featureFlagDisabledErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-400-002", 400702,
                Map.of("en", "Feature flag is disabled", "zh", "功能标志已禁用"),
                "feature-flag", 400);
        assertEquals("FF-400-002", errorCode.code());
        assertEquals(400, errorCode.status());
    }

    @Test
    void featureFlagEvaluationFailedErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-500-001", 500701,
                Map.of("en", "Feature flag evaluation failed", "zh", "功能标志评估失败"),
                "feature-flag", 500);
        assertEquals("FF-500-001", errorCode.code());
        assertEquals(500, errorCode.status());
    }

    @Test
    void featureFlagProviderUnavailableErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-503-001", 503701,
                Map.of("en", "Feature flag provider unavailable", "zh", "功能标志提供者不可用"),
                "feature-flag", 503);
        assertEquals("FF-503-001", errorCode.code());
        assertEquals(503, errorCode.status());
    }

    @Test
    void featureFlagContextInvalidErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-400-003", 400703,
                Map.of("en", "Feature flag context is invalid", "zh", "功能标志上下文无效"),
                "feature-flag", 400);
        assertEquals("FF-400-003", errorCode.code());
        assertEquals(400, errorCode.status());
    }

    @Test
    void featureFlagRuleInvalidErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-400-004", 400704,
                Map.of("en", "Feature flag targeting rule is invalid", "zh", "功能标志目标规则无效"),
                "feature-flag", 400);
        assertEquals("FF-400-004", errorCode.code());
        assertEquals(400, errorCode.status());
    }

    @Test
    void featureFlagVariantInvalidErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-400-005", 400705,
                Map.of("en", "Feature flag variant is invalid", "zh", "功能标志变体无效"),
                "feature-flag", 400);
        assertEquals("FF-400-005", errorCode.code());
        assertEquals(400, errorCode.status());
    }

    @Test
    void featureFlagRolloutInvalidErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-400-006", 400706,
                Map.of("en", "Feature flag rollout percentage is invalid", "zh", "功能标志灰度百分比无效"),
                "feature-flag", 400);
        assertEquals("FF-400-006", errorCode.code());
        assertEquals(400, errorCode.status());
    }

    @Test
    void featureFlagAccessDeniedErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-403-001", 403701,
                Map.of("en", "Feature flag access denied", "zh", "功能标志访问被拒绝"),
                "feature-flag", 403);
        assertEquals("FF-403-001", errorCode.code());
        assertEquals(403, errorCode.status());
    }

    @Test
    void featureFlagOperationNotAllowedErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-403-002", 403702,
                Map.of("en", "Feature flag operation not allowed", "zh", "功能标志操作不允许"),
                "feature-flag", 403);
        assertEquals("FF-403-002", errorCode.code());
        assertEquals(403, errorCode.status());
    }

    @Test
    void openFeatureInitFailedErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-500-002", 500702,
                Map.of("en", "OpenFeature initialization failed", "zh", "OpenFeature 初始化失败"),
                "feature-flag", 500);
        assertEquals("FF-500-002", errorCode.code());
        assertEquals(500, errorCode.status());
    }

    @Test
    void policyFeatureFlagDeniedErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-403-003", 403703,
                Map.of("en", "Policy denied by feature flag", "zh", "策略被功能标志拒绝"),
                "feature-flag", 403);
        assertEquals("FF-403-003", errorCode.code());
        assertEquals(403, errorCode.status());
    }

    @Test
    void navigationDisabledByFeatureFlagErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-403-004", 403704,
                Map.of("en", "Navigation disabled by feature flag", "zh", "导航被功能标志禁用"),
                "feature-flag", 403);
        assertEquals("FF-403-004", errorCode.code());
        assertEquals(403, errorCode.status());
    }

    @Test
    void platformExceptionWithFeatureFlagErrorCode() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-404-001", 404701,
                Map.of("en", "Feature flag not found", "zh", "功能标志不存在"),
                "feature-flag", 404);
        PlatformException exception = new PlatformException(
                errorCode, "Flag not found: my-flag",
                Map.of("flagKey", "my-flag"), "en");

        assertEquals(errorCode, exception.getErrorCode());
        assertEquals("Flag not found: my-flag", exception.getMessage());
        assertEquals("Feature flag not found", exception.getLocalizedMessage());
        assertEquals("en", exception.getLocale());
        assertEquals(Map.of("flagKey", "my-flag"), exception.getDetails());
    }

    @Test
    void platformExceptionWithFeatureFlagErrorCodeZhLocale() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "FF-404-001", 404701,
                Map.of("en", "Feature flag not found", "zh", "功能标志不存在"),
                "feature-flag", 404);
        PlatformException exception = new PlatformException(
                errorCode, "Flag not found",
                Map.of(), "zh");

        assertEquals("功能标志不存在", exception.getLocalizedMessage());
    }

    @Test
    void allFeatureFlagErrorCodesHaveUniqueNumericCodes() {
        ConfigurableErrorCode[] codes = {
                ffCode("FF-404-001", 404701),
                ffCode("FF-400-002", 400702),
                ffCode("FF-500-001", 500701),
                ffCode("FF-503-001", 503701),
                ffCode("FF-400-003", 400703),
                ffCode("FF-400-004", 400704),
                ffCode("FF-400-005", 400705),
                ffCode("FF-400-006", 400706),
                ffCode("FF-403-001", 403701),
                ffCode("FF-403-002", 403702),
                ffCode("FF-500-002", 500702),
                ffCode("FF-403-003", 403703),
                ffCode("FF-403-004", 403704)
        };
        long uniqueCount = java.util.Arrays.stream(codes)
                .mapToInt(ConfigurableErrorCode::numericCode)
                .distinct()
                .count();
        assertEquals(codes.length, uniqueCount);
    }

    @Test
    void allFeatureFlagErrorCodesHaveEnAndZhMessages() {
        ConfigurableErrorCode[] codes = {
                ffCode("FF-404-001", 404701),
                ffCode("FF-400-002", 400702),
                ffCode("FF-500-001", 500701),
                ffCode("FF-503-001", 503701),
                ffCode("FF-400-003", 400703),
                ffCode("FF-400-004", 400704),
                ffCode("FF-400-005", 400705),
                ffCode("FF-400-006", 400706),
                ffCode("FF-403-001", 403701),
                ffCode("FF-403-002", 403702),
                ffCode("FF-500-002", 500702),
                ffCode("FF-403-003", 403703),
                ffCode("FF-403-004", 403704)
        };
        for (ConfigurableErrorCode code : codes) {
            assertTrue(code.messages().containsKey("en"),
                    "Missing en message for " + code.code());
            assertTrue(code.messages().containsKey("zh"),
                    "Missing zh message for " + code.code());
        }
    }

    @Test
    void allFeatureFlagErrorCodesBelongToFeatureFlagModule() {
        ConfigurableErrorCode code = ffCode("FF-404-001", 404701);
        assertEquals("feature-flag", code.module());
    }

    @Test
    void errorCodeImplementsInterface() {
        ErrorCode code = ffCode("FF-404-001", 404701);
        assertNotNull(code.code());
        assertNotNull(code.title());
        assertTrue(code.status() >= 400 && code.status() < 600);
    }

    @Test
    void errorCodeMessageFallbackToEn() {
        ConfigurableErrorCode code = new ConfigurableErrorCode(
                "FF-TEST", 999999,
                Map.of("en", "English message"),
                "feature-flag", 400);
        assertEquals("English message", code.message("fr"));
        assertEquals("English message", code.message("de"));
    }

    @Test
    void errorCodeMessageFallbackToCode() {
        ConfigurableErrorCode code = new ConfigurableErrorCode(
                "FF-TEST", 999999,
                null, "feature-flag", 400);
        assertEquals("FF-TEST", code.message("en"));
    }

    @Test
    void errorCodeDefaultMessageIsEnglish() {
        ConfigurableErrorCode code = ffCode("FF-404-001", 404701);
        assertEquals(code.message("en"), code.message());
    }

    private static ConfigurableErrorCode ffCode(String code, int numericCode) {
        int status = switch (code) {
            case "FF-404-001" -> 404;
            case "FF-400-002" -> 400;
            case "FF-500-001" -> 500;
            case "FF-503-001" -> 503;
            case "FF-400-003" -> 400;
            case "FF-400-004" -> 400;
            case "FF-400-005" -> 400;
            case "FF-400-006" -> 400;
            case "FF-403-001" -> 403;
            case "FF-403-002" -> 403;
            case "FF-500-002" -> 500;
            case "FF-403-003" -> 403;
            case "FF-403-004" -> 403;
            default -> 400;
        };
        return new ConfigurableErrorCode(
                code, numericCode,
                Map.of("en", "English message", "zh", "中文消息"),
                "feature-flag", status);
    }
}
