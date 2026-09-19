-- Forward classification at this migration boundary; V1/V2/V3 remain immutable.
-- Existence, not activity, establishes the canonical Tenant relationship.
-- NULL (including values V2 could not infer) has no matching canonical Tenant.
-- Preserve every previous field and never clear an existing quarantine.
update user_role_assignment a set scope_unresolved = true
where not a.scope_unresolved
  and not exists (select 1 from tenant t where t.id = a.tenant_id);
-- A Tenant restored before this boundary cannot be distinguished from one always present.
