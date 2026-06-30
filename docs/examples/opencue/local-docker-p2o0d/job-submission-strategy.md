# P2O.0d — Job Submission Strategy

## Status: PARTIAL_JOB_SUBMISSION_READY

## Discovery Summary

No true OpenCue submission mechanism available:

| Tool | Status |
|------|--------|
| cueadmin | NOT FOUND in containers |
| cuesubmit | NOT FOUND in containers |
| cuecmd | NOT FOUND in containers |
| Python OpenCue client | NOT on PyPI, not in containers |
| gRPC CLI (grpcurl) | NOT installed on host |
| Cuebot REST API | NOT available (gRPC only) |

## Selected Approach: Option D — Container Exec Fallback

Run smoke scripts inside RQD container via `docker exec opencue-rqd`.

This is NOT true OpenCue job submission. It validates:
- RQD container has ffmpeg/ffprobe
- RQD container can access shared path
- Smoke scripts execute successfully on worker

This does NOT validate:
- Cuebot job scheduling
- RQD job dispatch via Cuebot
- OpenCue job lifecycle (pending → running → succeeded)

## Future True Submission Path

To achieve COMPLETE_ALL_SMOKE_LEVELS, install OpenCue Python client (pycue) from GitHub:
```bash
pip install git+https://github.com/AcademySoftwareFoundation/OpenCue.git#subdirectory=pycue
```
Then use pycue API to submit jobs to Cuebot gRPC endpoint.
