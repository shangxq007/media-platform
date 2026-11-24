-- Qualify catalog deparsing consistently for the frozen PostgreSQL 18 V1 lineage.
-- Roles, grants, data values and sequence positions vary across valid installations.
DO $verify$
DECLARE
    fingerprint text;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN RETURN; END IF;
    IF NOT EXISTS (SELECT FROM public.flyway_schema_history WHERE version='1' AND success)
       OR EXISTS (SELECT FROM public.flyway_schema_history WHERE version='2' AND success) THEN RETURN; END IF;
    PERFORM set_config('search_path', 'pg_catalog', true);
    SELECT digest INTO fingerprint FROM (
        WITH relations AS (
            SELECT oid, relname, relkind, relpersistence, relrowsecurity, relforcerowsecurity, reloptions
            FROM pg_class WHERE relnamespace = 'public'::regnamespace
        ), shape(item) AS (
            SELECT jsonb_build_array('relation', relname, relkind, relpersistence, relrowsecurity, relforcerowsecurity, reloptions) FROM relations
            UNION ALL SELECT jsonb_build_array('column', r.relname, a.attnum, a.attname, format_type(a.atttypid,a.atttypmod),
                a.attnotnull, a.attidentity, a.attgenerated, pg_get_expr(d.adbin,d.adrelid), col.collname)
                FROM relations r JOIN pg_attribute a ON a.attrelid=r.oid
                LEFT JOIN pg_attrdef d ON d.adrelid=r.oid AND d.adnum=a.attnum
                LEFT JOIN pg_collation col ON col.oid=a.attcollation
                WHERE a.attnum>0 AND NOT a.attisdropped AND r.relkind IN ('r','v','m','p','c','f')
            UNION ALL SELECT jsonb_build_array('constraint', r.relname, c.conname, pg_get_constraintdef(c.oid), c.convalidated)
                FROM pg_constraint c JOIN relations r ON r.oid=c.conrelid
            UNION ALL SELECT jsonb_build_array('index', pg_get_indexdef(i.indexrelid), i.indisvalid, i.indisready)
                FROM pg_index i JOIN relations r ON r.oid=i.indexrelid
            UNION ALL SELECT jsonb_build_array('trigger', pg_get_triggerdef(t.oid), t.tgenabled)
                FROM pg_trigger t JOIN relations r ON r.oid=t.tgrelid WHERE NOT t.tgisinternal
            UNION ALL SELECT jsonb_build_array('routine', pg_get_functiondef(p.oid))
                FROM pg_proc p WHERE p.pronamespace='public'::regnamespace
            UNION ALL SELECT jsonb_build_array('sequence', r.relname, s.seqstart, s.seqincrement, s.seqmax, s.seqmin, s.seqcache, s.seqcycle)
                FROM pg_sequence s JOIN relations r ON r.oid=s.seqrelid
            UNION ALL SELECT jsonb_build_array('type', typname, typtype, typcategory)
                FROM pg_type WHERE typnamespace='public'::regnamespace
            UNION ALL SELECT jsonb_build_array('extension', extname, extversion)
                FROM pg_extension WHERE extnamespace='public'::regnamespace
            UNION ALL SELECT jsonb_build_array('policy', r.relname, p.polname, pg_get_expr(p.polqual,p.polrelid), pg_get_expr(p.polwithcheck,p.polrelid))
                FROM pg_policy p JOIN relations r ON r.oid=p.polrelid
        )
        SELECT encode(sha256(convert_to(string_agg(item::text, E'\n' ORDER BY item::text COLLATE "C"),'UTF8')), 'hex') FROM shape
    ) AS baseline(digest);
    IF fingerprint <> '5ae12c58fb0b50023107fdd41ba3daa4de607106092156f19df40995f88f0c43' THEN
        RAISE EXCEPTION 'Unsupported PostgreSQL V1 catalog. An isolated historical transition is required.';
    END IF;
END;
$verify$;
