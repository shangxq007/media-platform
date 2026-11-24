-- Apply as the migration owner after Flyway. Cluster provisioning supplies the three
-- separate login roles with NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS.
-- The database owner must revoke database CREATE/TEMP from PUBLIC during provisioning.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = pg_catalog;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO mp_api, mp_worker, mp_readonly;
GRANT SELECT ON public.flyway_schema_history TO mp_api, mp_worker, mp_readonly;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

GRANT USAGE ON SCHEMA analytics TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA analytics TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA analytics FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    analytics."nlq_query_history",
    analytics."nlq_report_execution",
    analytics."user_behavior_event" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    analytics."nlq_report_definition",
    analytics."user_habits",
    analytics."user_profile",
    analytics."user_segment" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    analytics."search_projection" TO mp_api;

GRANT USAGE ON SCHEMA artifacts TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA artifacts TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA artifacts TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA artifacts GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA artifacts GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA artifacts FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    artifacts."artifact_pin",
    artifacts."artifact_relation" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    artifacts."artifact_replica" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    artifacts."artifact" TO mp_api;
GRANT SELECT ON TABLE
    artifacts."artifact_pin" TO mp_worker;
GRANT SELECT, INSERT ON TABLE
    artifacts."artifact",
    artifacts."artifact_relation",
    artifacts."artifact_replica" TO mp_worker;

GRANT USAGE ON SCHEMA billing TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA billing TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA billing TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA billing GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA billing GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA billing FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    billing."billable_usage",
    billing."billing_invoice_command",
    billing."billing_ledger_entry",
    billing."credit_transaction",
    billing."credit_wallet_command",
    billing."custom_pricing_rule",
    billing."discount_policy",
    billing."invoice_line_item",
    billing."observed_runtime_usage",
    billing."provider_cost_observation",
    billing."rated_usage_record" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    billing."billing_invoice",
    billing."credit_reservation",
    billing."credit_wallet",
    billing."pricing_rule",
    billing."subscription_command",
    billing."subscription_contract",
    billing."subscription_plan" TO mp_api;

GRANT USAGE ON SCHEMA commerce TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA commerce TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA commerce TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA commerce GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA commerce GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA commerce FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    commerce."provider_product_mapping",
    commerce."purchase_order" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    commerce."commerce_cart_line" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    commerce."checkout_session",
    commerce."commerce_cart",
    commerce."commerce_product",
    commerce."commercial_offering",
    commerce."product_catalog_command" TO mp_api;

GRANT USAGE ON SCHEMA composition TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA composition TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA composition TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA composition GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA composition GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA composition FROM PUBLIC;
GRANT INSERT ON TABLE
    composition."composition_validation_snapshot" TO mp_api;
GRANT SELECT, INSERT ON TABLE
    composition."composition_version" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    composition."composition_application_draft",
    composition."composition_template_workflow_draft" TO mp_api;

GRANT USAGE ON SCHEMA entitlement TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA entitlement TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA entitlement TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA entitlement GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA entitlement GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA entitlement FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON TABLE
    entitlement."entitlement_bundle",
    entitlement."entitlement_command_audit",
    entitlement."entitlement_grant",
    entitlement."entitlement_override",
    entitlement."quota_profile",
    entitlement."quota_usage",
    entitlement."quota_usage_operation",
    entitlement."shared_resource_grant",
    entitlement."tenant_entitlement_tier",
    entitlement."workspace_entitlement_pool",
    entitlement."workspace_member_entitlement_grant",
    entitlement."workspace_quota_allocation" TO mp_api;
GRANT SELECT ON TABLE
    entitlement."quota_profile" TO mp_worker;
GRANT SELECT, INSERT, UPDATE ON TABLE
    entitlement."quota_usage",
    entitlement."quota_usage_operation" TO mp_worker;

GRANT USAGE ON SCHEMA execution TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA execution TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA execution TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA execution GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA execution GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA execution FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    execution."platform_task" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    execution."platform_execution_result",
    execution."platform_job" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    execution."platform_execution_admission" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    execution."platform_execution_result" TO mp_worker;
GRANT SELECT, UPDATE ON TABLE
    execution."platform_execution_admission" TO mp_worker;

GRANT USAGE ON SCHEMA extension TO mp_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA extension TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA extension TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA extension GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA extension GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA extension FROM PUBLIC;

GRANT USAGE ON SCHEMA identity TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA identity TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA identity TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA identity FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    identity."group_role_assignment",
    identity."permission",
    identity."role",
    identity."role_permission",
    identity."tenant",
    identity."workspace_group",
    identity."workspace_group_member" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    identity."project_import_metadata",
    identity."user_role_assignment" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    identity."account",
    identity."api_key",
    identity."user",
    identity."workspace",
    identity."workspace_member" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    identity."project" TO mp_api;
