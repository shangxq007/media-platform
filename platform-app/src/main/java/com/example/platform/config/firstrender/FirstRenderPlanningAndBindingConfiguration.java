package com.example.platform.config.firstrender;

import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Phase 5 planning-and-binding coordinator.
 *
 * <p>Exposes the typed-chain entry services (#20 {@link RenderPlanningEntryService}, #21
 * {@link PhysicalPlanningEntryService}, #22 {@link ProviderBindingEntryService}) as beans so the
 * coordinator injects them (and Phase 5 tests can spy them). These services are stateless pure
 * classes; this configuration does not change them.
 */
@Configuration
public class FirstRenderPlanningAndBindingConfiguration {

    @Bean
    RenderPlanningEntryService renderPlanningEntryService() {
        return new RenderPlanningEntryService();
    }

    @Bean
    PhysicalPlanningEntryService physicalPlanningEntryService() {
        return new PhysicalPlanningEntryService();
    }

    @Bean
    ProviderBindingEntryService providerBindingEntryService() {
        return new ProviderBindingEntryService();
    }
}
