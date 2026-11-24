-- Preserve row, constraint and index identities, owned sequences and trigger bindings.
-- Flyway executes this migration as a single PostgreSQL transaction.
SET LOCAL search_path = pg_catalog;

CREATE SCHEMA analytics;
CREATE SCHEMA artifacts;
CREATE SCHEMA billing;
CREATE SCHEMA commerce;
CREATE SCHEMA composition;
CREATE SCHEMA entitlement;
CREATE SCHEMA execution;
CREATE SCHEMA extension;
CREATE SCHEMA identity;
CREATE SCHEMA infrastructure;
CREATE SCHEMA marketplace;
CREATE SCHEMA media;
CREATE SCHEMA notification;
CREATE SCHEMA payment;
CREATE SCHEMA policy;
CREATE SCHEMA prompt;
CREATE SCHEMA publication;
CREATE SCHEMA render;
CREATE SCHEMA storage;
CREATE SCHEMA timeline;
CREATE SCHEMA worker_fabric;
CREATE SCHEMA workflow;

ALTER TABLE public."nlq_query_history" SET SCHEMA analytics;
ALTER TABLE public."nlq_report_definition" SET SCHEMA analytics;
ALTER TABLE public."nlq_report_execution" SET SCHEMA analytics;
ALTER TABLE public."search_projection" SET SCHEMA analytics;
ALTER TABLE public."user_behavior_event" SET SCHEMA analytics;
ALTER TABLE public."user_habits" SET SCHEMA analytics;
ALTER TABLE public."user_profile" SET SCHEMA analytics;
ALTER TABLE public."user_segment" SET SCHEMA analytics;

ALTER TABLE public."artifact" SET SCHEMA artifacts;
ALTER TABLE public."artifact_details" SET SCHEMA artifacts;
ALTER TABLE public."artifact_governance" SET SCHEMA artifacts;
ALTER TABLE public."artifact_media_details" SET SCHEMA artifacts;
ALTER TABLE public."artifact_pin" SET SCHEMA artifacts;
ALTER TABLE public."artifact_relation" SET SCHEMA artifacts;
ALTER TABLE public."artifact_replica" SET SCHEMA artifacts;
ALTER TABLE public."conversion_specification" SET SCHEMA artifacts;

ALTER TABLE public."billable_usage" SET SCHEMA billing;
ALTER TABLE public."billing_invoice" SET SCHEMA billing;
ALTER TABLE public."billing_invoice_command" SET SCHEMA billing;
ALTER TABLE public."billing_ledger_entry" SET SCHEMA billing;
ALTER TABLE public."credit_reservation" SET SCHEMA billing;
ALTER TABLE public."credit_transaction" SET SCHEMA billing;
ALTER TABLE public."credit_wallet" SET SCHEMA billing;
ALTER TABLE public."credit_wallet_command" SET SCHEMA billing;
ALTER TABLE public."custom_pricing_rule" SET SCHEMA billing;
ALTER TABLE public."discount_policy" SET SCHEMA billing;
ALTER TABLE public."invoice_line_item" SET SCHEMA billing;
ALTER TABLE public."observed_runtime_usage" SET SCHEMA billing;
ALTER TABLE public."pricing_rule" SET SCHEMA billing;
ALTER TABLE public."provider_cost_observation" SET SCHEMA billing;
ALTER TABLE public."rated_usage_record" SET SCHEMA billing;
ALTER TABLE public."subscription_command" SET SCHEMA billing;
ALTER TABLE public."subscription_contract" SET SCHEMA billing;
ALTER TABLE public."subscription_plan" SET SCHEMA billing;
ALTER TABLE public."usage_meter" SET SCHEMA billing;

ALTER TABLE public."checkout_session" SET SCHEMA commerce;
ALTER TABLE public."commerce_cart" SET SCHEMA commerce;
ALTER TABLE public."commerce_cart_line" SET SCHEMA commerce;
ALTER TABLE public."commerce_product" SET SCHEMA commerce;
ALTER TABLE public."commercial_offering" SET SCHEMA commerce;
ALTER TABLE public."product_catalog_command" SET SCHEMA commerce;
ALTER TABLE public."provider_product_mapping" SET SCHEMA commerce;
ALTER TABLE public."purchase_order" SET SCHEMA commerce;

