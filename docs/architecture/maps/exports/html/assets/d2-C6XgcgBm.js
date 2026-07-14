var e=e=>{switch(e){case`index`:return`direction: down

User: {
  label: "User"
}
Reviewer: {
  label: "Human Reviewer"
}
Hermes: {
  label: "Hermes Control Plane"
}
Cloudflare: {
  label: "Cloudflare"
}
Telegram: {
  label: "Telegram"
}
MediaPlatform: {
  label: "media-platform"
}
AiProviders: {
  label: "AI Providers"
}
Storage: {
  label: "Storage"
}

User -> MediaPlatform: "uses"
Reviewer -> Hermes: "reviews"
Hermes -> Cloudflare: "publishes"
Hermes -> Telegram: "notifies"
MediaPlatform -> AiProviders: "calls models"
MediaPlatform -> Storage: "reads/writes"
Hermes -> MediaPlatform: "develops"
`;case`systemContext`:return`direction: down

MediaPlatform: {
  label: "media-platform"

  Opencue: {
    label: "OpenCue"
  }
  Remotion: {
    label: "Remotion"
  }
}
`;case`containerDiagram`:return`direction: down

MediaPlatform: {
  label: "media-platform"

  PlatformApp: {
    label: "platform-app"
  }
  AiModule: {
    label: "ai-module"
  }
  RenderModule: {
    label: "render-module"
  }
  StorageModule: {
    label: "storage-module"
  }
  IngestModule: {
    label: "ingest-module"
  }
  Opencue: {
    label: "OpenCue"
  }
  Remotion: {
    label: "Remotion"
  }
  SharedKernel: {
    label: "shared-kernel"
  }
}
Storage: {
  label: "Storage"
}

MediaPlatform.PlatformApp -> MediaPlatform.RenderModule: "uses"
MediaPlatform.PlatformApp -> MediaPlatform.StorageModule: "uses"
MediaPlatform.PlatformApp -> MediaPlatform.IngestModule: "uses"
MediaPlatform.RenderModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.RenderModule -> MediaPlatform.Opencue: "routes ExecutionEnv only"
MediaPlatform.RenderModule -> MediaPlatform.Remotion: "routes POC only"
MediaPlatform.StorageModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.IngestModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.AiModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.RenderModule -> Storage: "persists artifacts"
MediaPlatform.StorageModule -> Storage: "R2/S3-compatible"
`;case`controlPlane`:return`direction: down

Hermes: {
  label: "Hermes Control Plane"

  HermesAgent: {
    label: "Hermes Agent"
  }
  CodingAgents: {
    label: "Coding Agents"
  }
  ReviewInfra: {
    label: "Review Infrastructure"
  }
  Policies: {
    label: "Policies"
  }
  Dashboard: {
    label: "Dashboard"
  }
}
`;case`controlPlaneContext`:return`direction: down

Hermes: {
  label: "Hermes Control Plane"

  HermesAgent: {
    label: "Hermes Agent"
  }
  CodingAgents: {
    label: "Coding Agents"
  }
  ReviewInfra: {
    label: "Review Infrastructure"
  }
  Policies: {
    label: "Policies"
  }
  Dashboard: {
    label: "Dashboard"
  }
}
User: {
  label: "User"
}
Reviewer: {
  label: "Human Reviewer"
}
Cloudflare: {
  label: "Cloudflare"
}
Telegram: {
  label: "Telegram"
}
MediaPlatform: {
  label: "media-platform"
}

User -> MediaPlatform: "uses"
Reviewer -> Hermes: "reviews"
Hermes -> Cloudflare: "publishes"
Hermes -> Telegram: "notifies"
Hermes -> MediaPlatform: "develops"
`;case`vs0VerticalSlice`:return`direction: down

MediaPlatformPlatformApp: {
  label: "platform-app"
}
MediaPlatformRenderModule: {
  label: "render-module"

  TimelineEdit: {
    label: "Timeline Edit Command"
  }
  FakeTestLayer: {
    label: "Fake Test Layer"
  }
  CaptionTemplate: {
    label: "Caption Template"
  }
  PreviewRenderJobService: {
    label: "Preview Render Job Service"
  }
  PreviewArtifactQueryService: {
    label: "Preview Artifact Query Service"
  }
  AssStyleMapper: {
    label: "AssStyleMapper"
  }
  ProviderBinding: {
    label: "Provider Binding"
  }
  FfmpegBaseline: {
    label: "FFmpeg/libass Baseline"
  }
  ProductRuntime: {
    label: "Product Runtime"
  }
  StorageRuntime: {
    label: "Storage Runtime"
  }
}
Storage: {
  label: "Storage"
}

MediaPlatformPlatformApp -> MediaPlatformRenderModule.PreviewRenderJobService: "exposes API"
MediaPlatformPlatformApp -> MediaPlatformRenderModule.PreviewArtifactQueryService: "exposes API"
MediaPlatformRenderModule.TimelineEdit -> MediaPlatformRenderModule.CaptionTemplate: "generates typed intent"
MediaPlatformRenderModule.CaptionTemplate -> MediaPlatformRenderModule.AssStyleMapper: "maps to ASS parameters"
MediaPlatformRenderModule.AssStyleMapper -> MediaPlatformRenderModule.ProviderBinding: "resolves provider"
MediaPlatformRenderModule.ProviderBinding -> MediaPlatformRenderModule.FfmpegBaseline: "routes PRODUCTION"
MediaPlatformRenderModule.PreviewRenderJobService -> MediaPlatformRenderModule.ProviderBinding: "compiles plan"
MediaPlatformRenderModule.FfmpegBaseline -> MediaPlatformRenderModule.ProductRuntime: "produces output"
MediaPlatformRenderModule.PreviewRenderJobService -> MediaPlatformRenderModule.FfmpegBaseline: "executes preview"
MediaPlatformRenderModule.ProductRuntime -> MediaPlatformRenderModule.StorageRuntime: "manages lifecycle"
MediaPlatformRenderModule.PreviewRenderJobService -> MediaPlatformRenderModule.ProductRuntime: "creates product"
MediaPlatformRenderModule.PreviewArtifactQueryService -> MediaPlatformRenderModule.ProductRuntime: "queries product"
MediaPlatformRenderModule.PreviewArtifactQueryService -> MediaPlatformRenderModule.StorageRuntime: "queries storage"
MediaPlatformRenderModule.FakeTestLayer -> MediaPlatformRenderModule.PreviewRenderJobService: "tests"
MediaPlatformRenderModule.FakeTestLayer -> MediaPlatformRenderModule.PreviewArtifactQueryService: "tests"
MediaPlatformRenderModule.StorageRuntime -> Storage: "persists artifacts"
`;case`captionTemplateBoundary`:return`direction: down

MediaPlatformRenderModuleTimelineEdit: {
  label: "Timeline Edit Command"
}
MediaPlatformRenderModuleCaptionTemplate: {
  label: "Caption Template"
}
MediaPlatformRenderModuleAssStyleMapper: {
  label: "AssStyleMapper"
}
MediaPlatformRenderModuleProviderBinding: {
  label: "Provider Binding"
}
MediaPlatformRenderModuleFfmpegBaseline: {
  label: "FFmpeg/libass Baseline"
}

MediaPlatformRenderModuleTimelineEdit -> MediaPlatformRenderModuleCaptionTemplate: "generates typed intent"
MediaPlatformRenderModuleCaptionTemplate -> MediaPlatformRenderModuleAssStyleMapper: "maps to ASS parameters"
MediaPlatformRenderModuleAssStyleMapper -> MediaPlatformRenderModuleProviderBinding: "resolves provider"
MediaPlatformRenderModuleProviderBinding -> MediaPlatformRenderModuleFfmpegBaseline: "routes PRODUCTION"
`;case`providerExecutionBoundary`:return`direction: down

MediaPlatformRenderModuleProviderBinding: {
  label: "Provider Binding"
}
MediaPlatformRenderModuleFfmpegBaseline: {
  label: "FFmpeg/libass Baseline"
}
MediaPlatformOpencue: {
  label: "OpenCue"
}
MediaPlatformRemotion: {
  label: "Remotion"
}

MediaPlatformRenderModuleProviderBinding -> MediaPlatformRenderModuleFfmpegBaseline: "routes PRODUCTION"
MediaPlatformRenderModuleProviderBinding -> MediaPlatformOpencue: "routes ExecutionEnv only"
MediaPlatformRenderModuleProviderBinding -> MediaPlatformRemotion: "routes POC only"
`;case`previewRenderJobApiFlow`:return`direction: down

MediaPlatformRenderModulePreviewRenderJobService: {
  label: "Preview Render Job Service"
}
MediaPlatformRenderModuleProviderBinding: {
  label: "Provider Binding"
}
MediaPlatformRenderModuleFfmpegBaseline: {
  label: "FFmpeg/libass Baseline"
}
MediaPlatformRenderModuleProductRuntime: {
  label: "Product Runtime"
}
MediaPlatformRenderModuleStorageRuntime: {
  label: "Storage Runtime"
}
Storage: {
  label: "Storage"
}

MediaPlatformRenderModulePreviewRenderJobService -> MediaPlatformRenderModuleProviderBinding: "compiles plan"
MediaPlatformRenderModulePreviewRenderJobService -> MediaPlatformRenderModuleFfmpegBaseline: "executes preview"
MediaPlatformRenderModuleProviderBinding -> MediaPlatformRenderModuleFfmpegBaseline: "routes PRODUCTION"
MediaPlatformRenderModulePreviewRenderJobService -> MediaPlatformRenderModuleProductRuntime: "creates product"
MediaPlatformRenderModuleFfmpegBaseline -> MediaPlatformRenderModuleProductRuntime: "produces output"
MediaPlatformRenderModuleProductRuntime -> MediaPlatformRenderModuleStorageRuntime: "manages lifecycle"
MediaPlatformRenderModuleStorageRuntime -> Storage: "persists artifacts"
`;case`headlessApiValidationFlow`:return`direction: down

MediaPlatformRenderModuleFakeTestLayer: {
  label: "Fake Test Layer"
}
MediaPlatformRenderModulePreviewRenderJobService: {
  label: "Preview Render Job Service"
}
MediaPlatformRenderModulePreviewArtifactQueryService: {
  label: "Preview Artifact Query Service"
}

MediaPlatformRenderModuleFakeTestLayer -> MediaPlatformRenderModulePreviewRenderJobService: "tests"
MediaPlatformRenderModuleFakeTestLayer -> MediaPlatformRenderModulePreviewArtifactQueryService: "tests"
`;case`storageDeliveryProfileArchitecture`:return`direction: down

MediaPlatformStorageModule: {
  label: "storage-module"

  StorageDeliveryProfileValidator: {
    label: "StorageDeliveryProfileValidator"
  }
  StorageDeliveryProfileConfig: {
    label: "StorageDeliveryProfileConfig"
  }
  StorageDeliveryProfileRegistry: {
    label: "StorageDeliveryProfileRegistry"
  }
  StorageDeliveryProfile: {
    label: "Storage Delivery Profile"
  }
  StorageDeliveryProfileDTO: {
    label: "StorageDeliveryProfile DTO"
  }
}

MediaPlatformStorageModule.StorageDeliveryProfile -> MediaPlatformStorageModule.StorageDeliveryProfileDTO: "maps to DTOs"
MediaPlatformStorageModule.StorageDeliveryProfileValidator -> MediaPlatformStorageModule.StorageDeliveryProfile: "validates"
MediaPlatformStorageModule.StorageDeliveryProfileConfig -> MediaPlatformStorageModule.StorageDeliveryProfile: "binds config"
MediaPlatformStorageModule.StorageDeliveryProfileRegistry -> MediaPlatformStorageModule.StorageDeliveryProfile: "holds profiles"
`;case`ingestPreflightPolicyFlow`:return`direction: down

MediaPlatformIngestModule: {
  label: "ingest-module"

  UploadHook: {
    label: "UploadReportOnlyPreflightHook"
  }
  TikaProvider: {
    label: "TikaDetectorProvider"
  }
  FfprobeProvider: {
    label: "FFprobeMetadataProvider"
  }
  MetadataMerger: {
    label: "IngestMetadataMerger"
  }
  SafeReportDTO: {
    label: "SafePreflightReportSummary"
  }
  PolicyEvaluator: {
    label: "ReportOnlyPreflightPolicyEvaluator"
  }
  PolicyResult: {
    label: "PreflightPolicyEvaluationResult"
  }
}

MediaPlatformIngestModule.UploadHook -> MediaPlatformIngestModule.TikaProvider: "detects MIME"
MediaPlatformIngestModule.UploadHook -> MediaPlatformIngestModule.FfprobeProvider: "probes media"
MediaPlatformIngestModule.UploadHook -> MediaPlatformIngestModule.MetadataMerger: "merges results"
MediaPlatformIngestModule.MetadataMerger -> MediaPlatformIngestModule.SafeReportDTO: "produces safe report"
MediaPlatformIngestModule.SafeReportDTO -> MediaPlatformIngestModule.PolicyEvaluator: "evaluates policy"
MediaPlatformIngestModule.PolicyEvaluator -> MediaPlatformIngestModule.PolicyResult: "produces result"
`;case`r2ArtifactAccessPath`:return`direction: down

MediaPlatformStorageModuleS3Materializer: {
  label: "S3ObjectMaterializer"
}
MediaPlatformRenderModuleStorageRuntime: {
  label: "Storage Runtime"
}
MediaPlatformStorageModuleAccessDescriptor: {
  label: "AccessDescriptor"
}
Storage: {
  label: "Storage"
}

MediaPlatformStorageModuleS3Materializer -> MediaPlatformStorageModuleAccessDescriptor: "generates signed URL"
MediaPlatformRenderModuleStorageRuntime -> Storage: "persists artifacts"
MediaPlatformStorageModuleS3Materializer -> Storage: "R2/S3-compatible"
`;case`productStorageBoundary`:return`direction: down

MediaPlatformRenderModuleFfmpegBaseline: {
  label: "FFmpeg/libass Baseline"
}
MediaPlatformRenderModuleProductRuntime: {
  label: "Product Runtime"
}
MediaPlatformRenderModuleStorageRuntime: {
  label: "Storage Runtime"
}
Storage: {
  label: "Storage"
}

MediaPlatformRenderModuleFfmpegBaseline -> MediaPlatformRenderModuleProductRuntime: "produces output"
MediaPlatformRenderModuleProductRuntime -> MediaPlatformRenderModuleStorageRuntime: "manages lifecycle"
MediaPlatformRenderModuleStorageRuntime -> Storage: "persists artifacts"
`;case`productArtifactResponseFlow`:return`direction: down

MediaPlatformRenderModulePreviewArtifactQueryService: {
  label: "Preview Artifact Query Service"
}
MediaPlatformRenderModuleProductRuntime: {
  label: "Product Runtime"
}
MediaPlatformRenderModuleStorageRuntime: {
  label: "Storage Runtime"
}
Storage: {
  label: "Storage"
}

MediaPlatformRenderModulePreviewArtifactQueryService -> MediaPlatformRenderModuleProductRuntime: "queries product"
MediaPlatformRenderModulePreviewArtifactQueryService -> MediaPlatformRenderModuleStorageRuntime: "queries storage"
MediaPlatformRenderModuleProductRuntime -> MediaPlatformRenderModuleStorageRuntime: "manages lifecycle"
MediaPlatformRenderModuleStorageRuntime -> Storage: "persists artifacts"
`;default:throw Error(`Unknown viewId: `+e)}};export{e as d2Source};