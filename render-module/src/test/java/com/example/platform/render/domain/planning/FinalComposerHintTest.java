package com.example.platform.render.domain.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FinalComposerHintTest {

    @Test
    void collapsedProviderTokenDoesNotPreserveInventedIdentity() {
        assertEquals(FinalComposerHint.AUTO, FinalComposerHint.fromString("provider"));
    }

    @Test
    void providerNeutralTypedStrategyTokenParsesExplicitly() {
        assertEquals(FinalComposerHint.TYPED_PROVIDER_PLUGIN,
                FinalComposerHint.fromString("typed_provider_plugin"));
    }
}