ALTER TABLE public."composition_application_draft" SET SCHEMA composition;
ALTER TABLE public."composition_template_workflow_draft" SET SCHEMA composition;
ALTER TABLE public."composition_validation_snapshot" SET SCHEMA composition;
ALTER TABLE public."composition_version" SET SCHEMA composition;

ALTER TABLE public."entitlement_bundle" SET SCHEMA entitlement;
ALTER TABLE public."entitlement_command_audit" SET SCHEMA entitlement;
ALTER TABLE public."entitlement_grant" SET SCHEMA entitlement;
ALTER TABLE public."entitlement_override" SET SCHEMA entitlement;
ALTER TABLE public."feature_bundle" SET SCHEMA entitlement;
ALTER TABLE public."feature_bundle_item" SET SCHEMA entitlement;
ALTER TABLE public."feature_definition" SET SCHEMA entitlement;
ALTER TABLE public."quota_definitions" SET SCHEMA entitlement;
ALTER TABLE public."quota_profile" SET SCHEMA entitlement;
ALTER TABLE public."quota_usage" SET SCHEMA entitlement;
ALTER TABLE public."quota_usage_operation" SET SCHEMA entitlement;
ALTER TABLE public."shared_resource_grant" SET SCHEMA entitlement;
ALTER TABLE public."tenant_entitlement_tier" SET SCHEMA entitlement;
ALTER TABLE public."workspace_entitlement_pool" SET SCHEMA entitlement;
ALTER TABLE public."workspace_member_entitlement_grant" SET SCHEMA entitlement;
ALTER TABLE public."workspace_quota_allocation" SET SCHEMA entitlement;

ALTER TABLE public."platform_execution_admission" SET SCHEMA execution;
ALTER TABLE public."platform_execution_result" SET SCHEMA execution;
ALTER TABLE public."platform_job" SET SCHEMA execution;
ALTER TABLE public."platform_task" SET SCHEMA execution;

ALTER TABLE public."extension_audit_event" SET SCHEMA extension;
ALTER TABLE public."extension_definition" SET SCHEMA extension;
ALTER TABLE public."extension_invocation" SET SCHEMA extension;
ALTER TABLE public."extension_resource_limit" SET SCHEMA extension;
ALTER TABLE public."extension_rollback_point" SET SCHEMA extension;
ALTER TABLE public."extension_routing_rule" SET SCHEMA extension;
ALTER TABLE public."sandbox_execution_job" SET SCHEMA extension;

ALTER TABLE public."account" SET SCHEMA identity;
ALTER TABLE public."api_client" SET SCHEMA identity;
ALTER TABLE public."api_key" SET SCHEMA identity;
ALTER TABLE public."group_role_assignment" SET SCHEMA identity;
ALTER TABLE public."permission" SET SCHEMA identity;
ALTER TABLE public."project" SET SCHEMA identity;
ALTER TABLE public."project_import_metadata" SET SCHEMA identity;
ALTER TABLE public."role" SET SCHEMA identity;
ALTER TABLE public."role_permission" SET SCHEMA identity;
ALTER TABLE public."service_account" SET SCHEMA identity;
ALTER TABLE public."tenant" SET SCHEMA identity;
ALTER TABLE public."user" SET SCHEMA identity;
ALTER TABLE public."user_role_assignment" SET SCHEMA identity;
ALTER TABLE public."workspace" SET SCHEMA identity;
ALTER TABLE public."workspace_group" SET SCHEMA identity;
ALTER TABLE public."workspace_group_member" SET SCHEMA identity;
ALTER TABLE public."workspace_member" SET SCHEMA identity;

ALTER TABLE public."app_datasource" SET SCHEMA infrastructure;
ALTER TABLE public."cloud_resource_definition" SET SCHEMA infrastructure;
ALTER TABLE public."config_item" SET SCHEMA infrastructure;
ALTER TABLE public."frontend_route_definition" SET SCHEMA infrastructure;
ALTER TABLE public."outbox_events" SET SCHEMA infrastructure;
ALTER TABLE public."secret_ref" SET SCHEMA infrastructure;
ALTER TABLE public."tenant_litellm_virtual_key" SET SCHEMA infrastructure;

