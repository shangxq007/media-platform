package com.example.platform.render.infrastructure.unified;

/**
 * Unified Execution & Economy Graph (UEEG) module.
 * 
 * <p>This module provides the canonical model for render job lifecycle,
 * unifying all subsystem traces into a single graph.
 * 
 * <p>Architecture:
 * <ul>
 *   <li>UnifiedRequestGraph - immutable graph model</li>
 *   <li>GraphNode - subsystem decision/artifact nodes</li>
 *   <li>GraphEdge - causal links between nodes</li>
 *   <li>UnifiedExecutionTracer - trace collection</li>
 *   <li>UnifiedGraphRepository - persistence</li>
 *   <li>RequestLifecycleEngine - orchestrator</li>
 * </ul>
 */
public class UnifiedModule {
    // Module marker class
}
