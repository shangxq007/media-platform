package com.example.platform.workerfabric.domain;

/** Converts a trust-validated callback envelope into canonical observation evidence. */
@FunctionalInterface
public interface WebhookIngressNormalizationPort<I> {

    ExecutionObservation normalize(I trustValidatedWebhook);
}