ALTER TABLE public."marketplace_command" SET SCHEMA marketplace;
ALTER TABLE public."marketplace_listing" SET SCHEMA marketplace;
ALTER TABLE public."marketplace_review" SET SCHEMA marketplace;
ALTER TABLE public."marketplace_review_comment" SET SCHEMA marketplace;
ALTER TABLE public."marketplace_review_decision" SET SCHEMA marketplace;
ALTER TABLE public."marketplace_review_thread" SET SCHEMA marketplace;

ALTER TABLE public."ingest_preflight_safe_report_records" SET SCHEMA media;
ALTER TABLE public."media_probe_observation" SET SCHEMA media;
ALTER TABLE public."media_stream" SET SCHEMA media;
ALTER TABLE public."source_visual_description_snapshot" SET SCHEMA media;

ALTER TABLE public."notification_channel_binding" SET SCHEMA notification;
ALTER TABLE public."notification_delivery" SET SCHEMA notification;
ALTER TABLE public."notification_delivery_record" SET SCHEMA notification;
ALTER TABLE public."notification_event" SET SCHEMA notification;
ALTER TABLE public."notification_event_definition" SET SCHEMA notification;
ALTER TABLE public."notification_preference" SET SCHEMA notification;
ALTER TABLE public."notification_record" SET SCHEMA notification;
ALTER TABLE public."notification_subscription" SET SCHEMA notification;
ALTER TABLE public."notification_template" SET SCHEMA notification;
ALTER TABLE public."notification_user_inbox" SET SCHEMA notification;

ALTER TABLE public."payment_command" SET SCHEMA payment;
ALTER TABLE public."payment_outbox" SET SCHEMA payment;
ALTER TABLE public."payment_refund" SET SCHEMA payment;
ALTER TABLE public."payment_transaction" SET SCHEMA payment;
ALTER TABLE public."provider_webhook_receipt" SET SCHEMA payment;

ALTER TABLE public."audit_records" SET SCHEMA policy;
ALTER TABLE public."feature_flag_definition" SET SCHEMA policy;
ALTER TABLE public."feature_flag_evaluation_snapshot" SET SCHEMA policy;
ALTER TABLE public."feature_flag_targeting_rule" SET SCHEMA policy;
ALTER TABLE public."navigation_policy" SET SCHEMA policy;
ALTER TABLE public."problematic_data_record" SET SCHEMA policy;
ALTER TABLE public."problematic_data_rule_config" SET SCHEMA policy;
ALTER TABLE public."quarantined_prompt_executions" SET SCHEMA policy;
ALTER TABLE public."quarantined_provider_workers" SET SCHEMA policy;
ALTER TABLE public."quarantined_render_jobs" SET SCHEMA policy;

ALTER TABLE public."prompt_evaluation_result" SET SCHEMA prompt;
ALTER TABLE public."prompt_execution_run" SET SCHEMA prompt;
ALTER TABLE public."prompt_template" SET SCHEMA prompt;
ALTER TABLE public."prompt_template_version" SET SCHEMA prompt;

ALTER TABLE public."delivery" SET SCHEMA publication;
ALTER TABLE public."social_connected_platform" SET SCHEMA publication;
ALTER TABLE public."social_post" SET SCHEMA publication;
ALTER TABLE public."social_post_analytics" SET SCHEMA publication;

