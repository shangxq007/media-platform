# Projects 浏览与 Canvas/Selection 定向验证

所有命令从 `frontend` 执行；每个工作树独立 node_modules/Vite 缓存/QueryClient。不得使用其他会话的数据库、认证 profile 或服务端口。产物用任务专用 `--outDir`，默认 build 会写 platform-app，禁止直接使用默认输出。

## Projects 浏览（交付 A）

- `npm run test:projects`：真实路由及共享 transport 的 getHome 适配（HTTP mock，非真实后端）；最近数据标识、nullable createdAt、组合筛选、稳定排序、初始失败与重试、刷新保留/移除选择、并发结果退休、Workspace 切换、OIDC 退休、离开/返回的条件/选择/滚动恢复、摘要键盘关闭与焦点、Open 禁用。
- `npm test -- src/product/publication src/product/timeline/NleWorkspace.test.tsx src/product/review/ReviewWorkspace.test.tsx`：修改 WorkspaceSession/useWorkspaceHome/ProjectContext、RootLayout 或共享导航/对话框语义时追加，保留 V11 精确 binding 行为。
- `npm run typecheck`、`npm run lint`、`npm run architecture:guard`、`npm run build -- --outDir <任务绝对产物目录> --emptyOutDir`。
- 数据均在测试进程内隔离；正常路由没有 fixture 开关或数据源回退。改动列表模型、读取适配、作用域/缓存寿命、路由导航、摘要组件或布局会使相关旧结果失效。纯后端无重叠提交不触发全前端重跑。
- 浏览器：专用 Vite 43127，后端代理 43129；未提供任务专用后端/授权 principal/Workspace 数据时，真实认证集成标记 NOT_RUN。HTTP 拦截 fixture 可证明渲染、导航、焦点、滚动与响应式行为，不证明生产授权。

## 已知数据与恢复边界

`/w/:workspaceId/projects` → `useWorkspaceHome` → `platformClient.workspace.getHome` → 既有认证 Axios → `GET /api/me/dashboard`。MeController 从当前 TenantContext 获取 Workspace，按 createdAt 降序最多 5 条，投影 id/name/description/tenantId/status/createdAt（可空）；没有 updatedAt、总数、分页 cursor 或项目级解析。

查询沿用 React Query，增加内存 binding id；持久根 provider 观察现有 OIDC retirement。Workspace 切换销毁旧浏览条件，离开到非 Workspace 页保持当前条件；返回重新读取，只有仍在返回数据中的项目继续被选中。无项目 payload 或凭据的新持久化。登出、失效会话、pagehide、Workspace scope mismatch、401/403/404 清数据和选择；退休会话需重新打开/认证，不自动复用。后端没有 silent revocation 事件，因此不声称即时检测静默撤权。无 updatedAt，不显示“最近更新排序”；createdAt 为空/无效置后，所有并列值由名称与 ID 稳定排序。

## Canvas / Selection（交付 B）

- `npm run test:canvas`：Canvas model/组件、SelectionProvider 生命周期和真实路由；空对象、单选/多选适配、不同呈现矩形、0.5–2 缩放边界、默认视图、容器变化、同项目返回、项目切换/会话退休、只读摘要、清选焦点、输入/IME/对话框键盘排除、框选、群组移动与一次本地撤销。
- 组件手势测试模拟 pointer capture；真实 Chromium 验证记录另存，不能用 DOM 仿真声称验证了浏览器原生 capture。fixture 只在测试中注入 getHome 或替换空布局模型，不改变正常应用的来源。
- Canvas 模型、键盘/手势处理、SelectionProvider、Inspector 或视口恢复改动会使该组结果失效。涉及 WorkspaceSession、RootLayout、共享 Inspector/ActionBar 时追加 `npm run test:projects` 和上述 Publication/NLE/Review 消费者组。
- `npm run typecheck`、`npm run lint`、`npm run architecture:guard`、独立目录 build 同样适用。只有 guard 实现本身变化才追加 `npm run architecture:guard:test`，不因增加普通测试重跑全仓。
- 相机仅保存当前 binding/project 的 zoom、viewportX、viewportY；对象选择和50步本地编辑历史仍随 Canvas owner 卸载退休。切换项目，即使在非 Canvas 页面，也清除旧视图。手动视图 resize 保持中心；显式 fit 模式 resize 重新计算。最小缩放不能容纳全部对象时保持下限并提示可平移，不宣称强行全部可见。
- 生产读取要求根 WorkspaceSessionProvider；原 NLE/Review 孤立测试入口已迁移到明确的独立 provider，不保留无作用域查询缓存的备用分支。
