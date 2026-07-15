var e=e=>{switch(e){case`index`:return`---
title: "Landscape view"
---
graph TB
  User@{ shape: rectangle, label: "User" }
  Reviewer@{ shape: rectangle, label: "Human Reviewer" }
  Hermes@{ shape: rectangle, label: "Hermes Control Plane" }
  Cloudflare@{ shape: rectangle, label: "Cloudflare" }
  Telegram@{ shape: rectangle, label: "Telegram" }
  MediaPlatform@{ shape: rectangle, label: "media-platform" }
  AiProviders@{ shape: rectangle, label: "AI Providers" }
  Storage@{ shape: rectangle, label: "Storage" }
  User -. "\`uses\`" .-> MediaPlatform
  Reviewer -. "\`reviews\`" .-> Hermes
  Hermes -. "\`publishes\`" .-> Cloudflare
  Hermes -. "\`notifies\`" .-> Telegram
  MediaPlatform -. "\`calls models\`" .-> AiProviders
  MediaPlatform -. "\`reads/writes\`" .-> Storage
  Hermes -. "\`develops\`" .-> MediaPlatform
`;case`systemContext`:return`---
title: "System Context"
---
graph TB
  subgraph MediaPlatform["\`media-platform\`"]
    MediaPlatform.Opencue@{ shape: rectangle, label: "OpenCue" }
    MediaPlatform.Remotion@{ shape: rectangle, label: "Remotion" }
  end
`;case`containerDiagram`:return`---
title: "Container View"
---
graph TB
  subgraph MediaPlatform["\`media-platform\`"]
    MediaPlatform.PlatformApp@{ shape: rectangle, label: "platform-app" }
    MediaPlatform.AiModule@{ shape: rectangle, label: "ai-module" }
    MediaPlatform.RenderModule@{ shape: rectangle, label: "render-module" }
    MediaPlatform.StorageModule@{ shape: rectangle, label: "storage-module" }
    MediaPlatform.IngestModule@{ shape: rectangle, label: "ingest-module" }
    MediaPlatform.Opencue@{ shape: rectangle, label: "OpenCue" }
    MediaPlatform.Remotion@{ shape: rectangle, label: "Remotion" }
    MediaPlatform.SharedKernel@{ shape: rectangle, label: "shared-kernel" }
  end
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.RenderModule
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.StorageModule
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.IngestModule
  MediaPlatform.RenderModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.RenderModule -. "\`routes ExecutionEnv only\`" .-> MediaPlatform.Opencue
  MediaPlatform.RenderModule -. "\`routes POC only\`" .-> MediaPlatform.Remotion
  MediaPlatform.StorageModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.IngestModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.AiModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.RenderModule -. "\`persists artifacts\`" .-> Storage
  MediaPlatform.StorageModule -. "\`R2/S3-compatible\`" .-> Storage
`;case`controlPlane`:return`---
title: "Hermes Control Plane"
---
graph TB
  subgraph Hermes["\`Hermes Control Plane\`"]
    Hermes.HermesAgent@{ shape: rectangle, label: "Hermes Agent" }
    Hermes.CodingAgents@{ shape: rectangle, label: "Coding Agents" }
    Hermes.ReviewInfra@{ shape: rectangle, label: "Review Infrastructure" }
    Hermes.Policies@{ shape: rectangle, label: "Policies" }
    Hermes.Dashboard@{ shape: rectangle, label: "Dashboard" }
  end
`;case`controlPlaneContext`:return`---
title: "Control Plane in Context"
---
graph TB
  subgraph Hermes["\`Hermes Control Plane\`"]
    Hermes.HermesAgent@{ shape: rectangle, label: "Hermes Agent" }
    Hermes.CodingAgents@{ shape: rectangle, label: "Coding Agents" }
    Hermes.ReviewInfra@{ shape: rectangle, label: "Review Infrastructure" }
    Hermes.Policies@{ shape: rectangle, label: "Policies" }
    Hermes.Dashboard@{ shape: rectangle, label: "Dashboard" }
  end
  User@{ shape: rectangle, label: "User" }
  Reviewer@{ shape: rectangle, label: "Human Reviewer" }
  Cloudflare@{ shape: rectangle, label: "Cloudflare" }
  Telegram@{ shape: rectangle, label: "Telegram" }
  MediaPlatform@{ shape: rectangle, label: "media-platform" }
  User -. "\`uses\`" .-> MediaPlatform
  Reviewer -. "\`reviews\`" .-> Hermes
  Hermes -. "\`publishes\`" .-> Cloudflare
  Hermes -. "\`notifies\`" .-> Telegram
  Hermes -. "\`develops\`" .-> MediaPlatform
`;case`vs0VerticalSlice`:return'---\ntitle: "VS.0 — Timeline-to-Caption-Render Vertical Slice"\n---\ngraph TB\n  MediaPlatformPlatformApp@{ shape: rectangle, label: "platform-app" }\n  subgraph MediaPlatformRenderModule["`render-module`"]\n    MediaPlatformRenderModule.TimelineEdit@{ shape: rectangle, label: "Timeline Edit Command" }\n    MediaPlatformRenderModule.FakeTestLayer@{ shape: rectangle, label: "Fake Test Layer" }\n    MediaPlatformRenderModule.CaptionTemplate@{ shape: rectangle, label: "Caption Template" }\n    MediaPlatformRenderModule.PreviewRenderJobService@{ shape: rectangle, label: "Preview Render Job Service" }\n    MediaPlatformRenderModule.PreviewArtifactQueryService@{ shape: rectangle, label: "Preview Artifact Query Service" }\n    MediaPlatformRenderModule.AssStyleMapper@{ shape: rectangle, label: "AssStyleMapper" }\n    MediaPlatformRenderModule.RenderJobClaimService@{ shape: rectangle, label: "RenderJob Claim Service" }\n    MediaPlatformRenderModule.RenderJobFailureService@{ shape: rectangle, label: "RenderJob Failure Service" }\n    MediaPlatformRenderModule.RenderJobStateMachine@{ shape: rectangle, label: "RenderJob State Machine" }\n    MediaPlatformRenderModule.ProviderBinding@{ shape: rectangle, label: "Provider Binding" }\n    MediaPlatformRenderModule.FfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }\n    MediaPlatformRenderModule.ProductRuntime@{ shape: rectangle, label: "Product Runtime" }\n    MediaPlatformRenderModule.ProviderRegistry@{ shape: rectangle, label: "Provider Registry" }\n    MediaPlatformRenderModule.StorageRuntime@{ shape: rectangle, label: "Storage Runtime" }\n  end\n  Storage@{ shape: rectangle, label: "Storage" }\n  MediaPlatformPlatformApp -. "`exposes API`" .-> MediaPlatformRenderModule.PreviewRenderJobService\n  MediaPlatformPlatformApp -. "`exposes API`" .-> MediaPlatformRenderModule.PreviewArtifactQueryService\n  MediaPlatformRenderModule.TimelineEdit -. "`generates typed intent`" .-> MediaPlatformRenderModule.CaptionTemplate\n  MediaPlatformRenderModule.CaptionTemplate -. "`maps to ASS parameters`" .-> MediaPlatformRenderModule.AssStyleMapper\n  MediaPlatformRenderModule.AssStyleMapper -. "`resolves provider`" .-> MediaPlatformRenderModule.ProviderBinding\n  MediaPlatformRenderModule.ProviderBinding -. "`routes PRODUCTION`" .-> MediaPlatformRenderModule.FfmpegBaseline\n  MediaPlatformRenderModule.ProviderBinding -. "`resolves by canonical ID`" .-> MediaPlatformRenderModule.ProviderRegistry\n  MediaPlatformRenderModule.PreviewRenderJobService -. "`compiles plan`" .-> MediaPlatformRenderModule.ProviderBinding\n  MediaPlatformRenderModule.FfmpegBaseline -. "`produces output`" .-> MediaPlatformRenderModule.ProductRuntime\n  MediaPlatformRenderModule.FfmpegBaseline -. "`registers as ffmpeg`" .-> MediaPlatformRenderModule.ProviderRegistry\n  MediaPlatformRenderModule.PreviewRenderJobService -. "`executes preview`" .-> MediaPlatformRenderModule.FfmpegBaseline\n  MediaPlatformRenderModule.ProductRuntime -. "`manages lifecycle`" .-> MediaPlatformRenderModule.StorageRuntime\n  MediaPlatformRenderModule.PreviewRenderJobService -. "`creates product`" .-> MediaPlatformRenderModule.ProductRuntime\n  MediaPlatformRenderModule.PreviewArtifactQueryService -. "`queries product`" .-> MediaPlatformRenderModule.ProductRuntime\n  MediaPlatformRenderModule.PreviewArtifactQueryService -. "`queries storage`" .-> MediaPlatformRenderModule.StorageRuntime\n  MediaPlatformRenderModule.PreviewRenderJobService -. "`claims execution`" .-> MediaPlatformRenderModule.RenderJobClaimService\n  MediaPlatformRenderModule.PreviewRenderJobService -. "`records failure`" .-> MediaPlatformRenderModule.RenderJobFailureService\n  MediaPlatformRenderModule.PreviewRenderJobService -. "`transitions state`" .-> MediaPlatformRenderModule.RenderJobStateMachine\n  MediaPlatformRenderModule.FakeTestLayer -. "`tests`" .-> MediaPlatformRenderModule.PreviewRenderJobService\n  MediaPlatformRenderModule.FakeTestLayer -. "`tests`" .-> MediaPlatformRenderModule.PreviewArtifactQueryService\n  MediaPlatformRenderModule.RenderJobClaimService -. "`verifies provider`" .-> MediaPlatformRenderModule.ProviderRegistry\n  MediaPlatformRenderModule.StorageRuntime -. "`persists artifacts`" .-> Storage\n';case`captionTemplateBoundary`:return`---
title: "Caption Template Boundary"
---
graph TB
  MediaPlatformRenderModuleTimelineEdit@{ shape: rectangle, label: "Timeline Edit Command" }
  MediaPlatformRenderModuleCaptionTemplate@{ shape: rectangle, label: "Caption Template" }
  MediaPlatformRenderModuleAssStyleMapper@{ shape: rectangle, label: "AssStyleMapper" }
  MediaPlatformRenderModuleProviderBinding@{ shape: rectangle, label: "Provider Binding" }
  MediaPlatformRenderModuleFfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }
  MediaPlatformRenderModuleTimelineEdit -. "\`generates typed intent\`" .-> MediaPlatformRenderModuleCaptionTemplate
  MediaPlatformRenderModuleCaptionTemplate -. "\`maps to ASS parameters\`" .-> MediaPlatformRenderModuleAssStyleMapper
  MediaPlatformRenderModuleAssStyleMapper -. "\`resolves provider\`" .-> MediaPlatformRenderModuleProviderBinding
  MediaPlatformRenderModuleProviderBinding -. "\`routes PRODUCTION\`" .-> MediaPlatformRenderModuleFfmpegBaseline
`;case`providerExecutionBoundary`:return`---
title: "Provider Binding vs Execution Environment"
---
graph TB
  MediaPlatformRenderModuleProviderBinding@{ shape: rectangle, label: "Provider Binding" }
  MediaPlatformRenderModuleFfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }
  MediaPlatformOpencue@{ shape: rectangle, label: "OpenCue" }
  MediaPlatformRemotion@{ shape: rectangle, label: "Remotion" }
  MediaPlatformRenderModuleProviderBinding -. "\`routes PRODUCTION\`" .-> MediaPlatformRenderModuleFfmpegBaseline
  MediaPlatformRenderModuleProviderBinding -. "\`routes ExecutionEnv only\`" .-> MediaPlatformOpencue
  MediaPlatformRenderModuleProviderBinding -. "\`routes POC only\`" .-> MediaPlatformRemotion
`;case`previewRenderJobApiFlow`:return`---
title: "VS.1 — Preview Render Job API Flow"
---
graph TB
  MediaPlatformRenderModulePreviewRenderJobService@{ shape: rectangle, label: "Preview Render Job Service" }
  MediaPlatformRenderModuleProviderBinding@{ shape: rectangle, label: "Provider Binding" }
  MediaPlatformRenderModuleFfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }
  MediaPlatformRenderModuleProductRuntime@{ shape: rectangle, label: "Product Runtime" }
  MediaPlatformRenderModuleStorageRuntime@{ shape: rectangle, label: "Storage Runtime" }
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatformRenderModulePreviewRenderJobService -. "\`compiles plan\`" .-> MediaPlatformRenderModuleProviderBinding
  MediaPlatformRenderModulePreviewRenderJobService -. "\`executes preview\`" .-> MediaPlatformRenderModuleFfmpegBaseline
  MediaPlatformRenderModuleProviderBinding -. "\`routes PRODUCTION\`" .-> MediaPlatformRenderModuleFfmpegBaseline
  MediaPlatformRenderModulePreviewRenderJobService -. "\`creates product\`" .-> MediaPlatformRenderModuleProductRuntime
  MediaPlatformRenderModuleFfmpegBaseline -. "\`produces output\`" .-> MediaPlatformRenderModuleProductRuntime
  MediaPlatformRenderModuleProductRuntime -. "\`manages lifecycle\`" .-> MediaPlatformRenderModuleStorageRuntime
  MediaPlatformRenderModuleStorageRuntime -. "\`persists artifacts\`" .-> Storage
`;case`headlessApiValidationFlow`:return`---
title: "VS.1 — Headless API Validation Flow"
---
graph TB
  MediaPlatformRenderModuleFakeTestLayer@{ shape: rectangle, label: "Fake Test Layer" }
  MediaPlatformRenderModulePreviewRenderJobService@{ shape: rectangle, label: "Preview Render Job Service" }
  MediaPlatformRenderModulePreviewArtifactQueryService@{ shape: rectangle, label: "Preview Artifact Query Service" }
  MediaPlatformRenderModuleFakeTestLayer -. "\`tests\`" .-> MediaPlatformRenderModulePreviewRenderJobService
  MediaPlatformRenderModuleFakeTestLayer -. "\`tests\`" .-> MediaPlatformRenderModulePreviewArtifactQueryService
`;case`storageDeliveryProfileArchitecture`:return`---
title: "Storage Delivery Profile Architecture"
---
graph TB
  subgraph MediaPlatformStorageModule["\`storage-module\`"]
    MediaPlatformStorageModule.StorageDeliveryProfileValidator@{ shape: rectangle, label: "StorageDeliveryProfileValidator" }
    MediaPlatformStorageModule.StorageDeliveryProfileConfig@{ shape: rectangle, label: "StorageDeliveryProfileConfig" }
    MediaPlatformStorageModule.StorageDeliveryProfileRegistry@{ shape: rectangle, label: "StorageDeliveryProfileRegistry" }
    MediaPlatformStorageModule.StorageDeliveryProfile@{ shape: rectangle, label: "Storage Delivery Profile" }
    MediaPlatformStorageModule.StorageDeliveryProfileDTO@{ shape: rectangle, label: "StorageDeliveryProfile DTO" }
  end
  MediaPlatformStorageModule.StorageDeliveryProfile -. "\`maps to DTOs\`" .-> MediaPlatformStorageModule.StorageDeliveryProfileDTO
  MediaPlatformStorageModule.StorageDeliveryProfileValidator -. "\`validates\`" .-> MediaPlatformStorageModule.StorageDeliveryProfile
  MediaPlatformStorageModule.StorageDeliveryProfileConfig -. "\`binds config\`" .-> MediaPlatformStorageModule.StorageDeliveryProfile
  MediaPlatformStorageModule.StorageDeliveryProfileRegistry -. "\`holds profiles\`" .-> MediaPlatformStorageModule.StorageDeliveryProfile
`;case`ingestPreflightPolicyFlow`:return`---
title: "Ingest Preflight Policy Flow"
---
graph TB
  subgraph MediaPlatformIngestModule["\`ingest-module\`"]
    MediaPlatformIngestModule.UploadHook@{ shape: rectangle, label: "UploadReportOnlyPreflightHook" }
    MediaPlatformIngestModule.TikaProvider@{ shape: rectangle, label: "TikaDetectorProvider" }
    MediaPlatformIngestModule.FfprobeProvider@{ shape: rectangle, label: "FFprobeMetadataProvider" }
    MediaPlatformIngestModule.MetadataMerger@{ shape: rectangle, label: "IngestMetadataMerger" }
    MediaPlatformIngestModule.SafeReportDTO@{ shape: rectangle, label: "SafePreflightReportSummary" }
    MediaPlatformIngestModule.PolicyEvaluator@{ shape: rectangle, label: "ReportOnlyPreflightPolicyEvaluator" }
    MediaPlatformIngestModule.PolicyResult@{ shape: rectangle, label: "PreflightPolicyEvaluationResult" }
  end
  MediaPlatformIngestModule.UploadHook -. "\`detects MIME\`" .-> MediaPlatformIngestModule.TikaProvider
  MediaPlatformIngestModule.UploadHook -. "\`probes media\`" .-> MediaPlatformIngestModule.FfprobeProvider
  MediaPlatformIngestModule.UploadHook -. "\`merges results\`" .-> MediaPlatformIngestModule.MetadataMerger
  MediaPlatformIngestModule.MetadataMerger -. "\`produces safe report\`" .-> MediaPlatformIngestModule.SafeReportDTO
  MediaPlatformIngestModule.SafeReportDTO -. "\`evaluates policy\`" .-> MediaPlatformIngestModule.PolicyEvaluator
  MediaPlatformIngestModule.PolicyEvaluator -. "\`produces result\`" .-> MediaPlatformIngestModule.PolicyResult
`;case`r2ArtifactAccessPath`:return`---
title: "R2 Artifact Access Path"
---
graph TB
  MediaPlatformStorageModuleS3Materializer@{ shape: rectangle, label: "S3ObjectMaterializer" }
  MediaPlatformRenderModuleStorageRuntime@{ shape: rectangle, label: "Storage Runtime" }
  MediaPlatformStorageModuleAccessDescriptor@{ shape: rectangle, label: "AccessDescriptor" }
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatformStorageModuleS3Materializer -. "\`generates signed URL\`" .-> MediaPlatformStorageModuleAccessDescriptor
  MediaPlatformRenderModuleStorageRuntime -. "\`persists artifacts\`" .-> Storage
  MediaPlatformStorageModuleS3Materializer -. "\`R2/S3-compatible\`" .-> Storage
`;case`renderJobClaimFailureArchitecture`:return`---
title: "RenderJob Claim and Failure Architecture"
---
graph TB
  MediaPlatformRenderModulePreviewRenderJobService@{ shape: rectangle, label: "Preview Render Job Service" }
  MediaPlatformRenderModuleRenderJobClaimService@{ shape: rectangle, label: "RenderJob Claim Service" }
  MediaPlatformRenderModuleRenderJobFailureService@{ shape: rectangle, label: "RenderJob Failure Service" }
  MediaPlatformRenderModuleRenderJobStateMachine@{ shape: rectangle, label: "RenderJob State Machine" }
  MediaPlatformRenderModuleProviderBinding@{ shape: rectangle, label: "Provider Binding" }
  MediaPlatformRenderModuleFfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }
  MediaPlatformRenderModuleProviderRegistry@{ shape: rectangle, label: "Provider Registry" }
  MediaPlatformRenderModulePreviewRenderJobService -. "\`claims execution\`" .-> MediaPlatformRenderModuleRenderJobClaimService
  MediaPlatformRenderModulePreviewRenderJobService -. "\`records failure\`" .-> MediaPlatformRenderModuleRenderJobFailureService
  MediaPlatformRenderModulePreviewRenderJobService -. "\`transitions state\`" .-> MediaPlatformRenderModuleRenderJobStateMachine
  MediaPlatformRenderModuleRenderJobClaimService -. "\`verifies provider\`" .-> MediaPlatformRenderModuleProviderRegistry
  MediaPlatformRenderModulePreviewRenderJobService -. "\`compiles plan\`" .-> MediaPlatformRenderModuleProviderBinding
  MediaPlatformRenderModuleProviderBinding -. "\`resolves by canonical ID\`" .-> MediaPlatformRenderModuleProviderRegistry
  MediaPlatformRenderModulePreviewRenderJobService -. "\`executes preview\`" .-> MediaPlatformRenderModuleFfmpegBaseline
  MediaPlatformRenderModuleProviderBinding -. "\`routes PRODUCTION\`" .-> MediaPlatformRenderModuleFfmpegBaseline
  MediaPlatformRenderModuleFfmpegBaseline -. "\`registers as ffmpeg\`" .-> MediaPlatformRenderModuleProviderRegistry
`;case`productStorageBoundary`:return`---
title: "Storage Boundary"
---
graph TB
  MediaPlatformRenderModuleFfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }
  MediaPlatformRenderModuleProductRuntime@{ shape: rectangle, label: "Product Runtime" }
  MediaPlatformRenderModuleStorageRuntime@{ shape: rectangle, label: "Storage Runtime" }
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatformRenderModuleFfmpegBaseline -. "\`produces output\`" .-> MediaPlatformRenderModuleProductRuntime
  MediaPlatformRenderModuleProductRuntime -. "\`manages lifecycle\`" .-> MediaPlatformRenderModuleStorageRuntime
  MediaPlatformRenderModuleStorageRuntime -. "\`persists artifacts\`" .-> Storage
`;case`productArtifactResponseFlow`:return`---
title: "Artifact Response Flow"
---
graph TB
  MediaPlatformRenderModulePreviewArtifactQueryService@{ shape: rectangle, label: "Preview Artifact Query Service" }
  MediaPlatformRenderModuleProductRuntime@{ shape: rectangle, label: "Product Runtime" }
  MediaPlatformRenderModuleStorageRuntime@{ shape: rectangle, label: "Storage Runtime" }
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatformRenderModulePreviewArtifactQueryService -. "\`queries product\`" .-> MediaPlatformRenderModuleProductRuntime
  MediaPlatformRenderModulePreviewArtifactQueryService -. "\`queries storage\`" .-> MediaPlatformRenderModuleStorageRuntime
  MediaPlatformRenderModuleProductRuntime -. "\`manages lifecycle\`" .-> MediaPlatformRenderModuleStorageRuntime
  MediaPlatformRenderModuleStorageRuntime -. "\`persists artifacts\`" .-> Storage
`;default:throw Error(`Unknown viewId: `+e)}};export{e as mmdSource};