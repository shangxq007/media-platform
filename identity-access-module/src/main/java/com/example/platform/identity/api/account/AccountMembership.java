package com.example.platform.identity.api.account;

/** Stable Identity-owned Account and selected business membership; never a grant or balance. */
public record AccountMembership(String accountId, String membershipId, String tenantId, String role) {}
