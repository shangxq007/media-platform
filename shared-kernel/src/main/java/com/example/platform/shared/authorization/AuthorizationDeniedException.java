package com.example.platform.shared.authorization;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/**
 * Typed exception thrown by {@link AuthorizationDecisionPort#requireAuthorized} when
 * an {@link AuthorizationDecision} is DENY.
 *
 * <p>Extends {@link PlatformException} so the platform {@code GlobalExceptionHandler}
 * maps it to HTTP 403 (Forbidden) by default. The carried {@link AuthorizationDecision}
 * lets a controller translate specific denials into other statuses — e.g. a
 * {@code TENANT_BOUNDARY} denial on a read is surfaced as 404 to avoid leaking
 * cross-tenant existence (the W2 no-existence-leak contract).</p>
 */
public class AuthorizationDeniedException extends PlatformException {

    private static final ErrorCode AUTHORIZATION_DENIED = new ErrorCode() {
        @Override
        public String code() {
            return "SECURITY-403-001";
        }

        @Override
        public String title() {
            return "Forbidden";
        }

        @Override
        public int status() {
            return 403;
        }
    };

    private final AuthorizationDecision decision;

    public AuthorizationDeniedException(AuthorizationDecision decision) {
        super(AUTHORIZATION_DENIED, decision.detail());
        this.decision = decision;
    }

    public AuthorizationDecision decision() {
        return decision;
    }

    /**
     * @return true when this denial is a tenant-boundary (cross-tenant) denial that
     *         callers may choose to surface as 404 to avoid existence leaks.
     */
    public boolean isTenantBoundary() {
        return "TENANT_BOUNDARY".equals(decision.reasonCode());
    }
}
