package com.example.platform.render.domain.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

import org.springframework.stereotype.Component;

/**
 * Validator for CaptionTemplateRenderRequest.
 *
 * <p>Internal domain validator — validates request contract before mapping
 * to the existing PLAN_BASED render flow.</p>
 */
@Component
public class CaptionTemplateRenderContractValidator {

    private static final int MAX_SEGMENTS = 1000;
    private static final int MAX_TEXT_LENGTH = 10000;
    private static final int MAX_FONT_SIZE = 200;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_OUTLINE_WIDTH = 10;
    private static final int MAX_LINES = 10;
    private static final double MIN_LINE_HEIGHT = 0.5;
    private static final double MAX_LINE_HEIGHT = 3.0;
    private static final long MAX_SEGMENT_DURATION_MS = 600_000; // 10 minutes

    private static final Set<String> ALLOWED_FONTS = Set.of(
            "DejaVu Sans", "DejaVu Serif", "Liberation Sans", "Liberation Serif",
            "Noto Sans", "Noto Serif", "Arial", "Helvetica", "Times New Roman",
            "sans-serif", "serif", "monospace");

    private static final Set<String> ALLOWED_PLACEMENTS = Set.of(
            "BOTTOM_CENTER", "TOP_CENTER", "CENTER");

    private static final Pattern SCRIPT_TAG = Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{\\\\[a-zA-Z]");
    private static final Pattern LOCAL_PATH = Pattern.compile("(/tmp/|/home/|/var/|[A-Za-z]:\\\\)");
    private static final Pattern REMOTE_URL = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);

    /**
     * Validate a caption template render request.
     */
    public CaptionTemplateValidationResult validate(CaptionTemplateRenderRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Request must not be null");
            return new CaptionTemplateValidationResult(false, errors);
        }

        // Project ID
        if (request.projectId() == null || request.projectId().isBlank()) {
            errors.add("projectId is required");
        }

        // Source product ID
        if (request.sourceProductId() == null || request.sourceProductId().isBlank()) {
            errors.add("sourceProductId is required");
        }

        // Caption segments
        if (request.captionSegments() == null || request.captionSegments().isEmpty()) {
            errors.add("At least one caption segment is required");
        } else {
            validateSegments(request.captionSegments(), errors);
        }

        // Template/style
        if (request.template() != null && request.template().style() != null) {
            validateStyle(request.template().style(), errors);
        }

        // Output profile
        if (request.outputProfile() != null) {
            validateOutputProfile(request.outputProfile(), errors);
        }

        return new CaptionTemplateValidationResult(errors.isEmpty(), errors);
    }

    private void validateSegments(List<CaptionSegmentSpec> segments, List<String> errors) {
        if (segments.size() > MAX_SEGMENTS) {
            errors.add("Too many segments: " + segments.size() + " (max " + MAX_SEGMENTS + ")");
        }

        for (int i = 0; i < segments.size(); i++) {
            CaptionSegmentSpec seg = segments.get(i);
            String prefix = "Segment[" + i + "]: ";

            if (seg.startMs() < 0) {
                errors.add(prefix + "startMs must be >= 0");
            }
            if (seg.endMs() <= seg.startMs()) {
                errors.add(prefix + "endMs must be > startMs");
            }
            if (seg.endMs() - seg.startMs() > MAX_SEGMENT_DURATION_MS) {
                errors.add(prefix + "duration exceeds maximum");
            }
            if (seg.text() == null || seg.text().isBlank()) {
                errors.add(prefix + "text must not be blank");
            } else {
                validateText(seg.text(), prefix, errors);
            }
        }
    }

    private void validateText(String text, String prefix, List<String> errors) {
        if (text.length() > MAX_TEXT_LENGTH) {
            errors.add(prefix + "text too long (" + text.length() + " max " + MAX_TEXT_LENGTH + ")");
        }
        if (SCRIPT_TAG.matcher(text).find()) {
            errors.add(prefix + "script tags not allowed");
        }
        if (ASS_OVERRIDE.matcher(text).find()) {
            errors.add(prefix + "ASS override codes not allowed in text");
        }
        if (LOCAL_PATH.matcher(text).find()) {
            errors.add(prefix + "local paths not allowed in text");
        }
    }

    private void validateStyle(CaptionStyleSpec style, List<String> errors) {
        // Font
        if (style.font() != null) {
            if (style.font().family() != null && !ALLOWED_FONTS.contains(style.font().family())) {
                errors.add("Font family not allowed: " + style.font().family());
            }
            if (style.font().outlineWidth() < 0 || style.font().outlineWidth() > MAX_OUTLINE_WIDTH) {
                errors.add("Outline width out of range");
            }
            if (style.font().color() != null && !isValidHexColor(style.font().color())) {
                errors.add("Invalid font color: " + style.font().color());
            }
            if (style.font().outlineColor() != null && !isValidHexColor(style.font().outlineColor())) {
                errors.add("Invalid outline color");
            }
        }

        // Placement
        if (style.placement() != null && !ALLOWED_PLACEMENTS.contains(style.placement().name())) {
            errors.add("Unknown placement: " + style.placement());
        }

        // Font size
        if (style.fontSize() < MIN_FONT_SIZE || style.fontSize() > MAX_FONT_SIZE) {
            errors.add("Font size out of range (" + MIN_FONT_SIZE + "-" + MAX_FONT_SIZE + ")");
        }

        // Max lines
        if (style.maxLines() < 1 || style.maxLines() > MAX_LINES) {
            errors.add("maxLines out of range (1-" + MAX_LINES + ")");
        }

        // Line height
        if (style.lineHeight() < MIN_LINE_HEIGHT || style.lineHeight() > MAX_LINE_HEIGHT) {
            errors.add("lineHeight out of range (" + MIN_LINE_HEIGHT + "-" + MAX_LINE_HEIGHT + ")");
        }
    }

    private void validateOutputProfile(CaptionOutputProfileSpec profile, List<String> errors) {
        if (profile.width() <= 0 || profile.height() <= 0) {
            errors.add("Output dimensions must be positive");
        }
        if (profile.fps() <= 0) {
            errors.add("FPS must be positive");
        }
        if (profile.container() != null
                && !profile.container().equals("mp4")
                && !profile.container().equals("webm")) {
            errors.add("Unsupported container: " + profile.container());
        }
    }

    private boolean isValidHexColor(String color) {
        return color != null && color.matches("^#[0-9a-fA-F]{6}$");
    }
}
