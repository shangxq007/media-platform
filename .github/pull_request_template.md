## Summary

<!-- Brief description of changes -->

## TASK_ID

<!-- e.g. TL.0, PB.0, CT.0 -->

## Changes

<!-- List key changes -->

## Test Plan

- [ ] `./gradlew compileJava` passes
- [ ] Domain tests pass (`./gradlew :render-module:test --tests "com.example.platform.render.domain.*"`)
- [ ] No forbidden paths touched
- [ ] No secrets in diff

## Architecture Boundary Checklist

- [ ] Timeline remains canonical editing model
- [ ] Product remains canonical result object
- [ ] StorageRuntime/ProductRuntime boundaries not violated
- [ ] OpenCue treated as ExecutionEnvironment, not Provider
- [ ] Remotion not production-dispatched
- [ ] FFmpeg/libass remains production baseline
- [ ] Provider binding is deterministic eligibility + priority
- [ ] Artifact DAG not implemented as active runtime
- [ ] Spring AI not introduced
- [ ] Template/Plugin/Workflow cannot generate raw commands
- [ ] No secrets or local path exposure in public API

## Review Packet

<!-- Link to Review Packet if available -->

## Merge Recommendation

<!-- MERGE / REQUEST_CHANGES / BLOCK -->
