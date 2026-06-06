-- V2__backfill_audit_record_categories.sql
-- Backfill category for historical audit_records where category IS NULL.
--
-- Strategy:
--   - Only updates records where category IS NULL
--   - Does NOT overwrite existing non-null categories
--   - Uses action prefix and resource_type for classification
--   - Idempotent: safe to run multiple times
--   - Records that cannot be reliably classified are left as NULL
--
-- Category values must match AuditCategory enum in:
--   audit-compliance-module/.../AuditCategory.java

-- 1. Admin operations
UPDATE audit_records
SET category = 'ADMIN_AUDIT'
WHERE category IS NULL
  AND action LIKE 'ADMIN_%';

-- 2. Render operations
UPDATE audit_records
SET category = 'RENDER'
WHERE category IS NULL
  AND (
    action LIKE 'RENDER_%'
    OR action = 'ARTIFACT_CREATED'
    OR action = 'ARTIFACT_TOMBSTONED'
    OR resource_type = 'RENDER_JOB'
  );

-- 3. Data governance (problematic data detection)
UPDATE audit_records
SET category = 'DATA_GOVERNANCE'
WHERE category IS NULL
  AND action LIKE 'PROBLEMATIC_DATA_%';

-- 4. Feature flags
UPDATE audit_records
SET category = 'FEATURE_FLAG'
WHERE category IS NULL
  AND (
    action LIKE 'FEATURE_FLAG_%'
    OR resource_type = 'FEATURE_FLAG'
  );

-- 5. Entitlement management
UPDATE audit_records
SET category = 'ENTITLEMENT'
WHERE category IS NULL
  AND (
    action LIKE 'ENTITLEMENT_%'
    OR resource_type = 'ENTITLEMENT'
  );

-- 6. GraphQL operations
-- GraphQLAuditInterceptor records: action='EXECUTE', resource_type='graphql_operation'
-- Also match any action/resource_type containing 'graphql' (case-insensitive via lowercase)
UPDATE audit_records
SET category = 'GRAPHQL_OPERATION'
WHERE category IS NULL
  AND (
    action LIKE '%GRAPHQL%'
    OR resource_type LIKE '%graphql%'
    OR resource_type LIKE '%GRAPHQL%'
    OR (action = 'EXECUTE' AND resource_type IN ('graphql_operation', 'GRAPHQL_OPERATION', 'GRAPHQL'))
  );

-- 7. NLQ (Natural Language Query)
UPDATE audit_records
SET category = 'NLQ'
WHERE category IS NULL
  AND (
    action LIKE 'NLQ_%'
    OR resource_type IN ('NLQ_QUERY', 'NLQ_REPORT')
  );

-- 8. Provider health
UPDATE audit_records
SET category = 'PROVIDER_HEALTH'
WHERE category IS NULL
  AND (
    action LIKE 'PROVIDER_%'
    OR resource_type = 'PROVIDER_HEALTH'
  );

-- 9. Identity (user operations)
UPDATE audit_records
SET category = 'IDENTITY'
WHERE category IS NULL
  AND (
    action IN ('VIEW_DASHBOARD', 'SUBMIT_FEEDBACK')
    OR resource_type IN ('DASHBOARD', 'FEEDBACK')
  );

-- 10. API requests
UPDATE audit_records
SET category = 'API_REQUEST'
WHERE category IS NULL
  AND (
    action LIKE 'API_%'
    OR action = 'REQUEST_RECEIVED'
    OR resource_type = 'API_REQUEST'
    OR resource_type = 'HTTP_REQUEST'
  );

-- 11. Extension module operations
UPDATE audit_records
SET category = 'EXTENSION'
WHERE category IS NULL
  AND action LIKE 'EXTENSION_%';

-- 12. Extension routing
UPDATE audit_records
SET category = 'EXTENSION_ROUTING'
WHERE category IS NULL
  AND action LIKE 'ROUTING_RULE_%';

-- 13. Extension resource limits
UPDATE audit_records
SET category = 'EXTENSION_RESOURCE'
WHERE category IS NULL
  AND (
    action LIKE 'RESOURCE_LIMIT_%'
    OR action LIKE 'ROLLBACK_POINT_%'
  );

-- 14. Workspace/permission operations
UPDATE audit_records
SET category = 'PERMISSION'
WHERE category IS NULL
  AND action IN ('ROLE_ASSIGN', 'ROLE_REVOKE', 'MEMBER_ADD', 'MEMBER_REMOVE');

UPDATE audit_records
SET category = 'CONFIG'
WHERE category IS NULL
  AND action IN ('WORKSPACE_CREATE', 'WORKSPACE_UPDATE', 'GROUP_CREATE');

-- 15. Prompt operations
UPDATE audit_records
SET category = 'PROMPT'
WHERE category IS NULL
  AND resource_type = 'PROMPT';

-- 16. Policy operations
UPDATE audit_records
SET category = 'POLICY'
WHERE category IS NULL
  AND resource_type = 'POLICY';

-- 17. Sandbox operations
UPDATE audit_records
SET category = 'SANDBOX'
WHERE category IS NULL
  AND action LIKE 'SANDBOX_%';

-- Note: Records that still have category=NULL after all rules cannot be
-- reliably classified. They are left as NULL per the conservative strategy.
-- A future migration (P2-5) may introduce a GENERAL/UNKNOWN category
-- and a NOT NULL constraint if needed.
