var e=e=>{switch(e){case`index`:return`@startuml
title "Landscape view"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<User>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Reviewer>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Hermes>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Cloudflare>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Telegram>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatform>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<AiProviders>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==User\\n\\nPlatform user" <<User>> as User
rectangle "==Human Reviewer\\n\\nFinal arbiter" <<Reviewer>> as Reviewer
rectangle "==Hermes Control Plane\\n\\nMulti-agent orchestration" <<Hermes>> as Hermes
rectangle "==Cloudflare\\n\\nR2, Pages, AI Gateway" <<Cloudflare>> as Cloudflare
rectangle "==Telegram\\n\\nNotifications" <<Telegram>> as Telegram
rectangle "==media-platform\\n\\nRender platform" <<MediaPlatform>> as MediaPlatform
rectangle "==AI Providers\\n\\nMiMo, LongCat, OpenRouter, etc." <<AiProviders>> as AiProviders
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

User .[#8D8D8D,thickness=2].> MediaPlatform : <color:#8D8D8D>uses
Reviewer .[#8D8D8D,thickness=2].> Hermes : <color:#8D8D8D>reviews
Hermes .[#8D8D8D,thickness=2].> Cloudflare : <color:#8D8D8D>publishes
Hermes .[#8D8D8D,thickness=2].> Telegram : <color:#8D8D8D>notifies
MediaPlatform .[#8D8D8D,thickness=2].> AiProviders : <color:#8D8D8D>calls models
MediaPlatform .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>reads/writes
Hermes .[#8D8D8D,thickness=2].> MediaPlatform : <color:#8D8D8D>develops
@enduml
`;case`systemContext`:return`@startuml
title "System Context"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformOpencue>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRemotion>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "media-platform" <<MediaPlatform>> as MediaPlatform {
  skinparam RectangleBorderColor<<MediaPlatform>> #3b82f6
  skinparam RectangleFontColor<<MediaPlatform>> #3b82f6
  skinparam RectangleBorderStyle<<MediaPlatform>> dashed

  rectangle "==OpenCue\\n\\nExecutionEnvironment only — NOT a Provider" <<MediaPlatformOpencue>> as MediaPlatformOpencue
  rectangle "==Remotion\\n\\nNon-production/POC subtitle template provider" <<MediaPlatformRemotion>> as MediaPlatformRemotion
}
@enduml
`;case`containerDiagram`:return`@startuml
title "Container View"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformPlatformApp>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformAiModule>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModule>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformStorageModule>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModule>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformOpencue>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRemotion>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformSharedKernel>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "media-platform" <<MediaPlatform>> as MediaPlatform {
  skinparam RectangleBorderColor<<MediaPlatform>> #3b82f6
  skinparam RectangleFontColor<<MediaPlatform>> #3b82f6
  skinparam RectangleBorderStyle<<MediaPlatform>> dashed

  rectangle "==platform-app\\n\\nSpring Boot entry point" <<MediaPlatformPlatformApp>> as MediaPlatformPlatformApp
  rectangle "==ai-module\\n\\nAI integration" <<MediaPlatformAiModule>> as MediaPlatformAiModule
  rectangle "==render-module\\n\\nCore render domain" <<MediaPlatformRenderModule>> as MediaPlatformRenderModule
  rectangle "==storage-module\\n\\nStorage delivery and materialization" <<MediaPlatformStorageModule>> as MediaPlatformStorageModule
  rectangle "==ingest-module\\n\\nUpload preflight and metadata detection" <<MediaPlatformIngestModule>> as MediaPlatformIngestModule
  rectangle "==OpenCue\\n\\nExecutionEnvironment only — NOT a Provider" <<MediaPlatformOpencue>> as MediaPlatformOpencue
  rectangle "==Remotion\\n\\nNon-production/POC subtitle template provider" <<MediaPlatformRemotion>> as MediaPlatformRemotion
  rectangle "==shared-kernel\\n\\nShared domain primitives" <<MediaPlatformSharedKernel>> as MediaPlatformSharedKernel
}
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformRenderModule : <color:#8D8D8D>uses
MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformStorageModule : <color:#8D8D8D>uses
MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformIngestModule : <color:#8D8D8D>uses
MediaPlatformRenderModule .[#8D8D8D,thickness=2].> MediaPlatformSharedKernel : <color:#8D8D8D>depends on
MediaPlatformRenderModule .[#8D8D8D,thickness=2].> MediaPlatformOpencue : <color:#8D8D8D>routes ExecutionEnv only
MediaPlatformRenderModule .[#8D8D8D,thickness=2].> MediaPlatformRemotion : <color:#8D8D8D>routes POC only
MediaPlatformStorageModule .[#8D8D8D,thickness=2].> MediaPlatformSharedKernel : <color:#8D8D8D>depends on
MediaPlatformIngestModule .[#8D8D8D,thickness=2].> MediaPlatformSharedKernel : <color:#8D8D8D>depends on
MediaPlatformAiModule .[#8D8D8D,thickness=2].> MediaPlatformSharedKernel : <color:#8D8D8D>depends on
MediaPlatformRenderModule .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>persists artifacts
MediaPlatformStorageModule .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>R2/S3-compatible
@enduml
`;case`controlPlane`:return`@startuml
title "Hermes Control Plane"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<HermesHermesAgent>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesCodingAgents>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesReviewInfra>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesPolicies>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesDashboard>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "Hermes Control Plane" <<Hermes>> as Hermes {
  skinparam RectangleBorderColor<<Hermes>> #3b82f6
  skinparam RectangleFontColor<<Hermes>> #3b82f6
  skinparam RectangleBorderStyle<<Hermes>> dashed

  rectangle "==Hermes Agent\\n\\nGateway, orchestrator, Level 3 Feature Coordinator" <<HermesHermesAgent>> as HermesHermesAgent
  rectangle "==Coding Agents\\n\\nOpenCode, Codex, Kilo Code, Claude Code, Aider" <<HermesCodingAgents>> as HermesCodingAgents
  rectangle "==Review Infrastructure\\n\\nCODEOWNERS, Semgrep, Review Packets" <<HermesReviewInfra>> as HermesReviewInfra
  rectangle "==Policies\\n\\nPermissions, stop conditions" <<HermesPolicies>> as HermesPolicies
  rectangle "==Dashboard\\n\\nscribe.cc.cd" <<HermesDashboard>> as HermesDashboard
}
@enduml
`;case`controlPlaneContext`:return`@startuml
title "Control Plane in Context"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<HermesHermesAgent>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesCodingAgents>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesReviewInfra>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesPolicies>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<HermesDashboard>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<User>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Reviewer>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Cloudflare>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Telegram>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatform>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "Hermes Control Plane" <<Hermes>> as Hermes {
  skinparam RectangleBorderColor<<Hermes>> #3b82f6
  skinparam RectangleFontColor<<Hermes>> #3b82f6
  skinparam RectangleBorderStyle<<Hermes>> dashed

  rectangle "==Hermes Agent\\n\\nGateway, orchestrator, Level 3 Feature Coordinator" <<HermesHermesAgent>> as HermesHermesAgent
  rectangle "==Coding Agents\\n\\nOpenCode, Codex, Kilo Code, Claude Code, Aider" <<HermesCodingAgents>> as HermesCodingAgents
  rectangle "==Review Infrastructure\\n\\nCODEOWNERS, Semgrep, Review Packets" <<HermesReviewInfra>> as HermesReviewInfra
  rectangle "==Policies\\n\\nPermissions, stop conditions" <<HermesPolicies>> as HermesPolicies
  rectangle "==Dashboard\\n\\nscribe.cc.cd" <<HermesDashboard>> as HermesDashboard
}
rectangle "==User\\n\\nPlatform user" <<User>> as User
rectangle "==Human Reviewer\\n\\nFinal arbiter" <<Reviewer>> as Reviewer
rectangle "==Cloudflare\\n\\nR2, Pages, AI Gateway" <<Cloudflare>> as Cloudflare
rectangle "==Telegram\\n\\nNotifications" <<Telegram>> as Telegram
rectangle "==media-platform\\n\\nRender platform" <<MediaPlatform>> as MediaPlatform

User .[#8D8D8D,thickness=2].> MediaPlatform : <color:#8D8D8D>uses
Reviewer .[#8D8D8D,thickness=2].> Hermes : <color:#8D8D8D>reviews
Hermes .[#8D8D8D,thickness=2].> Cloudflare : <color:#8D8D8D>publishes
Hermes .[#8D8D8D,thickness=2].> Telegram : <color:#8D8D8D>notifies
Hermes .[#8D8D8D,thickness=2].> MediaPlatform : <color:#8D8D8D>develops
@enduml
`;case`vs0VerticalSlice`:return`@startuml
title "VS.0 — Timeline-to-Caption-Render Vertical Slice"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformPlatformApp>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleTimelineEdit>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleFakeTestLayer>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleCaptionTemplate>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModulePreviewRenderJobService>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModulePreviewArtifactQueryService>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleAssStyleMapper>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProviderBinding>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleFfmpegBaseline>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProductRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleStorageRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==platform-app\\n\\nSpring Boot entry point" <<MediaPlatformPlatformApp>> as MediaPlatformPlatformApp
rectangle "render-module" <<MediaPlatformRenderModule>> as MediaPlatformRenderModule {
  skinparam RectangleBorderColor<<MediaPlatformRenderModule>> #3b82f6
  skinparam RectangleFontColor<<MediaPlatformRenderModule>> #3b82f6
  skinparam RectangleBorderStyle<<MediaPlatformRenderModule>> dashed

  rectangle "==Timeline Edit Command\\n\\nTL.0: Sealed interface with 12 typed command records" <<MediaPlatformRenderModuleTimelineEdit>> as MediaPlatformRenderModuleTimelineEdit
  rectangle "==Fake Test Layer\\n\\nVS.1-TEST.1: Fake implementations for integration testing" <<MediaPlatformRenderModuleFakeTestLayer>> as MediaPlatformRenderModuleFakeTestLayer
  rectangle "==Caption Template\\n\\nCT.0: Typed intent model for caption/subtitle rendering" <<MediaPlatformRenderModuleCaptionTemplate>> as MediaPlatformRenderModuleCaptionTemplate
  rectangle "==Preview Render Job Service\\n\\nVS.1: Preview render job lifecycle" <<MediaPlatformRenderModulePreviewRenderJobService>> as MediaPlatformRenderModulePreviewRenderJobService
  rectangle "==Preview Artifact Query Service\\n\\nVS.1: Product/artifact metadata query" <<MediaPlatformRenderModulePreviewArtifactQueryService>> as MediaPlatformRenderModulePreviewArtifactQueryService
  rectangle "==AssStyleMapper\\n\\nCT.0: Maps CaptionTemplateSpec to ASS format parameters" <<MediaPlatformRenderModuleAssStyleMapper>> as MediaPlatformRenderModuleAssStyleMapper
  rectangle "==Provider Binding\\n\\nDeterministic eligibility + priority provider selection" <<MediaPlatformRenderModuleProviderBinding>> as MediaPlatformRenderModuleProviderBinding
  rectangle "==FFmpeg/libass Baseline\\n\\nProduction rendering baseline" <<MediaPlatformRenderModuleFfmpegBaseline>> as MediaPlatformRenderModuleFfmpegBaseline
  rectangle "==Product Runtime\\n\\nProduct lifecycle management" <<MediaPlatformRenderModuleProductRuntime>> as MediaPlatformRenderModuleProductRuntime
  rectangle "==Storage Runtime\\n\\nStorage/materialization management" <<MediaPlatformRenderModuleStorageRuntime>> as MediaPlatformRenderModuleStorageRuntime
}
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformRenderModulePreviewRenderJobService : <color:#8D8D8D>exposes API
MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformRenderModulePreviewArtifactQueryService : <color:#8D8D8D>exposes API
MediaPlatformRenderModuleTimelineEdit .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleCaptionTemplate : <color:#8D8D8D>generates typed intent
MediaPlatformRenderModuleCaptionTemplate .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleAssStyleMapper : <color:#8D8D8D>maps to ASS parameters
MediaPlatformRenderModuleAssStyleMapper .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProviderBinding : <color:#8D8D8D>resolves provider
MediaPlatformRenderModuleProviderBinding .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleFfmpegBaseline : <color:#8D8D8D>routes PRODUCTION
MediaPlatformRenderModulePreviewRenderJobService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProviderBinding : <color:#8D8D8D>compiles plan
MediaPlatformRenderModuleFfmpegBaseline .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>produces output
MediaPlatformRenderModulePreviewRenderJobService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleFfmpegBaseline : <color:#8D8D8D>executes preview
MediaPlatformRenderModuleProductRuntime .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleStorageRuntime : <color:#8D8D8D>manages lifecycle
MediaPlatformRenderModulePreviewRenderJobService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>creates product
MediaPlatformRenderModulePreviewArtifactQueryService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>queries product
MediaPlatformRenderModulePreviewArtifactQueryService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleStorageRuntime : <color:#8D8D8D>queries storage
MediaPlatformRenderModuleFakeTestLayer .[#8D8D8D,thickness=2].> MediaPlatformRenderModulePreviewRenderJobService : <color:#8D8D8D>tests
MediaPlatformRenderModuleFakeTestLayer .[#8D8D8D,thickness=2].> MediaPlatformRenderModulePreviewArtifactQueryService : <color:#8D8D8D>tests
MediaPlatformRenderModuleStorageRuntime .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>persists artifacts
@enduml
`;case`captionTemplateBoundary`:return`@startuml
title "Caption Template Boundary"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformRenderModuleTimelineEdit>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleCaptionTemplate>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleAssStyleMapper>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProviderBinding>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleFfmpegBaseline>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==Timeline Edit Command\\n\\nTL.0: Sealed interface with 12 typed command records" <<MediaPlatformRenderModuleTimelineEdit>> as MediaPlatformRenderModuleTimelineEdit
rectangle "==Caption Template\\n\\nCT.0: Typed intent model for caption/subtitle rendering" <<MediaPlatformRenderModuleCaptionTemplate>> as MediaPlatformRenderModuleCaptionTemplate
rectangle "==AssStyleMapper\\n\\nCT.0: Maps CaptionTemplateSpec to ASS format parameters" <<MediaPlatformRenderModuleAssStyleMapper>> as MediaPlatformRenderModuleAssStyleMapper
rectangle "==Provider Binding\\n\\nDeterministic eligibility + priority provider selection" <<MediaPlatformRenderModuleProviderBinding>> as MediaPlatformRenderModuleProviderBinding
rectangle "==FFmpeg/libass Baseline\\n\\nProduction rendering baseline" <<MediaPlatformRenderModuleFfmpegBaseline>> as MediaPlatformRenderModuleFfmpegBaseline

MediaPlatformRenderModuleTimelineEdit .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleCaptionTemplate : <color:#8D8D8D>generates typed intent
MediaPlatformRenderModuleCaptionTemplate .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleAssStyleMapper : <color:#8D8D8D>maps to ASS parameters
MediaPlatformRenderModuleAssStyleMapper .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProviderBinding : <color:#8D8D8D>resolves provider
MediaPlatformRenderModuleProviderBinding .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleFfmpegBaseline : <color:#8D8D8D>routes PRODUCTION
@enduml
`;case`providerExecutionBoundary`:return`@startuml
title "Provider Binding vs Execution Environment"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformRenderModuleProviderBinding>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleFfmpegBaseline>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformOpencue>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRemotion>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==Provider Binding\\n\\nDeterministic eligibility + priority provider selection" <<MediaPlatformRenderModuleProviderBinding>> as MediaPlatformRenderModuleProviderBinding
rectangle "==FFmpeg/libass Baseline\\n\\nProduction rendering baseline" <<MediaPlatformRenderModuleFfmpegBaseline>> as MediaPlatformRenderModuleFfmpegBaseline
rectangle "==OpenCue\\n\\nExecutionEnvironment only — NOT a Provider" <<MediaPlatformOpencue>> as MediaPlatformOpencue
rectangle "==Remotion\\n\\nNon-production/POC subtitle template provider" <<MediaPlatformRemotion>> as MediaPlatformRemotion

MediaPlatformRenderModuleProviderBinding .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleFfmpegBaseline : <color:#8D8D8D>routes PRODUCTION
MediaPlatformRenderModuleProviderBinding .[#8D8D8D,thickness=2].> MediaPlatformOpencue : <color:#8D8D8D>routes ExecutionEnv only
MediaPlatformRenderModuleProviderBinding .[#8D8D8D,thickness=2].> MediaPlatformRemotion : <color:#8D8D8D>routes POC only
@enduml
`;case`previewRenderJobApiFlow`:return`@startuml
title "VS.1 — Preview Render Job API Flow"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformRenderModulePreviewRenderJobService>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProviderBinding>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleFfmpegBaseline>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProductRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleStorageRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==Preview Render Job Service\\n\\nVS.1: Preview render job lifecycle" <<MediaPlatformRenderModulePreviewRenderJobService>> as MediaPlatformRenderModulePreviewRenderJobService
rectangle "==Provider Binding\\n\\nDeterministic eligibility + priority provider selection" <<MediaPlatformRenderModuleProviderBinding>> as MediaPlatformRenderModuleProviderBinding
rectangle "==FFmpeg/libass Baseline\\n\\nProduction rendering baseline" <<MediaPlatformRenderModuleFfmpegBaseline>> as MediaPlatformRenderModuleFfmpegBaseline
rectangle "==Product Runtime\\n\\nProduct lifecycle management" <<MediaPlatformRenderModuleProductRuntime>> as MediaPlatformRenderModuleProductRuntime
rectangle "==Storage Runtime\\n\\nStorage/materialization management" <<MediaPlatformRenderModuleStorageRuntime>> as MediaPlatformRenderModuleStorageRuntime
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

MediaPlatformRenderModulePreviewRenderJobService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProviderBinding : <color:#8D8D8D>compiles plan
MediaPlatformRenderModulePreviewRenderJobService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleFfmpegBaseline : <color:#8D8D8D>executes preview
MediaPlatformRenderModuleProviderBinding .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleFfmpegBaseline : <color:#8D8D8D>routes PRODUCTION
MediaPlatformRenderModulePreviewRenderJobService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>creates product
MediaPlatformRenderModuleFfmpegBaseline .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>produces output
MediaPlatformRenderModuleProductRuntime .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleStorageRuntime : <color:#8D8D8D>manages lifecycle
MediaPlatformRenderModuleStorageRuntime .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>persists artifacts
@enduml
`;case`headlessApiValidationFlow`:return`@startuml
title "VS.1 — Headless API Validation Flow"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformRenderModuleFakeTestLayer>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModulePreviewRenderJobService>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModulePreviewArtifactQueryService>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==Fake Test Layer\\n\\nVS.1-TEST.1: Fake implementations for integration testing" <<MediaPlatformRenderModuleFakeTestLayer>> as MediaPlatformRenderModuleFakeTestLayer
rectangle "==Preview Render Job Service\\n\\nVS.1: Preview render job lifecycle" <<MediaPlatformRenderModulePreviewRenderJobService>> as MediaPlatformRenderModulePreviewRenderJobService
rectangle "==Preview Artifact Query Service\\n\\nVS.1: Product/artifact metadata query" <<MediaPlatformRenderModulePreviewArtifactQueryService>> as MediaPlatformRenderModulePreviewArtifactQueryService

MediaPlatformRenderModuleFakeTestLayer .[#8D8D8D,thickness=2].> MediaPlatformRenderModulePreviewRenderJobService : <color:#8D8D8D>tests
MediaPlatformRenderModuleFakeTestLayer .[#8D8D8D,thickness=2].> MediaPlatformRenderModulePreviewArtifactQueryService : <color:#8D8D8D>tests
@enduml
`;case`storageDeliveryProfileArchitecture`:return`@startuml
title "Storage Delivery Profile Architecture"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformStorageModuleStorageDeliveryProfileValidator>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformStorageModuleStorageDeliveryProfileConfig>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformStorageModuleStorageDeliveryProfileRegistry>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformStorageModuleStorageDeliveryProfile>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformStorageModuleStorageDeliveryProfileDTO>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "storage-module" <<MediaPlatformStorageModule>> as MediaPlatformStorageModule {
  skinparam RectangleBorderColor<<MediaPlatformStorageModule>> #3b82f6
  skinparam RectangleFontColor<<MediaPlatformStorageModule>> #3b82f6
  skinparam RectangleBorderStyle<<MediaPlatformStorageModule>> dashed

  rectangle "==StorageDeliveryProfileValidator\\n\\nLocal validation: access mode, capability, security rules" <<MediaPlatformStorageModuleStorageDeliveryProfileValidator>> as MediaPlatformStorageModuleStorageDeliveryProfileValidator
  rectangle "==StorageDeliveryProfileConfig\\n\\nSpring config binding: storage.delivery.*" <<MediaPlatformStorageModuleStorageDeliveryProfileConfig>> as MediaPlatformStorageModuleStorageDeliveryProfileConfig
  rectangle "==StorageDeliveryProfileRegistry\\n\\nRead-only registry: 8 canonical profiles, not used for provider selection" <<MediaPlatformStorageModuleStorageDeliveryProfileRegistry>> as MediaPlatformStorageModuleStorageDeliveryProfileRegistry
  rectangle "==Storage Delivery Profile\\n\\nProfile contract: 8 canonical profiles, access modes, capabilities" <<MediaPlatformStorageModuleStorageDeliveryProfile>> as MediaPlatformStorageModuleStorageDeliveryProfile
  rectangle "==StorageDeliveryProfile DTO\\n\\nInternal value objects: Profile, Capabilities, SecurityPolicy" <<MediaPlatformStorageModuleStorageDeliveryProfileDTO>> as MediaPlatformStorageModuleStorageDeliveryProfileDTO
}

MediaPlatformStorageModuleStorageDeliveryProfile .[#8D8D8D,thickness=2].> MediaPlatformStorageModuleStorageDeliveryProfileDTO : <color:#8D8D8D>maps to DTOs
MediaPlatformStorageModuleStorageDeliveryProfileValidator .[#8D8D8D,thickness=2].> MediaPlatformStorageModuleStorageDeliveryProfile : <color:#8D8D8D>validates
MediaPlatformStorageModuleStorageDeliveryProfileConfig .[#8D8D8D,thickness=2].> MediaPlatformStorageModuleStorageDeliveryProfile : <color:#8D8D8D>binds config
MediaPlatformStorageModuleStorageDeliveryProfileRegistry .[#8D8D8D,thickness=2].> MediaPlatformStorageModuleStorageDeliveryProfile : <color:#8D8D8D>holds profiles
@enduml
`;case`ingestPreflightPolicyFlow`:return`@startuml
title "Ingest Preflight Policy Flow"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformIngestModuleUploadHook>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModuleTikaProvider>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModuleFfprobeProvider>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModuleMetadataMerger>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModuleSafeReportDTO>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModulePolicyEvaluator>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformIngestModulePolicyResult>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "ingest-module" <<MediaPlatformIngestModule>> as MediaPlatformIngestModule {
  skinparam RectangleBorderColor<<MediaPlatformIngestModule>> #3b82f6
  skinparam RectangleFontColor<<MediaPlatformIngestModule>> #3b82f6
  skinparam RectangleBorderStyle<<MediaPlatformIngestModule>> dashed

  rectangle "==UploadReportOnlyPreflightHook\\n\\nReport-only hook: disabled by default, fail-open, never rejects" <<MediaPlatformIngestModuleUploadHook>> as MediaPlatformIngestModuleUploadHook
  rectangle "==TikaDetectorProvider\\n\\nMIME detection, extension mismatch, content-type detection" <<MediaPlatformIngestModuleTikaProvider>> as MediaPlatformIngestModuleTikaProvider
  rectangle "==FFprobeMetadataProvider\\n\\nMedia technical metadata: duration, codec, resolution" <<MediaPlatformIngestModuleFfprobeProvider>> as MediaPlatformIngestModuleFfprobeProvider
  rectangle "==IngestMetadataMerger\\n\\nMerges Tika + FFprobe into unified result" <<MediaPlatformIngestModuleMetadataMerger>> as MediaPlatformIngestModuleMetadataMerger
  rectangle "==SafePreflightReportSummary\\n\\nSafe internal DTO: no raw metadata, no storage internals" <<MediaPlatformIngestModuleSafeReportDTO>> as MediaPlatformIngestModuleSafeReportDTO
  rectangle "==ReportOnlyPreflightPolicyEvaluator\\n\\nReport-only evaluator: never rejects, fails open" <<MediaPlatformIngestModulePolicyEvaluator>> as MediaPlatformIngestModulePolicyEvaluator
  rectangle "==PreflightPolicyEvaluationResult\\n\\nPolicy result: ACCEPT, ACCEPT_WITH_WARNINGS, REJECT_CANDIDATE" <<MediaPlatformIngestModulePolicyResult>> as MediaPlatformIngestModulePolicyResult
}

MediaPlatformIngestModuleUploadHook .[#8D8D8D,thickness=2].> MediaPlatformIngestModuleTikaProvider : <color:#8D8D8D>detects MIME
MediaPlatformIngestModuleUploadHook .[#8D8D8D,thickness=2].> MediaPlatformIngestModuleFfprobeProvider : <color:#8D8D8D>probes media
MediaPlatformIngestModuleUploadHook .[#8D8D8D,thickness=2].> MediaPlatformIngestModuleMetadataMerger : <color:#8D8D8D>merges results
MediaPlatformIngestModuleMetadataMerger .[#8D8D8D,thickness=2].> MediaPlatformIngestModuleSafeReportDTO : <color:#8D8D8D>produces safe report
MediaPlatformIngestModuleSafeReportDTO .[#8D8D8D,thickness=2].> MediaPlatformIngestModulePolicyEvaluator : <color:#8D8D8D>evaluates policy
MediaPlatformIngestModulePolicyEvaluator .[#8D8D8D,thickness=2].> MediaPlatformIngestModulePolicyResult : <color:#8D8D8D>produces result
@enduml
`;case`r2ArtifactAccessPath`:return`@startuml
title "R2 Artifact Access Path"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformStorageModuleS3Materializer>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleStorageRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformStorageModuleAccessDescriptor>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==S3ObjectMaterializer\\n\\nS3/R2-compatible storage materializer" <<MediaPlatformStorageModuleS3Materializer>> as MediaPlatformStorageModuleS3Materializer
rectangle "==Storage Runtime\\n\\nStorage/materialization management" <<MediaPlatformRenderModuleStorageRuntime>> as MediaPlatformRenderModuleStorageRuntime
rectangle "==AccessDescriptor\\n\\nUser-facing access contract: SIGNED_URL generated on demand" <<MediaPlatformStorageModuleAccessDescriptor>> as MediaPlatformStorageModuleAccessDescriptor
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

MediaPlatformStorageModuleS3Materializer .[#8D8D8D,thickness=2].> MediaPlatformStorageModuleAccessDescriptor : <color:#8D8D8D>generates signed URL
MediaPlatformRenderModuleStorageRuntime .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>persists artifacts
MediaPlatformStorageModuleS3Materializer .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>R2/S3-compatible
@enduml
`;case`productStorageBoundary`:return`@startuml
title "Storage Boundary"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformRenderModuleFfmpegBaseline>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProductRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleStorageRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==FFmpeg/libass Baseline\\n\\nProduction rendering baseline" <<MediaPlatformRenderModuleFfmpegBaseline>> as MediaPlatformRenderModuleFfmpegBaseline
rectangle "==Product Runtime\\n\\nProduct lifecycle management" <<MediaPlatformRenderModuleProductRuntime>> as MediaPlatformRenderModuleProductRuntime
rectangle "==Storage Runtime\\n\\nStorage/materialization management" <<MediaPlatformRenderModuleStorageRuntime>> as MediaPlatformRenderModuleStorageRuntime
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

MediaPlatformRenderModuleFfmpegBaseline .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>produces output
MediaPlatformRenderModuleProductRuntime .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleStorageRuntime : <color:#8D8D8D>manages lifecycle
MediaPlatformRenderModuleStorageRuntime .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>persists artifacts
@enduml
`;case`productArtifactResponseFlow`:return`@startuml
title "Artifact Response Flow"
top to bottom direction

hide stereotype
skinparam ranksep 60
skinparam nodesep 30
skinparam {
  arrowFontSize 10
  defaultTextAlignment center
  wrapWidth 200
  maxMessageSize 100
  shadowing false
}

skinparam rectangle<<MediaPlatformRenderModulePreviewArtifactQueryService>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleProductRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformRenderModuleStorageRuntime>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<Storage>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==Preview Artifact Query Service\\n\\nVS.1: Product/artifact metadata query" <<MediaPlatformRenderModulePreviewArtifactQueryService>> as MediaPlatformRenderModulePreviewArtifactQueryService
rectangle "==Product Runtime\\n\\nProduct lifecycle management" <<MediaPlatformRenderModuleProductRuntime>> as MediaPlatformRenderModuleProductRuntime
rectangle "==Storage Runtime\\n\\nStorage/materialization management" <<MediaPlatformRenderModuleStorageRuntime>> as MediaPlatformRenderModuleStorageRuntime
rectangle "==Storage\\n\\nObject storage / shared filesystem" <<Storage>> as Storage

MediaPlatformRenderModulePreviewArtifactQueryService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleProductRuntime : <color:#8D8D8D>queries product
MediaPlatformRenderModulePreviewArtifactQueryService .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleStorageRuntime : <color:#8D8D8D>queries storage
MediaPlatformRenderModuleProductRuntime .[#8D8D8D,thickness=2].> MediaPlatformRenderModuleStorageRuntime : <color:#8D8D8D>manages lifecycle
MediaPlatformRenderModuleStorageRuntime .[#8D8D8D,thickness=2].> Storage : <color:#8D8D8D>persists artifacts
@enduml
`;default:throw Error(`Unknown viewId: `+e)}};export{e as pumlSource};