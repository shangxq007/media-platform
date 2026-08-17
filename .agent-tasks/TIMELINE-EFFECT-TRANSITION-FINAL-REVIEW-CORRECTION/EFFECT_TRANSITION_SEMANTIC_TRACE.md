# EFFECT_TRANSITION_SEMANTIC_TRACE.md

ChatGPT final-review semantic trace — repository reality of Effect/Transition/
Automation through the complete Timeline pipeline (base 6e91f809 + correction).

SEMANTIC CONCERN | ACTUAL CODE PATH | EFFECT | TRANSITION | AUTOMATION | RESULT
---|---|---|---|---|---|---
canonical state | timeline JSON composition (Internal Timeline Schema 1.0): clip.effects[] / composition.transitions[] / composition.automations[] (NEW) | clip.effects[] with id/effectKey/parameters | composition.transitions[] with typed participants/MediaTime/alignment (NEW authoring path) | composition.automations[] with MediaTime keyframes (NEW authoring path) | PASS (correction)
serialization | TimelineImportService.buildComposition + InternalTimelineJson (deterministic, sortKeyedEntityMaps) | import writes clip.effects | import writes composition.transitions (CORRECTION) | import writes composition.automations (CORRECTION) | PASS
hash | TimelineContentHasher.hashInternalTimeline → sha256(canonical timeline JSON) | parameter change → hash change (H1) | duration/alignment change → hash change (H4/H5) | keyframe value change → hash change (H6) | PASS (correction)
equality | InternalTimelineJson.jsonEqualsIgnoringRevision (canonical JSON comparison) | canonical JSON covers effects | canonical JSON covers transitions | canonical JSON covers automations (CORRECTION) | PASS
diff | TimelineSemanticDiffService.diff → TimelineEntityIndex.indexAll entity compare → SemanticChangeType | CLIP_EFFECT_CHANGED (D1 PASS) | TRANSITION_CHANGED (D2 PASS) | AUTOMATION_CHANGED (NEW, D3 PASS) | PASS (correction)
patch | RFC6902 JSON ops (TimelinePatchService.applyPatch) applied to payload | JSON-level ops cover any canonical field incl. effects | JSON-level ops cover transitions | JSON-level ops cover automations (CORRECTION — now in JSON) | PASS
merge | TimelineMergeEngine.toInternalPayload — deep-copies target, replaces only composition.tracks; non-track fields (transitions/automations) preserved from target | effects preserved via clip opaque payload (CNM1) | transitions preserved (target side, never silently dropped) | automations preserved (target side, never silently dropped) | PASS (correction)
conflict | TimelineMergeConflictAnalysis — structural conflict detection; CNM1 opaque preservation policy (no silent drop) | coarse CLIP conflict acceptable | coarse conflict acceptable | coarse conflict acceptable | PASS (fail-safe: preserve not drop)
revision | TimelineRevisionService — payload snapshot → contentHash → revision row | effects in payload → hashed | transitions in payload → hashed (CORRECTION) | automations in payload → hashed (CORRECTION) | PASS
restore | TimelineSnapshotService reads payload snapshot → canonical JSON | effects restored | transitions restored (CORRECTION) | automations restored (CORRECTION) | PASS
render lowering | AdvancedEffectsPipeline/EffectMappingService consume authored state; one-way FFmpeg lowering in render services | clip.effects → filter lowering | transitions → provider availability mapping | n/a (execution projection) | PASS (boundary preserved; providerKey/assetPath deferred #22)

RESULT_LEGEND: PASS (pre-existing), PASS-CORRECTION (fixed in this review), NOT_APPLICABLE
UNKNOWN_COUNT = 0
