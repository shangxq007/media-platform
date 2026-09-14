package com.example.platform.identity.api.account;

/** Inputs must come from verified authentication, never unverified identity headers. */
public interface AccountIdentityQueries {
    AccountMembership resolve(String verifiedIssuer, String verifiedSubject, String selectedTenant);
}
