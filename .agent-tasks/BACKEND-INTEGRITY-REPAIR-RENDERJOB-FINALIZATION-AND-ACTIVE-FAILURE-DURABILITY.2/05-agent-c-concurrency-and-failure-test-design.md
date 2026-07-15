# Agent C — Concurrency and Failure Test Design

## Test Environment

```text
@SpringBootTest(webEnvironment = RANDOM_PORT)
Testcontainers PostgreSQL
real TCP HTTP requests
real Spring ApplicationContext
CyclicBarrier/CountDownLatch for deterministic overlap
```

## Test Cases

### Test A — Normal Start

```text
Create RenderJob (QUEUED)
POST /start
Assert:
  claim winner = 1
  selector = 1
  selected_provider write = 1
  render = 1
  logical execution = 1
  final state = COMPLETED
```

### Test B — Sequential Duplicate Start

```text
Create RenderJob (QUEUED)
POST /start (first)
POST /start (second - should be no-op)
Assert:
  claim attempts = 2
  accepted executions = 1
  selector = 1
  selected_provider writes = 1
  render = 1
  logical executions = 1
```

### Test C — Overlapping Concurrent Start

```text
Create RenderJob (QUEUED)
CyclicBarrier(2) ensures both requests reach server simultaneously
Thread 1: POST /start
Thread 2: POST /start
Assert:
  claim attempts = 2
  claim winners = 1
  claim losers = 1
  script resolution = 1
  selector = 1
  selected_provider writes = 1
  EXECUTING transitions = 1
  render = 1
  logical executions = 1
  duplicate executions = 0
  lost updates = 0
  state regressions = 0
```

### Test D — Selector Failure

```text
Create RenderJob with invalid profile
POST /start
Assert:
  final state = FAILED
  selected_provider = NULL
  not SELECTING_PROVIDER
  survives new-transaction reload
```

### Test E — Post-Selection Failure

```text
Create RenderJob
Inject failure after provider selection
POST /start
Assert:
  final state = FAILED
  selected_provider = ffmpeg
  not PROVIDER_SELECTED
  survives reload
```

### Test F — Render Failure

```text
Create RenderJob with invalid media
POST /start
Assert:
  final state = FAILED
  selected_provider = ffmpeg
  render count = 1
  not EXECUTING
  survives reload
```

### Test G — Billing Failure

```text
Create RenderJob
Mock billing to fail
POST /start
Assert:
  final state = FAILED
  not COMPLETED
  no false Product readiness
  failure survives reload
```

### Test H — Storage Failure

```text
Create RenderJob
Mock storage to fail
POST /start
Assert:
  final state = FAILED
  no false COMPLETED
  no false READY Artifact/Product
  failure survives reload
```

### Test I — Product/Finalization Failure

```text
Create RenderJob
Mock product publication to fail
POST /start
Assert:
  final state = FAILED
  Product not falsely READY
  COMPLETED not persisted
  failure survives reload
```

### Test J — Error Safety

```text
Assert:
  false QUEUED = absent
  false COMPLETED = absent
  internal error details = absent
  execute-local = 404
  retry = 404
  SPA fallback = /app/**
```

## Classification

```text
TEST_PLAN_COMPLETE: YES
```
