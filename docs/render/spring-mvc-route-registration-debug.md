# Spring MVC Route Registration Debug

**Date:** 2026-07-08
**Status:** BLOCKED / NEEDS DEEPER DEBUG
**Authority:** SPRING-MVC-ROUTE-REGISTRATION-DEBUG.0

## Summary

Expected endpoint methods are present in source, compiled class files, and the bootJar. The bootJar inside the container matches local MD5. Existing render-module controller endpoints are loaded and working. However, newly added methods are not registered in Spring MVC mappings and diagnostic @PostConstruct logging did not trigger.

## Verified Facts

| Check | Result |
|-------|--------|
| Method exists in source | YES |
| Method exists in compiled class | YES |
| Method exists in bootJar | YES |
| Local bootJar MD5 matches container | YES |
| RenderController bean loaded | YES |
| Submit endpoint works | YES |
| Artifact metadata works | YES |
| New preview/media registered | NO |
| New content endpoint registered | NO |

## Failing Endpoints

- POST /api/v1/preview/media -> 404
- GET .../artifacts/{artifactId}/content -> 404

## Hypotheses

1. Spring Boot nested JAR class loading anomaly
2. Stale class from another module/package
3. Duplicate controller shadowing (partially resolved)
4. Component scan picks old implementation

## Classification

- Execution Plane: READY
- Real Media Execution: VERIFIED via fixture
- Real Media API Upload: BLOCKED
- Artifact Content: BLOCKED
- REAL-MEDIA-INPUT.0: PARTIAL

## Recommended Next Task

SPRING-BOOT-CLASSLOADER-DEEP-DIAG.0