ALTER TABLE public."artifact_graph" SET SCHEMA render;
ALTER TABLE public."artifact_node" SET SCHEMA render;
ALTER TABLE public."asset_semantic_metadata" SET SCHEMA render;
ALTER TABLE public."client_export_session" SET SCHEMA render;
ALTER TABLE public."effect_pack" SET SCHEMA render;
ALTER TABLE public."effect_pack_effect" SET SCHEMA render;
ALTER TABLE public."product" SET SCHEMA render;
ALTER TABLE public."product_dependency" SET SCHEMA render;
ALTER TABLE public."render_binding_inputs" SET SCHEMA render;
ALTER TABLE public."render_execution_context" SET SCHEMA render;
ALTER TABLE public."render_graph_execution" SET SCHEMA render;
ALTER TABLE public."render_job" SET SCHEMA render;
ALTER TABLE public."render_job_lifecycle_events" SET SCHEMA render;
ALTER TABLE public."render_job_status_history" SET SCHEMA render;
ALTER TABLE public."render_submission_idempotency" SET SCHEMA render;
ALTER TABLE public."system_canonical_edge" SET SCHEMA render;
ALTER TABLE public."system_canonical_event" SET SCHEMA render;
ALTER TABLE public."system_canonical_graph" SET SCHEMA render;
ALTER TABLE public."unified_graph_edge" SET SCHEMA render;
ALTER TABLE public."unified_graph_node" SET SCHEMA render;
ALTER TABLE public."unified_request_graph" SET SCHEMA render;

ALTER TABLE public."storage_database_binding" SET SCHEMA storage;
ALTER TABLE public."storage_logical_object" SET SCHEMA storage;
ALTER TABLE public."storage_object" SET SCHEMA storage;
ALTER TABLE public."storage_object_placement" SET SCHEMA storage;
ALTER TABLE public."storage_placement_receipt" SET SCHEMA storage;
ALTER TABLE public."storage_reference" SET SCHEMA storage;
ALTER TABLE public."storage_write_intent" SET SCHEMA storage;

ALTER TABLE public."apply_command" SET SCHEMA timeline;
ALTER TABLE public."project_revision_counter" SET SCHEMA timeline;
ALTER TABLE public."review_decision" SET SCHEMA timeline;
ALTER TABLE public."review_thread" SET SCHEMA timeline;
ALTER TABLE public."timeline_comment" SET SCHEMA timeline;
ALTER TABLE public."timeline_review" SET SCHEMA timeline;
ALTER TABLE public."timeline_revision" SET SCHEMA timeline;
ALTER TABLE public."timeline_revision_parent" SET SCHEMA timeline;
ALTER TABLE public."timeline_revision_ref" SET SCHEMA timeline;
ALTER TABLE public."timeline_snapshot" SET SCHEMA timeline;

ALTER TABLE public."wf_artifact_reuse_index" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_completion_event" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_execution_assignment" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_execution_assignment_device" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_execution_attempt" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_execution_backend_selection" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_execution_observation" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_execution_ownership_generation" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_host_readiness" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_host_registration" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_host_resource_snapshot" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_host_resource_snapshot_device" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_host_snapshot_generation_authority" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_local_admission" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_physical_host_connection" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_physical_host_descriptor" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_physical_host_descriptor_device" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_physical_release_confirmation" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_request_work_resolution" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_reservation" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_reservation_device" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_runtime_registration" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_task_lease" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_task_lease_reservation" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_task_ownership" SET SCHEMA worker_fabric;
ALTER TABLE public."wf_worker_runtime_connection" SET SCHEMA worker_fabric;

ALTER TABLE public."schedules" SET SCHEMA workflow;
ALTER TABLE public."user_workflow_definition" SET SCHEMA workflow;
ALTER TABLE public."user_workflow_definition_edge" SET SCHEMA workflow;
ALTER TABLE public."user_workflow_definition_node" SET SCHEMA workflow;
ALTER TABLE public."user_workflow_definition_version" SET SCHEMA workflow;
ALTER TABLE public."workflow_execution" SET SCHEMA workflow;
ALTER TABLE public."workflow_operation_receipt" SET SCHEMA workflow;
ALTER TABLE public."workflow_run" SET SCHEMA workflow;
ALTER TABLE public."workflow_run_step" SET SCHEMA workflow;

ALTER FUNCTION public.advance_social_credential_revision() SET SCHEMA publication;
ALTER FUNCTION publication.advance_social_credential_revision() SET search_path = pg_catalog;

ALTER FUNCTION public.composition_version_immutable() SET SCHEMA composition;
ALTER FUNCTION composition.composition_version_immutable() SET search_path = pg_catalog;

