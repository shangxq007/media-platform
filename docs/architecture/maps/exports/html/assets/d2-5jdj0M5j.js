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
  SharedKernel: {
    label: "shared-kernel"
  }
}

MediaPlatform.PlatformApp -> MediaPlatform.RenderModule: "uses"
MediaPlatform.PlatformApp -> MediaPlatform.AiModule: "uses"
MediaPlatform.RenderModule -> MediaPlatform.SharedKernel: "depends on"
MediaPlatform.AiModule -> MediaPlatform.SharedKernel: "depends on"
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
`;default:throw Error(`Unknown viewId: `+e)}};export{e as d2Source};