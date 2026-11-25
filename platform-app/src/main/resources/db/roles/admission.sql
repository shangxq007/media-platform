-- Run as the database owner before Flyway and again before applying runtime grants.
SET search_path = pg_catalog;
DO $admission$
DECLARE
    runtime_roles oid[] := ARRAY['mp_api'::regrole, 'mp_worker'::regrole, 'mp_combined'::regrole, 'mp_readonly'::regrole];
    app_schemas constant name[] := ARRAY[
        'public', 'analytics', 'artifacts', 'billing', 'commerce', 'composition', 'entitlement', 'execution',
        'extension', 'identity', 'infrastructure', 'marketplace', 'media', 'notification', 'payment',
        'policy', 'prompt', 'publication', 'render', 'storage', 'timeline', 'worker_fabric', 'workflow'];
    database_id oid;
BEGIN
    SELECT oid INTO database_id FROM pg_database
    WHERE datname = current_database() AND datdba = current_user::regrole;
    IF database_id IS NULL OR current_user::regrole = ANY(runtime_roles) THEN
        RAISE EXCEPTION 'Runtime role admission requires a separate database owner';
    END IF;

    IF EXISTS (
        SELECT FROM pg_roles r WHERE r.oid = ANY(runtime_roles)
          AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls
               OR has_database_privilege(r.oid, database_id, 'CREATE,TEMP')
               OR EXISTS (SELECT FROM pg_namespace n WHERE n.nspname = ANY(app_schemas)
                          AND has_schema_privilege(r.oid, n.oid, 'CREATE')))
    ) OR EXISTS (
        SELECT FROM pg_shdepend WHERE dbid = database_id AND refclassid = 'pg_authid'::regclass
          AND refobjid = ANY(runtime_roles) AND deptype = 'o'
    ) THEN
        RAISE EXCEPTION 'Runtime roles must have no elevated attributes, DDL privileges or object ownership';
    END IF;

    IF EXISTS (
        SELECT FROM pg_auth_members WHERE member = ANY(runtime_roles)
          AND NOT (member = 'mp_combined'::regrole AND roleid IN ('mp_api'::regrole, 'mp_worker'::regrole)
                   AND inherit_option AND NOT set_option AND NOT admin_option)
    ) OR 2 <> (
        SELECT count(DISTINCT roleid) FROM pg_auth_members WHERE member = 'mp_combined'::regrole
          AND roleid IN ('mp_api'::regrole, 'mp_worker'::regrole)
    ) THEN
        RAISE EXCEPTION 'Runtime role membership must be limited to the inherited COMBINED union';
    END IF;

    -- Restrict ACL checks to the canonical namespaces on either side of V2. Third-party
    -- grantees are retained. PUBLIC and delegated ACLs require an owner decision.
    IF EXISTS (
        WITH namespaces AS (
            SELECT oid, nspowner, nspacl FROM pg_namespace WHERE nspname = ANY(app_schemas)
        ), relations AS (
            SELECT c.* FROM pg_class c JOIN namespaces n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S')
        ), acl_entries AS (
            SELECT relowner AS owner, relacl AS acl, false AS public_usage FROM relations
            UNION ALL SELECT r.relowner, a.attacl, false FROM relations r JOIN pg_attribute a ON a.attrelid = r.oid
            UNION ALL SELECT nspowner, nspacl, true FROM namespaces
            UNION ALL SELECT p.proowner, p.proacl, true FROM pg_proc p JOIN namespaces n ON n.oid = p.pronamespace
        )
        SELECT FROM acl_entries e LEFT JOIN LATERAL aclexplode(e.acl) a ON true
        WHERE e.owner NOT IN (current_user::regrole, 'pg_database_owner'::regrole)
           OR (a.grantee = 0 AND NOT (e.public_usage AND a.privilege_type IN ('USAGE', 'EXECUTE')))
           OR (a.grantee = ANY(runtime_roles) AND (a.is_grantable OR a.grantor <> e.owner))
    ) THEN
        RAISE EXCEPTION 'Incompatible owner, PUBLIC or delegated ACL on application objects';
    END IF;

    IF EXISTS (
        SELECT FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid = d.defaclnamespace
        CROSS JOIN LATERAL aclexplode(d.defaclacl) a
        WHERE d.defaclrole = current_user::regrole
          AND (d.defaclnamespace = 0 OR n.nspname = ANY(app_schemas))
          AND (
              (a.grantee = 0 AND d.defaclobjtype IN ('r', 'S', 'n', 'f')
               AND NOT (d.defaclnamespace = 0 AND d.defaclobjtype = 'f' AND a.privilege_type = 'EXECUTE'))
              OR (a.grantee = ANY(runtime_roles) AND NOT (
                  a.grantee = 'mp_readonly'::regrole AND d.defaclnamespace <> 0 AND n.nspname <> 'public'
                  AND d.defaclobjtype IN ('r', 'S') AND a.privilege_type = 'SELECT' AND NOT a.is_grantable)))
    ) THEN
        RAISE EXCEPTION 'Incompatible default ACL for application runtime roles';
    END IF;
END;
$admission$;
