# 前端交付交接（2026-09-12）

基线 `c1594f3488f2dfaa00089eb009208b2a9d1b98ab`，分支 `codex/frontend-projects-canvas-20260912`，任务树 `/home/user/Documents/workspace/projects/.worktrees/codex-frontend-projects-canvas-20260912`。基线包含后端 EP14 本地交付，相对 ded5b899 无前端变化。独立记录 `/home/user/Documents/workspace/audit-runs/FRONTEND_PROJECTS_CANVAS_20260912`，不写后端共享 ledger。

## 交付 A

补上最近项目的实际状态筛选、稳定名称/创建时间排序、只读摘要、刷新与重试、作用域内条件/选择/滚动恢复。改进现有 getHome 接线和 React Query 生命周期，复用原有设计组件、InteractionDialog 和 OIDC 会话退休。导航改为既有 Router Link，未新增路由。旧 Wave2 只用于参考 UI/请求退休方法，没有移植 proposal source、fixture、旧会话逻辑或整个文件树。

审查修正：nullable createdAt 对齐实际返回；StrictMode 清理不能抹掉滚动；刷新移除摘要后的焦点恢复；结构相同的新响应也钳制滚动；移动端摘要补齐独立样式。现有内联 ProjectList 被实际 ProjectBrowser 调用替代。

验证：Projects 18/18；受影响 Publication/NLE/Review 41/41；typecheck、lint（0 errors，46 个已有 warnings）、architecture guard、独立目录 production build 通过。桌面 1280×800 与移动 390×844 浏览器检查已执行，真实生产路由失败呈现通过，HTTP fixture 的搜索/筛选/摘要/焦点/返回/滚动/移除选择通过。真实认证后端联调 **NOT_RUN**：没有任务独有的后端服务、认证 principal/Workspace seed 和数据库；没有借用另一个会话的可变资源。

证据：外部目录 `projects.json`、`shared-A.json`、`browser-A.json`、`browser-A.mjs`、`projects-desktop.png`、`projects-mobile.png`、`guard-A.log`、`lint-A.log`。稳定命令见 `projects-canvas-tests.md`。

## 后端依赖（现状与建议分开）

| 用户操作 | 当前合同及精确缺口 | 所需权限/失败语义（建议，未声称后端同意） | 当前诚实行为 |
| --- | --- | --- | --- |
| 打开项目 | getHome recentProjects 不是 Workspace→Project scoped resolution；FB-GAP-001 | 由平台验证当前 principal、Workspace、Project 的关系；拒绝不得泄露存在性；失效绑定不可沿用 | Open 禁用；摘要只显示已返回字段，不绕路进入项目 |
| 查找任意项目/更多页 | 已接入 dashboard 最多5条，没有该合同的完整性、总数或 cursor。代码中存在 `/api/me/projects`，但本交付未评估/采纳为替代已接受 source，不宣称后端完全无列表 API | 若未来接入扩展查询，需确认该投影的授权作用域、稳定排序/分页/错误语义 | 明确“returned recent projects”，不造总数/全局搜索/分页 |
| 按最近修改排序 | ProjectSummary 只有 createdAt，无 updatedAt | owner 提供具有明确语义的更新时间字段后才能接入 | 仅名称与创建时间排序 |
| 立即发现静默撤权/投影局部故障 | dashboard 无授权 revision/撤权事件；MeController 内部 recent query 异常会记录日志并返回空数组，无 partial/error 标记 | 平台可提供撤权/有效性事件和 partial/error 字段；读取失败不应被表示成确认无项目 | 利用 OIDC 退休、HTTP401/403/404、scope mismatch 清除；不声称检测静默撤权或区分服务端吞掉的局部异常 |

## 集成边界

A 与 B 分开提交，最终提交身份和路径清单保存在外部 HANDOFF.md，避免在提交内自引用 SHA。仅准备串行集成；不合入 main、不 push、不部署、不清理历史工作树。
