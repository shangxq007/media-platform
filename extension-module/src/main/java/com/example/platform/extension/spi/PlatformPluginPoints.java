package com.example.platform.extension.spi;

/**
 * Catalog of extension points that SHOULD be implemented as PF4J plugins when
 * vendor-specific or frequently changing. Core platform modules register built-in
 * defaults; optional JARs under {@code app.extensions.plugins-dir} override or extend.
 *
 * <p>Keep in core (not plugins): domain aggregates, REST/GraphQL APIs, Flyway schema,
 * entitlement/billing policy engine, outbox, audit, identity.</p>
 *
 * <h2>Recommended plugin candidates</h2>
 * <ul>
 *   <li>{@code RenderProvider} — ffmpeg, gstreamer, gpac, mlt, javacv, ofx (heavy native deps)</li>
 *   <li>{@code PaymentProvider} / {@code BillingEngine} — Stripe, Hyperswitch, Kill Bill adapters</li>
 *   <li>{@code NotificationDeliveryProvider} — Novu, SendGrid, Twilio, webhooks</li>
 *   <li>{@code AiChatProvider} — OpenAI, Anthropic, local models</li>
 *   <li>{@code SocialPlatformPublisher} — YouTube, TikTok, Instagram adapters</li>
 *   <li>{@code StorageBackend} — S3, GCS, MinIO (when not using catalog-only mode)</li>
 *   <li>{@code CloudResourceProvisioner} — AWS/GCP/Azure resource hooks</li>
 *   <li>{@code ExtensionTool} (PF4J already) — user-defined scripts, WASM, sandbox tools</li>
 * </ul>
 *
 * <h2>Stay in core modules</h2>
 * <ul>
 *   <li>Identity, tenant/workspace RBAC, quota metering schema</li>
 *   <li>Commerce checkout orchestration, entitlement decision graph</li>
 *   <li>GraphQL federation resolvers, NLQ catalog (metadata in DB)</li>
 *   <li>Feature flag evaluation engine (storage in DB; Unleash as remote provider)</li>
 * </ul>
 */
public final class PlatformPluginPoints {

    private PlatformPluginPoints() {
    }
}
