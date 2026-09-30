package com.example.platform.shared.capability;

/**
 * Platform capability parameter contract (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b).
 *
 * <p>Provider-neutral, implementation-neutral typed schema of one capability's
 * parameter surface. This marker type is a code-owned {@code sealed} interface
 * whose variants are validating records — the capability analogue of the
 * operation-module {@code OperationParameters} pattern.</p>
 *
 * <p>Hard prohibitions for every variant (mirrors {@code OperationParameters}):</p>
 * <ul>
 *   <li>no {@code Map<String,Object>} / {@code Map<String,String>};</li>
 *   <li>no {@code JsonNode} / arbitrary tree;</li>
 *   <li>no {@code Object} / {@code Serializable} catch-all;</li>
 *   <li>no raw provider / plugin / backend / worker / FFmpeg string;</li>
 *   <li>no business intent ({@code COVER_OF} / {@code THUMBNAIL}) — those are
 *       operation effects, never capability parameters.</li>
 * </ul>
 */
public sealed interface CapabilityParameterContract
        permits MediaFrameExtractParametersV1 {
}
