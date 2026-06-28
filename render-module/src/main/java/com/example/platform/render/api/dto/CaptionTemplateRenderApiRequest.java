package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

@Schema(description = "字幕模板渲染请求")
public record CaptionTemplateRenderApiRequest(
        @NotBlank @Schema(description = "源视频Product ID", example = "prod-source-video") String sourceProductId,
        @NotEmpty @Valid @Schema(description = "字幕片段列表") List<CaptionTemplateSegmentDto> captionSegments,
        @Valid @Schema(description = "字幕模板") CaptionTemplateDto template,
        @Valid @Schema(description = "输出配置") CaptionOutputProfileDto outputProfile,
        @Schema(description = "安全元数据") Map<String, String> safeMetadata) {}
