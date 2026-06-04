# 提示词扩展

> **模块：** `prompt-module`、`extension-module`
> **最后更新：** 2026-05-18

## 概述

提示词扩展允许通过扩展平台在运行时插入自定义提示词渲染逻辑。

## 扩展 SPI

```java
public interface PromptExtensionSPI {
    String render(String template, Map<String, String> variables);
}

public interface PromptExtensionSPIV2 extends PromptExtensionSPI {
    ExtensionTrustLevel trustLevel();
    ExtensionResult execute(ExtensionContext context, String inputJson);
    ExtensionResourceLimits resourceLimits();
}
```

## 内置扩展

| 扩展 | 用途 | 信任级别 |
|------|------|----------|
| 默认渲染器 | `{{variable}}` 替换 | FULLY_TRUSTED |
| 自定义渲染 | 自定义逻辑 | SEMI_TRUSTED |

## 示例：自定义提示词渲染扩展

```java
@Component
public class CustomPromptRenderExtension implements PromptExtensionSPIV2 {
    @Override
    public String render(String template, Map<String, String> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        // 报告未解析的变量
        List<String> unresolved = findUnresolvedVariables(result);
        if (!unresolved.isEmpty()) {
            throw new PromptExtensionException("未解析的变量: " + unresolved);
        }
        return result;
    }

    @Override
    public ExtensionTrustLevel trustLevel() {
        return ExtensionTrustLevel.SEMI_TRUSTED;
    }
}
```

## 扩展点

| 扩展点 | 描述 |
|--------|------|
| 渲染前 | 渲染前修改模板 |
| 渲染后 | 渲染后修改输出 |
| 变量解析器 | 自定义变量解析逻辑 |
| 安全检查 | 自定义安全校验 |
