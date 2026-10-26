# Canonical development baseline

Open this repository root in VS Code. Select a JDK25 installation through Java: Configure Java
Runtime. The Gradle wrapper pins9.1.0; SpringBoot4.0.4, JDBC42.7.14, Flyway11.20.3 and jOOQ3.21.9
are declared by the build. No machine-specific SDK path or credential belongs in VS Code settings.

## Build

From the repository root, with JAVA_HOME selecting JDK25:

```sh
GRADLE_USER_HOME=/tmp/media-platform-gradle ./gradlew --no-daemon --max-workers=2 \
  --project-cache-dir /tmp/media-platform-project-cache -PbuildRoot=/tmp/media-platform-build \
  compileJava testClasses assemble :shared-kernel:testFixturesJar
```

Use a new or explicitly owned external directory instead of these example /tmp paths when running
concurrently. API is `/tmp/media-platform-build/_platform-app/libs/platform-app.jar`; Worker is
`platform-ffmpeg-worker.jar` in the same directory. All active modules compile; hand-written sources
remain separate from the committed jOOQ snapshot. Never copy a personal Gradle configuration or token
into a shared validation cache.

## Real local First Render validation

Requires Linux, rootless Podman already available, bubblewrap, nsenter, OpenSSL, curl, JDK25 and
host FFmpeg. Managed GPL FFmpeg with H.264 support is staged from the declared Bytedeco artifact;
host FFmpeg only prepares synthetic source media. This command is the reproducible full entry:

```sh
python3 -I -B scripts/dev/first-render --output /tmp/media-platform-first-render-new
```

The output path must not exist and must be outside the repository. The entry builds API/Worker,
pulls official PostgreSQL18/Temporal images into its own container store, creates a network-isolated
new PG18 database/role/volume and a dedicated Temporal namespace, migrates the empty database,
executes91 PG scenarios, starts separate API and WORKER through the existing Bootstrap profiles,
submits authenticated formal HTTP requests, verifies terminal state/final output/download/hash/media,
and tests authorization and cancellation. Synthetic OIDC/accounts/media/credentials are fixtures,
never cloud credentials. It does not publish a host database port or inspect other container stores.

Optional `--gradle /path/to/gradle9.1/bin/gradle` uses an existing distribution. Optional `--cache
/path/to/modules-2` seeds only an explicitly chosen dependency cache; `--offline` forbids build
network resolution, and requires the needed distribution/artifacts already available. Image pulls
still use only public official registries. No personal container authentication configuration is read.

On success the entry stops all application descendants, confirms no running workflows or database
sessions, then removes only its exact labelled containers and volume. Source, video and external
reports remain. On failure or uncertain ownership/liveness it preserves resources and evidence;
inspect `commands.json`, `07-runtime/run.json`, process proofs and `07-runtime/local-context.json`
before any manual recovery. Never prune or kill by name. Cgroup aggregate limits are not guaranteed
on a desktop host: process/database limits and two-CPU affinity bound this small synthetic fixture;
formal runner admission remains separate.

API and Worker use the same official jars for normal deployment. API uses Bootstrap `--profile api`;
Worker uses `--profile worker` and the ffmpeg-worker configuration. Real datasource/OIDC/Temporal and
Storage endpoints must be provided by an authorized protected launcher; see scripts/startup/README.md
and docs/testing/first-render.md. Do not manually inherit cloud management secrets into either JVM.
The local validation entry demonstrates the exact independent processes using synthetic configuration.

## Schema and generated code

There is exactly one active migration: platform-app/src/main/resources/db/migration/V1__baseline.sql.
It incorporates the accepted graph execution/final Artifact/native fencing constraints. This is a
pre-release greenfield baseline; do not apply it to old populated development databases or edit their
Flyway history. Create a fresh owned database. Original accepted V1+V2 bytes remain in immutable audit
snapshots, not in the active migration directory.

jOOQ3.21.9 output under typed-schema-module/src/main/java/.../jooq/generated is versioned and must be
regenerated from actual PG18 using typed-schema-module/jooq-codegen.xml. The local validation records
fresh introspection and compiled generated sources externally. Review staged output before installing
it; never mix generated and hand-written contract classes. `verifyJooqGeneratedSources` checks exact
canonical table/record identity parity and generated layout. Baseline consolidation compared all
business DDL and447 generated files between original V1+V2 and newV1, excluding only Flyway bookkeeping,
dump restrict randomness, and deliberately distinct test-role ownership/ACLs. Structural constraints,
functions, indexes and views are included.

## Architecture and adoption

Render owns job transitions/history/final-output acceptance and completion quota/outbox transaction.
Artifact owns commits/replicas; native Execution owns fenced attempts/completions; Temporal owns
scheduling/retry/cancel; Storage owns bytes. First Render is a platform use-case probe, not a second
render lifecycle. Local downloads currently validate whole objects up to64MiB before responding.

Original dirty repository changes are not overwritten or assumed obsolete. Divergent later work is
preserved in the external integration audit with per-path dispositions; choosing it over the accepted
implementation requires explicit reconciliation. The formal repository is the sole ongoing development baseline. Original dirty sources and linked
worktrees remain protected historical evidence. Develop on a task branch from main, verify and review
before an authorized fast-forward, then remove only the safely merged task branch/worktree. Opening a
workspace never authorizes remote push or disposal of historical files.

`DEPLOYMENT_ISOLATION_NOT_VERIFIED`: formal protected runner/executor/cgroup containment, sustained
native RECOVERY_HOLD capacity recovery, production Neon/BWS permission scopes and production readiness
remain separate gates. BMF/advanced editing are outside the minimum FFmpeg closure.
