# PostgreSQL 18 verification

Use Java 25, Gradle 9.1.0 and the root dependency constraints: Spring Boot 4.0.4,
JDBC 42.7.14, Flyway 11.20.3, jOOQ 3.21.9. Outputs and Gradle User Home belong outside
this checkout. Use a fresh environment and an isolated cache; never copy personal
Gradle properties, init scripts or credentials. Maven Central resolution requires the
operator's network authorization. Offline verification never downloads dependencies.

## Entry points

From the repository root, with a new external output directory:

```sh
python3 -I -B scripts/test-infra unit --output /external/new-unit
python3 -I -B scripts/test-infra fake --output /external/new-fake \
  --cache /external/gradle-home/caches/modules-2/files-2.1
python3 -I -B scripts/test-infra pg18-local --output /external/new-pg18 \
  --binding /private/owned-container-binding.json \
  --password-file /private/synthetic-test-password \
  --classpath-file /private/platform-app-test-classpath.txt
```

`unit` has no Java/database dependency. `fake` additionally exercises all 32 original
safety contracts, real bounded child processes, dedicated Secret descriptors and actual
Provider factory calls through a fake JDBC Driver. Its pinned dependency lock now uses
JDBC 42.7.14 and jOOQ 3.21.9. Fake success never proves database behavior or OS isolation.

`pg18-local` executes the fixed Java target against a newly owned local PostgreSQL 18
container. The controller must first verify its own container creation receipt, exact
container ID/label, new storage/network and process birth. It creates a mode-0600 binding
with these fields: `container_id`, `identity` (pid/boot/start), `network_namespace`,
`user_namespace`, `source`, `runtime_dirs`, `ca`, `scratch`, `run`, `attempt`, `launch`.
`source` must be this exact checkout; runtime roots must contain the compiled classes and
resolved dependency jars. The runtime classpath file contains their absolute colon-separated
paths. The controller's host network is refused. No JDBC URL or full Spec environment
input is accepted. The private binding is controller configuration, not proof of a privileged
OS identity. This local path is not available to an untrusted test process on a protected runner.

The controller creates `mp_f8_<run>_<attempt>` from template0 with an empty public schema
and a restricted `mp_test_<run>_<attempt>` login, a unique CA/server certificate for private
loopback and synthetic passwords. It keeps management credentials outside the Java mount
namespace. No ports are published; the Java namespace shares only the newly owned container
network and mounts readonly code, jars and CA plus its writable scratch root. It must not
join an existing database or historical container. Resource creation and exact cleanup remain
controller operations; the CLI neither discovers nor deletes arbitrary containers.

The fixed real selection runs 83 methods: the 28 business targets, 30 migrated test methods, 6 graph lifecycle cases and 13
connection-capacity checks, plus six Artifact commit recovery scenarios. Before tests it validates PG18/DB/role/TLS, runs canonical classpath
Flyway migrations, executes jOOQ GenerationTool against the real schema, and compiles every
generated Java source in scratch. `CONTROLLED_TEST` records each actual JUnit outcome.
Source files, migration files and committed generated code are never overwritten by this step.

`compatibility` is only a cached-coordinate/static audit. `runner-plan` prepares deployment
configuration without installation. `pg18` (protected/cloud admission) and `neon` remain
closed before adapter/Secret access; local-container authorization does not open them.

## Build and runtime isolation

Gradle needs local IPC for its FileLockContentionHandler. A private Linux user/mount/PID/
network namespace with loopback supports it while denying external routes. Bind only the
checkout, private cache/output directories, JDK/distribution and public CA trust store;
never mount the host home or daemon socket into build JVMs. Gradle 9 needs the project
mount writable, so redirect every project's buildDirectory, project cache and User Home
externally and reconcile the complete source manifest after execution. A separately
network-authorized resolution run may share network while preserving the filesystem boundary.

API and Worker are separate launch artifacts: `:platform-app:bootJar` and
`:platform-app:ffmpegWorkerBootJar`. The `temporal` profile is client-only. The worker adds
`ffmpeg-worker` and discovers only its capability packages and `workflow.temporal.mediatask`;
it must not poll `workflow-process`. The API role guard rejects accidental worker configuration.
The namespace postprocessor is registered and both clients and readiness use the same namespace.

`pg18-integration` is an isolated integration profile, not production admission. It uses the
formal OIDC JWT and persisted Account/membership/RBAC authorities; dev tokens and HMAC fallback
remain off. Supply an isolated issuer/JWKS and synthetic identities, explicit database/Flyway
credentials and Temporal namespace. AI/payment services are disabled, never replaced by successful
fake calls. The production profile and its commercial/AI safety requirements remain unchanged.

The FFmpeg provider emits H.264 using libx264. Stage its explicit Linux tool dependency with:

```sh
./gradlew :ffmpeg-provider-module:stageFfmpegLinuxRuntime
```

This resolves `org.bytedeco:ffmpeg:7.1.1-1.5.12:linux-x86_64-gpl` into the module build output,
separate from the application classpath. Preserve bundled license files and review GPL obligations
before redistribution. Install or readonly-bind the entire runtime directory under a protected
`/usr/local/lib/media-platform/ffmpeg` in the runner filesystem, and configure worker ffmpeg/ffprobe
paths there. A binary copied alone to a sandbox input loses adjacent shared libraries. No host
installation is implicit. The executable start probe proves start/version only; actual media
execution must still validate codecs, output bytes, digest and Artifact readback.

## Lifecycle and security

