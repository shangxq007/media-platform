package com.example.platform.shared.commercial;

/** Generic provider-neutral commercial decision reasons accepted by H5. */
public enum CommercialDecisionReason {
    ALLOWED,
    NOT_ENTITLED,
    POLICY_DENIED,
    QUOTA_EXCEEDED,
    SUBSCRIPTION_INACTIVE,
    COMMERCIAL_ACCOUNT_SUSPENDED,
    BILLING_ACTION_REQUIRED,
    PAYMENT_FAILED,
    TRIAL_EXPIRED
}
