# Runbook: Cuebot gRPC Job Submission Spike

## 1. Scope

Operator-run validation of Cuebot gRPC job submission.
Not production. Not automated. Not pushed.

## 2. Prerequisites

- Docker and Docker Compose
- grpcurl binary at /tmp/grpcurl
- Proto files at /tmp/opencue-protos/
- P2O.0c runtime ready

## 3. Start Local OpenCue Runtime

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/prepare-runtime-ready.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-runtime-health.sh
```

## 4. Inspect Cuebot/RQD Tooling

```bash
bash docs/examples/opencue/local-docker-p2o0e/scripts/inspect-cuebot-grpc.sh
```

## 5. Inspect gRPC Reflection

```bash
/tmp/grpcurl -plaintext localhost:8443 list
# Expected: "server does not support the reflection API"
```

## 6. Locate Proto Files

Proto files from OpenCue GitHub:
https://github.com/AcademySoftwareFoundation/OpenCue/tree/master/proto/src

Downloaded to /tmp/opencue-protos/

## 7. Determine Submission Method

grpcurl with proto files is the only viable method.
No CLI tools, Python client, or Java runtime available in containers.

## 8. Run Minimal Client Spike

```bash
bash docs/examples/opencue/local-docker-p2o0e/scripts/prepare-grpc-submission-smoke.sh
bash docs/examples/opencue/local-docker-p2o0e/scripts/submit-grpc-smoke-level-0.sh
```

## 9. Submit Smoke Level 0

```bash
SPEC=$(cat /tmp/p2o0e-specs/smoke-level-0.xml | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))')
/tmp/grpcurl -import-path /tmp/opencue-protos -proto job.proto -plaintext \
  -d "{\"spec\": $SPEC}" localhost:8443 job.JobInterface.LaunchSpecAndWait
```

## 10. Validate Shared-Path Output

```bash
docker exec opencue-rqd cat /mnt/opencue-shared/media-platform-smoke/preview/p2o0e/smoke-level-0/shared-path-probe.txt
```

## 11. Copy Preview Artifacts

```bash
bash docs/examples/opencue/local-docker-p2o0e/scripts/copy-preview-artifacts.sh
```

## 12. Collect Logs

```bash
bash docs/examples/opencue/local-docker-p2o0e/scripts/collect-grpc-submission-logs.sh
```

## 13. Troubleshooting

### RQD Not Registering

Check if V11 GPU column migration applied:
```sql
SELECT column_name FROM information_schema.columns
WHERE table_name='host' AND column_name='int_gpus';
```

### Job Not Dispatching

Check job facility matches host allocation:
```sql
SELECT j.str_name, f.str_name as facility
FROM job j JOIN facility f ON j.pk_facility = f.pk_facility;
```

### Frame DEAD

Check Cuebot logs for SQL errors:
```bash
docker compose -f docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml logs cuebot | grep -i error
```

## 14. Cleanup

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/stop-runtime.sh
```

## 15. What Not to Do

- Do not push Docker images
- Do not install packages on host
- Do not use production secrets
- Do not modify .env files
- Do not commit generated code

## 16. Transition to P2O.0f

P2O.0f can extend with:
- Multi-frame jobs
- Job dependencies
- Complex specs
- Error handling

## 17. Transition to PVE

P2O.1 PVE smoke can use this submission infrastructure.
