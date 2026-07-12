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
  MediaPlatform@{ shape: rectangle, label: "media-platform" }
`;case`containerDiagram`:return`---
title: "Container View"
---
graph TB
  subgraph MediaPlatform["\`media-platform\`"]
    MediaPlatform.PlatformApp@{ shape: rectangle, label: "platform-app" }
    MediaPlatform.RenderModule@{ shape: rectangle, label: "render-module" }
    MediaPlatform.AiModule@{ shape: rectangle, label: "ai-module" }
    MediaPlatform.SharedKernel@{ shape: rectangle, label: "shared-kernel" }
  end
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.RenderModule
  MediaPlatform.PlatformApp -. "\`uses\`" .-> MediaPlatform.AiModule
  MediaPlatform.RenderModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
  MediaPlatform.AiModule -. "\`depends on\`" .-> MediaPlatform.SharedKernel
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
`;default:throw Error(`Unknown viewId: `+e)}};export{e as mmdSource};