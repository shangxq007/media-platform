package com.example.platform.billing.app;

import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
class BillingProjectionServiceTest {

    @Test
    void absenceDoesNotFabricateActiveOrProState() {
        SubscriptionJdbcRepository repository = new SubscriptionJdbcRepository(null) {
            @Override public List<com.example.platform.billing.domain.SubscriptionContract> findActive(
                    PrincipalRef principal, Instant now) { return List.of(); }
            @Override public Optional<com.example.platform.billing.domain.SubscriptionContract> findByPrincipalAndId(
                    PrincipalRef principal, String contractId) { return Optional.empty(); }
        };
        BillingProjectionService projection = new BillingProjectionService(repository);

        assertNull(projection.currentState(principal()));
        assertNull(projection.getContract(principal(), "missing"));
    }

    @Test
    void persistenceFailureFailsClosed() {
        SubscriptionJdbcRepository repository = new SubscriptionJdbcRepository(null) {
            @Override public List<com.example.platform.billing.domain.SubscriptionContract> findActive(
                    PrincipalRef principal, Instant now) { throw new IllegalStateException("database unavailable"); }
            @Override public Optional<com.example.platform.billing.domain.SubscriptionContract> findByPrincipalAndId(
                    PrincipalRef principal, String contractId) { throw new IllegalStateException("database unavailable"); }
        };
        BillingProjectionService projection = new BillingProjectionService(repository);

        assertNull(projection.currentState(principal()));
        assertNull(projection.getContract(principal(), "contract"));
    }

    private static PrincipalRef principal() {
        return PrincipalRef.tenantScoped("tenant-a", PrincipalType.USER, "user-a");
    }
}
