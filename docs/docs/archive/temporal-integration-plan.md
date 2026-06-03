# Temporal Integration Plan

> **Generated**: 2026-05-08T11:00Z
> **Status**: Preparation Phase — adapters and port boundaries established; Temporal Server not yet required for local dev/test.

---

## 1. Overview

This document describes the plan for integrating [Temporal](https://temporal.io/) as the workflow orchestration engine for the media platform's render pipeline. The architecture uses a **port/adapter** pattern so that:

- Local development and testing require **no Temporal Server**.
- Production deployments can switch to Temporal via configuration.
- The domain layer remains decoupled from the orchestration mechanism.

---

## 2. Architecture

### 2.1 Port Boundary

```
┌─────────────────────────────────────────────────────────────┐
│                      RenderController                        │
│                   (REST API layer)                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ uses
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  RenderExecutionPort                         │
│                  (workflow port)                             │
│                                                             │
│  String execute(renderJobId, tenantId, projectId,           │
│                 prompt, profile)                             │
└──────────┬──────────────────────────────────┬───────────────┘
           │                                  │
           ▼                                  ▼
┌─────────────────────────┐    ┌─────────────────────────────┐
│ LocalRenderExecution    │    │ TemporalRenderExecution      │
│ Adapter                 │    │ Adapter                      │
│                         │    │                              │
│ Delegates to            │    │ Starts Temporal Workflow     │
│ RenderOrchestrator      │    │ via WorkflowClient           │
│ Service                 │    │                              │
│                         │    │ Requires Temporal Server     │
│ (render-module)         │    │                              │
└─────────────────────────┘    └─────────────────────────────┘
           │                                  │
           ▼                                  ▼
┌─────────────────────────┐    ┌─────────────────────────────┐
│ RenderWorkflowImpl      │    │ Temporal Server              │
│ (only for interface     │    │ ┌─────────────────────────┐  │
│  compatibility tests)   │    │ │ RenderWorkflowImpl      │  │
│                         │    │ │ (runs on Worker)        │  │
│                         │    │ └───────────┬─────────────┘  │
│                         │    │             │                │
│                         │    │             ▼                │
│                         │    │ ┌─────────────────────────┐  │
│                         │    │ │ RenderActivitiesImpl    │  │
│                         │    │ │ (runs on Worker)        │  │
│                         │    │ └─────────────────────────┘  │
└─────────────────────────┘    └─────────────────────────────┘
```

### 2.2 Package Structure

```
workflow-module/
├── port/
│   └── RenderExecutionPort.java          # Execution port interface
├── adapter/
│   ├── LocalRenderExecutionAdapter.java  # Local (default) adapter
│   └── TemporalRenderExecutionAdapter.java # Temporal adapter (skeleton)
├── temporal/
│   ├── RenderWorkflow.java               # @WorkflowInterface
│   ├── RenderWorkflowImpl.java           # Workflow implementation
│   ├── RenderActivities.java             # @ActivityInterface
│   ├── RenderActivitiesImpl.java         # Activity implementation
│   ├── RenderTaskQueue.java              # Task queue constant
│   ├── AppTemporalProperties.java        # Configuration properties
│   └── TemporalWorkflowStarter.java      # Spring configuration
└── test/
    └── java/.../workflow/temporal/
        ├── RenderWorkflowTest.java
        └── TemporalWorkflowStarterTest.java
```

---

## 3. Temporal Server Requirements

### 3.1 Version

| Component | Minimum Version | Recommended |
|-----------|----------------|-------------|
| Temporal Server | 1.22 | 1.24+ |
| Temporal SDK (Java) | 1.22 | 1.33 (current) |
| Temporal CLI | 1.0 | latest |

### 3.2 Namespace

```yaml
spring:
  temporal:
    namespace: media-platform
```

Create namespace with:
```bash
temporal operator namespace create media-platform \
  --retention 30 \
  --description "Media platform render workflows"
```

### 3.3 Task Queue

| Name | Purpose |
|------|---------|
| `media-platform-tasks` | All render workflow and activity tasks |

---

## 4. Worker Deployment Configuration

### 4.1 Spring Boot Auto-Configuration (Development)

```yaml
spring:
  temporal:
    connection:
      target: 127.0.0.1:7233
    namespace: media-platform
    start-workers: true
    workers-auto-discovery:
      packages:
        - com.example.platform.workflow.temporal
```

### 4.2 Standalone Worker (Production)

For production, run workers as separate processes for independent scaling:

```java
@Configuration
public class TemporalWorkerConfig {

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        
        // Workflow worker
        Worker workflowWorker = factory.newWorker(RenderTaskQueue.NAME);
        workflowWorker.registerWorkflowImplementationTypes(RenderWorkflowImpl.class);
        
        // Activity worker (can be separate deployment)
        Worker activityWorker = factory.newWorker(RenderTaskQueue.NAME);
        activityWorker.registerActivitiesImplementations(renderActivitiesImpl());
        
        return factory;
    }
}
```

### 4.3 Worker Scaling

| Metric | Threshold | Action |
|--------|-----------|--------|
| Task queue backlog | > 100 | Scale up workers |
| Worker CPU | > 70% | Scale up workers |
| Workflow latency p99 | > 5 min | Investigate + scale |

---

## 5. Retry Policy

### 5.1 Workflow-Level Retry

```java
WorkflowOptions.newBuilder()
    .setWorkflowExecutionTimeout(Duration.ofHours(2))
    .setWorkflowRunTimeout(Duration.ofMinutes(30))
    .setRetryOptions(RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumInterval(Duration.ofMinutes(5))
    .setBackoffCoefficient(2.0)
    .setMaximumAttempts(3)
    .setDoNotRetry(
        IllegalArgumentException.class.getName(),
        IllegalStateException.class.getName())
    .build())
    .build();
```

### 5.2 Activity-Level Retry

```java
ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(2))
    .setRetryOptions(RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumInterval(Duration.ofMinutes(1))
        .setBackoffCoefficient(2.0)
        .setMaximumAttempts(5)
        .setDoNotRetry(
            NonRetryableException.class.getName())
    .build())
    .build();
```

---

## 6. Activity Timeout

| Activity | Start-to-Close | Reason |
|----------|----------------|--------|
| `decideRenderPipeline` | 2 min | Feature flag evaluation |
| `generateAiScript` | 5 min | LLM API call |
| `executeRender` | 30 min | Video rendering |
| `storeArtifact` | 5 min | Blob storage upload |

---

## 7. Local Development with Temporal

### 7.1 Docker Compose

```yaml
# docker-compose.temporal.yml
version: "3.8"
services:
  temporal:
    image: temporalio/auto-setup:1.24
    ports:
      - "7233:7233"     # gRPC
      - "8233:8233"     # Web UI
    environment:
      - DB=postgresql
      - DB_PORT=5432
      - POSTGRES_USER=temporal
      - POSTGRES_PWD=temporal
      - POSTGRES_SEEDS=temporal-db
      - DYNAMIC_CONFIG_FILE_PATH=config/dynamicconfig/development-sql.yaml

  temporal-db:
    image: postgres:15
    environment:
      POSTGRES_USER: temporal
      POSTGRES_PASSWORD: temporal
    volumes:
      - temporal-data:/var/lib/postgresql/data

  temporal-ui:
    image: temporalio/ui:2.26
    ports:
      - "8080:8080"
    environment:
      - TEMPORAL_ADDRESS=temporal:7233

volumes:
  temporal-data:
```

### 7.2 Start Local Temporal

```bash
# Start Temporal stack
docker compose -f docker-compose.temporal.yml up -d

# Verify
temporal operator namespace list --address 127.0.0.1:7233
```

### 7.3 Application Configuration for Local Temporal

```yaml
# application-temporal.yml (profile: temporal)
render:
  execution:
    mode: temporal

spring:
  temporal:
    connection:
      target: 127.0.0.1:7233
    namespace: media-platform
    start-workers: true
    workers-auto-discovery:
      packages:
        - com.example.platform.workflow.temporal
```

---

## 8. Production Deployment Considerations

### 8.1 High Availability

| Component | HA Strategy |
|-----------|-------------|
| Temporal Server | Multi-node cluster with etcd/PostgreSQL |
| Workers | Multiple replicas behind task queue |
| Database | PostgreSQL with streaming replication |

### 8.2 Monitoring

| Metric | Source | Alert |
|--------|--------|-------|
| Workflow completion rate | Temporal metrics | < 95% |
| Activity failure rate | Temporal metrics | > 5% |
| Task queue latency | Temporal metrics | p99 > 1 min |
| Worker health | Spring Boot actuator | Down |

### 8.3 Security

```yaml
spring:
  temporal:
    connection:
      target: temporal.prod.svc:7233
    tls:
      cert-file: /certs/client.pem
      key-file: /certs/client.key
      ca-file: /certs/ca.pem
```

### 8.4 Data Retention

- Default workflow retention: **30 days**
- Audit workflows: **90 days** (compliance requirement)
- Configure at namespace level

---

## 9. Mode Switching Strategy

### 9.1 Configuration

```yaml
# Local mode (default)
render:
  execution:
    mode: local

# Temporal mode
render:
  execution:
    mode: temporal
```

### 9.2 Conditional Beans

| Mode | Active Adapter | Temporal Server Required |
|------|----------------|-------------------------|
| `local` | `LocalRenderExecutionAdapter` | No |
| `temporal` | `TemporalRenderExecutionAdapter` | Yes |

### 9.3 Migration Path

1. **Phase 1** (current): Local mode, Temporal adapters as skeleton
2. **Phase 2**: Deploy Temporal Server, run both adapters in shadow mode
3. **Phase 3**: Switch production to Temporal mode
4. **Phase 4**: Keep local mode for dev/test only

---

## 10. Testing Strategy

### 10.1 Unit Tests

- `RenderWorkflowTest`: Validates workflow interface annotations
- `TemporalWorkflowStarterTest`: Validates configuration properties

### 10.2 Integration Tests

- `RenderFlowIntegrationTest`: Runs against local adapter (no Temporal Server)
- Temporal integration tests require Testcontainers or external Temporal

### 10.3 Temporal-Specific Tests

```java
@SpringBootTest
@ActiveProfiles("temporal-test")
@Testcontainers
class TemporalRenderWorkflowIntegrationTest {

    @Container
    static GenericContainer<?> temporal = new GenericContainer<>("temporalio/auto-setup:1.24")
        .withExposedPorts(7233);

    @Test
    void shouldCompleteRenderWorkflow() {
        // Test full workflow execution against real Temporal
    }
}
```

---

## 11. Troubleshooting

### 11.1 Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Workflow not starting | Worker not polling | Check task queue name match |
| Activity timeout | Long-running operation | Increase `setStartToCloseTimeout` |
| Workflow stuck | Non-deterministic code | Review workflow implementation |
| Connection refused | Temporal Server down | Check server health |

### 11.2 Debug Commands

```bash
# List workflows
temporal workflow list --namespace media-platform

# Describe workflow
temporal workflow describe --workflow-id <id> --namespace media-platform

# Show workflow history
temporal workflow show --workflow-id <id> --namespace media-platform
```

---

## 12. References

- [Temporal Documentation](https://docs.temporal.io/)
- [Temporal Java SDK](https://github.com/temporalio/sdk-java)
- [Spring Boot Temporal Starter](https://github.com/temporalio/sdk-java-spring)
