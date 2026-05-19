package com.example.platform.federation.graphql.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLOperationLoggerTest {

    @Test
    void logOperationDoesNotThrow() {
        GraphQLOperationLogger logger = new GraphQLOperationLogger();

        assertDoesNotThrow(() -> logger.logOperation(
                "meOverview", "abc123", "{}",
                "tenant-1", "user-1", "trace-123",
                42, 10, 3, "SUCCESS", null
        ));
    }

    @Test
    void logOperationWithErrorCodeDoesNotThrow() {
        GraphQLOperationLogger logger = new GraphQLOperationLogger();

        assertDoesNotThrow(() -> logger.logOperation(
                "entitlementDecision", "def456", "{featureKey: \"gpu\"}",
                "tenant-1", "user-1", "trace-456",
                120, 5, 2, "ERROR", "ENTITLEMENT-403-001"
        ));
    }

    @Test
    void logErrorDoesNotThrow() {
        GraphQLOperationLogger logger = new GraphQLOperationLogger();

        assertDoesNotThrow(() -> logger.logError(
                "billingSummary", "ghi789",
                "tenant-1", "user-1", "trace-789",
                "BILLING-500-001", "Ledger write failed"
        ));
    }
}
