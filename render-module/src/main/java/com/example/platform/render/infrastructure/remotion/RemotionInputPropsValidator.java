package com.example.platform.render.infrastructure.remotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Schema validation for Remotion input props.
 *
 * <p>Ensures Remotion render inputs are declarative, schema-validated,
 * and safe before dispatch. Used by RemotionRenderCommandBuilder and
 * RemotionRenderProvider before rendering.
 */
public final class RemotionInputPropsValidator {

    private static final int MIN_WIDTH = 1;
    private static final int MAX_WIDTH = 7680;
    private static final int MIN_HEIGHT = 1;
    private static final int MAX_HEIGHT = 4320;
    private static final int MIN_FPS = 1;
    private static final int MAX_FPS = 120;
    private static final int MIN_DURATION_FRAMES = 1;
    private static final int MAX_DURATION_FRAMES = 1_080_000;
    private static final int MIN_FONT_WEIGHT = 1;
    private static final int MAX_FONT_WEIGHT = 1000;
    private static final Set<String> VALID_OUTPUT_FORMATS = Set.of("mp4", "webm", "mov", "png-sequence", "jpeg-sequence");
    private static final int MAX_CAPTIONS = 5000;
    private static final int MAX_FONT_SPECS = 100;
    private static final int MAX_CAPTION_TEXT_LENGTH = 10_000;
    private static final int MAX_TEMPLATE_PARAMS = 200;
    private static final double MAX_CAPTION_DURATION_SEC = 86_400;

    private RemotionInputPropsValidator() {}

    public static List<String> validate(RemotionInputProps props) {
        List<String> errors = new ArrayList<>();

        if (props == null) {
            errors.add("remotion input props must not be null");
            return errors;
        }

        if (props.compositionWidth() < MIN_WIDTH || props.compositionWidth() > MAX_WIDTH) {
            errors.add("compositionWidth must be between " + MIN_WIDTH + " and " + MAX_WIDTH + ", got: " + props.compositionWidth());
        }
        if (props.compositionHeight() < MIN_HEIGHT || props.compositionHeight() > MAX_HEIGHT) {
            errors.add("compositionHeight must be between " + MIN_HEIGHT + " and " + MAX_HEIGHT + ", got: " + props.compositionHeight());
        }
        if (props.fps() < MIN_FPS || props.fps() > MAX_FPS) {
            errors.add("fps must be between " + MIN_FPS + " and " + MAX_FPS + ", got: " + props.fps());
        }
        if (props.durationInFrames() < MIN_DURATION_FRAMES || props.durationInFrames() > MAX_DURATION_FRAMES) {
            errors.add("durationInFrames must be between " + MIN_DURATION_FRAMES + " and " + MAX_DURATION_FRAMES + ", got: " + props.durationInFrames());
        }
        if (props.outputFormat() != null && !VALID_OUTPUT_FORMATS.contains(props.outputFormat())) {
            errors.add("outputFormat must be one of " + VALID_OUTPUT_FORMATS + ", got: " + props.outputFormat());
        }

        errors.addAll(validateCaptions(props.captions()));
        errors.addAll(validateFontSpecs(props.fontSpecs()));
        errors.addAll(validateTemplate(props.template()));

        return errors;
    }

    private static List<String> validateCaptions(List<RemotionCaption> captions) {
        List<String> errors = new ArrayList<>();
        if (captions == null) return errors;

        if (captions.size() > MAX_CAPTIONS) {
            errors.add("too many captions: " + captions.size() + " (max " + MAX_CAPTIONS + ")");
        }

        for (int i = 0; i < captions.size(); i++) {
            RemotionCaption c = captions.get(i);
            String prefix = "captions[" + i + "]";

            if (c.id() == null || c.id().isBlank()) {
                errors.add(prefix + ".id must not be blank");
            }
            if (c.text() == null) {
                errors.add(prefix + ".text must not be null");
            } else if (c.text().length() > MAX_CAPTION_TEXT_LENGTH) {
                errors.add(prefix + ".text exceeds max length " + MAX_CAPTION_TEXT_LENGTH);
            }
            if (c.startTime() < 0) {
                errors.add(prefix + ".startTime must not be negative, got: " + c.startTime());
            }
            if (c.endTime() <= c.startTime()) {
                errors.add(prefix + ".endTime must be greater than startTime (" + c.startTime() + "), got: " + c.endTime());
            }
            if (c.endTime() - c.startTime() > MAX_CAPTION_DURATION_SEC) {
                errors.add(prefix + ".duration exceeds max " + MAX_CAPTION_DURATION_SEC + "s");
            }
            if (c.style() != null) {
                errors.addAll(validateCaptionStyle(c.style(), prefix + ".style"));
            }
            if (c.words() != null) {
                errors.addAll(validateCaptionWords(c.words(), prefix + ".words"));
            }
        }
        return errors;
    }

