package com.example.platform.ai.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageRecordEmissionPort;
import com.example.platform.billing.usage.UsageUnit;
import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.ai.domain.ModelRouter;
import com.example.platform.ai.domain.RoutePlan;
import com.example.platform.ai.domain.RouteTarget;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiGatewayService implements AiGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private final ModelRouter router;
    private final Map<String, ChatProvider> providers;
    private final UsageRecordEmissionPort emissionPort;

    /**
     * @deprecated existing two-argument constructor kept for backward compatibility with
     * tests/instantiations that do not provide an emission port. The port is optional —
     * when absent, usage emission is a no-op.
     */
    @Deprecated
    public AiGatewayService(ModelRouter router, Map<String, ChatProvider> providers) {
        this(router, providers, null);
    }

    @Autowired
    public AiGatewayService(ModelRouter router, Map<String, ChatProvider> providers,
            UsageRecordEmissionPort emissionPort) {
        this.router = router;
        this.providers = providers;
        this.emissionPort = emissionPort;
    }

    @Override
    public ChatResult chat(String capability, String prompt) {
        return invokeRouted(capability, prompt);
    }

    /**
     * Generates a script with no tenant context available. The platform has no tenant
     * context on this path, so per the usage contract a tenant is required to emit —
     * emission is skipped with a warning. Callers that can supply a tenant should use
     * {@link #generateScript(String, String, String)}.
     */
    public AiScriptResult generateScript(String prompt, String profile) {
        return generateScript(prompt, profile, null);
    }

    /**
     * Generates a script and, when a tenant is present, emits a canonical estimated
     * TOKEN_INPUT usage record as a side effect. Emission never breaks script generation.
     *
     * @param tenantId the emitting tenant, or {@code null} if no tenant context is available
     */
    public AiScriptResult generateScript(String prompt, String profile, String tenantId) {
        log.info("AiGatewayService: generating script for profile={}", profile);
        long start = System.currentTimeMillis();
        ChatResult result = invokeRouted("script-generation", prompt);
        long elapsed = System.currentTimeMillis() - start;
        int tokensUsed = prompt.length() / 4 + result.content().length() / 4;
        log.info("AiGatewayService: script generated in {}ms, tokensUsed={}", elapsed, tokensUsed);
        emitUsage(tenantId, tokensUsed);
        return new AiScriptResult(result.content(), result.model(), tokensUsed, Instant.now());
    }

    /**
     * Emits one canonical TOKEN_INPUT {@link UsageRecord} for the heuristic token count.
     *
     * <p>Provenance is deliberately ESTIMATED (a chars/4 heuristic) — this path MUST NOT
     * claim provider-reported usage, and it MUST NOT fabricate a provider cost observation
     * because no cost data is available. A tenant is required; if none is present, emission
     * is skipped with a warning. Any emission failure is swallowed so it can never break
     * script generation.</p>
     */
    private void emitUsage(String tenantId, int tokensUsed) {
        if (emissionPort == null) {
            return;
        }
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("AiGatewayService: skipping usage emission, no tenant context available (tenant required)");
            return;
        }
        try {
            String operationId = Ids.newId("aiop");
            UsageRecord record = UsageRecord.record(
                    tenantId,
                    null,
                    null,
                    OperationRef.of(operationId),
                    null,
                    null,
                    "script-generation",
                    UsageDimension.TOKEN_INPUT,
                    UsageQuantity.fromBaseUnits(tokensUsed, UsageUnit.TOKEN),
                    Instant.now(),
                    Instant.now(),
                    Instant.now(),
                    "ai-" + operationId + "-1",
                    "ESTIMATED",
                    "ai-gateway-heuristic");
            emissionPort.emit(record);
        } catch (Exception e) {
            log.warn("AiGatewayService: usage emission failed, suppressing to preserve generateScript behavior: {}",
                    e.getMessage());
        }
    }

    private ChatResult invokeRouted(String capability, String prompt) {
        RoutePlan plan = router.routePlan(capability);
        List<String> attempted = new ArrayList<>();
        RuntimeException lastFailure = null;

        for (RouteTarget target : plan.targets()) {
            ChatProvider provider = providers.get(target.providerId());
            if (provider == null) {
                lastFailure = new IllegalStateException("No ChatProvider bean: " + target.providerId());
                attempted.add(target.providerId() + "(missing)");
                continue;
            }
            attempted.add(target.providerId());
            log.info("AiGatewayService: capability={} provider={} model={}",
                    capability, target.providerId(), target.model());
            try {
                return provider.chat(new ChatRequest(capability, prompt, target.model()));
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("AiGatewayService: provider {} failed for capability={}: {}",
                        target.providerId(), capability, ex.getMessage());
            }
        }
        throw new IllegalStateException(
                "All providers failed for capability=" + capability + " attempted=" + attempted,
                lastFailure);
    }
}
