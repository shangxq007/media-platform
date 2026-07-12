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
    MediaPlatform.RenderModule@{ shape: rectangle, label: "render-module" }
    MediaPlatform.AiModule@{ shape: rectangle, label: "ai-module" }
    MediaPlatform.Opencue@{ shape: rectangle, label: "OpenCue" }
    MediaPlatform.Remotion@{ shape: rectangle, label: "Remotion" }
    MediaPlatform.SharedKernel@{ shape: rectangle, label: "shared-kernel" }
  end
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.RenderModule
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.AiModule
  MediaPlatform.RenderModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.RenderModule -. "\`routes ExecutionEnv only\`" .-> MediaPlatform.Opencue
  MediaPlatform.RenderModule -. "\`routes POC only\`" .-> MediaPlatform.Remotion
  MediaPlatform.AiModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.RenderModule -. "\`persists artifacts\`" .-> Storage
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
`;case`vs0VerticalSlice`:return`---
title: "VS.0 — Timeline-to-Caption-Render Vertical Slice"
---
graph TB
  MediaPlatformPlatformApp@{ shape: rectangle, label: "platform-app" }
  subgraph MediaPlatformRenderModule["\`render-module\`"]
    MediaPlatformRenderModule.TimelineEdit@{ shape: rectangle, label: "Timeline Edit Command" }
    MediaPlatformRenderModule.FakeTestLayer@{ shape: rectangle, label: "Fake Test Layer" }
    MediaPlatformRenderModule.CaptionTemplate@{ shape: rectangle, label: "Caption Template" }
    MediaPlatformRenderModule.PreviewRenderJobService@{ shape: rectangle, label: "Preview Render Job Service" }
    MediaPlatformRenderModule.PreviewArtifactQueryService@{ shape: rectangle, label: "Preview Artifact Query Service" }
    MediaPlatformRenderModule.AssStyleMapper@{ shape: rectangle, label: "AssStyleMapper" }
    MediaPlatformRenderModule.ProviderBinding@{ shape: rectangle, label: "Provider Binding" }
    MediaPlatformRenderModule.FfmpegBaseline@{ shape: rectangle, label: "FFmpeg/libass Baseline" }
    MediaPlatformRenderModule.ProductRuntime@{ shape: rectangle, label: "Product Runtime" }
    MediaPlatformRenderModule.StorageRuntime@{ shape: rectangle, label: "Storage Runtime" }
  end
  Storage@{ shape: rectangle, label: "Storage" }
  MediaPlatformPlatformApp -. "\`exposes API\`" .-> MediaPlatformRenderModule.PreviewRenderJobService
  MediaPlatformPlatformApp -. "\`exposes API\`" .-> MediaPlatformRenderModule.PreviewArtifactQueryService
  MediaPlatformRenderModule.TimelineEdit -. "\`generates typed intent\`" .-> MediaPlatformRenderModule.CaptionTemplate
  MediaPlatformRenderModule.CaptionTemplate -. "\`maps to ASS parameters\`" .-> MediaPlatformRenderModule.AssStyleMapper
  MediaPlatformRenderModule.AssStyleMapper -. "\`resolves provider\`" .-> MediaPlatformRenderModule.ProviderBinding
  MediaPlatformRenderModule.ProviderBinding -. "\`routes PRODUCTION\`" .-> MediaPlatformRenderModule.FfmpegBaseline
  MediaPlatformRenderModule.PreviewRenderJobService -. "\`compiles plan\`" .-> MediaPlatformRenderModule.ProviderBinding
  MediaPlatformRenderModule.FfmpegBaseline -. "\`produces output\`" .-> MediaPlatformRenderModule.ProductRuntime
  MediaPlatformRenderModule.PreviewRenderJobService -. "\`executes preview\`" .-> MediaPlatformRenderModule.FfmpegBaseline
  MediaPlatformRenderModule.ProductRuntime -. "\`manages lifecycle\`" .-> MediaPlatformRenderModule.StorageRuntime
  MediaPlatformRenderModule.PreviewRenderJobService -. "\`creates product\`" .-> MediaPlatformRenderModule.ProductRuntime
  MediaPlatformRenderModule.PreviewArtifactQueryService -. "\`queries product\`" .-> MediaPlatformRenderModule.ProductRuntime
  MediaPlatformRenderModule.PreviewArtifactQueryService -. "\`queries storage\`" .-> MediaPlatformRenderModule.StorageRuntime
  MediaPlatformRenderModule.FakeTestLayer -. "\`tests\`" .-> MediaPlatformRenderModule.PreviewRenderJobService
  MediaPlatformRenderModule.FakeTestLayer -. "\`tests\`" .-> MediaPlatformRenderModule.PreviewArtifactQueryService
  MediaPlatformRenderModule.StorageRuntime -. "\`persists artifacts\`" .-> Storage
`;case`captionTemplateBoundary`:return`---
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