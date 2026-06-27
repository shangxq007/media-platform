---
status: blueprint
created: 2026-06-27
scope: platform-wide
truth_level: target
owner: platform
---

# Workflow Platform Blueprint

> **Linked ADR:** [ADR-021](adr/ADR-021-capability-composition.md)
> **Parent:** [Platform Constitution](platform-constitution-v1.md)

## 1. Platform Evolution

| Version | Model | Kernel Status |
|---------|-------|---------------|
| V1 — Media Build Platform | Low-level reusable media capabilities | ✅ Frozen |
| V2 — Media Capability Platform | Users compose capabilities into pipelines | ✅ Same kernel |
| V3 — Media Workflow Platform | Visual workflow orchestration | ✅ Same kernel |
| V4 — Media Automation Platform | Autonomous media production | ✅ Same kernel |

Every version uses the same Platform Kernel. Evolution adds layers above the kernel — never modifies it.

## 2. Capability Composition

Users compose Capabilities (not internal SPIs):

```
ASR → Summary → Clip Selection → Timeline → Rendering → Publishing
```

Platform resolves implementation automatically: Capability → Capability Resolution → Producer → Backend → Environment.

## 3. Workflow Layer Position

```
Applications
    ↓
Workflow (orchestrates capabilities)
    ↓
Creative Planning (LLM → TimelineEditPlan)
    ↓
Timeline (canonical editing model)
    ↓
Capability Runtime (Producer, Backend, Environment)
    ↓
Kernel (Product, Planner, Pipeline, Storage)
```

Workflow orchestrates Capabilities. Timeline describes media. They are complementary — not competitive.

## 4. Workflow Node Types (Conceptual)

| Node | Purpose |
|------|---------|
| Capability Node | Execute a registered Public Capability |
| Script Transform | User-defined JS/Python/WASM/Container |
| External Capability | User-registered REST Service, LLM, AI Model |
| Condition | Branch based on Product metadata |
| Loop | Iterate over Product list |
| Merge | Combine multiple Product streams |
| Human Approval | Pause for review |
| Trigger | Scheduled or webhook event |
| Delay | Time-based pause |
| Webhook | External system notification |

## 5. Data Model

Nodes exchange: Products, Timelines, JSON. Never exchange runtime objects. Every input/output must be typed.

## 6. Governance

Every node passes through: Access → Policy → Metering → Audit → Execution → Product Registration → Storage. Users never bypass governance.

## 7. Marketplace (Future)

Marketplace distributes: Capabilities, Workflow Templates, Timeline Templates, Prompt Templates, Script Nodes, External Connectors. Never distributes Platform Kernel.
