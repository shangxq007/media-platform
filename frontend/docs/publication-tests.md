# Publication 模块验证

从 frontend 运行 `npm run test:publication`：覆盖实际 route/source 接线、严格 DTO/binding 校验、UTC 有界列表、内容/Artifact可用性过滤、稳定排序、列表/日历/详情、读取失败重试、旧响应退休、账户binding切换及访问失效。fixture仅显式测试注入，无生产回退。

每个测试使用独立 SelectionProvider/source/QueryClient；工作树独立node_modules/Vite缓存。端到端使用任务独有浏览器context、端口和后端数据库，不能借用其他会话可变资源。DOM测试不等于真实浏览器或认证集成。

修改 Publication model/components/strict adapter 会使该组旧结果失效。修改共享Workspace/session/router/Selection/对话框时，追加 `npm run test:projects`、`npm run test:canvas` 和受影响 Publication/NLE/Review 消费者。typecheck、lint、architecture:guard及独立输出目录build适用。

当前 `.github/workflows/ci.yml` frontend job（Run tests）明确执行 `npx vitest run`；因此本次在最终验收点运行一次完整Vitest，不在每次编辑后重跑。build默认写platform-app，必须使用 `npm run build -- --outDir <绝对任务目录>/build --emptyOutDir`，并先核对目录归属。

本切片新增：初始账户及详情读重试、401/403/404退休、可用性过滤、无效/缺失日期稳定置后、空结果月份导航、时区跨月日期入口、空内容可访问名称。连续性测试挂载实际Router/WorkspaceSessionProvider，覆盖路由返回新读、账户binding校验后恢复、分别保存列表/日历滚动、短列表钳制、同ID移除后不复活选择、项目切换/离开时OIDC退休。

记忆仅复用既有 Workspace binding 的 `publicationBrowsing`，保存source身份、Project、已确认tenant、账户ID/bindingVersion与呈现选择；不保存records/账户payload/token。Workspace hydration的临时null tenant不授予权限，读取仍逐次由后端验证；确认tenant改变即丢弃记忆。相同账户binding验证失败时清筛选/选择/滚动，不复用旧记录。跨账户保留显式源月份，重新读取；不跨账户恢复旧选择。

浏览器验证：本任务新内部网络与PostgreSQL、接受的后端镜像22297d62b234（872e0b08代码，与本任务基线后端相同）、43427/43429端口、真实JWT filter/RBAC/三种Social读取及新Chromium context。数据只含虚构的DRAFT记录，只有social.read/content.read/artifact.read权限，无provider凭据。503故障注入单独标记，不能冒充真实后端失败；生产OIDC、provider发布、Project Open、调度器修复未验证。
