# Prompt Extension Examples

> **Purpose:** Examples of prompt template extensions and custom rendering scripts.  
> **Last Updated:** 2026-05-14

---

## Custom Prompt Template Extension

### Example 1: Greeting Template

```java
public class GreetingPromptExtension implements PromptExtensionSPI {
    @Override
    public String extensionKey() { return "prompt.greeting"; }
    
    @Override
    public String extensionType() { return "TEMPLATE"; }
    
    @Override
    public String version() { return "1.0.0"; }
    
    @Override
    public String execute(String templateBody, String variablesJson, String contextJson) {
        // Parse variables
        Map<String, Object> vars = parseJson(variablesJson);
        String name = (String) vars.getOrDefault("name", "World");
        String language = (String) vars.getOrDefault("language", "en");
        
        // Render greeting
        String greeting = switch (language) {
            case "zh" -> "你好, " + name + "!";
            case "es" -> "¡Hola, " + name + "!";
            default -> "Hello, " + name + "!";
        }
        
        return "{\"renderedPrompt\":\"" + greeting + "\"}";
    }
    
    @Override
    public String validate(String inputJson) {
        // Validate required variables
        if (!inputJson.contains("\"name\"")) {
            return "{\"valid\":false,\"errors\":[\"Missing required variable: name\"]}";
        }
        return "{\"valid\":true}";
    }
    
    @Override
    public void onUnload() { /* cleanup */ }
}
```

### Example 2: Multi-Language Template

```java
public class MultiLangPromptExtension implements PromptExtensionSPI {
    @Override
    public String extensionKey() { return "prompt.multilang"; }
    
    @Override
    public String extensionType() { return "TEMPLATE"; }
    
    @Override
    public String version() { return "1.0.0"; }
    
    @Override
    public String execute(String templateBody, String variablesJson, String contextJson) {
        Map<String, Object> vars = parseJson(variablesJson);
        String text = (String) vars.get("text");
        List<String> languages = (List<String>) vars.get("languages");
        
        StringBuilder result = new StringBuilder();
        for (String lang : languages) {
            result.append(translate(text, lang)).append("\n");
        }
        
        return "{\"renderedPrompt\":\"" + result.toString().trim() + "\"}";
    }
    
    private String translate(String text, String targetLang) {
        // Placeholder: actual translation would call a translation service
        return "[" + targetLang + "] " + text;
    }
    
    @Override
    public String validate(String inputJson) {
        if (!inputJson.contains("\"text\"")) {
            return "{\"valid\":false,\"errors\":[\"Missing required variable: text\"]}";
        }
        return "{\"valid\":true}";
    }
    
    @Override
    public void onUnload() {}
}
```

## Custom Rendering Script Extension

### Example: Watermark Overlay Script

```java
public class WatermarkRenderExtension implements PromptExtensionSPI {
    @Override
    public String extensionKey() { return "script.watermark"; }
    
    @Override
    public String extensionType() { return "RENDER_SCRIPT"; }
    
    @Override
    public String version() { return "1.0.0"; }
    
    @Override
    public String execute(String templateBody, String variablesJson, String contextJson) {
        Map<String, Object> vars = parseJson(variablesJson);
        String inputFile = (String) vars.get("inputFile");
        String watermarkText = (String) vars.get("watermarkText");
        String position = (String) vars.getOrDefault("position", "bottom-right");
        
        // Generate FFmpeg filter for watermark
        String filter = String.format(
            "drawtext=text='%s':x=w-tw-10:y=h-th-10:fontsize=24:fontcolor=white@0.5",
            watermarkText
        );
        
        return "{\"filter\":\"" + filter + "\",\"inputFile\":\"" + inputFile + "\"}";
    }
    
    @Override
    public String validate(String inputJson) {
        if (!inputJson.contains("\"inputFile\"")) {
            return "{\"valid\":false,\"errors\":[\"Missing required variable: inputFile\"]}";
        }
        if (!inputJson.contains("\"watermarkText\"")) {
            return "{\"valid\":false,\"errors\":[\"Missing required variable: watermarkText\"]}";
        }
        return "{\"valid\":true}";
    }
    
    @Override
    public void onUnload() {}
}
```

## Registration via REST API

```bash
# Register a new prompt extension
curl -X POST http://localhost:8080/api/v1/prompts/templates \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -H "X-API-Key: your-api-key" \
  -d '{"name": "Custom Greeting", "category": "custom", "schemaVersion": "1.0.0"}'

# Execute the extension
curl -X POST http://localhost:8080/api/v1/prompts/templates/{id}/render \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"variables": {"name": "World", "language": "en"}, "dryRun": true}'
```
