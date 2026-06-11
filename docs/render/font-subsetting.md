# Font Subsetting

## 概述

字体子集化是字体资产管线的第三道防线。功能校验通过后，需要将字体文件子集化为仅包含所需字符的精简版本，以减少文件大小并确保前后端一致性。

## 核心原则

1. **功能校验通过后才能进入子集化**
2. **子集化可以异步执行**
3. **前端 Remotion Player 和后端 Remotion Renderer 必须使用同一个 FontManifest / subset URL**
4. **不允许依赖系统字体**
5. **如果缺字，必须返回 missingGlyphs**
6. **allowFallback=true 时使用 fallback font stack**
7. **allowFallback=false 时阻止导出并提示缺字**

## FontSubsetter 接口

```java
public interface FontSubsetter {
    String subsetterName();
    FontSubsetResult subset(Path fontFile, Set<Integer> codePoints, SubsetOptions options);

    record SubsetOptions(
        String format,           // 输出格式：woff2
        boolean hinting,         // 是否保留 hinting
        boolean kerning,         // 是否保留 kerning
        boolean ligatures,       // 是否保留 ligatures
        Set<String> layoutFeatures,  // OpenType 特性
        boolean desubroutinize,  // 是否去除子程序化
        boolean nameIds,         // 是否保留 name IDs
        boolean glyphNames,      // 是否保留字形名称
        boolean notdefOutline    // 是否保留 .notdef 轮廓
    ) {
        public static SubsetOptions defaultWoff2() {
            return new SubsetOptions("woff2", true, true, true,
                Set.of("kern", "liga", "calt"), true, true, true, true);
        }
    }
}
```

## 子集化结果

```java
public record FontSubsetResult(
    String strategy,              // 子集化策略
    boolean cacheable,            // 是否可缓存
    String cacheKey,              // 缓存键
    String subsetUri,             // 子集文件 URI
    String subsetFormat,          // 子集格式
    long subsetSize,              // 子集文件大小
    int originalGlyphCount,       // 原始字形数
    int subsetGlyphCount,         // 子集字形数
    List<MissingGlyph> missingGlyphs,  // 缺失字形
    Map<String, String> fallbackChains  // fallback 链
) {}
```

## FontSubsetCache 接口

```java
public interface FontSubsetCache {
    String computeCacheKey(String fontHash, String charsHash, String optionsHash);
    boolean contains(String cacheKey);
    String getSubsetUri(String cacheKey);
    void put(String cacheKey, String subsetUri);
    void invalidate(String cacheKey);
}
```

## 缓存键计算

```
subsetCacheKey = SHA256(fontHash + charsHash + subsetOptionsHash)
```

其中：
- `fontHash` = 原始字体文件的 SHA256
- `charsHash` = 所需字符集合的 SHA256
- `subsetOptionsHash` = 子集化选项的 SHA256

## NoopFontSubsetter

**productionSafe=false**。仅用于 dev/test 环境。

```java
public class NoopFontSubsetter implements FontSubsetter {
    @Override
    public String subsetterName() {
        return "NoopFontSubsetter";
    }

    @Override
    public FontSubsetResult subset(Path fontFile, Set<Integer> codePoints, SubsetOptions options) {
        log.warn("NoopFontSubsetter used. This is NOT production-safe.");
        return new FontSubsetResult(
            "noop", false, null, null, "ttf", 0, 0, 0,
            Set.of(), Map.of()
        );
    }
}
```

## 未来接入 pyftsubset

```bash
pyftsubset font.ttf \
  --text-file=chars.txt \
  --output-file=subset.woff2 \
  --flavor=woff2 \
  --layout-features='kern,liga,calt' \
  --desubroutinize \
  --name-IDs='*' \
  --glyph-names \
  --notdef-outline
```

## 缺字检测

```java
public interface MissingGlyphDetector {
    String detectorName();
    List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints);
}

public record MissingGlyph(
    int codePoint,      // Unicode 码点
    String character,   // 字符
    String script,      // 脚本
    boolean resolvedByFallback  // 是否被 fallback 解析
) {}
```

## Fallback 解析

```java
public interface FontStackResolver {
    String resolverName();
    FontStack resolve(String fontFamily, Map<String, FontAsset> availableFonts);
    FallbackChain resolveChain(String fontId, Set<Integer> requiredCodePoints, Map<String, FontAsset> availableFonts);

    record FontStack(String primaryFont, List<String> fallbackFonts, String systemFallback) {}
    record FallbackChain(String primaryFontId, List<String> fallbackFontIds, boolean systemFallbackUsed) {}
}
```

## 相关文档

- [Font Pipeline](./font-pipeline.md)
- [Font Security](./font-security.md)
- [Font Validation](./font-validation.md)
- [Font Manifest Schema](./font-manifest-schema.md)
