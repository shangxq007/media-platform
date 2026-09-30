/**
 * Provider-neutral capability parameter contract vocabulary
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / Phase E-2b).
 *
 * <p>Typed value objects describing the implementation-neutral schema of a
 * platform capability: types, units and semantic rules only — never values,
 * never business intent, never provider/plugin/backend/worker identity.
 * Mirrors the operation-module {@code OperationParameters} sealed-type pattern
 * (code-owned sealed interface, validating records, no
 * {@code Map<String,Object>}/{@code JsonNode}/{@code Object}).</p>
 */
package com.example.platform.shared.capability;
