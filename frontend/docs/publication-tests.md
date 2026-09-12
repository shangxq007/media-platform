# Publication 模块验证

从 frontend 运行 `npm run test:publication`：覆盖实际 route/source 接线、严格 DTO/binding 校验、UTC 有界列表、内容/Artifact可用性过滤、稳定排序、列表/日历/详情、读取失败重试、旧响应退休、账户binding切换及访问失效。fixture仅显式测试注入，无生产回退。

每个测试使用独立 SelectionProvider/source/QueryClient；工作树独立node_modules/Vite缓存。端到端使用任务独有浏览器context、端口和后端数据库，不能借用其他会话可变资源。DOM测试不等于真实浏览器或认证集成。

修改 Publication model/components/strict adapter 会使该组旧结果失效。修改共享Workspace/session/router/Selection/对话框时，追加 `npm run test:projects`、`npm run test:canvas` 和受影响 Publication/NLE/Review 消费者。typecheck、lint、architecture:guard及独立输出目录build适用。

当前 `.github/workflows/ci.yml` frontend job（Run tests）明确执行 `npx vitest run`；因此本次在最终验收点运行一次完整Vitest，不在每次编辑后重跑。build默认写platform-app，必须使用 `npm run build -- --outDir <绝对任务目录>/build --emptyOutDir`，并先核对目录归属。
