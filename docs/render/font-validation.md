# Font Validation

## 概述

字体功能校验是字体资产管线的第二道防线。安全扫描通过后，需要验证字体文件的完整性和可用性。

## 核心原则

1. **安全扫描通过后才能进入功能校验**
2. **功能校验通过后才能进入 READY**
3. **校验结果必须记录在 FontManifest 中**
4. **RenderJob 使用字体前必须检查 FontAsset 状态**

## FontValidator 接口

```java
public interface FontValidator {
    String validatorName();
    FontValidationResult validate(Path fontFile);
    FontValidationResult validate(InputStream fontData, String fileName);
}
```

## 校验项

### 必需表检查

| 表名 | 说明 | 是否必需 |
|------|------|----------|
| cmap | 字符映射表 | 必需 |
| glyf | 字形数据 | 必需（CFF 字体为 CFF） |
| head | 字体头 | 必需 |
| hhea | 水平头 | 必需 |
| maxp | 最大描述 | 必需 |
| OS/2 | OS/2 度量 | 必需 |
| post | PostScript | 必需 |
| name | 名称表 | 必需 |

### 校验结果

```java
public record FontValidationResult(
    String validator,              // 校验器名称
    String validationStatus,       // PASSED / FAILED / WARNING
    List<String> missingRequiredTables,  // 缺失的必需表
    List<String> warnings,         // 警告信息
    String fontFamily,             // 字体族名
    String fontSubfamily,          // 字体子族名
    Integer weight,                // 字重
    String style,                  // 样式
    boolean hasCmap,               // 是否有 cmap
    boolean hasGlyf,               // 是否有 glyf
    boolean hasHead,               // 是否有 head
    boolean hasHhea,               // 是否有 hhea
    boolean hasMaxp,               // 是否有 maxp
    boolean hasOs2,                // 是否有 OS/2
    boolean hasPost,               // 是否有 post
    boolean hasName                // 是否有 name
) {}
```

## NoopFontValidator

**productionSafe=false**。仅用于 dev/test 环境。

```java
public class NoopFontValidator implements FontValidator {
    @Override
    public String validatorName() {
        return "NoopFontValidator";
    }

    @Override
    public FontValidationResult validate(Path fontFile) {
        log.warn("NoopFontValidator used. This is NOT production-safe.");
        return new FontValidationResult(
            validatorName(), "WARNING_PASS",
            List.of(), List.of("NoopFontValidator does not perform real validation"),
            null, null, null, null,
            false, false, false, false, false, false, false, false
        );
    }
}
```

## 未来接入 fontTools

```java
// 未来实现示例
public class FontToolsValidator implements FontValidator {
    @Override
    public FontValidationResult validate(Path fontFile) {
        try (TTFont font = TTFont(fontFile.toString())) {
            List<String> missingTables = new ArrayList<>();
            for (String required : List.of("cmap", "glyf", "head", "hhea", "maxp", "OS/2", "post", "name")) {
                if (!font.hasTable(required)) {
                    missingTables.add(required);
                }
            }
            return new FontValidationResult(
                validatorName(),
                missingTables.isEmpty() ? "PASSED" : "FAILED",
                missingTables,
                List.of(),
                font.getName("1"),
                font.getName("2"),
                font.getOS2Weight(),
                font.isItalic() ? "italic" : "normal",
                font.hasTable("cmap"),
                font.hasTable("glyf"),
                font.hasTable("head"),
                font.hasTable("hhea"),
                font.hasTable("maxp"),
                font.hasTable("OS/2"),
                font.hasTable("post"),
                font.hasTable("name")
            );
        }
    }
}
```

## 相关文档

- [Font Pipeline](./font-pipeline.md)
- [Font Security](./font-security.md)
- [Font Subsetting](./font-subsetting.md)
- [Font Manifest Schema](./font-manifest-schema.md)
