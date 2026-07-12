package com.example.platform.render.api;

import com.example.platform.render.api.dto.*;
import com.example.platform.render.domain.caption.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Maps between API DTOs and domain models for Caption Template Render.
 * Internal only.
 */
@Component
public class CaptionTemplateRenderApiMapper {

    public CaptionTemplateRenderRequest toDomainRequest(
            String projectId, CaptionTemplateRenderApiRequest apiRequest) {
        List<CaptionSegmentSpec> segments = apiRequest.captionSegments().stream()
                .map(s -> new CaptionSegmentSpec(s.startMs(), s.endMs(), s.text()))
                .toList();

        CaptionTemplateSpec template = null;
        if (apiRequest.template() != null) {
            CaptionStyleSpec style = toDomainStyle(apiRequest.template().style());
            template = new CaptionTemplateSpec(
                    apiRequest.template().templateId(),
                    apiRequest.template().name(),
                    style);
        }

        CaptionOutputProfileSpec profile = null;
        if (apiRequest.outputProfile() != null) {
            profile = new CaptionOutputProfileSpec(
                    apiRequest.outputProfile().width() != null ? apiRequest.outputProfile().width() : 1920,
                    apiRequest.outputProfile().height() != null ? apiRequest.outputProfile().height() : 1080,
                    apiRequest.outputProfile().fps() != null ? apiRequest.outputProfile().fps() : 30.0,
                    apiRequest.outputProfile().container() != null ? apiRequest.outputProfile().container() : "mp4");
        }

        return new CaptionTemplateRenderRequest(
                projectId,
                apiRequest.sourceProductId(),
                segments,
                template,
                profile,
                apiRequest.safeMetadata() != null ? apiRequest.safeMetadata() : Map.of());
    }

    public CaptionTemplateRenderApiResponse toApiResponse(CaptionTemplateRenderResult result) {
        if (result.hasValidationErrors()) {
            List<CaptionTemplateValidationErrorDto> errors = result.validationErrors().stream()
                    .map(e -> new CaptionTemplateValidationErrorDto(null, "INVALID", e))
                    .toList();
            return CaptionTemplateRenderApiResponse.validationFailed(errors);
        }

        CaptionOutputProfileDto profileDto = null;
        if (result.outputProfile() != null) {
            profileDto = new CaptionOutputProfileDto(
                    result.outputProfile().width(),
                    result.outputProfile().height(),
                    result.outputProfile().fps(),
                    result.outputProfile().container());
        }

        if (result.isSuccess()) {
            return CaptionTemplateRenderApiResponse.success(
                    result.renderJobId(), result.outputProductId(), profileDto);
        }

        return CaptionTemplateRenderApiResponse.failed(result.safeMessage());
    }

    private CaptionStyleSpec toDomainStyle(CaptionTemplateStyleDto styleDto) {
        if (styleDto == null) return CaptionStyleSpec.defaults();

        CaptionPlacement placement = CaptionPlacement.BOTTOM_CENTER;
        if (styleDto.placement() != null) {
            try {
                placement = CaptionPlacement.valueOf(styleDto.placement());
            } catch (IllegalArgumentException e) {
                // Default
            }
        }

        FontStyleSpec font = FontStyleSpec.defaults();
        if (styleDto.font() != null) {
            font = new FontStyleSpec(
                    styleDto.font().family() != null ? styleDto.font().family() : "DejaVu Sans",
                    styleDto.font().weight() != null ? styleDto.font().weight() : 400,
                    styleDto.font().color() != null ? styleDto.font().color() : "#FFFFFF",
                    styleDto.font().outlineColor() != null ? styleDto.font().outlineColor() : "#000000",
                    styleDto.font().outlineWidth() != null ? styleDto.font().outlineWidth() : 2,
                    styleDto.font().backgroundColor());
        }

        return new CaptionStyleSpec(
                placement,
                font,
                styleDto.fontSize() != null ? styleDto.fontSize() : 24,
                styleDto.maxLines() != null ? styleDto.maxLines() : 2,
                styleDto.lineHeight() != null ? styleDto.lineHeight() : 1.4,
                styleDto.textAlign() != null ? styleDto.textAlign() : "center");
    }
}
