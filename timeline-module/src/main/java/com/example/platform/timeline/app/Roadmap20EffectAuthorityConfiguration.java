package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ROADMAP20 authority-integration production wiring (AI14/AI15): the Effect
 * semantic snapshot authority is constructed with DURABLE Jdbc registry +
 * store — InMemory implementations are test/domain-test only and can never be
 * selected by production construction.
 */
@Configuration
public class Roadmap20EffectAuthorityConfiguration {

    @Bean
    public EffectDefinitionVersionRegistry effectDefinitionVersionRegistry(DSLContext dsl) {
        return new JdbcEffectDefinitionVersionRegistry(dsl);
    }

    @Bean
    public EffectSemanticSnapshotStore effectSemanticSnapshotStore(DSLContext dsl) {
        return new JdbcEffectSemanticSnapshotStore(dsl);
    }

    @Bean
    public EffectSemanticSnapshotAuthority effectSemanticSnapshotAuthority(
            EffectDefinitionVersionRegistry effectDefinitionVersionRegistry,
            EffectSemanticSnapshotStore effectSemanticSnapshotStore) {
        return new EffectSemanticSnapshotAuthority(
                effectDefinitionVersionRegistry, effectSemanticSnapshotStore);
    }

    @Bean
    public JdbcTimelineRevisionSemanticContextStore timelineRevisionSemanticContextStore(DSLContext dsl) {
        return new JdbcTimelineRevisionSemanticContextStore(dsl);
    }
}
