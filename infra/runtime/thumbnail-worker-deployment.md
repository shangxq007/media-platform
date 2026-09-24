# Thumbnail Activity worker deployment preparation

This runbook is preparation only. It does not stop CT501, change PVE, or deploy an
image. The API and worker roles share the existing workflow/activity contracts and
the single `media-platform-tasks` queue.

## Roles

- API: activate `temporal,api`; retain `WorkflowClient` and HTTP admission; set
  `spring.temporal.start-workers=false` and discover no worker packages.
- Worker: run `platform-thumbnail-worker.jar` with
  `temporal,thumbnail-worker`; set `WebApplicationType.NONE`; discover only
  `com.example.platform.thumbnail`; run as UID 10001.

The worker has no HTTP listener and must not be run with privileged mode, setuid
Bubblewrap, broad capabilities, `seccomp=unconfined`, host PID/network namespaces,
or unrestricted host mounts. Its only network destinations are the approved
Temporal, PostgreSQL and RustFS endpoints. FFmpeg input is read-only and output is
confined to the declared scratch/output root.

## Safe order (future deployment)

1. Build and verify the immutable worker artifact and tool hashes.
2. Start the dedicated worker on an approved VM and verify one intended poller.
3. Stop Activity registration in the CT501 API role while retaining its
   `WorkflowClient` and admission path.
4. Verify queue ownership, namespace, worker identity and sandbox self-check.
5. Run the disposable thumbnail canary.
6. Verify retry, cancellation, ownership fencing, compensation and cleanup.
7. Roll back by restoring the previous CT501 role configuration and stopping the
   dedicated worker.

No workflow, activity, queue, result repository, provider registry or database
authority is introduced by this boundary.
