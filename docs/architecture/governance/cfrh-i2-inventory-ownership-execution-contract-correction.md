# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I2 INVENTORY + OWNERSHIP EXECUTION-CONTRACT CORRECTION

## Publication record

| Field | Value |
|---|---|
| BASE_SHA | 5318a3fd0477a92511ebb8dd1d56eaf6caa2ee41 |
| REVIEWED_PREDECESSOR_SHA | 93650e1937ede00824c12abd08d2aa9fda2d113c |
| CORRECTION_SHA | 8f14079d35addca457447c9877de3cfed27c5c98 |
| PUBLICATION_SHA | d1656ed5c47c9d70ce9ca2683cd9212082240689 |
| RETAINED_BEHAVIOR_COUNT | 11 |
| LEGACY_QUERY_INVOCATION_SITE_COUNT | 22 |
| LEGACY_QUERY_CALLER_METHOD_COUNT | 18 |
| CALLER_INVENTORY_ROW_COUNT | 22 |
| HTTP_ENDPOINT_COUNT | 15 |
| HTTP_ENDPOINT_WITH_LEGACY_QUERY_DEPENDENCY_COUNT | 11 |
| P1_SYMBOL_COUNT | 8 |
| OWNERSHIP_SURFACE_COUNT | 19 |
| BASE_JOB_OWNERSHIP_UNRESOLVED_COUNT | 0 |
| UNRESOLVED_DISPOSITION_COUNT | 0 |
| PRODUCTION_CHANGE_COUNT | 0 |
| TEST_CHANGE_COUNT | 0 |
| BUILD_CHANGE_COUNT | 0 |
| MIGRATION_CHANGE_COUNT | 0 |
| UNEXPECTED_CHANGE_COUNT | 0 |
| ARCHITECTURE_PREMISE_FAILURE | NO |
| ARCHITECTURE_ESCALATION | NONE |
| PREDECESSOR_REJECTED_METRIC | PRODUCTION_CALLER_COUNT = 19 (superseded; conflated caller methods/sites/rows/endpoints) |

## Scope of this correction

Supersedes ONLY the inaccurate/incomplete evidence accounting of the
predecessor I2 decision-recovery publication. The accepted CFRH-I2
architecture (I2-C1..C15, system-authority model, I2-A..G sequencing) is
UNCHANGED and NOT redesigned.

## Corrections applied

### A. Production invocation-site inventory (PRODUCTION_CALLER_INVENTORY)
- Unit definitions frozen: LEGACY_QUERY_INVOCATION_SITE_COUNT (22) vs
  LEGACY_QUERY_CALLER_METHOD_COUNT (18) vs HTTP_ENDPOINT_COUNT (15) vs
  CALLER_INVENTORY_ROW_COUNT (22) are distinct dimensions.
- Caller TSV rewritten: one row per physical invocation site (22 rows,
  site_id CS-01..CS-22).
- Previously missing sites accounted: patchPreview findById (CS-07),
  patchSteps findById (CS-09), restore response getDetail +
  getRevisionSnapshotPayload (CS-13/CS-14), mergeDiff dual compare sites
  split (CS-17/CS-18).

### B. HTTP endpoint dependency model (ENDPOINT_DEPENDENCY)
- Endpoint inventory now separates write_authority / direct /
  transitive / effective legacy-query dependency.
- Restore: WRITE_AUTHORITY = CANONICAL (restoreRevision);
  DIRECT_LEGACY_QUERY_DEPENDENCY = NO;
  TRANSITIVE_LEGACY_QUERY_DEPENDENCY = YES (toRestoreResponse → getDetail +
  getRevisionSnapshotPayload);
  EFFECTIVE_LEGACY_QUERY_DEPENDENCY = YES.
- RESTORE_I2_DISPOSITION = MIGRATE_RESPONSE_QUERY_DEPENDENCY_BEFORE_I2_E_SERVICE_DELETION.
- 11 endpoints carry effective legacy-query dependency; 4 canonical-only.

### C. Metric single source of truth (CALLER_METRIC_SINGLE_TRUTH)
- PRODUCTION_CALLER_COUNT = 19 removed as authoritative; retained only as
  PREDECESSOR_REJECTED_METRIC above.
- All publication numbers derived from ledgers via
  verify-cfrh-i2-inventory-contract.py (mechanical validator).

### D. BaseJobTimelineLoader ownership contract (BASE_JOB_OWNERSHIP)
- Value flow traced: AiTimelineEditService.editFromBaseJob (context.projectId()
  available L106) + IncrementalRenderOrchestrationService.tryResolve →
  loader.loadInternalTimelineJson(baseJobId, tenantId) →
  renderJobRepository.findTimelineDataById → findPayload(snapshotId) [global].
- AUTHORITATIVE_PROJECT_SOURCE: render_job.PROJECT_ID column (present in row,
  not projected into TimelineData).
- FINAL_DISPOSITION: THREAD_EXISTING_PROJECT_CONTEXT_TO_LOADER —
  TimelineData gains project_id projection; loader uses
  findOwnedById(projectId, tenantId, snapshotId); wave I2-B; unresolved = 0.
- Evidence: base-job-timeline-loader-ownership-value-flow.md.

## Newly adopted narrow refinements (appended, I2-C1..C15 unchanged)
- CALLER_METRICS_MUST_DECLARE_THEIR_COUNTING_UNIT_V1
- ONE_INVOCATION_SITE_ONE_LEDGER_ROW_V1
- ENDPOINT_AUTHORITY_AND_QUERY_DEPENDENCY_ARE_DISTINCT_V1
- OWNERSHIP_EXECUTION_CONTEXT_MUST_BE_RESOLVED_BEFORE_IMPLEMENTATION_V1

## Validation
- verify-cfrh-i2-inventory-contract.py: mechanical evidence M/M PASS
- RED behavior 10/10 PASS (collapse sites, remove patchPreview/patchSteps
  findById, mergeDiff collapse, restore transitive=NO, BaseJob TBD, missing
  project source, publication ±1, duplicate site_id, UNKNOWN disposition)
- GREEN from committed publication tree: PASS
- committed-range scope: production=0, test=0, build=0, migration=0, generated=0

READY_FOR_CHATGPT_CFRH_I2_FINAL_DECISION_RECOVERY_REVIEW
