-- V3__enforce_audit_record_category_constraints.sql
-- Enforce category constraints on audit_records.
--
-- Strategy:
--   1. Backfill remaining NULL categories to 'UNKNOWN' (from V2 conservative backfill)
--   2. Add CHECK constraint: category must be a valid AuditCategory enum value
--   3. Add NOT NULL constraint: category must always have a value
--
-- This migration is idempotent for Flyway (runs once).
-- All category values must match AuditCategory enum in:
--   audit-compliance-module/.../AuditCategory.java

-- Step 1: Backfill remaining NULL to UNKNOWN
UPDATE audit_records
SET category = 'UNKNOWN'
WHERE category IS NULL;

-- Step 2: Add CHECK constraint (PostgreSQL syntax)
-- H2 compatible: CHECK constraints use standard SQL syntax
ALTER TABLE audit_records
ADD CONSTRAINT chk_audit_records_category
CHECK (category IN (
    'CONFIG',
    'PROMPT',
    'POLICY',
    'PLUGIN',
    'MANUAL_RETRY',
    'PERMISSION',
    'EXTENSION',
    'EXTENSION_ROUTING',
    'EXTENSION_RESOURCE',
    'SANDBOX',
    'ENTITLEMENT',
    'GRAPHQL_OPERATION',
    'PROVIDER_HEALTH',
    'API_REQUEST',
    'FEATURE_FLAG',
    'NLQ',
    'GENERAL',
    'RENDER',
    'DATA_GOVERNANCE',
    'IDENTITY',
    'ADMIN_AUDIT',
    'UNKNOWN'
));

-- Step 3: Add NOT NULL constraint
-- PostgreSQL syntax; H2 also supports ALTER COLUMN SET NOT NULL
ALTER TABLE audit_records
ALTER COLUMN category SET NOT NULL;
