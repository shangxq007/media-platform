package com.example.platform.ingest.preflight.policy;

import com.example.platform.ingest.contract.IngestRejectionReasonCode;
import com.example.platform.ingest.contract.IngestWarningCode;

public record PreflightPolicyFinding(
    PreflightPolicyFindingCode code,
    PreflightPolicyRuleId ruleId,
    PreflightPolicySeverity severity,
    IngestWarningCode sourceWarningCode,
    IngestRejectionReasonCode sourceRejectionReasonCode,
    UserSafePolicyMessage message
) {
    public PreflightPolicyFinding(PreflightPolicyFindingCode code, PreflightPolicySeverity severity) {
        this(code, null, severity, null, null, null);
    }
}
