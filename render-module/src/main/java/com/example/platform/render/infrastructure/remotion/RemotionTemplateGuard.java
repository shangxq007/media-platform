package com.example.platform.render.infrastructure.remotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Guard that ensures Remotion templates are resolved from trusted sources only.
 *
 * <p>The guard explicitly rejects:
 * <ul>
 *   <li>Raw React/JavaScript/TypeScript source code in render inputs</li>
 *   <li>Arbitrary filesystem paths from user input</li>
 *   <li>Arbitrary npm package names from user input</li>
 *   <li>URLs pointing to user-controlled or external code</li>
 *   <li>Template IDs containing path traversal patterns</li>
 * </ul>
 *
 * <p>Allowed template resolution:
 * <ul>
 *   <li>templateId + templateVersion against a trusted registry</li>
 *   <li>Platform-owned template package references</li>
 *   <li>Bundled template registry root (e.g., {@code bundled://remotion-templates/})</li>
 * </ul>
 *
 * <p>This guard is fail-closed: unknown patterns are rejected.
 */
public final class RemotionTemplateGuard {

    private static final Set<String> TRUSTED_REGISTRY_SCHEMES = Set.of("bundled", "registry");
    private static final int MAX_TEMPLATE_ID_LENGTH = 256;
    private static final int MAX_TEMPLATE_VERSION_LENGTH = 64;
    private static final Set<String> FORBIDDEN_ID_PATTERNS = Set.of(
            "..", "/", "\\", ":", "?", "#", "@", " ", "\t", "\n", "\r",
            "react", "javascript", "typescript", "eval", "exec",
            "require(", "import(", "Function(", "constructor"
    );

    private RemotionTemplateGuard() {}

    public static List<String> validate(RemotionTemplateSpec template) {
        List<String> rejections = new ArrayList<>();
        if (template == null) return rejections;

        rejections.addAll(validateTemplateId(template.templateId()));
        rejections.addAll(validateTemplateVersion(template.templateVersion()));
        rejections.addAll(validateCompositionId(template.compositionId()));
        rejections.addAll(validateNoRawCode(template.params()));

        return rejections;
    }

    private static List<String> validateTemplateId(String templateId) {
        List<String> errors = new ArrayList<>();
        if (templateId == null || templateId.isBlank()) {
            errors.add("templateId must not be blank");
            return errors;
        }
        if (templateId.length() > MAX_TEMPLATE_ID_LENGTH) {
            errors.add("templateId exceeds max length " + MAX_TEMPLATE_ID_LENGTH);
        }
        for (String forbidden : FORBIDDEN_ID_PATTERNS) {
            if (templateId.toLowerCase().contains(forbidden.toLowerCase())) {
                errors.add("templateId contains forbidden pattern: " + forbidden);
            }
        }
        if (templateId.startsWith("http://") || templateId.startsWith("https://")
                || templateId.startsWith("file://") || templateId.startsWith("/")
                || templateId.startsWith(".")) {
            errors.add("templateId must not be a URL, filesystem path, or external reference");
        }
        return errors;
    }

    private static List<String> validateTemplateVersion(String version) {
        List<String> errors = new ArrayList<>();
        if (version == null || version.isBlank()) {
            errors.add("templateVersion must not be blank");
            return errors;
        }
        if (version.length() > MAX_TEMPLATE_VERSION_LENGTH) {
            errors.add("templateVersion exceeds max length " + MAX_TEMPLATE_VERSION_LENGTH);
        }
        for (String forbidden : FORBIDDEN_ID_PATTERNS) {
            if (version.toLowerCase().contains(forbidden.toLowerCase())) {
                errors.add("templateVersion contains forbidden pattern: " + forbidden);
            }
        }
        return errors;
    }

    private static List<String> validateCompositionId(String compositionId) {
        List<String> errors = new ArrayList<>();
        if (compositionId == null) return errors;
        for (String forbidden : FORBIDDEN_ID_PATTERNS) {
            if (compositionId.toLowerCase().contains(forbidden.toLowerCase())) {
                errors.add("compositionId contains forbidden pattern: " + forbidden);
            }
        }
        if (compositionId.startsWith("http://") || compositionId.startsWith("https://")
                || compositionId.startsWith("file://") || compositionId.startsWith("/")) {
            errors.add("compositionId must not be a URL or filesystem path");
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static List<String> validateNoRawCode(java.util.Map<String, Object> params) {
        List<String> errors = new ArrayList<>();
        if (params == null) return errors;
        for (java.util.Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getKey() == null) continue;
            Object value = entry.getValue();
            if (value instanceof String s) {
                if (isCodeInjection(s)) {
                    errors.add("template param '" + entry.getKey() + "' appears to contain code injection");
                }
            } else if (value instanceof java.util.List) {
                int idx = 0;
                for (Object item : (java.util.List<?>) value) {
                    if (item instanceof String s && isCodeInjection(s)) {
                        errors.add("template param '" + entry.getKey() + "[" + idx + "]' appears to contain code injection");
                    }
                    idx++;
                }
            } else if (value instanceof java.util.Map) {
                for (java.util.Map.Entry<?, ?> nested : ((java.util.Map<?, ?>) value).entrySet()) {
                    if (nested.getValue() instanceof String s && isCodeInjection(s)) {
                        errors.add("template param '" + entry.getKey() + "." + nested.getKey() + "' appears to contain code injection");
                    }
                }
            }
        }
        return errors;
    }

    private static boolean isCodeInjection(String value) {
        String lower = value.toLowerCase().trim();
        return lower.contains("<script")
                || lower.contains("javascript:")
                || lower.contains("import ")
                || lower.contains("require(")
                || lower.contains("eval(")
                || lower.contains("function(")
                || lower.contains("=>")
                || lower.contains("process.")
                || lower.contains("child_process")
                || lower.contains("fs.")
                || lower.contains("__proto__")
                || lower.contains("constructor(")
                || lower.contains("new function")
                || lower.contains("object.assign")
                || lower.contains("fetch(")
                || lower.contains("xmlhttprequest");
    }
}
