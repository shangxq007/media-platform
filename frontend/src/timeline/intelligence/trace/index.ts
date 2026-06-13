// =============================================================================
// Trace Module
// =============================================================================
// Decision tracing, explainability, and replay for intelligence operations.
//
// Architecture:
// - decisionTrace.ts     → Core trace data structures and store
// - ruleTracker.ts       → Rule execution tracking and metrics
// - explainer.ts         → Human-readable explanation generation
// - intelligenceReplay.ts → Deterministic replay of intelligence operations
// =============================================================================

export * from './decisionTrace'
export * from './ruleTracker'
export * from './explainer'
export * from './intelligenceReplay'
