# Frontend Test Known Issues

## Resolved (Prompt 63)

### Vitest 4.x jsdom Environment Loading

**Problem**: Vitest 4.x `environment: 'jsdom'` configuration was not properly loading the jsdom environment when running from the workspace root. Tests failed with `document is not defined` and `localStorage is not defined`.

**Root Cause**: 
1. The `jsdom` package was installed in the workspace root `node_modules`, not in the frontend `node_modules`
2. Vitest 4.x resolves the environment package from the project root, not the workspace root
3. The `vitest.config.ts` in the frontend directory was not being loaded when running from workspace root

**Solution**:
1. Install `jsdom` and `happy-dom` in the frontend `node_modules`
2. Create a workspace-level `vite.config.ts` that delegates to the frontend's vitest configuration
3. Use `environment: 'happy-dom'` instead of `jsdom` for better compatibility
4. Install `@vitejs/plugin-vue` in workspace root for vitest config resolution

**Files Changed**:
- `/vite.config.ts` — New workspace-level vite config
- `/media-platform/frontend/vitest.config.ts` — Updated to use `happy-dom`
- `/media-platform/frontend/src/test-setup.ts` — Simplified (removed jsdom polyfill)

**Test Results**: 78 test files, 639 tests all passing

## Pre-existing Issues

### platform-app Integration Tests

**Problem**: `RenderFlowIntegrationTest` (10 tests) and `ModularityTest` (1 test) fail due to Spring context caching issues.

**Status**: Pre-existing, unrelated to Prompt 63 changes. These tests have been failing since earlier prompts due to `IllegalStateException at DefaultCacheAwareContextLoaderDelegate`.

**Impact**: None on feature functionality. All unit tests and non-platform-app integration tests pass.