    private static List<String> validateCaptionStyle(RemotionCaptionStyle style, String prefix) {
        List<String> errors = new ArrayList<>();
        if (style.fontFamily() != null && style.fontFamily().isBlank()) {
            errors.add(prefix + ".fontFamily must not be blank if provided");
        }
        if (style.fontSize() <= 0) {
            errors.add(prefix + ".fontSize must be positive, got: " + style.fontSize());
        }
        if (style.outlineWidth() < 0) {
            errors.add(prefix + ".outlineWidth must not be negative, got: " + style.outlineWidth());
        }
        if (style.opacity() < 0.0 || style.opacity() > 1.0) {
            errors.add(prefix + ".opacity must be between 0.0 and 1.0, got: " + style.opacity());
        }
        return errors;
    }

    private static List<String> validateCaptionWords(List<RemotionCaptionWord> words, String prefix) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            RemotionCaptionWord w = words.get(i);
            String wPrefix = prefix + "[" + i + "]";
            if (w.text() == null || w.text().isBlank()) {
                errors.add(wPrefix + ".text must not be blank");
            }
            if (w.startTime() < 0) {
                errors.add(wPrefix + ".startTime must not be negative");
            }
            if (w.endTime() <= w.startTime()) {
                errors.add(wPrefix + ".endTime must be greater than startTime");
            }
        }
        return errors;
    }

    private static List<String> validateFontSpecs(List<RemotionFontSpec> fontSpecs) {
        List<String> errors = new ArrayList<>();
        if (fontSpecs == null) return errors;

        if (fontSpecs.size() > MAX_FONT_SPECS) {
            errors.add("too many font specs: " + fontSpecs.size() + " (max " + MAX_FONT_SPECS + ")");
        }

        for (int i = 0; i < fontSpecs.size(); i++) {
            RemotionFontSpec f = fontSpecs.get(i);
            String prefix = "fontSpecs[" + i + "]";

            if (f.fontFamily() == null || f.fontFamily().isBlank()) {
                errors.add(prefix + ".fontFamily must not be blank");
            }
            if (f.weight() != null && (f.weight() < MIN_FONT_WEIGHT || f.weight() > MAX_FONT_WEIGHT)) {
                errors.add(prefix + ".weight must be between " + MIN_FONT_WEIGHT + " and " + MAX_FONT_WEIGHT + ", got: " + f.weight());
            }
            if (f.subsetUrl() == null && f.sourceUrl() == null) {
                errors.add(prefix + " must have at least one of subsetUrl or sourceUrl");
            }
            if (!f.productionSafe()) {
                errors.add(prefix + " is not production-safe and cannot be used in production mode");
            }
        }
        return errors;
    }

    private static List<String> validateTemplate(RemotionTemplateSpec template) {
        List<String> errors = new ArrayList<>();
        if (template == null) return errors;

        if (template.templateId() == null || template.templateId().isBlank()) {
            errors.add("template.templateId must not be blank");
        }
        if (template.templateVersion() == null || template.templateVersion().isBlank()) {
            errors.add("template.templateVersion must not be blank");
        }
        if (template.params() != null && template.params().size() > MAX_TEMPLATE_PARAMS) {
            errors.add("template.params exceeds max size " + MAX_TEMPLATE_PARAMS);
        }

        errors.addAll(RemotionTemplateGuard.validate(template));

        return errors;
    }
}
