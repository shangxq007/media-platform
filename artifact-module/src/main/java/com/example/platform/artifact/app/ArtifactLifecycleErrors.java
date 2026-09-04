package com.example.platform.artifact.app;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.util.Map;

/** Error information owned by the Artifact lifecycle bounded context. */
final class ArtifactLifecycleErrors {
    private static final ConfigurableErrorCode NOT_FOUND = code("ARTIFACT-404-001", 404501, 404,
            "Artifact not found in catalog", "制品目录中未找到该制品");
    private static final ConfigurableErrorCode STILL_REFERENCED = code("ARTIFACT-409-001", 409501, 409,
            "Artifact is still referenced and cannot be purged", "制品仍被引用，无法清除");
    private static final ConfigurableErrorCode TOMBSTONED = code("ARTIFACT-410-001", 410501, 410,
            "Artifact has been tombstoned", "制品已标记删除（tombstone）");

    private ArtifactLifecycleErrors() {}

    static PlatformException notFound(String artifactId) { return exception(NOT_FOUND, "artifactId", artifactId); }
    static PlatformException stillReferenced(String artifactId) { return exception(STILL_REFERENCED, "artifactId", artifactId); }
    static PlatformException tombstoned(String artifactId) { return exception(TOMBSTONED, "artifactId", artifactId); }

    private static PlatformException exception(ConfigurableErrorCode code, String detailKey, String detailValue) {
        return new PlatformException(code, detailValue, Map.of(detailKey, detailValue), "en");
    }

    private static ConfigurableErrorCode code(String value, int numericCode, int status, String en, String zh) {
        return new ConfigurableErrorCode(value, numericCode, Map.of("en", en, "zh", zh), "artifact", status);
    }
}
