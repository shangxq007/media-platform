# P2O.0e Job Submission Discovery

## 1. gRPC Reflection

gRPC reflection is NOT enabled on Cuebot port 8443.
grpcurl -plaintext localhost:8443 list → "server does not support the reflection API"

## 2. Proto Files

20 proto files downloaded from OpenCue GitHub repository:
https://github.com/AcademySoftwareFoundation/OpenCue/tree/master/proto/src

Files saved to /tmp/opencue-protos/

Key proto files:
- job.proto — JobInterface with LaunchSpec/LaunchSpecAndWait
- show.proto — ShowInterface with GetActiveShows
- facility.proto — FacilityInterface, AllocationInterface
- host.proto — HostInterface with GetHosts, FindHost
- rqd.proto — RqdReportInterface

## 3. Submission API

Service: job.JobInterface
Method: LaunchSpecAndWait (synchronous, waits for DB commit)
Request: JobLaunchSpecAndWaitRequest { string spec = 1; }
Response: JobLaunchSpecAndWaitResponse { JobSeq jobs = 1; }

Alternative: LaunchSpec (async, returns names immediately)

## 4. Job Spec Format

CJSL (Cue Job Specification Language) XML format.
DTD: cjsl-1.15.dtd (served by Cuebot at http://localhost:8080/spcue/dtd/)
Root element: <spec>

Required elements:
- <show> — show name (must exist in database)
- <shot> — shot name (3+ chars, alphanumeric + . + _)
- <user> — username (not "root")
- <job name="..."> — job name (3+ chars)
- <layer name="..." type="Render"> — layer with type
- <cmd> — command to execute
- <range> — frame range (e.g., "1" for single frame)
- <chunk> — frames per chunk

Optional elements:
- <facility> — facility name (must match host allocation)
- <paused> — true/false
- <cores> — core points (100 = 1 core)
- <memory> — memory limit (e.g., "256m", "4g")
- <services> — service requirements

## 5. Mandatory Fields

For minimal job submission:
- show (must exist: "testing")
- shot (any valid name)
- user (not "root": "operator")
- job name (unique, 3+ chars)
- layer name + type ("Render")
- cmd (shell command)
- range ("1")
- chunk ("1")

## 6. Command → RQD Execution

The <cmd> value is passed to RQD as a shell command.
RQD executes it in a shell context with access to shared paths.
Output files written to shared path are accessible from other containers.

## 7. Facility/Alloc/Show Requirements

Cuebot requires:
- A show (created by seed: "testing")
- A facility (created by seed: "local", "cloud")
- An allocation in the facility (created by seed: "local.general")
- The job's facility must match the host's allocation facility

## 8. Database Seed

P2O.0c database seed created:
- Show: "testing"
- Facility: "local", "cloud"
- Allocation: "local.general" (default), "local.desktop", "local.unassigned", "cloud.general", "cloud.unassigned"

## 9. CLI/Client in Image

No CLI tools found in Cuebot or RQD containers:
- cueadmin: NOT FOUND
- cuesubmit: NOT FOUND
- cuecmd: NOT FOUND
- python: NOT FOUND
- grpcurl: NOT FOUND

## 10. Minimal Client Strategy

Use grpcurl from host with proto files:
1. Download proto files from OpenCue GitHub
2. Download grpcurl binary
3. Build CJSL XML spec
4. Submit via grpcurl -import-path -proto job.proto -plaintext -d '{...}' localhost:8443 job.JobInterface.LaunchSpecAndWait

## 11. Schema Migration

Cuebot 1.19.1 requires V11+ GPU columns not present in V1 schema.
Applied V11 migration (partial) to add:
- host: int_gpus, int_gpus_idle, int_gpu_mem, int_gpu_mem_idle
- host_stat: int_gpu_mem_total, int_gpu_mem_free
- show: int_default_min_gpus, int_default_max_gpus
- job_resource: int_gpus, int_min_gpus, int_max_gpus
- layer_resource: int_gpus
- folder_resource: int_gpus, int_min_gpus, int_max_gpus
- proc: int_gpus_reserved, int_gpu_mem_used, int_gpu_mem_max_used
- Views: vs_show_resource, vs_job_resource, vs_alloc_usage
- Triggers: trigger__before_insert_layer

## 12. True Submission Result

All 3 smoke levels passed via true Cuebot gRPC submission:
- Level 0: shared-path probe (echo command) → SUCCEEDED
- Level 1: ffmpeg video generation → SUCCEEDED
- Level 2: ffmpeg caption overlay → SUCCEEDED

Preview artifacts saved to build/opencue-shared/media-platform-smoke/preview/p2o0e/

## Classification

PROTO_FOUND
GRPC_REFLECTION_NOT_AVAILABLE
API_DISCOVERED_VIA_PROTO_FILES
TRUE_SUBMISSION_SPIKE_COMPLETE
