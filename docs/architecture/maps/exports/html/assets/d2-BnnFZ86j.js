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
  RenderModule: {
    label: "render-module"
  }
  AiModule: {
    label: "ai-module"
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
MediaPlatform.PlatformApp -> MediaPlatform.AiModule: "uses"
MediaPlatform.RenderModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.RenderModule -> MediaPlatform.Opencue: "routes ExecutionEnv only"
MediaPlatform.RenderModule -> MediaPlatform.Remotion: "routes POC only"
MediaPlatform.AiModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.RenderModule -> Storage: "persists artifacts"
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

MediaPlatformRenderModule: {
  label: "render-module"

  TimelineEdit: {
    label: "Timeline Edit Command"
  }
  CaptionTemplate: {
    label: "Caption Template"
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

MediaPlatformRenderModule.TimelineEdit -> MediaPlatformRenderModule.CaptionTemplate: "generates typed intent"
MediaPlatformRenderModule.CaptionTemplate -> MediaPlatformRenderModule.AssStyleMapper: "maps to ASS parameters"
MediaPlatformRenderModule.AssStyleMapper -> MediaPlatformRenderModule.ProviderBinding: "resolves provider"
MediaPlatformRenderModule.ProviderBinding -> MediaPlatformRenderModule.FfmpegBaseline: "routes PRODUCTION"
MediaPlatformRenderModule.FfmpegBaseline -> MediaPlatformRenderModule.ProductRuntime: "produces output"
MediaPlatformRenderModule.ProductRuntime -> MediaPlatformRenderModule.StorageRuntime: "manages lifecycle"
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
`;default:throw Error(`Unknown viewId: `+e)}};export{e as d2Source};