# asdf-vm（asdf）与本地 JDK 约定

> **文档导航**：[docs/README.md](./README.md)。CI 使用固定 JDK，见仓库根目录 `.github/workflows/ci.yml`。

本仓库 **Gradle 工具链为 Java 25**（见根目录 [`build.gradle.kts`](../build.gradle.kts) 中 `JavaLanguageVersion.of(25)`）。**asdf** 用于在开发者机器上 **安装并切换 JDK**，与 **Gradle Wrapper**、**Foojay Toolchains** 互补，而不是替代。

---

## 1. 推荐工作流

1. 安装 [asdf](https://asdf-vm.com/)（按官方文档）。
2. 安装 Java 插件（常用为 [halcyon/asdf-java](https://github.com/halcyon/asdf-java)）：

   ```bash
   asdf plugin add java https://github.com/halcyon/asdf-java.git
   ```

3. 在仓库根目录查看可用 JDK 25 列表（名称因平台与数据更新会略有差异）：

   ```bash
   asdf list all java | rg -i '25\.|adoptopenjdk-25|temurin'
   ```

4. 安装与本仓库约定一致的版本（见下文 **`.tool-versions`**），例如：

   ```bash
   asdf install java adoptopenjdk-25.0.2+10.0.LTS
   ```

5. 在仓库根目录固定本地版本（asdf 0.16+ 常用 `asdf set`，旧版可用 `asdf local`）：

   ```bash
   asdf set java adoptopenjdk-25.0.2+10.0.LTS
   ```

6. 配置 **`JAVA_HOME`**（插件提供脚本，按 [asdf-java README](https://github.com/halcyon/asdf-java) 将对应 `set-java-home` 加入 `~/.bashrc` / `~/.zshrc`）。

7. 验证：

   ```bash
   java -version
   ./gradlew -version
   ```

---

## 2. 根目录 `.tool-versions`

仓库根目录应存在 **`.tool-versions`**（与 `settings.gradle.kts` 同级）。若你本地尚未检出该文件，可新建并写入 **一行**（无 `#` 注释行，避免部分工具不识别）：

```text
java adoptopenjdk-25.0.2+10.0.LTS
```

说明：

- 该标识来自 **asdf-java** 对 **Eclipse Temurin / Adoptium** 的命名（常显示为 `adoptopenjdk-…`），与 **Linux x64** 常见条目一致。
- 若你使用 **macOS ARM/x64** 或数据更新后安装失败，请用第 1 步中的 `asdf list all java` 选取 **本机可用** 的 JDK 25 条目，将 **同一语义版本**（如更新的补丁）替换进 `.tool-versions`，并在 MR 中说明原因。

---

## 3. 与 Gradle / CI 的关系

| 层级 | 作用 |
|------|------|
| **asdf** | 本机 `java` / `JAVA_HOME` 与团队对齐。 |
| **Gradle Wrapper**（`./gradlew`） | 统一 Gradle 版本，**所有人应使用 wrapper 构建**。 |
| **Java Toolchains**（`build.gradle.kts`） | 构建与测试使用的语言版本 **25**；未安装 JDK 25 时，Foojay 等可自动解析（见 [`layering-and-open-source.md`](./layering-and-open-source.md)）。 |
| **GitHub Actions** | 使用 `actions/setup-java` 固定 **Temurin 25**，**不依赖** asdf。 |

结论：**生产与 CI 不以 asdf 为准**；asdf 是 **提升多人本地体验** 的可选层。

---

## 4. 可选替代

- **[SDKMAN!](https://sdkman.io/)**：偏 JVM 一站式，同样可装 JDK 25。  
- **[mise](https://mise.jdx.dev/)**：与 asdf 类似，部分工作流可迁移。

---

## 5. 常见问题

- **Windows**：原生环境对 asdf 支持较弱，建议使用 **WSL2（Linux）** 或直接使用 **IDE 内置 JDK + Gradle Toolchains**。  
- **与 Docker 开发**：容器内 JDK 以 **镜像** 为准；asdf 仅影响宿主机命令行。  
- **版本升级**：升级 JDK 补丁时，同步更新 `.tool-versions`、本说明中的示例版本号，并跑通 `./gradlew test`。
