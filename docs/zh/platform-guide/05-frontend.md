# 05 · 前端实现

> [← 分卷索引](README.md) | 上一卷：[04-核心实现](04-implementation.md) | 下一卷：[06-集成矩阵](06-integration.md)

---

## 技术栈

React 19 + Zustand + TanStack Router + TanStack Query + Vite 6 + TypeScript 5.7 + Tailwind。  
视频合成使用 Remotion 4。  
构建产物写入 `platform-app/src/main/resources/static/`。

---

## 主要页面与组件

| 区域 | 路径 / 组件 | 职责 |
|------|-------------|------|
| 编辑器 | `EditorPage.vue`、`TimelineEditor` | 多轨时间线、特效、字幕 |
| 导出 | `ExportPanel.vue` | **传统** / **增量+AI** 模式 |
| AI 编辑 | `AiTimelineEditPanel.vue` | 自然语言改时间线、`humanInTheLoop` |
| 增量 | `IncrementalRenderPanel.vue` | 计划预览、`incremental/submit` |
| 预览 1.0 | `TimelineInternalPreviewPanel.vue` | `preview-internal` |
| AI 建议 | `AiProposalsPanel.vue` | adopt / reject |
| 跨页 | `stores/exportUi.ts` | 我的导出 → 编辑器增量改稿 |
| API | `api/ai-timeline.ts`、`api/render-incremental.ts`、`api/timelineSync.ts` | 租户 REST + 时间线同步 |
| 时间线同步 | `useTimelineSync` + `timelineSyncMeta` store | pull / sync、`localStorage` 离线草稿 |
| 冲突合并 | `TimelineConflictDialog` + `timelineConflictMerge.ts` | 保留本地 / 使用服务端 / 三路 smart merge |
| 用户门户 | `/me/*` | 项目、导出、计费、交付目的地 |

---

## 开发服与代理

```bash
cd platform/frontend && npm install && npm run dev
```

| 项 | 值 |
|----|-----|
| Vite 端口 | 默认 `3000`（占用时 `3001`） |
| API 代理 | `/api` → `http://localhost:8080` |
| 租户头 | `X-Tenant-ID: tenant-1`（axios 拦截器） |

---

## 可配置导航

1. 进入路由前 `navigationGuard` 调用 `fetchNavigation()`。
2. 后端返回每路由 `visible` / `enabled` / `reasonCode`。
3. **`visible: false`** → 重定向 `/forbidden`，`reasonCode` 常为 **`NAV-404-HIDDEN`**。
4. **`enabled: false`** → `/route-disabled` 或 `/upgrade-required`。

**常见误拦：** 访问的路由不在后端导航列表中，合并 fallback 时被标为隐藏。  
解决：从菜单进入已开放路由（如 `/`、`/me`、`/me/exports`），或调整后端导航配置。

详见 [faq.md](../faq.md)。

---

## 增量改稿深链

从「我的导出」跳转：

```
/me/exports → 点击「增量改稿」
  → /?export=incremental&projectId=...&baseJobId=...
  → EditorPage 打开 Export 侧栏 + 预选基准作业
```

---

## 测试

```bash
cd platform/frontend && npm run test    # Vitest
npm run build                           # vue-tsc + vite build
```
