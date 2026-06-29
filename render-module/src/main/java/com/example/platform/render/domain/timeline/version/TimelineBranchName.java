package com.example.platform.render.domain.timeline.version;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safe name for a timeline branch.
 * Internal domain model. Rejects storage/provider/internal keywords.
 */
public record TimelineBranchName(String value) {

    private static final Pattern UNSAFE_PATTERN = Pattern.compile("[\\s;|&$`\\\\]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.");
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "providerName", "backendName",
            "executionEnvironment", "autoDispatch", "rawCommand", "processEnvironment");

    public TimelineBranchName {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineBranchName must not be blank");
        if (UNSAFE_PATTERN.matcher(value).find())
            throw new IllegalArgumentException("TimelineBranchName contains unsafe characters");
        if (PATH_TRAVERSAL.matcher(value).find())
            throw new IllegalArgumentException("TimelineBranchName must not contain path traversal");
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (value.contains(keyword))
                throw new IllegalArgumentException("TimelineBranchName must not contain: " + keyword);
        }
    }
}
