# 动态扩展系统说明

> **最后更新:** 2026-05-14

---

## 概述

动态扩展系统支持在运行时加载/卸载插件和脚本，无需重新部署。所有扩展在沙箱环境中执行，具有完整的审计跟踪和回滚能力。

---

## SPI 接口

### ProviderExtensionSPI - 提供者扩展

用于第三方渲染、AI、通知、存储提供者：

```java
public interface ProviderExtensionSPI {
    String providerKey();        // 唯一标识
    String providerType();       // RENDER, AI, NOTIFICATION, STORAGE
    String version();            // 语义版本
    String inputSchema();        // 输入 JSON Schema
    String outputSchema();       // 输出 JSON Schema
    String execute(String inputJson) throws ExtensionExecutionException;
    boolean isAvailable();
    void onUnload();
    default void onRollback(String targetVersion) {}
}
```

### PromptExtensionSPI - 提示词扩展

用于自定义提示词模板、渲染脚本、后处理：

```java
public interface PromptExtensionSPI {
    String extensionKey();
    String extensionType();      // TEMPLATE, RENDER_SCRIPT, POST_PROCESSOR, VALIDATOR
    String version();
    String execute(String templateBody, String variables, String contextJson);
    String validate(String inputJson);
    void onUnload();
}
```

### WorkflowStepExtensionSPI - 工作流步骤扩展

用于自定义渲染流水线步骤：

```java
public interface WorkflowStepExtensionSPI {
    String stepKey();
    String stepType();           // PRE_PROCESS, POST_PROCESS, VALIDATION, CUSTOM
    String version();
    String inputSchema();
    String outputSchema();
    String executeStep(String stepInput, String workflowContext);
    void onUnload();
}
```

---

## 沙箱执行

### 安全限制

| 资源 | 默认值 | 最大值 |
|------|--------|--------|
| 执行超时 | 30 秒 | 120 秒 |
| 输出大小 | 4 MB | 4 MB |
| 网络访问 | 禁用 | 可配置 |
| 文件系统 | 仅工作目录 | 可配置 |
| 线程池 | 缓存（无限） | 受 JVM 限制 |

### 危险模式（自动阻止）

- `Runtime.exec`、`ProcessBuilder` - 进程执行
- `java.net.URL`、`HttpURLConnection` - 网络访问（未经授权）
- `java.io.File` 访问工作目录外路径
- `System.exit`、`Runtime.halt` - JVM 关闭
- `java.lang.reflect` - 反射（未经授权）
- `native` 方法 - 本地代码执行

---

## 扩展示例

### 注册第三方 AI 提供者

```java
registry.registerProviderExtension("ai.openai", new ProviderExtensionSPI() {
    public String providerKey() { return "ai.openai"; }
    public String providerType() { return "AI"; }
    public String version() { return "1.0.0"; }
    public String inputSchema() { return "{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\"}}}"; }
    public String outputSchema() { return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}"; }
    public String execute(String inputJson) { /* 调用 OpenAI API */ return "{}"; }
    public boolean isAvailable() { return true; }
    public void onUnload() { /* 清理 */ }
}, "system");
```

### 注册自定义提示词模板

```java
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

### 注册工作流步骤扩展

```java
registry.registerWorkflowStepExtension("workflow.quality_check", new WorkflowStepExtensionSPI() {
    public String stepKey() { return "workflow.quality_check"; }
    public String stepType() { return "POST_PROCESS"; }
    public String version() { return "1.0.0"; }
    public String inputSchema() { return "{}"; }
    public String outputSchema() { return "{}"; }
    public String executeStep(String input, String ctx) {
        return "{\"qualityScore\": 95}";
    }
    public void onUnload() {}
}, "admin");
```

---

## 回滚机制

### 卸载扩展
```java
registry.unloadExtension("ai.openai", "admin");
```

### 回滚到前一版本
```java
registry.rollbackExtension("ai.openai", "1.0.0", "admin");
```

### 回滚提示词模板
```java
promptTemplateService.rollbackToVersion("template-id", "1.0.0");
```

### 重试失败的渲染任务
```java
renderJobService.retry("job-123", "tenant-1");
```

---

## 扩展审批工作流

1. **提交** - 扩展注册，状态为 PENDING_REVIEW
2. **扫描** - 自动安全扫描检查危险模式
3. **审查** - 高风险扩展需要人工审批
4. **激活** - 批准的扩展变为 ACTIVE
5. **监控** - 所有执行记录审计日志并监控

---

## 审计事件

| 事件 | 类别 | 详情 |
|------|------|------|
| `EXTENSION_REGISTERED` | EXTENSION | 密钥、版本、类型、动作 |
| `EXTENSION_UNLOADED` | EXTENSION | 密钥、版本 |
| `EXTENSION_ROLLED_BACK` | EXTENSION | 密钥、从版本、到版本 |
| `EXTENSION_EXECUTION_STARTED` | EXTENSION | 密钥、租户 ID、输入大小 |
| `EXTENSION_EXECUTION_COMPLETED` | EXTENSION | 密钥、租户 ID、耗时、输出大小 |
| `EXTENSION_EXECUTION_TIMEOUT` | EXTENSION | 密钥、租户 ID、超时时间 |
