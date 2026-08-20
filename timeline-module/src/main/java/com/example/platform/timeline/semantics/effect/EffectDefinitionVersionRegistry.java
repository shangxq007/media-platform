package com.example.platform.timeline.semantics.effect;

/**
 * ROADMAP20 final implementation: enforcement contract for
 * EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1 — the same
 * (definitionId, version) MUST map to exactly one semantic content digest
 * across ALL authoritative snapshots; a different digest FAILS CLOSED
 * (D1, §17, §39).
 *
 * <p>It owns ONLY identity integrity: (definitionId, version) -> digest. It
 * cannot redefine Effect semantic fields and is not a mutable-latest
 * EffectDefinition authority.
 *
 * <p>Implementations: {@link InMemory} (pure domain tests),
 * {@code JdbcEffectDefinitionVersionRegistry} (durable, restart-safe).
 */
public interface EffectDefinitionVersionRegistry {

    /** @throws IllegalArgumentException on (id, version) -> different digest collision */
    void register(EffectDefinitionSnapshot definition);

    /** In-memory bounded implementation for domain tests (NOT durable). */
    final class InMemory implements EffectDefinitionVersionRegistry {
        private final java.util.Map<String, String> idVersionToDigest = new java.util.LinkedHashMap<>();

        @Override
        public synchronized void register(EffectDefinitionSnapshot definition) {
            java.util.Objects.requireNonNull(definition, "definition");
            String key = definition.definitionId() + "@" + definition.version();
            String existing = idVersionToDigest.get(key);
            if (existing != null) {
                if (!existing.equals(definition.definitionContentDigest())) {
                    throw new IllegalArgumentException(
                            "EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1 (D1): definition "
                                    + key + " registered with content digest '" + existing
                                    + "' but a different digest '" + definition.definitionContentDigest()
                                    + "' was supplied — same (id, version) MUST have exactly one "
                                    + "semantic content digest");
                }
                return;
            }
            idVersionToDigest.put(key, definition.definitionContentDigest());
        }

        public String digestFor(String definitionId, String version) {
            return idVersionToDigest.get(definitionId + "@" + version);
        }
    }
}
