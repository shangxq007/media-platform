# Prompt Extensions

> **Module:** `prompt-module`, `extension-module`
> **Last Updated:** 2026-05-18

## Overview

Prompt extensions allow custom prompt rendering logic to be plugged in at runtime via the extension platform.

## Extension SPI

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

## Built-in Extensions

| Extension | Purpose | Trust Level |
|-----------|---------|-------------|
| Default renderer | `{{variable}}` substitution | FULLY_TRUSTED |
| Custom render | Custom logic | SEMI_TRUSTED |

## Example: Custom Prompt Render Extension

```java
@Component
public class CustomPromptRenderExtension implements PromptExtensionSPIV2 {
    @Override
    public String render(String template, Map<String, String> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        // Report unresolved variables
        List<String> unresolved = findUnresolvedVariables(result);
        if (!unresolved.isEmpty()) {
            throw new PromptExtensionException("Unresolved variables: " + unresolved);
        }
        return result;
    }

    @Override
    public ExtensionTrustLevel trustLevel() {
        return ExtensionTrustLevel.SEMI_TRUSTED;
    }
}
```

## Extension Points

| Extension Point | Description |
|----------------|-------------|
| Pre-render | Modify template before rendering |
| Post-render | Modify output after rendering |
| Variable resolver | Custom variable resolution logic |
| Safety check | Custom safety validation |
