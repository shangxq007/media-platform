/**
 * Canonical authorization contract (APPD-CHV1).
 *
 * <p>Defines the bounded security closed loop: {@link com.example.platform.shared.authorization.CanonicalActor}
 * → tenant default-deny → {@link com.example.platform.shared.authorization.AuthorizationRequest}
 * → RBAC + bounded resource relation → {@link com.example.platform.shared.authorization.AuthorizationDecision}.
 * The separate Entitlement → FeatureFlag → Capability → Quota composition stays
 * independent of this package.</p>
 */
package com.example.platform.shared.authorization;
