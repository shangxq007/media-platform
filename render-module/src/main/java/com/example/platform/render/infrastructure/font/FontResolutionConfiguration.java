package com.example.platform.render.infrastructure.font;

import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.operation.operation.TextOperationPlanner;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P2-5.5 MINIMAL FONT RESOLUTION WIRING.
 *
 * <p>Provides a production {@link TextOperationPlanner.FontResolutionInput} so the
 * canonical text-op write path can be planned. This is a MINIMAL wiring: the
 * validated font catalog is EMPTY and the runtime capability view is
 * conservative. Text ops that require font resolution (ADD_TEXT_ELEMENT,
 * SET_FONT_SELECTION with AUTO optical sizing, SET_VARIABLE_FONT_AXIS) therefore
 * fail closed at plan time until catalog content is supplied by a later task —
 * the catalog content is deliberately deferred and is NOT invented here.
 */
@Configuration
public class FontResolutionConfiguration {

    @Bean
    @ConditionalOnMissingBean(TextOperationPlanner.FontResolutionInput.class)
    public TextOperationPlanner.FontResolutionInput textFontResolutionInput() {
        TechnicalFontResolver resolver = new TechnicalFontResolver(new MinimalRuntimeCapabilityView());
        return new TextOperationPlanner.FontResolutionInput(
                resolver, new ValidatedFontCatalogSnapshot(List.of()), null);
    }

    /** Conservative provider-neutral runtime capability observation (minimal wiring). */
    static final class MinimalRuntimeCapabilityView implements TechnicalFontResolver.RuntimeCapabilityView {
        @Override public boolean supportsFormat(String formatName) { return true; }
        @Override public boolean supportsColorTechnology(String technology) { return true; }
        @Override public boolean supportsVariationAxes() { return false; }
        @Override public boolean supportsOpticalSizing() { return false; }
    }
}
