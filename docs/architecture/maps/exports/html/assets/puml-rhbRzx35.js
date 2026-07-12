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

skinparam rectangle<<MediaPlatform>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
rectangle "==media-platform\\n\\nRender platform" <<MediaPlatform>> as MediaPlatform
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
skinparam rectangle<<MediaPlatformRenderModule>>{
  BackgroundColor #3b82f6
  FontColor #eff6ff
  BorderColor #2563eb
}
skinparam rectangle<<MediaPlatformAiModule>>{
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
  rectangle "==render-module\\n\\nCore render domain" <<MediaPlatformRenderModule>> as MediaPlatformRenderModule
  rectangle "==ai-module\\n\\nAI integration" <<MediaPlatformAiModule>> as MediaPlatformAiModule
  rectangle "==shared-kernel\\n\\nShared domain primitives" <<MediaPlatformSharedKernel>> as MediaPlatformSharedKernel
}

MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformRenderModule : <color:#8D8D8D>uses
MediaPlatformPlatformApp .[#8D8D8D,thickness=2].> MediaPlatformAiModule : <color:#8D8D8D>uses
MediaPlatformRenderModule .[#8D8D8D,thickness=2].> MediaPlatformSharedKernel : <color:#8D8D8D>depends on
MediaPlatformAiModule .[#8D8D8D,thickness=2].> MediaPlatformSharedKernel : <color:#8D8D8D>depends on
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
`;default:throw Error(`Unknown viewId: `+e)}};export{e as pumlSource};