ALTER FUNCTION public.enforce_marketplace_listing_revision() SET SCHEMA marketplace;
ALTER FUNCTION marketplace.enforce_marketplace_listing_revision() SET search_path = pg_catalog;

ALTER FUNCTION public.enforce_storage_placement_receipt_match() SET SCHEMA storage;
CREATE OR REPLACE FUNCTION storage.enforce_storage_placement_receipt_match()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    if not exists (
        select 1
          from storage.storage_object_placement p
          join storage.storage_logical_object o on o.object_id = p.object_id
         where p.object_id = new.object_id
           and p.replica_id = new.replica_id
           and p.provider_id = new.provider_id
           and p.namespace_tenant_id = new.namespace_tenant_id
           and p.namespace_project_id is not distinct from new.namespace_project_id
           and p.namespace_class = new.namespace_class
           and p.region_policy = new.region_policy
           and p.data_classification = new.data_classification
           and p.opaque_locator = new.opaque_locator
           and p.provider_version_token is not distinct from new.provider_version_token
           and p.placement_state = new.placement_state
           and p.region is not distinct from new.region
           and p.committed_digest_algorithm = new.committed_digest_algorithm
           and p.committed_digest = new.committed_digest
           and p.committed_length = new.committed_length
           and p.provider_correlation_id = new.provider_correlation_id
           and (
               new.receipt_purpose <> 'ORIGINAL_ISSUANCE'
               or (
                   new.idempotency_key = o.issuance_idempotency_key
                   and new.semantic_fingerprint = o.semantic_fingerprint
               )
           )
    ) then
        raise exception using
            errcode = '23514',
            message = 'storage placement receipt must exactly match its placement';
    end if;
    return new;
end;
$function$;
ALTER FUNCTION storage.enforce_storage_placement_receipt_match() SET search_path = pg_catalog;

ALTER FUNCTION public.enforce_storage_write_intent_object_match() SET SCHEMA storage;
CREATE OR REPLACE FUNCTION storage.enforce_storage_write_intent_object_match()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    if not exists (
        select 1
          from storage.storage_logical_object o
         where o.object_id = new.object_id
           and o.tenant_id = new.tenant_id
           and o.project_id is not distinct from new.project_id
           and o.issuance_idempotency_key = new.issuance_idempotency_key
           and o.semantic_fingerprint = new.semantic_fingerprint
    ) then
        raise exception using
            errcode = '23514',
            message = 'storage write intent must exactly match its logical object';
    end if;
    return new;
end;
$function$;
ALTER FUNCTION storage.enforce_storage_write_intent_object_match() SET search_path = pg_catalog;

ALTER FUNCTION public.platform_execution_admission_state_guard() SET SCHEMA execution;
ALTER FUNCTION execution.platform_execution_admission_state_guard() SET search_path = pg_catalog;

ALTER FUNCTION public.platform_execution_result_state_guard() SET SCHEMA execution;
ALTER FUNCTION execution.platform_execution_result_state_guard() SET search_path = pg_catalog;

ALTER FUNCTION public.preserve_project_workspace_scope() SET SCHEMA identity;
ALTER FUNCTION identity.preserve_project_workspace_scope() SET search_path = pg_catalog;

ALTER FUNCTION public.preserve_render_execution_context() SET SCHEMA render;
ALTER FUNCTION render.preserve_render_execution_context() SET search_path = pg_catalog;

ALTER FUNCTION public.protect_delivery_record() SET SCHEMA publication;
ALTER FUNCTION publication.protect_delivery_record() SET search_path = pg_catalog;

