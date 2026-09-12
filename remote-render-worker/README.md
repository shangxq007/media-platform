# Fixed runtime execution host

This host executes existing `RuntimeExecutionBundle` commands. It does not accept a Timeline,
choose a provider, assign jobs, register workers, or accept a callback that changes task state.
The former independent worker/job registries and unconsumed Render dispatcher are retired.

Platform composition preserves the accepted lowerer and `RuntimeAdapter`:

```java
var binding = contribution.createRuntimeBinding(platformContext)
    .withCommandExecutor(new HttpWorkerRuntimeCommandExecutor(
        endpoint, assignedRuntimeId, assignedIncarnationId, apiKey, timeout));
```

Use that binding in the existing `RuntimeClosedLoopOrchestrator` binding map. Its Artifact commit
and completion fence remain the only completion authority. Neither a successful HTTP response
nor worker output bytes complete a task. This library composition is tested with isolated HTTP
and runtime adapters; it does not claim a deployed remote provider or an integrated product UI.

The trusted platform transport sends `RemoteWorkerInvocation` encoded with `WorkerInvocationCodec`
to `POST /api/remote-worker/executions`, with `X-Worker-Api-Key`, `X-Worker-Runtime-Id`, and
`X-Worker-Incarnation`. The response is output bytes with an exact `X-Execution-Context` correlation.
Malformed input, unsupported installed requirements, and mismatched identities fail closed.
Non-loopback client transport requires HTTPS. Missing host credentials never enable execution.

Input handles must already be materialized on the target under its configured root by the existing
materialization/deployment setup. This API does not implement file transfer or accept storage URLs.
It verifies input scope, length and content digest; it does not manufacture Artifact identity.
Per-execution scratch is separate from these inputs and retired when the output is closed.

`POST /api/remote-worker/executions/cancel` accepts the exact existing `RuntimeExecutionContext`.
It only signals that context's local sandbox cancellation handle. Acknowledgement is not canonical
task cancellation. Attempt/generation ownership, replay decisions and late completion fencing stay
on the platform; the worker retains no completed-job authority. Transport uncertainty remains an
unknown execution outcome, never an inferred success or confirmed process cancellation.

Deployment must supply runtime id, incarnation id, lifecycle kind, physical host id for a local
runtime, plugin directory, materialization root, API key, and one
`app.remote-worker.executables.<pluginId>` path per installed plugin. The same PF4J host validates
contributions. Advertisement is installed-support candidate evidence only; central eligibility and
assignment checks remain mandatory. There are no new provider or scheduling policies in this change.
