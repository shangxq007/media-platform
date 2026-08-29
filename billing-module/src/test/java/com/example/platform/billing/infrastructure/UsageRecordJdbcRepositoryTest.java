package com.example.platform.billing.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UsageRecordJdbcRepositoryTest {

    @Test
    void legacyBillingObservationRepositoryIsGone() {
        assertFalse(Arrays.stream(ObservedRuntimeUsageJdbcRepository.class.getMethods())
                .anyMatch(method -> method.getName().equals("update")
                        || method.getName().equals("delete")));
    }

    @Test
    void neutralRepositoryExposesAppendAndTenantScopedReadsOnly() {
        assertTrue(Arrays.stream(ObservedRuntimeUsageJdbcRepository.class.getMethods())
                .anyMatch(method -> method.getName().equals("append")));
        assertTrue(Arrays.stream(ObservedRuntimeUsageJdbcRepository.class.getMethods())
                .anyMatch(method -> method.getName().equals("findByTenantAndId")));
        assertTrue(Arrays.stream(ObservedRuntimeUsageJdbcRepository.class.getMethods())
                .anyMatch(method -> method.getName().equals("findByTenantAndIdempotencyKey")));
    }
}
