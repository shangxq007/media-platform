# 开发注意事项

> **最后更新:** 2026-05-14

---

## 代码规范

### Java 代码风格
- **Record 优先:** 不可变数据结构使用 Java 25 record
- **模块边界:** 使用 Spring Modulith `@ApplicationModule` 标注模块
- **端口接口:** 跨模块通信通过 `port/` 包中的接口
- **错误处理:** 所有异常使用 `PlatformException`，包含结构化 `errorCode` + `message` + `details`
- **国际化:** 错误码支持中/英双语（`error-codes.json`）

### 命名约定
- 模块包名: `com.example.platform.<module-name>`
- 端口接口: `XxxPort`（如 `RenderExecutionPort`）
- SPI 接口: `XxxSPI`（如 `ProviderExtensionSPI`）
- 事件类: `XxxEvent`（如 `RenderJobCreatedEvent`）
- 服务类: `XxxService`（如 `RenderJobService`）
- 仓库类: `XxxRepository`

### 模块结构
```
<module>/
├── domain/          # 领域模型（record、enum）
├── app/             # 应用服务（@Service）
├── api/             # REST 控制器（@Controller）
├── port/            # 端口接口（被其他模块实现）
├── infrastructure/  # 基础设施实现
├── config/          # 配置类
└── package-info.java # @ApplicationModule 标注
```

---

## 模块边界规则

### 依赖方向
- `shared-kernel` 被所有模块依赖，不依赖任何模块
- `platform-app` 依赖所有模块（组合根）
- 业务模块通过端口接口通信，不直接依赖其他业务模块
- 工作流模块允许依赖 `render :: API` 和 `policy :: feature-flags`

### 跨模块通信
1. **同步调用:** 通过端口接口（如 `RenderExecutionPort`）
2. **异步事件:** 通过 Spring `ApplicationEventPublisher`
3. **共享数据:** 通过 `shared-kernel` 中的事件和端口

---

## 动态扩展模块使用规范

### 扩展类型

| 类型 | SPI 接口 | 用途 |
|------|----------|------|
| 提供者扩展 | `ProviderExtensionSPI` | 第三方渲染/AI/通知/存储提供者 |
| 提示词扩展 | `PromptExtensionSPI` | 提示词模板、渲染脚本、后处理 |
| 工作流步骤扩展 | `WorkflowStepExtensionSPI` | 自定义工作流步骤 |

### 安全限制

| 资源 | 默认限制 | 最大值 |
|------|----------|--------|
| 执行超时 | 30 秒 | 120 秒 |
| 输出大小 | 4 MB | 4 MB |
| 网络访问 | 禁用 | 可配置 |
| 文件系统 | 仅工作目录 | 可配置 |

### 危险模式（自动阻止）

以下代码模式将触发 BLOCK：
- `Runtime.exec`、`ProcessBuilder` - 进程执行
- `java.net.URL`、`HttpURLConnection` - 网络访问（未经授权）
- `java.io.File` 访问工作目录外路径
- `System.exit`、`Runtime.halt` - JVM 关闭
- `java.lang.reflect` - 反射（未经授权）
- `native` 方法 - 本地代码执行

### 扩展示例

```java
// 注册自定义提示词扩展
registry.registerPromptExtension("prompt.custom_greeting", new PromptExtensionSPI() {
    public String extensionKey() { return "prompt.custom_greeting"; }
    public String extensionType() { return "TEMPLATE"; }
    public String version() { return "1.0.0"; }
    public String execute(String body, String vars, String ctx) {
        return body.replace("{{greeting}}", "你好");
    }
    public String validate(String input) { return "{\"valid\":true}"; }
    public void onUnload() {}
}, "admin");
```

---

## 提示词平台使用规范

### 模板管理
1. 创建模板 → 状态为 DRAFT
2. 添加版本 → 自动递增语义版本（1.0.0 → 1.0.1）
3. 激活模板 → 状态变为 ACTIVE
4. 废弃模板 → 状态变为 DEPRECATED
5. 归档模板 → 状态变为 ARCHIVED

### 变量类型

| 类型 | 说明 | 敏感 |
|------|------|------|
| STRING | 字符串 | 可选 |
| NUMBER | 数字 | 可选 |
| BOOLEAN | 布尔值 | 否 |
| ENUM | 枚举值 | 否 |
| ARRAY | 数组 | 否 |
| OBJECT | 对象 | 否 |
| SECRET_REFERENCE | 密钥引用 | 是 |
| FILE_REFERENCE | 文件引用 | 否 |

### 安全扫描
- **密钥检测:** API 密钥、密码、AWS 密钥、GitHub 令牌
- **破坏性命令检测:** rm -rf、terraform destroy、chmod 777 等
- **生产访问检测:** production + deploy/apply/destroy 组合

### 风险等级

| 等级 | 动作 | 说明 |
|------|------|------|
| LOW | ALLOW | 安全内容 |
| MEDIUM | WARN | 轻微问题，可继续 |
| HIGH | REQUIRE_REVIEW | 需要人工复核 |
| CRITICAL | BLOCK | 自动阻止 |

---

## 问题数据处理规范

### 检测流程
1. **检测** - 自动扫描渲染任务、提示词执行、提供者/工作者输出
2. **标记** - 异常数据标记为 DETECTED
3. **隔离** - 严重问题移入隔离表
4. **自动修复** - 可修复项自动处理
5. **人工复核** - 复杂问题标记为 HUMAN_REVIEW_REQUIRED

### 自动修复范围

| 问题类型 | 自动修复 | 说明 |
|----------|----------|------|
| 缺失字段 | ✅ 是 | 填充默认值 |
| 格式错误 | ✅ 是 | 转换为标准格式 |
| 重复条目 | ✅ 是 | 标记为重复 |
| 卡住任务 | ✅ 是 | 重置为 QUEUED 重试 |
| 工作者心跳超时 | ✅ 是 | 标记为离线 |
| SLA 违规 | ❌ 否 | 需要业务影响评估 |
| 输出不匹配 | ❌ 否 | 需要重新渲染 |
| 成本异常 | ❌ 否 | 需要定价模型审查 |
| 密钥泄露 | ❌ 否 | 需要安全事件响应 |

### 审计要求
所有操作必须包含：
- `actorType` - 操作者类型（user、service、system）
- `actorId` - 操作者标识
- `action` - 操作类型（REGISTER、UNLOAD、ROLLBACK、EXECUTE 等）
- `resourceType` - 资源类型
- `resourceId` - 资源标识
- `category` - 类别（EXTENSION、PROMPT、RENDER 等）

---

## 测试规范

### 测试分层
- **单元测试:** 纯 JUnit，无 Spring 上下文
- **集成测试:** `@SpringBootTest`，H2 内存数据库
- **模块测试:** Spring Modulith `@ApplicationModuleTest`

### 测试覆盖要求
- 每个模块至少 1 个测试类
- 服务类必须有单元测试
- 控制器必须有集成测试
- 仓库类必须有数据库测试

### 测试命名
- 测试类: `XxxTest`
- 测试方法: `should_xxx` 或 `testXxx`
- 异常测试: `should_throw_xxx_when_xxx`

---

## 提交规范

### 代码提交
- 每个修复/功能独立提交
- 提交信息格式: `模块: 简述`
- 示例: `render: 修复 JavaCV 字幕烧录内存泄漏`

### 质量门禁
提交前必须通过：
```bash
./gradlew clean test          # 所有测试通过
./gradlew :platform-app:bootJar  # 构建成功
```
