package com.example.platform.entitlement.api.commercial;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.entitlement.domain.EntitlementGrantView;
import java.util.Optional;
/** Exact owner grant identity/version for acceptance evidence; not an authorization grant. */
public interface EntitlementBasisQueries {
    Optional<EntitlementGrantView> findGrant(PrincipalRef principal,String grantId);
}
