# PostgreSQL migration lifecycle

The API and Worker share one PostgreSQL 18 database. Flyway applies the ordered scripts under
`platform-app/src/main/resources/db/migration`. V1 is immutable. Its SHA-256 is
`f6755e0663596721fa824cb436a5ed548475213fda11d3655e0899fcdce3e580`, with Flyway checksum
`-1975529748`. V2 moves the existing application objects into domain schemas in one transaction.
The Flyway beforeMigrate callback checks an installed V1 against the frozen PostgreSQL 18 catalog before relocation. Both the original catalog and the equivalent PostgreSQL 18 logical-restore rendering of its CHECK constraints are recognized. Flyway also validates the applied migration checksum. The migration history stays in `public.flyway_schema_history`.

Provision the migration owner, `mp_api`, `mp_worker`, `mp_combined` and `mp_readonly` before
rollout. The migration owner owns the application database and its managed objects. Revoke
database CREATE and TEMP from PUBLIC. Give each runtime role CONNECT and set its database-specific
`search_path` to `pg_catalog`. The only runtime memberships are:

```sql
GRANT mp_api, mp_worker TO mp_combined WITH INHERIT TRUE, SET FALSE;
```

For COMBINED, set `PLATFORM_RUNTIME_ROLE=COMBINED` and supply `mp_combined` and its dedicated
password through the existing datasource secret inputs. Use `mp_api` and `mp_worker` for separate
processes. Fresh Compose initialization sets a login password for `mp_combined` only when
`COMBINED_DATASOURCE_PASSWORD` is supplied. Existing clusters need owner-side provisioning before
migration. The migrator uses `public` as Flyway's explicit default schema.
Run the flyway bootstrap profile with the application jar and this command:

```sh
java -Dloader.main=com.example.platform.DatabaseMigration -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

The migration entry checks the database and role prerequisites before Flyway and again in the
post-migration grant transaction. It rejects runtime object owners, elevated attributes, unsafe
memberships, delegated runtime ACLs and incompatible PUBLIC or default ACLs. Keep unrelated
external grants in place. Resolve incompatible starting privileges with the database owner before
retrying. The managed namespaces are reserved for objects owned by the migrator.

In the grant transaction, `platform-app/src/main/resources/db/roles/runtime.sql` replaces direct
managed-role schema, table, column, routine and sequence ACLs with the intended grants. This
removes excessive direct V1 privileges carried through V2. Revocation uses RESTRICT, retaining
independent third-party grants and failing if removing a delegated grant would be necessary.
If the grant transaction fails after V2 commits, leave the application stopped, resolve the grant
failure and rerun the migration entry before serving traffic.

Each application process validates installed Flyway history using its ordinary runtime identity.
Application authorization scopes tenants and projects inside the API process.

## Upgrading the frozen V1

Inventory the source schema, owners, grants and the current Flyway history. Check the physical
catalog against an independently initialized database from the matching V1. Check for unexpected
consumers, active executions, workflows and storage bindings. For a retained database, obtain
separate rollout authorization, take a restorable backup including sequences and roles, stop
admissions and drain writers before scheduling the cutover. PostgreSQL must take exclusive locks
while moving tables. Existing prepared queries and running application versions reference the old
physical names. Restart both processes using the matching code and verify effective privileges.

PostgreSQL rolls back uncommitted DDL if V2 fails. For recovery after commit, restore a verified
backup into a separate target. Coordinate any post-cutover writes before routing traffic back.

## Historical V1 with checksum 586329146

The source migration is recoverable at commit `4040ccfa`. Commit `93119de9` changed the V1
declaration of `first_render_idempotency` to `render_submission_idempotency` together with its
consumer. Treat them as distinct Flyway lineages. Reject an unknown checksum or materially
different catalog. Preserve the source database, its history and its backups.

1. Restore a read-only consistent dump of the source application schema into an isolated staging
   database. Initialize a reference from the historical Git migration and compare equivalent
   restored catalogs, including indexes, constraints, functions and triggers. Inventory grants
   separately from structural identity. Verify no external dependency would be stranded.
2. Inspect the old reservation table and its callers. The inspected historical table was empty.
   If it contains records, stop and resolve their request, replay and lifecycle obligations before
   transfer. Never discard a reservation based on its name alone.
3. Initialize an empty independent target through canonical V1. Verify V1 checksum, structural
   identity and the fresh seed population. Transfer business rows and sequence states from the
   offline staging copy, omitting the historical migration journal and the verified empty
   obsolete table. Replace only fresh target seeds. Keep checks, uniqueness and foreign keys in
   force. In the offline target transaction defer FKs for ordering, suspend application-time
   triggers, check all deferred constraints and restore the exact original definitions. On
   failure discard the task-owned target and initialize a new one, since sequence state is
   nontransactional.
4. Compare per-table binary row digests and all sequence values and `is_called` flags. Apply the
   canonical V2 through Flyway. Compare the final catalog against a fresh installation and
   repeat the data, privilege and application checks. Keep private data in the isolated copy.
   The external verification report records the actual transition and its recovery limits.

The single code generator introspects the final catalog. Regenerate using
`typed-schema-module/regenerate-jooq-schema.sh` and compare both generation passes.

Local Compose provisions a dedicated `pgdata18` volume for fresh PostgreSQL 18. Existing PostgreSQL 16 volumes require a separately authorized logical transfer.
