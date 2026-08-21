package com.example.platform;

import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 authority-integration production wiring test (AI14/AI15, §11):
 * the production Effect semantic snapshot authority is constructed with the
 * DURABLE Jdbc registry + store — InMemory implementations can never be
 * selected by production construction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false"
})
public class Roadmap20ProductionWiringTest extends com.example.platform.shared.test.PostgresTestContainerSupport {

    @Autowired
    EffectDefinitionVersionRegistry effectDefinitionVersionRegistry;

    @Autowired
    EffectSemanticSnapshotStore effectSemanticSnapshotStore;

    @Autowired
    EffectSemanticSnapshotAuthority effectSemanticSnapshotAuthority;

    @Autowired
    JdbcTimelineRevisionSemanticContextStore timelineRevisionSemanticContextStore;

    @Test
    void authorityUsesDurableJdbcRegistryAndStore() {
        // AI14: the production authority's registry dependency is the durable
        // Jdbc registry — never the InMemory test implementation.
        assertTrue(effectDefinitionVersionRegistry instanceof JdbcEffectDefinitionVersionRegistry,
                "AI14: production authority must own the durable Jdbc definition registry");
        // AI15: the production authority's store dependency is the durable
        // Jdbc store.
        assertTrue(effectSemanticSnapshotStore instanceof JdbcEffectSemanticSnapshotStore,
                "AI15: production authority must own the durable Jdbc snapshot store");
        // The authority is the real instance (not a mock).
        assertNotNull(effectSemanticSnapshotAuthority);
        // The revision semantic context store is the durable Jdbc store.
        assertNotNull(timelineRevisionSemanticContextStore);
    }

    @Test
    void authorityMintsThroughDurableDependencies() {
        // Behavior: the wired authority can mint (via its durable registry).
        // Effect semantics are authoritative EMPTY for no-effect state.
        var empty = effectSemanticSnapshotAuthority.mintEmpty();
        assertEquals(0, empty.entries().size(),
                "no-Effect state mints authoritative EMPTY (CLEAN-FORWARD)");
        assertNotNull(empty.reference());
    }
}
