package com.example.platform.social.app;

/** Internal account row containing only facts permitted in publication reads. */
public record SocialAccountReadModel(
        String accountId,
        String displayName,
        String platformType,
        String status,
        long bindingVersion) {}
