package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.policy.featureflag.FeatureFlagAuditService;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AccessDecisionFeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(AccessDecisionFeatureFlagService.class);

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagAuditService auditService;

    public AccessDecisionFeatureFlagService(FeatureFlagService featureFlagService,
                                              FeatureFlagAuditService auditService) {
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
    }

    public FeatureFlagAccessResult evaluateForAccessDecision(AccessCheckRequest request) {
        FeatureFlagContext ffContext = buildContext(request);

        List<FeatureFlagDecision> decisions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        boolean disabledByFlag = false;

        List<FeatureFlagDefinition> relevantFlags = featureFlagService.getFlagsForContext(ffContext);

        for (FeatureFlagDefinition flag : relevantFlags) {
            if (!flag.enabled() || flag.archived()) {
                continue;
            }
            FeatureFlagEvaluationRequest evalRequest = new FeatureFlagEvaluationRequest(
                    flag.flagKey(), ffContext, flag.defaultValue());
            FeatureFlagEvaluationResult result = featureFlagService.evaluate(evalRequest);
            FeatureFlagDecision decision = result.decision();
            decisions.add(decision);

            if (auditService != null) {
                auditService.auditEvaluated(decision, request.userId());
            }

            if (!decision.enabled()) {
                String reason = "Feature flag '" + flag.flagKey() + "' is disabled ("
                        + decision.reasonCode() + ")";
                reasons.add(reason);
                log.debug("AccessDecision: flag {} disabled for subject {}", flag.flagKey(), request.subjectId());
            }
        }

        boolean hasBlockingFlag = decisions.stream().anyMatch(d -> !d.enabled());

        return new FeatureFlagAccessResult(decisions, hasBlockingFlag, reasons);
    }

    public List<FeatureFlagDecision> evaluateSpecificFlags(AccessCheckRequest request,
                                                            List<String> flagKeys) {
        FeatureFlagContext ffContext = buildContext(request);
        List<FeatureFlagDecision> decisions = new ArrayList<>();

        for (String flagKey : flagKeys) {
            FeatureFlagEvaluationRequest evalRequest = new FeatureFlagEvaluationRequest(
                    flagKey, ffContext, false);
            FeatureFlagEvaluationResult result = featureFlagService.evaluate(evalRequest);
            FeatureFlagDecision decision = result.decision();
            decisions.add(decision);

            if (auditService != null) {
                auditService.auditEvaluated(decision, request.userId());
            }
        }

        return decisions;
    }

    private FeatureFlagContext buildContext(AccessCheckRequest request) {
        return new FeatureFlagContext(
                request.tenantId(),
                request.workspaceId(),
                request.userId(),
                null,
                List.of(),
                null,
                request.requestSource(),
                null,
                null,
                null,
                request.context() != null
                        ? Map.copyOf(request.context())
                        : Map.of()
        );
    }

    public record FeatureFlagAccessResult(
            List<FeatureFlagDecision> decisions,
            boolean disabledByFlag,
            List<String> reasons
    ) {}
}