Provisioner owns resource receipts, precise identity/ownership and restricted SQL roles.
Supervisor owns exclusive Journal use, controlled launch, complete Job proof and cleanup ordering.
ProcessJob uses the fixed Java entry, one-shot Spec fd0 and a distinct bounded Secret fd; fd2
retains native JVM stderr. RunConnectionSpec v2 binds cloud tuples; v3 binds only a local container
ID, private network namespace and exact run/attempt DB/role. Both enforce TLS verify-full and CA.
Provider schema derivation cannot change host, port, DB, role or TLS. Hikari pools bind every
physical connection to the schema rather than changing one borrowed connection's search_path.

Fresh subreaper ECHILD proof must precede fence/drain/delete. Main exit, setsid, vanished process
 groups, lock release or timeout do not prove complete Job exit. Unknown identity/observation,
uncertain create/delete, ledger loss/corruption/rollback and live descendants retain resources in
QUARANTINED. Hashes detect corruption; protected control ownership and reconciliation are required
for rollback resistance. External daemon delegation needs a protected executor/cgroup.

Real deployment remains `DEPLOYMENT_ISOLATION_NOT_VERIFIED` until the prepared broker/executor is
installed on an authorized dedicated runner and cross-UID, proc/ptrace, mounts, network, Journal,
policy and complete-job denial tests are actually executed. See deployment/README.md. Local user
namespaces and synthetic permission models do not certify D01–D05, Neon/BWS or production scope.

## Regression and maintenance

`contracts/safety.tsv` retains the original 32 requirements. Independent fixtures record actual
calls and forbidden side effects. `test-equivalence.json` records PG18 corrections to obsolete
business assumptions; the 28 method identities remain, with 239 assertions after Owner-authorized single-V1 consolidation (accepted V1+V2 input245; only six V2 bookkeeping assertions retired).
The six migrated classes retain 30 methods and now 91 assertions (previously 90): rollback now
requires genuine FK SQLSTATE 23503 rather than accepting an obsolete-column error. Artifact ownership
uses the canonical Artifact model; RESTRICT deletion requires PostgreSQL SQLSTATE 23001.
Flyway history distinguishes schema creation from the exact V1 baseline plus approved V2 graph execution migration. V1 bytes remain unchanged; no a1 history is rewritten to accept a changed V2.
No effective test was disabled or removed to pass compilation or execution.

Historical review packets remain audit records. Current real build/database/runtime evidence is
run-specific and must include commands, cwd, versions, exit codes, exact Candidate identity,
per-test outcomes, task-owned resource receipts and precise cleanup/retention results. A prior run's
PASS is never evidence for a new Candidate. B02/B07/B08 and formal First Render acceptance must be
reported from their actual checks and never inferred from Fake success.

## First Render verification boundary

The graph path projects through Render's public GraphRenderLifecycle contract and the existing
Render state machine. Render alone writes job status, accepted output, quota settlement and typed
terminal facts in one transaction. A projection failure is a durable retriable Temporal activity;
Temporal cannot report successful completion before this activity succeeds. Deterministic failure
and cancellation project an unsuccessful terminal state. Failed/cancelled jobs reject late success.

The final task is selected from the plan's typed FinalArtifactExpectation/RENDER_MASTER declaration
and exact physical-task membership, never row order or task order. Intermediate publications are
DERIVED_MEDIA. The actual current native attempt/generation and full tenant/project/job scope bind
publication IDs, storage sessions, Render's expected output, fenced completion and final Artifact.
V2 adds only Render's graph binding/output reference plus scope/fence foreign keys, not another
Execution status or Artifact commit authority.

Use authenticated GET /api/tenants/{tenant}/projects/{project}/render-jobs/{job}/result to query the
terminal result and finalArtifactId. Artifact /access returns a relative authenticated /content API
route; it contains no credential, signed URL, physical location or independent expiry. GET that route
with the normal bearer authentication. Artifact verifies scope/availability, Storage verifies exact
length/digest through the registered provider, and the HTTP response contains actual video bytes,
Content-Length, MIME and SHA-256 ETag. Current bounded downloads support up to 64 MiB and validate the
whole object before any bytes are disclosed; larger files fail closed. Every physical read closes
its stream. Local object reads walk directory handles without following symlinks.

See docs/testing/first-render.md for commands, ownership and recovery. Full product acceptance still
requires an actual authenticated API→workflow→video→terminal query→HTTP download run and independent
review of that exact Candidate. Fake success alone is never acceptance; no test SQL may advance job
status or choose the final Artifact.

Canonical completion now carries the committed ArtifactId, with exact duplicate-event identity
checks and reuse activation bound to that same Artifact. Opaque commit-reference/time columns are
retired from the write path; canonical V1 includes the accepted graph execution constraints. Artifact commits persist a full receipt in
the existing audit_provenance field and serialize same-tenant/key attempts with a transactional
advisory lock. Exact immutable intent replays its durable result; identity, scope, digest, storage,
provider, role or provenance drift conflicts. Retry clock readings are not new commit identities.
Rows without a valid matching receipt are never adopted. Existing storage publications are also
checked for exact length/digest before reuse. Errors after publication retain bytes for reconciliation.

Independent-review corrections: the base runtime explicitly defaults to API, while the worker
profile pins WORKER; the Bootstrap continues filtering a caller's PLATFORM_RUNTIME_ROLE override.
Real ConfigData tests cover both legal profiles. Receipt replay reconstructs every declared
provenance edge, compares every result field, reconciles the complete canonical outgoing relation
set and verifies parent tenant ownership. Structured but conflicting JSON or relation drift is
rejected without new rows, rather than accepted as an intact receipt.
