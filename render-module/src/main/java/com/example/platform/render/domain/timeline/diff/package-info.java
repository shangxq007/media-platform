/**
 * Semantic Diff V1 - read-only diff engine for Timeline Git.
 * 
 * <p>This package provides:</p>
 * <ul>
 *   <li>{@link com.example.platform.render.domain.timeline.diff.TimelineDiffEngine} - pure domain diff engine</li>
 *   <li>{@link com.example.platform.render.domain.timeline.diff.TimelineChangeSet} - immutable result model</li>
 *   <li>{@link com.example.platform.render.domain.timeline.diff.TimelineChange} - immutable change record</li>
 *   <li>{@link com.example.platform.render.domain.timeline.diff.ChangeSummary} - deterministic summary</li>
 * </ul>
 * 
 * <p><strong>Read-only:</strong> This package does NOT provide Patch, Merge, or mutation capabilities.</p>
 */
package com.example.platform.render.domain.timeline.diff;
