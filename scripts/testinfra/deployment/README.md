# Protected synthetic Linux runner

RUNNER_NOT_SELECTED; DEPLOYMENT_ISOLATION_NOT_VERIFIED. No service, account, mount, cgroup or
network rule has been installed or altered. Current desktop scope is excluded. Prefer a
project-dedicated Linux CI runner with a named administrator and immutable image/runtime.

## Implemented execution chain

protected_control.py (fixed root-owned config; euid=control) -> authenticated Unix broker
(root executor service) -> single-admission subreaper -> test-UID Java. The control path uses
only FakeNeon/SQL and freshly generated synthetic run passwords. No BWS/Neon Secret accessor,
real HTTP client or enable-live flag is present. Normal pg18/neon CLI commands stay rejected.

Both ends check kernel SO_PEERCRED. The socket is root:control0660 under a protected /run
root. START accepts exactly two anonymous read-pipe fds via SCM_RIGHTS: framed Spec and
separate Secret. Strict fields/digest/run/attempt/launch/phase/CA and replay checks precede
spawn. Broker reconstructs the command from the admin-owned pinned LaunchPolicy; request
cannot select executable/classpath/Main/mode. Broker reads the run-only password in protected
memory and re-pipes it to Java, never in argv/env/Journal/logs. Private observer fds stay out of
Java. Standard fd2 remains stderr, and publication is bounded/redacted after EOF.

Broker persists STARTING intent before allocation/spawn, then actual executor PID/boot/start.
Existing intent, occupied domain or run/attempt replay cannot create another Job. New control
connections query the existing live observer, using fresh numbered RPCs. Controller restart
stays QUARANTINED with reconciliation and zero SQL/delete; no password reissue. Broker restart
cannot turn stored identities into live proof; retained unknown Jobs block admission. A missing
control Journal initializes only when the independently queried broker history/domain namespace
is empty; existing history requires explicit reconciliation even if a prior Job was closed.
This intentionally favors retention over unattended reset.

Protected executor creates exactly one fixed-root child cgroup, writes pids/memory/swap/CPU
limits, assigns the JVM BEFORE exec, then drops groups/GID/UID and all root capabilities and
sets no_new_privs/non-dumpable. Subreaper stays outside the test domain. Fresh ECHILD after
Main reap AND an unchanged empty kernel-cgroup identity are required for complete proof.
Cgroup-bound startup persists the observer and effective test UID/GID before acknowledging
the launch. The observer persists its terminal proof before publishing completion, and the
broker retains a fresh verified copy. Sanitized failure categories exclude credentials and
exception text. A failed or missing proof remains quarantined.
Cgroup snapshots alone never prove fork-lineage completion. Stop uses exact birth/pidfd
signals. Domain replacement/observation failure/setsid survivor -> UNKNOWN -> quarantine.
A 300s watchdog requests bounded owned stop and persists quarantine; it performs no resource
cleanup. After fresh proof and observer shutdown, only the exact empty owned domain may be
removed. Journal/Job records remain. External delegation is denied by the planned IPC/mount/
network/capability configuration and must be tested on the selected runner.

## Principals and privileges

| Subject | Identity / authority | Data access |
| --- | --- | --- |
| admin | root/approved runner administrator | immutable install, root policy and job parent |
| Provisioner+Supervisor | b03-control UID/GID61001 | private Journal0600; root policy0640 read group; no arbitrary executable |
| executor broker/subreaper | root UID0 inside restricted systemd service | only SETUID/SETGID/KILL capabilities; private executor records; no management credentials |
| Java/JUnit | b03-test UID/GID61003, no groups/capabilities | immutable runtime+CA read; private scratch; run-only Spec/password |

The executor intentionally shares root identity with the installation authority; it is a
small privileged component, not another unprivileged helper pretending it can change UID.
Control/executor/test are distinct. Root executor lacks DAC_OVERRIDE and SYS_PTRACE; service
read-only mounts prevent root-owned runtime/policy mutation. The test cannot write the
cgroup tree/socket/policy or signal/attach the observer. These are CONFIGURED requirements,
not verified cross-UID facts in this task. SELinux/CI/cgroup permission semantics need real
runner execution; root identity alone does not prove least privilege.
The service carries only the configured SETUID, SETGID and KILL capabilities as ambient
capabilities inside the same bounding set. The credential pipe is created using a temporary
thread-local filesystem UID matching the test identity, then the original filesystem UID is
restored before launch. Failure to establish or restore that identity fails closed.

## Files and immutable runtime

