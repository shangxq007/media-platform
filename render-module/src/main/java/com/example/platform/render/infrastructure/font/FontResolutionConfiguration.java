package com.example.platform.render.infrastructure.font;

import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.operation.operation.TextOperationPlanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P2-5.5 MINIMAL FONT RESOLUTION WIRING.
 *
 * <p>Provides a production {@link TextOperationPlanner.FontResolutionInput} so the
 * canonical text-op write path can be planned. FONT-V1 Phase 6b replaces the former EMPTY catalog:
 * the catalog is now built once at startup by {@link FontCatalogBuilder} from the font files in
 * {@code ${app.fonts.dir}} (default {@code /tmp/platform/fonts}), through the Phase 6a pipeline.
 *
 * <p>The runtime capability view remains conservative. When the font directory is missing or empty,
 * the catalog is empty and text ops that require font resolution (ADD_TEXT_ELEMENT,
 * SET_FONT_SELECTION with AUTO optical sizing, SET_VARIABLE_FONT_AXIS) still fail closed at plan
 * time. There is no runtime catalog refresh.
 */
@Configuration
public class FontResolutionConfiguration {

    @Bean
    @ConditionalOnMissingBean(TextOperationPlanner.FontResolutionInput.class)
    public TextOperationPlanner.FontResolutionInput textFontResolutionInput(FontCatalogBuilder fontCatalogBuilder) {
        TechnicalFontResolver resolver = new TechnicalFontResolver(new MinimalRuntimeCapabilityView());
        return new TextOperationPlanner.FontResolutionInput(
                resolver, fontCatalogBuilder.build(), null);
    }

    /** Conservative provider-neutral runtime capability observation (minimal wiring). */
    static final class MinimalRuntimeCapabilityView implements TechnicalFontResolver.RuntimeCapabilityView {
        @Override public boolean supportsFormat(String formatName) { return true; }
        @Override public boolean supportsColorTechnology(String technology) { return true; }
        @Override public boolean supportsVariationAxes() { return false; }
        @Override public boolean supportsOpticalSizing() { return false; }
    }
}
