# Font Security

## 概述

字体安全扫描是字体资产管线的第一道防线。字体文件是用户上传的二进制文件，必须隔离处理，不能被视为普通静态资源。

## 核心原则

1. **字体文件是用户上传的二进制文件，必须隔离处理**
2. **用户上传字体必须先进入 quarantine**
3. **安全扫描通过后才能进入功能校验**
4. **生产环境不允许使用 NoopFontSecurityScanner 放行用户上传字体**
5. **dev/test 环境可以允许 Noop warning pass**

## 状态机

```
UPLOADED
    │
    ▼
QUARANTINED
    │
    ▼
SECURITY_CHECK_PENDING
    │
    ├── SECURITY_REJECTED (扫描失败)
    │
    ▼
VALIDATION_PENDING
    │
    ├── VALIDATION_FAILED (校验失败)
    │
    ▼
READY
    │
    ▼
SUBSETTING_PENDING
    │
    ├── SUBSETTING_FAILED
    │
    ▼
READY_WITH_SUBSETS
    │
    ▼
DISABLED
```

## 安全扫描器

### FontSecurityScanner 接口

```java
public interface FontSecurityScanner {
    String scannerName();
    boolean productionSafe();
    FontSecurityResult scan(Path fontFile);
    FontSecurityResult scan(InputStream fontData, String fileName);
}
```

### BasicFontSecurityScanner

生产环境推荐实现，至少包含：

| 检查项 | 说明 |
|--------|------|
| 文件大小限制 | 最大 50MB |
| 扩展名白名单 | .ttf, .otf, .woff2 |
| MIME sniffing | 检查 MIME 类型 |
| magic bytes 检查 | 验证文件头 |
| sha256 计算 | 文件完整性 |
| 路径安全检查 | 防止路径遍历 |
| 禁止压缩包 | 拒绝 .zip, .tar, .gz |
| 禁止不支持格式 | 拒绝 .svg, .eot, .woff, .ttc |

### NoopFontSecurityScanner

**productionSafe=false**。仅用于 dev/test 环境。

```java
public class NoopFontSecurityScanner implements FontSecurityScanner {
    @Override
    public boolean productionSafe() {
        return false;  // 明确标记不安全
    }

    @Override
    public FontSecurityResult scan(Path fontFile) {
        log.warn("NoopFontSecurityScanner used. This is NOT production-safe.");
        return new FontSecurityResult(
            scannerName(), "WARNING_PASS", Instant.now().toString(),
            false,  // productionSafe = false
            List.of("NoopFontSecurityScanner does not perform real security checks"),
            null, null, false, false, false
        );
    }
}
```

## 安全扫描结果

```java
public record FontSecurityResult(
    String scanner,           // 扫描器名称
    String scanStatus,        // PASSED / REJECTED / WARNING_PASS
    String scannedAt,         // 扫描时间
    boolean productionSafe,   // 是否生产安全
    List<String> warnings,    // 警告信息
    String sha256,            // 文件哈希
    String mimeType,          // MIME 类型
    boolean magicBytesValid,  // magic bytes 是否有效
    boolean pathSafe,         // 路径是否安全
    boolean extensionWhitelisted  // 扩展名是否在白名单
) {}
```

## 初期支持格式

| 格式 | 扩展名 | 说明 |
|------|--------|------|
| TrueType | .ttf | 主力支持 |
| OpenType | .otf | 主力支持 |
| WOFF2 | .woff2 | 主力支持，推荐用于 Web |

## 初期暂缓支持

| 格式 | 扩展名 | 暂缓原因 |
|------|--------|----------|
| TrueType Collection | .ttc | 需要处理子字体索引 |
| WOFF | .woff | 已被 WOFF2 替代 |
| Embedded OpenType | .eot | IE 专用，已过时 |
| SVG Font | .svg | 已废弃 |
| Color Emoji Font | .ttf/.otf | 需要特殊渲染支持 |
| Variable Font | .ttf/.otf | 需要额外轴处理 |

## 生产环境配置

```java
@Configuration
@Profile("!dev")
public class FontSecurityConfig {
    @Bean
    public FontSecurityScanner fontSecurityScanner() {
        return new BasicFontSecurityScanner();
    }
}
```

```java
@Configuration
@Profile("dev")
public class FontSecurityDevConfig {
    @Bean
    public FontSecurityScanner fontSecurityScanner() {
        return new NoopFontSecurityScanner();  // dev 环境允许
    }
}
```

## 相关文档

- [Font Pipeline](./font-pipeline.md)
- [Font Validation](./font-validation.md)
- [Font Subsetting](./font-subsetting.md)
- [Font Manifest Schema](./font-manifest-schema.md)
