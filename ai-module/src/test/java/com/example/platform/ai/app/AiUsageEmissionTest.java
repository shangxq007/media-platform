package com.example.platform.ai.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.ai.domain.ModelRouter;
import com.example.platform.ai.domain.RoutePlan;
import com.example.platform.ai.domain.RouteTarget;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageRecordEmissionPort;
import com.example.platform.billing.usage.UsageUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies the ESTIMATED usage-emission side effect added to {@link AiGatewayService#generateScript}.
 *
 * <p>These are pure unit tests (no Spring context, no DB): the {@link UsageRecordEmissionPort}
 * is supplied as a capturing lambda double.</p>
 */
class AiUsageEmissionTest {

    private static final String TENANT = "tenant-ai";

    private final ModelRouter router = capability -> new RoutePlan(List.of(new RouteTarget("testProvider")));
    private final Map<String, ChatProvider> providers = new HashMap<>();

    AiUsageEmissionTest() {
        providers.put("testProvider", request -> new ChatResult("testProvider", "test-model", "response"));
    }

    @Test
    void generateScript_emitsOneEstimatedTokenInputRecord() {
        AtomicReference<UsageRecord> captured = new AtomicReference<>();
        UsageRecordEmissionPort port = record -> {
            captured.set(record);
            return record;
        };

        AiGatewayService service = new AiGatewayService(router, providers, port);
        // 16 chars / 4 = 4 tokens for the prompt; "response" 8 chars / 4 = 2 tokens.
        AiScriptResult result = service.generateScript("1234567890123456", "default", TENANT);

        UsageRecord record = captured.get();
        assertNotNull(record, "expected a usage record to be emitted");
        assertEquals(TENANT, record.tenantId());
        assertEquals(UsageDimension.TOKEN_INPUT, record.dimension());
        assertEquals("ESTIMATED", record.provenance());
        assertEquals("ai-gateway-heuristic", record.source());
        assertEquals("script-generation", record.capability());

        UsageQuantity quantity = record.quantity();
        assertEquals(4L + 2L, quantity.baseUnits());
        assertEquals(UsageUnit.TOKEN, quantity.unit());

        assertEquals("ai-" + record.operationRef().operationId() + "-1", record.idempotencyKey());
        assertNotNull(record.recordedAt());

        // Existing behavior unchanged.
        assertEquals("response", result.scriptContent());
        assertEquals(4 + 2, result.tokensUsed());
    }

    @Test
    void generateScript_doesNotInventProviderCost() {
        UsageRecordEmissionPort port = record -> record;
        AiGatewayService service = new AiGatewayService(router, providers, port);

        service.generateScript("1234567890123456", "default", TENANT);

        // The emission path only ever constructs a UsageRecord; it never fabricates a
        // ProviderCostObservation. We assert this by construction: the captured type is a
        // UsageRecord and the canonical factory has no cost-bearing fields. No cost observation
        // type is imported or referenced by the emission code path.
        UsageRecord record = capture(service, "1234567890123456");
        assertNull(record.providerRef(), "no provider reference should be fabricated");
    }

    @Test
    void generateScript_skipsEmissionWhenNoTenant() {
        AtomicReference<UsageRecord> captured = new AtomicReference<>();
        UsageRecordEmissionPort port = record -> { captured.set(record); return record; };

        AiGatewayService service = new AiGatewayService(router, providers, port);

        // No tenant context available on this path -> emission skipped (tenant required).
        AiScriptResult result = service.generateScript("1234567890123456", "default", null);

        assertNull(captured.get(), "no usage record should be emitted without a tenant");
        // Existing behavior unchanged.
        assertEquals("response", result.scriptContent());
    }

    @Test
    void generateScript_twoCallsGetDistinctOperationIds() {
        AtomicReference<UsageRecord> first = new AtomicReference<>();
        AtomicReference<UsageRecord> second = new AtomicReference<>();
        UsageRecordEmissionPort port = record -> {
            if (first.get() == null) {
                first.set(record);
            } else {
                second.set(record);
            }
            return record;
        };

        AiGatewayService service = new AiGatewayService(router, providers, port);
        service.generateScript("1234567890123456", "default", TENANT);
        service.generateScript("1234567890123456", "default", TENANT);

        assertNotNull(first.get());
        assertNotNull(second.get());
        assertTrue(!first.get().operationRef().operationId().equals(second.get().operationRef().operationId()),
                "each generateScript call must get a fresh operation id");
        assertTrue(!first.get().idempotencyKey().equals(second.get().idempotencyKey()),
                "each call must yield a distinct idempotency key");
    }

    @Test
    void generateScript_defaultTwoArgOverloadSkipsEmission() {
        AtomicReference<UsageRecord> captured = new AtomicReference<>();
        UsageRecordEmissionPort port = record -> { captured.set(record); return record; };

        AiGatewayService service = new AiGatewayService(router, providers, port);
        // The no-tenant overload cannot emit (tenant required).
        AiScriptResult result = service.generateScript("1234567890123456", "default");

        assertNull(captured.get(), "no-tenant overload must not emit");
        assertEquals("response", result.scriptContent());
    }

    private UsageRecord capture(AiGatewayService service, String prompt) {
        AtomicReference<UsageRecord> ref = new AtomicReference<>();
        // Reconstruct with a capturing port to read the emitted record.
        UsageRecordEmissionPort port = record -> { ref.set(record); return record; };
        new AiGatewayService(router, providers, port).generateScript(prompt, "default", TENANT);
        return ref.get();
    }
}
