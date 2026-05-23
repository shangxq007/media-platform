-- =============================================================================
-- V6: Performance Indexes & Foreign Key Conventions
--
-- Purpose: Add missing indexes on foreign keys and frequently queried columns
--          to establish a performance baseline. All indexes are additive and
--          use IF NOT EXISTS for idempotency.
--
-- Conventions:
--   - Index names: ix_<table>_<column>[_<column>]
--   - Foreign key columns always indexed (JOIN / cascade performance)
--   - Status + timestamp columns indexed together (dispatch / polling queries)
--   - Unique constraints already create implicit indexes; no duplicates added
-- =============================================================================

-- =============================================================================
-- V1 tables: render_job, notification_event, notification_delivery, config_item
-- =============================================================================

-- render_job: queried by project for listing, by status for polling
create index if not exists ix_render_job_project_id on render_job(project_id);
create index if not exists ix_render_job_status on render_job(status);

-- notification_event: queried by event type for filtering, by created_at for range scans
create index if not exists ix_notification_event_event_type on notification_event(event_type);
create index if not exists ix_notification_event_created_at on notification_event(created_at);

-- notification_delivery: joined to notification_event via event_id, filtered by status
create index if not exists ix_notification_delivery_event_id on notification_delivery(event_id);
create index if not exists ix_notification_delivery_status on notification_delivery(status);

-- config_item: queried by namespace for scoped reads
create index if not exists ix_config_item_namespace_key on config_item(namespace_key);

-- =============================================================================
-- V2 tables: storage_object, extension_invocation
-- =============================================================================

-- storage_object: queried by provider + bucket for scoped lookups
create index if not exists ix_storage_object_provider_code on storage_object(provider_code);
create index if not exists ix_storage_object_bucket on storage_object(bucket);

-- extension_invocation: queried by extension_code for invocation history
create index if not exists ix_extension_invocation_extension_code on extension_invocation(extension_code);
create index if not exists ix_extension_invocation_created_at on extension_invocation(created_at);

-- =============================================================================
-- V3 tables: outbox_events, audit_records, schedules, quota_definitions
-- =============================================================================

-- outbox_events: dispatch queries filter by status + created_at; aggregate lookups
create index if not exists ix_outbox_events_status_created_at on outbox_events(status, created_at);
create index if not exists ix_outbox_events_aggregate on outbox_events(aggregate_type, aggregate_id);

-- audit_records: queried by actor, resource, and time range
create index if not exists ix_audit_records_created_at on audit_records(created_at);
create index if not exists ix_audit_records_actor_id on audit_records(actor_id);
create index if not exists ix_audit_records_resource on audit_records(resource_type, resource_id);

-- schedules: queried by schedule_code for lookups (unique already indexed, but explicit for clarity)
-- Note: schedule_code has no unique constraint; add one for data integrity
create index if not exists ix_schedules_schedule_code on schedules(schedule_code);

-- =============================================================================
-- V4 tables: commerce, billing, entitlement
-- =============================================================================

-- commerce_price: joined to commerce_product via product_id
create index if not exists ix_commerce_price_product_id on commerce_price(product_id);

-- provider_product_mapping: joined to commerce_product via product_id
create index if not exists ix_provider_product_mapping_product_id on provider_product_mapping(product_id);

-- checkout_session: joined to commerce_product via product_id
create index if not exists ix_checkout_session_product_id on checkout_session(product_id);

-- purchase_order: joined to checkout_session via checkout_session_id
create index if not exists ix_purchase_order_checkout_session_id on purchase_order(checkout_session_id);

-- payment_attempt: joined to purchase_order via purchase_order_id
create index if not exists ix_payment_attempt_purchase_order_id on payment_attempt(purchase_order_id);

-- subscription_contract: queried by subject for entitlement resolution
create index if not exists ix_subscription_contract_subject on subscription_contract(subject_type, subject_id);

-- billing_invoice: joined to subscription_contract via contract_id
create index if not exists ix_billing_invoice_contract_id on billing_invoice(contract_id);

-- feature_bundle_item: joined to feature_bundle via bundle_id, feature_definition via feature_id
create index if not exists ix_feature_bundle_item_bundle_id on feature_bundle_item(bundle_id);
create index if not exists ix_feature_bundle_item_feature_id on feature_bundle_item(feature_id);

-- entitlement_grant: queried by subject for entitlement resolution
create index if not exists ix_entitlement_grant_subject on entitlement_grant(subject_type, subject_id);

-- entitlement_override: queried by subject for override resolution
create index if not exists ix_entitlement_override_subject on entitlement_override(subject_type, subject_id);

-- =============================================================================
-- V5 columns: outbox_events (idempotency_key, next_attempt_at)
-- =============================================================================

-- outbox_events.idempotency_key: unique lookups for deduplication
create index if not exists ix_outbox_events_idempotency_key on outbox_events(idempotency_key);

-- outbox_events.next_attempt_at: queried for retry scheduling
create index if not exists ix_outbox_events_next_attempt_at on outbox_events(next_attempt_at);