GRANT SELECT ON TABLE
    identity."project",
    identity."tenant",
    identity."workspace" TO mp_worker;

GRANT USAGE ON SCHEMA infrastructure TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA infrastructure TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA infrastructure TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA infrastructure GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA infrastructure GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA infrastructure FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    infrastructure."config_item" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    infrastructure."outbox_events",
    infrastructure."secret_ref" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    infrastructure."tenant_litellm_virtual_key" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    infrastructure."outbox_events" TO mp_worker;

GRANT USAGE ON SCHEMA marketplace TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA marketplace TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA marketplace TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA marketplace GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA marketplace GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA marketplace FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    marketplace."marketplace_command",
    marketplace."marketplace_review_comment",
    marketplace."marketplace_review_decision" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    marketplace."marketplace_listing",
    marketplace."marketplace_review",
    marketplace."marketplace_review_thread" TO mp_api;

GRANT USAGE ON SCHEMA media TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA media TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA media TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA media GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA media GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA media FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    media."ingest_preflight_safe_report_records",
    media."media_probe_observation",
    media."source_visual_description_snapshot" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    media."media_stream" TO mp_api;

GRANT USAGE ON SCHEMA notification TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA notification TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA notification TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA notification GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA notification GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA notification FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    notification."notification_delivery",
    notification."notification_event",
    notification."notification_record",
    notification."notification_template" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    notification."notification_delivery_record",
    notification."notification_event_definition",
    notification."notification_preference",
    notification."notification_subscription",
    notification."notification_user_inbox" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    notification."notification_channel_binding" TO mp_api;

GRANT USAGE ON SCHEMA payment TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA payment TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA payment TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA payment GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA payment FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    payment."provider_webhook_receipt" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    payment."payment_command",
    payment."payment_outbox",
    payment."payment_refund",
    payment."payment_transaction" TO mp_api;

GRANT USAGE ON SCHEMA policy TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA policy TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA policy TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA policy GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA policy GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA policy FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    policy."audit_records",
    policy."feature_flag_evaluation_snapshot" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    policy."feature_flag_targeting_rule" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    policy."feature_flag_definition" TO mp_api;

GRANT USAGE ON SCHEMA prompt TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA prompt TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA prompt TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA prompt GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA prompt GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA prompt FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON TABLE
    prompt."prompt_execution_run",
    prompt."prompt_template",
    prompt."prompt_template_version" TO mp_api;

GRANT USAGE ON SCHEMA publication TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA publication TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA publication TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA publication GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA publication GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA publication FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    publication."delivery",
    publication."social_post_analytics" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    publication."social_connected_platform" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    publication."social_post" TO mp_api;

GRANT USAGE ON SCHEMA render TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA render TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA render TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA render GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA render GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA render FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    render."render_binding_inputs",
    render."render_execution_context",
    render."render_job_status_history" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    render."effect_pack_effect",
    render."product_dependency",
    render."render_job_lifecycle_events" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    render."product",
    render."render_graph_execution",
    render."render_job",
    render."render_submission_idempotency",
    render."system_canonical_edge",
    render."system_canonical_event",
    render."system_canonical_graph",
    render."unified_graph_edge",
    render."unified_graph_node",
    render."unified_request_graph" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    render."asset_semantic_metadata",
    render."client_export_session",
    render."effect_pack" TO mp_api;
GRANT SELECT ON TABLE
    render."render_binding_inputs",
    render."render_execution_context" TO mp_worker;
GRANT SELECT, INSERT ON TABLE
    render."render_job_lifecycle_events",
    render."render_job_status_history" TO mp_worker;
GRANT SELECT, INSERT, UPDATE ON TABLE
    render."render_graph_execution" TO mp_worker;
GRANT SELECT, UPDATE ON TABLE
    render."render_job" TO mp_worker;

GRANT USAGE ON SCHEMA storage TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA storage TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA storage TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA storage GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA storage GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA storage FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    storage."storage_logical_object",
    storage."storage_object_placement",
    storage."storage_placement_receipt" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    storage."storage_database_binding",
    storage."storage_write_intent" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    storage."storage_reference" TO mp_api;
GRANT SELECT, INSERT ON TABLE
    storage."storage_placement_receipt" TO mp_worker;
GRANT SELECT, INSERT, UPDATE ON TABLE
    storage."storage_logical_object",
    storage."storage_object_placement",
    storage."storage_write_intent" TO mp_worker;

GRANT USAGE ON SCHEMA timeline TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA timeline TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA timeline TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA timeline GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA timeline GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA timeline FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE
    timeline."review_decision",
    timeline."timeline_comment",
    timeline."timeline_revision_parent",
    timeline."timeline_snapshot" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    timeline."apply_command",
    timeline."project_revision_counter",
    timeline."review_thread",
    timeline."timeline_review",
    timeline."timeline_revision",
    timeline."timeline_revision_ref" TO mp_api;

