package com.example.platform.render.infrastructure.font;

import com.example.platform.fontpipeline.DefaultFontPipelineOrchestrator;
import com.example.platform.fontpipeline.FontPipelineOrchestrator;
import com.example.platform.fontpipeline.manifest.DefaultFontBoxManifestAdapter;
import com.example.platform.fontpipeline.manifest.DefaultFontFaceManifestAssembler;
import com.example.platform.fontpipeline.manifest.DefaultLwjglFreeTypeManifestAdapter;
import com.example.platform.fontpipeline.sanitize.OtsFontSanitizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FONT-V1 Phase 6b: assembles the isolated Phase 6a font pipeline for use inside render-module.
 *
 * <p>Explicit construction only — no component scanning of the pipeline package and no change to
 * {@code PlatformApplication}. The pipeline itself stays isolated; this configuration is the single
 * place where its concrete collaborators are created.
 */
@Configuration
public class FontPipelineConfiguration {

    @Bean
    public FontPipelineOrchestrator fontPipelineOrchestrator() {
        return new DefaultFontPipelineOrchestrator(
                new OtsFontSanitizer(),
                new DefaultFontBoxManifestAdapter(),
                new DefaultLwjglFreeTypeManifestAdapter(),
                new DefaultFontFaceManifestAssembler());
    }
}