| Path | Owner/mode and use |
| --- | --- |
| /opt/media-platform/testinfra | root0755 directories, root0644/0755 code, read-only service mount |
| /etc/media-platform/testinfra/{executor,control,launch}.json | root0640, restricted control-read group; no test access |
| /etc/media-platform/testinfra/ca/ca.pem | root0644; CA dir0755; no writable ancestor |
| /var/lib/media-platform/testinfra | control0700, Journal/lock0600; hidden from executor service |
| /var/lib/media-platform-executor | root0700, atomic Job records and per-Job config0600 |
| /var/lib/media-platform/testinfra-scratch | test0710:root, private1GiB bounded mount (executor traverse) |
| /run/media-platform-executor | root0711, socket root:control0660; no test writes |
| /sys/fs/cgroup/media.slice/media-platform.slice/media-platform-jobs.slice/<launchID> | protected root owner, no test delegation |

Admin reader rejects writable/non-admin ancestors, symlinks, duplicate fields and excess
bytes. LaunchPolicy pins the actual nonsymlink JDK25 root, entire code_home, classpath, CA,
Python and executor, not just /usr/bin/java alternatives. Launch mode remains synthetic
probe/provider-tests. The image must contain a self-contained immutable JDK tree without
external mutable symlinks; incomplete policies fail closed. Local same-UID mechanical tests
retain the existing limited cache policy; that is not the protected deployment policy.

## Reproducible package and exact deferred installation

    python3 -B scripts/testinfra/deployment/stage_install.py --stage /new/external/bundle --runner-id <specific-approved-runner-ID>

This packager copies immutable code, two systemd units, sysusers and fixed control/executor
config plus hash manifest and ordered installation-plan.json. It does not create accounts,
chown/copy into host roots, run systemd or start a runner. Unit tests exercise packaging with
an explicitly synthetic fixture ID; no real runner is selected by that test.

The separately authorized administrator must: verify target host/image and UID/GID/path
conflicts; install bundle with root ownership/read-only mounts; create/map control/test
accounts from sysusers; supply pinned compiled classpath/JDK/CA and root launch.json with
runtime_home and code_home plus full digest set; create private Journal/records/scratch;
install units and enable cpu/memory/pids only in the empty jobs.slice parent; verify effective
capability/mount policy; then authorize starting the slice/broker and control entry. Runtime
or target dependencies are not downloaded by packaging. Missing paths/policy/socket are
BLOCKED. No auto-unlink of incumbent sockets or takeover of pre-existing run records.

Budgets:1 Job,64 tasks,2 CPU,2GiB Job RAM,0 swap,1GiB scratch,300s wall; stop grace2s. Broker
and observer overhead has a separate service cap. Kernel admission, aggregate limits,
PrivateNetwork/IPC isolation and read-only cgroup exceptions require installed-runner checks.
The root service/installation operations above still require Owner permission.

## Synthetic permission verification

verify_denials.py runs as the expected non-root test UID. It never prints/read Secret bytes
or writes files: unexpected allowed open is FAIL. Missing fixtures are BLOCKED. It checks
synthetic manager marker denial, runtime/policy/CA/Journal write denial, broker entry denial,
then exact synthetic process birth/pidfd proc/signal/ptrace denials, cgroup write denial and a
bounded real setsid child's retained domain. Privileged fixture metadata must be root-owned
nonwritable /run/media-platform-executor/probe.json with phase=synthetic, test_uid,
control_identity/observer_identity={pid,boot,start}, and the exact Job domain. No arbitrary
PID arguments. No real management credentials are ever used. A control/admin fixture must
keep those synthetic processes alive through the probe. Crash-domain recovery and aggregate
network/mount denial cases in synthetic-contracts.tsv remain PLANNED for that runner.

Offline test_executor.py exercises actual kernel peer UID checks and descriptor transfer,
Java Provider/native stderr, replay rejection, reconnection, retained unknown restart and
cgroup-parser failures. These same-UID/model-domain tests explicitly do not count as cross-UID
or kernel cgroup PASS. Process-local UNIX-only seccomp denies INET sockets without host rules.

## Recovery, rollback and live gates

Disable new admission first; record exact owned Job/resource identities; controlled stop only
through the trusted observer. If unknown, preserve domain/records/Journal and seek admin
reconciliation, never blanket kill, unlink incumbent sockets or clean by resource name. Restore
only exact verified installation before-images after no active owners; never remove oldT1,
pre-existing users/services or unknown resources. Broker or Supervisor restart never authorizes
cloud deletion. Resource cleanup still requires full Job proof, fence, precise backend drain,
zero recheck, fresh complete tuple and exact delete/absence confirmation.

D01–D03 actual permission/domain tests remain unverified. D04 needs dedicated test organization/
Project/key and protected seed/default/production IDs, BWS Machine Account grants, quotas and
cost/cleanup approval. D05 needs actual PG18/TLS/template0/empty DB/effective role/session/delete
proof plus JDBC42.7.14/Flyway11.20.3/jOOQ3.21.9. Compatibility output lists exact missing cache
coordinates; no download or old-version substitution. Full target compile, 28DB, six migrated
DB classes and B02/B07/B08 remain unclosed; no E2E or integration authorization is implied.
