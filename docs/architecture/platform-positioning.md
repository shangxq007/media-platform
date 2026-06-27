---
status: blueprint
created: 2026-06-27
scope: platform-wide
truth_level: target
owner: platform
---

# Platform Positioning & Vision

## 1. What the Platform IS NOT

| NOT | Why |
|-----|-----|
| A video editor | The platform has no UI. Timeline is a Product type, not an application. |
| A render farm | The platform orchestrates rendering but does not own GPU infrastructure. |
| An AI application | AI providers are Producers — capabilities, not applications. |
| An FFmpeg wrapper | FFmpeg is one backend. BMF, Remotion, MLT are others. |

## 2. What the Platform IS

**A Media Build Platform.**

The platform provides low-level reusable media capabilities. Upper-layer applications orchestrate these capabilities. Timeline is the canonical communication model. Products are the canonical build artifacts. Capabilities produce Products.

## 3. Platform Vision

| Direction | Description |
|-----------|-------------|
| Long Video Understanding | ASR → OCR → Vision → Embedding → Search |
| Short Video Generation | Timeline Template → Render → Preview → Publish |
| AI Editing | LLM → TimelineEditPlan → TimelineMutation → Review |
| Media Asset Management | Upload → Enrich → Search → Marketplace |
| Batch Processing | Job → Task → Environment → Backend → Product |
| Cloud Rendering | ExecutionBackend → OpenCue/K8s → Workers |
| External Capability Platform | Public API → Capability → Producer → Product |
| Marketplace | Template/Effect/Plugin → Listing → Install → Use |
| Enterprise Automation | Workflow → Policy → Governance → Billing |

All directions share the same kernel. No direction requires kernel redesign.

## 4. Platform Layers

```
Applications (UI, CLI, API consumers)
    ↓
Creative Planning (LLM → TimelineEditPlan)
    ↓
Timeline Authoring (Timeline Mutation, Review, Merge)
    ↓
Capability Runtime (Producer, Backend, Environment)
    ↓
Platform Kernel (Product, Planner, Pipeline, Storage)
    ↓
Infrastructure (Object Storage, Workers, GPU)
```

## 5. Public vs Internal

| Public (consumed externally) | Internal (implementation detail) |
|------------------------------|--------------------------------|
| Capabilities | Producers |
| Jobs | Backend Compilers |
| Products | Execution Environment |
| Timelines | Storage Providers |
| Metering reports | Descriptors |

External users consume Capabilities, Jobs, Products, and Timelines. Never internal implementations.

## 6. Timeline-Centered Platform

Timeline is the canonical editing model. All editing modes converge to Timeline:
- Long Video → Timeline
- Short Clips → Timeline
- Templates → Timeline
- LLM → TimelineEditPlan → Timeline Mutation
- Manual Editor → Timeline

## 7. Products as Universal Communication

Every capability consumes Products and produces Products:
- Transcript is a Product
- Preview is a Product
- Render Output is a Product
- Timeline is a Product
- Intermediate Products are first-class
