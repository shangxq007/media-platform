/**
 * Composition application services exposed to platform bean assembly.
 *
 * <p>COMPOSITION_FOUNDATION: the platform assembly (e.g. materialization wiring) consumes the
 * canonical composition application ports through this named interface instead of reaching into
 * composition internals. Public HTTP transport remains the {@code composition.api} surface.
 */
@org.springframework.modulith.NamedInterface("app")
package com.example.platform.composition.app;
