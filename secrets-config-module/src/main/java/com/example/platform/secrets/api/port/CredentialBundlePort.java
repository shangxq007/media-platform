package com.example.platform.secrets.api.port;

import java.util.Map;

/** Resolves credential bundles from {@code credential_ref} or legacy inline JSON. */
public interface CredentialBundlePort {

    Map<String, String> resolve(String credentialRef, String legacyCredentialJson);

    boolean hasCredentials(String credentialRef, String legacyCredentialJson);
}
