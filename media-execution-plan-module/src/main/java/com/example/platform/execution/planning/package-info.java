/**
 * Provider-neutral canonical execution planning contracts.
 *
 * <p>ROADMAP_21: this package is provider-neutral planning input/output only. Provider-bound
 * admission payloads live in {@code execution.admission}; runtime/provider binding concepts must
 * not appear here. Exposed to downstream consumers (e.g. composition) that build and read
 * platform execution plans before canonical admission.
 */
@org.springframework.modulith.NamedInterface("planning")
package com.example.platform.execution.planning;
