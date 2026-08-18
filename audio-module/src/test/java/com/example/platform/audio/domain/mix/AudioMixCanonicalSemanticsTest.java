package com.example.platform.audio.domain.mix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A Round 4 (R4-A4): AudioMixCanonicalSemantics local-authority
 * tests.
 *
 * <p>Proves the Audio domain owns the AudioMix canonical representation /
 * fingerprint / codec — the Timeline adapter only delegates (source-level
 * guard proves zero DSP field knowledge in Timeline classes).
 */
class AudioMixCanonicalSemanticsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AudioMix mix(double gain, String inputClip) {
        return AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("v1", inputClip), AudioGain.of(gain))));
    }

    @Test
    void canonicalRoundTripLossless() throws Exception {
        AudioMix mix = mix(0.75, "c1");
        String encoded = AudioMixCanonicalSemantics.canonicalJson(mix);
        AudioMix decoded = AudioMixCanonicalSemantics.fromCanonicalJson(MAPPER.readTree(encoded));
        assertEquals(mix, decoded, "AudioMix canonical round-trip must be lossless");
    }

    @Test
    void fingerprintDeterministicAndSensitive() {
        AudioMix a = mix(0.5, "c1");
        AudioMix same = mix(0.5, "c1");
        AudioMix different = mix(0.9, "c1");
        assertEquals(AudioMixCanonicalSemantics.semanticFingerprint(a),
                AudioMixCanonicalSemantics.semanticFingerprint(same),
                "identical mix → identical fingerprint");
        assertNotEquals(AudioMixCanonicalSemantics.semanticFingerprint(a),
                AudioMixCanonicalSemantics.semanticFingerprint(different),
                "changed gain → changed fingerprint");
        assertTrue(AudioMixCanonicalSemantics.localSemanticsEquals(a, same));
        assertTrue(!AudioMixCanonicalSemantics.localSemanticsEquals(a, different));
    }

    @Test
    void canonicalValueIsStructuredJson() {
        ObjectNode node = AudioMixCanonicalSemantics.canonicalValue(mix(0.5, "c1"));
        assertEquals("master", node.path("masterBus").path("busId").asText());
        assertEquals(1, node.path("routes").size());
        assertEquals(0.5, node.path("routes").get(0).path("gain").path("linear").asDouble());
    }

    @Test
    void emptyMixCanonical() {
        assertEquals(AudioMix.empty(), AudioMixCanonicalSemantics.fromCanonicalJson(null));
        JsonNode empty = MAPPER.createObjectNode();
        assertEquals(AudioMix.empty(), AudioMixCanonicalSemantics.fromCanonicalJson(empty));
    }
}