ALTER FUNCTION public.reject_artifact_identity_mutation() SET SCHEMA artifacts;
ALTER FUNCTION artifacts.reject_artifact_identity_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.reject_conversion_specification_mutation() SET SCHEMA artifacts;
ALTER FUNCTION artifacts.reject_conversion_specification_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.reject_feature_flag_snapshot_mutation() SET SCHEMA policy;
ALTER FUNCTION policy.reject_feature_flag_snapshot_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.reject_storage_placement_receipt_mutation() SET SCHEMA storage;
ALTER FUNCTION storage.reject_storage_placement_receipt_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.reject_usage_fact_mutation() SET SCHEMA billing;
ALTER FUNCTION billing.reject_usage_fact_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.trg_fn_svd_snapshot_immutable() SET SCHEMA media;
ALTER FUNCTION media.trg_fn_svd_snapshot_immutable() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_assert_assignment_has_reservation() SET SCHEMA worker_fabric;
CREATE OR REPLACE FUNCTION worker_fabric.wf_assert_assignment_has_reservation()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare target_assignment varchar(128);
begin
    target_assignment := coalesce(new.assignment_id, old.assignment_id);
    if exists (select 1 from worker_fabric.wf_execution_assignment where assignment_id = target_assignment)
       and not exists (select 1 from worker_fabric.wf_reservation where assignment_id = target_assignment) then
        raise exception 'execution assignment % has no reservation', target_assignment;
    end if;
    return null;
end
$function$;
ALTER FUNCTION worker_fabric.wf_assert_assignment_has_reservation() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_assert_lease_reservation_set() SET SCHEMA worker_fabric;
CREATE OR REPLACE FUNCTION worker_fabric.wf_assert_lease_reservation_set()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare target_lease varchar(128);
declare target_assignment varchar(128);
begin
    target_lease := coalesce(new.lease_id, old.lease_id);
    select assignment_id into target_assignment
      from worker_fabric.wf_task_lease where lease_id = target_lease;
    if target_assignment is not null and (
        not exists (select 1 from worker_fabric.wf_task_lease_reservation where lease_id = target_lease)
        or exists (
            select 1 from worker_fabric.wf_reservation r
             where r.assignment_id = target_assignment
               and not exists (
                   select 1 from worker_fabric.wf_task_lease_reservation lr
                    where lr.lease_id = target_lease
                      and lr.reservation_id = r.reservation_id))) then
        raise exception 'lease % does not cover its assignment reservations', target_lease;
    end if;
    return null;
end
$function$;
ALTER FUNCTION worker_fabric.wf_assert_lease_reservation_set() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_close_terminal_backend_selection() SET SCHEMA worker_fabric;
CREATE OR REPLACE FUNCTION worker_fabric.wf_close_terminal_backend_selection()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    if new.state in ('SUCCEEDED','FAILED','CANCELLED','ABANDONED')
       and old.state is distinct from new.state then
        update worker_fabric.wf_execution_backend_selection
           set active = false, terminal_at = new.updated_at
         where selection_id = new.backend_selection_id and active;
    end if;
    return new;
end
$function$;
ALTER FUNCTION worker_fabric.wf_close_terminal_backend_selection() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_reject_published_snapshot_device_mutation() SET SCHEMA worker_fabric;
ALTER FUNCTION worker_fabric.wf_reject_published_snapshot_device_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_reject_published_snapshot_mutation() SET SCHEMA worker_fabric;
ALTER FUNCTION worker_fabric.wf_reject_published_snapshot_mutation() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_reject_snapshot_generation_regression() SET SCHEMA worker_fabric;
ALTER FUNCTION worker_fabric.wf_reject_snapshot_generation_regression() SET search_path = pg_catalog;

ALTER FUNCTION public.wf_require_snapshot_device_publication_transaction() SET SCHEMA worker_fabric;
CREATE OR REPLACE FUNCTION worker_fabric.wf_require_snapshot_device_publication_transaction()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare snapshot_publication_transaction_id bigint;
begin
    select publication_transaction_id into snapshot_publication_transaction_id
      from worker_fabric.wf_host_resource_snapshot
     where physical_host_id = new.physical_host_id
       and physical_host_incarnation_id = new.physical_host_incarnation_id
       and snapshot_generation = new.snapshot_generation;
    if snapshot_publication_transaction_id is null
       or snapshot_publication_transaction_id <> txid_current() then
        raise exception 'host resource snapshot device membership must be inserted in snapshot publication transaction';
    end if;
    return new;
end
$function$;
ALTER FUNCTION worker_fabric.wf_require_snapshot_device_publication_transaction() SET search_path = pg_catalog;
