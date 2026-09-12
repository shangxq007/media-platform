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

## 交付 B

复用原 Canvas model、SelectionProvider、框选/群组移动、0.5–2 的手动缩放与50步本地 undo/redo。扩展现有 fit 函数为全体/选中集合的边界适配，修复原 fit/reveal 可跌破最小缩放的问题；默认视图按钮从折叠菜单移至常用控制区。读取现有选择对象并复用 Inspector 以只读方式显示名字、已有类型与本地位置；多选数量和清选动作可见。F 适配选择，Shift+F 适配全体，0 默认视图，Ctrl/Meta+Z 和 Ctrl/Meta+Shift+Z 本地撤销/重做；输入、IME、dialog 和模态交互排除。

视图保存复用 A 的会话内 Workspace binding，仅三项几何与项目ID，不保存 payload、凭据或 Timeline/Workflow revision。相同项目返回恢复视图但选择/本地历史继续按原 owner 规则退休；项目切换或会话退休清空。移除 Canvas 自身900px强制最小宽度、工具栏换行；未改其他创作面的宽度约束。

额外依赖：当前 `createCanvasState` 只提供既有 Project 路由引用卡和本地 note；没有已接受、可读取授权项目对象的 Canvas 投影/持久布局合同。FB-GAP-001 仍阻止可信 Project resolution。若未来读取业务对象，需要平台授权的项目/对象投影及不可访问、不存在、绑定过期语义（建议，未声称后端已同意）。当前明确显示“本地布局演示”，不把卡片当作真实 Project 内容，不新增 canonical edit/read 命令，不启用 Project Open。

浏览器执行了真实鼠标框选、群组拖拽、一次撤销、Fit all/selection、默认视图、页面往返、模态快捷键排除、桌面1440×1000及移动390×844检查。HTTP响应是测试独占拦截，仅证明本地呈现行为；真实授权 Project/Canvas 集成 **NOT_RUN**。证据 `browser-B.json`/`browser-B.mjs`、`canvas-desktop.png`、`canvas-mobile.png`。

最终检查时 main 为 `88b7a8db589b2ee270445a8c3ce8d1879d253fb1`；从基线增加 Worker 后端文件，本任务路径重叠为零。不反复同步、不重放后端提交；保持串行集成准备状态。

最终源代码验证：Projects 18/18，Canvas/Selection 22/22，共享 Publication/NLE/Review 41/41，全部0失败/0跳过（两个组包含相同的路由测试，数字不直接相加）。typecheck、architecture guard、独立 production build 通过；lint 0 errors/46 warnings，与基线数量相同。构建保留既有大 chunk 提示，未修改阈值。未运行全前端或后端测试。路径分类只补已有前端 architecture guard 所需条目，不新增治理或审批系统。