GRANT USAGE ON SCHEMA worker_fabric TO mp_readonly, mp_api, mp_worker;
GRANT SELECT ON ALL TABLES IN SCHEMA worker_fabric TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA worker_fabric TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA worker_fabric GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA worker_fabric GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA worker_fabric FROM PUBLIC;
GRANT INSERT ON TABLE
    worker_fabric."wf_execution_ownership_generation" TO mp_api;
GRANT SELECT, INSERT ON TABLE
    worker_fabric."wf_completion_event",
    worker_fabric."wf_execution_assignment",
    worker_fabric."wf_execution_assignment_device",
    worker_fabric."wf_execution_observation",
    worker_fabric."wf_host_resource_snapshot",
    worker_fabric."wf_host_resource_snapshot_device",
    worker_fabric."wf_local_admission",
    worker_fabric."wf_physical_release_confirmation",
    worker_fabric."wf_reservation_device",
    worker_fabric."wf_task_lease_reservation" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    worker_fabric."wf_physical_host_descriptor_device" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    worker_fabric."wf_execution_attempt",
    worker_fabric."wf_execution_backend_selection",
    worker_fabric."wf_host_readiness",
    worker_fabric."wf_host_registration",
    worker_fabric."wf_host_snapshot_generation_authority",
    worker_fabric."wf_physical_host_connection",
    worker_fabric."wf_physical_host_descriptor",
    worker_fabric."wf_request_work_resolution",
    worker_fabric."wf_reservation",
    worker_fabric."wf_runtime_registration",
    worker_fabric."wf_task_lease",
    worker_fabric."wf_task_ownership",
    worker_fabric."wf_worker_runtime_connection" TO mp_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    worker_fabric."wf_artifact_reuse_index" TO mp_api;
GRANT INSERT ON TABLE
    worker_fabric."wf_execution_ownership_generation" TO mp_worker;
GRANT SELECT, INSERT ON TABLE
    worker_fabric."wf_completion_event",
    worker_fabric."wf_execution_assignment",
    worker_fabric."wf_execution_assignment_device",
    worker_fabric."wf_execution_observation",
    worker_fabric."wf_host_resource_snapshot",
    worker_fabric."wf_host_resource_snapshot_device",
    worker_fabric."wf_local_admission",
    worker_fabric."wf_physical_release_confirmation",
    worker_fabric."wf_reservation_device",
    worker_fabric."wf_task_lease_reservation" TO mp_worker;
GRANT SELECT, INSERT, DELETE ON TABLE
    worker_fabric."wf_physical_host_descriptor_device" TO mp_worker;
GRANT SELECT, INSERT, UPDATE ON TABLE
    worker_fabric."wf_execution_attempt",
    worker_fabric."wf_execution_backend_selection",
    worker_fabric."wf_host_readiness",
    worker_fabric."wf_host_registration",
    worker_fabric."wf_host_snapshot_generation_authority",
    worker_fabric."wf_physical_host_connection",
    worker_fabric."wf_physical_host_descriptor",
    worker_fabric."wf_request_work_resolution",
    worker_fabric."wf_reservation",
    worker_fabric."wf_runtime_registration",
    worker_fabric."wf_task_lease",
    worker_fabric."wf_task_ownership",
    worker_fabric."wf_worker_runtime_connection" TO mp_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    worker_fabric."wf_artifact_reuse_index" TO mp_worker;

GRANT USAGE ON SCHEMA workflow TO mp_readonly, mp_api;
GRANT SELECT ON ALL TABLES IN SCHEMA workflow TO mp_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA workflow TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT SELECT ON TABLES TO mp_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT SELECT ON SEQUENCES TO mp_readonly;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA workflow FROM PUBLIC;
GRANT INSERT ON TABLE
    workflow."user_workflow_definition" TO mp_api;
GRANT SELECT, INSERT ON TABLE
    workflow."workflow_operation_receipt" TO mp_api;
GRANT SELECT, INSERT, DELETE ON TABLE
    workflow."user_workflow_definition_edge",
    workflow."user_workflow_definition_node" TO mp_api;
GRANT SELECT, INSERT, UPDATE ON TABLE
    workflow."user_workflow_definition_version",
    workflow."workflow_run_step" TO mp_api;
GRANT SELECT, UPDATE ON TABLE
    workflow."workflow_run" TO mp_api;

GRANT USAGE ON SEQUENCE infrastructure.config_item_id_seq TO mp_api;
GRANT USAGE ON SEQUENCE render.render_binding_inputs_id_seq TO mp_api;
GRANT USAGE ON SEQUENCE notification.notification_template_id_seq TO mp_api;
GRANT USAGE ON SEQUENCE media.ingest_preflight_safe_report_records_id_seq TO mp_api;
COMMIT;
