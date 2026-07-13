# Architecture Assertions

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** ARCH-DRIFT-GUARD.0

---

## Purpose

This document lists status-critical architecture assertions that must remain true until intentionally changed by a named task/ADR. LikeC4 is the architecture intent map. The drift guard script checks selected code-level facts to reduce accidental drift.

---

## Storage Delivery Profile Assertions

| ID | Assertion |
|----|-----------|
| SDP-001 | StorageDeliveryProfileRegistry exists and is read-only |
| SDP-002 | StorageDeliveryProfileCatalog contains 8 canonical profile IDs |
| SDP-003 | Runtime profile switching is NOT_IMPLEMENTED |
| SDP-004 | No active StorageDeliveryProfileResolver is used by artifact access runtime |
| SDP-005 | Artifact access runtime does not use StorageDeliveryProfileRegistry for provider selection |
| SDP-006 | Current default runtime path remains preview R2 signed access |
| SDP-007 | OpenDAL remains experimental and disabled by default |
| SDP-008 | RustFS remains lab-only |
| SDP-009 | MinIO remains optional/design-only |
| SDP-010 | No remote calls are made by profile registry/config/validator |

---

## Storage Security Assertions

| ID | Assertion |
|----|-----------|
| SEC-001 | Signed URLs are generated on demand and not persisted |
| SEC-002 | Bucket is not exposed as user-facing artifact access contract |
| SEC-003 | ObjectKey is not exposed as user-facing artifact access contract |
| SEC-004 | StorageReferenceId is internal and not exposed to normal users |
| SEC-005 | Local file paths are not exposed to normal users |
| SEC-006 | Credentials/accessKey/secretKey are never included in profile registry/config DTOs |

---

## Ingest Preflight Policy Assertions

| ID | Assertion |
|----|-----------|
| ING-001 | Upload preflight policy evaluator is report-only |
| ING-002 | ReportOnlyPreflightPolicyEvaluator never emits REJECT |
| ING-003 | REJECT_CANDIDATE is diagnostic and non-blocking |
| ING-004 | Upload rejection is NOT_IMPLEMENTED |
| ING-005 | Enforce mode is NOT_ENABLED |
| ING-006 | Policy evaluator inherits report-only hook enablement |
| ING-007 | Policy evaluation result is internal-only |
| ING-008 | Public upload response is unchanged |
| ING-009 | Policy evaluation persistence is NOT_IMPLEMENTED |
| ING-010 | Preflight safe report persistence is NOT_IMPLEMENTED |
| ING-011 | Evaluator and hook fail open |

---

## Media Metadata Assertions

| ID | Assertion |
|----|-----------|
| META-001 | Tika is generic MIME/content detector only |
| META-002 | FFprobe remains primary video/audio technical metadata provider |
| META-003 | Tika does not replace FFprobe |
| META-004 | FFprobe does not replace Tika generic detection |
| META-005 | Full text extraction remains disabled |
| META-006 | OCR remains NOT_INTRODUCED |
| META-007 | Raw FFprobe JSON is not persisted or exposed |
| META-008 | Raw Tika metadata is not persisted or exposed |

---

## Deferred/Future-only Assertions

| ID | Assertion |
|----|-----------|
| FUT-001 | OpenCue remains NOT_STARTED |
| FUT-002 | Artifact DAG remains POSTPONED |
| FUT-003 | OpenDAL production path remains DEFERRED |
| FUT-004 | Customer-owned storage runtime is NOT_IMPLEMENTED |
| FUT-005 | Export bundle runtime is NOT_IMPLEMENTED |
| FUT-006 | Camel/APISIX/EventMesh runtime is NOT_INTRODUCED |

---

## Changing an Assertion

To intentionally change an assertion:
1. Create a named task
2. Update code
3. Update LikeC4
4. Update architecture assertions
5. Update current-system-state/module-status
6. Run drift guard
7. Record commit

---

## Safe Preflight Report Persistence Assertions

| ID | Assertion |
|----|-----------|
| PSP-001 | Safe preflight report persistence is NOT_IMPLEMENTED |
| PSP-002 | Any future persistence must be DEV_PREVIEW_EPHEMERAL_ONLY |
| PSP-003 | Any future persistence must be DEV_ONLY visible |
| PSP-004 | Any future persistence must use 7-day retention |
| PSP-005 | Persistence failure must fail open |
| PSP-006 | Public upload response must not include preflight fields |
| PSP-007 | Enforce mode must remain disabled |
| PSP-008 | Upload rejection must remain NOT_IMPLEMENTED |
| PSP-009 | Raw FFprobe JSON must not be persisted |
| PSP-010 | Raw Tika metadata must not be persisted |
| PSP-011 | Local paths/temp paths must not be persisted |
| PSP-012 | Storage internals must not be persisted |
| PSP-013 | Signed URLs must not be persisted |
| PSP-014 | Credentials must not be persisted |
| PSP-015 | OCR/extracted text must not be persisted |
