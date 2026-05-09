# SDKMAN! 与本地 JDK 约定

> **文档导航**：[docs/README.md](./README.md)。CI 使用固定 JDK，见仓库根目录 `.github/workflows/ci.yml`。

本仓库 **Gradle 工具链为 Java 25**（见根目录 [`build.gradle.kts`](../build.gradle.kts) 中 `JavaLanguageVersion.of(25)`）。**SDKMAN!** 用于在开发者机器上 **安装并切换 JDK**，与 **Gradle Wrapper**、**Foojay Toolchains** 互补，而不是替代。

---

## 1. 推荐工作流

1. 安装 [SDKMAN!](https://sdkman.io/)（按官方文档，通常为 `curl -s "https://get.sdkman.io" | bash`）。
2. 在仓库根目录创建或确认 **`.sdkmanrc`** 存在（与 `settings.gradle.kts` 同级），内容示例：

   ```properties
   java=25.0.2-tem
   ```

3. 在仓库根目录运行：

   ```bash
   sdk env
   ```

   SDKMAN! 会自动安装 `.sdkmanrc` 中声明的 JDK（若尚未安装），并切换当前 shell 的 `JAVA_HOME` 与 `PATH`。

4. 验证：

   ```bash
   java -version
   ./gradlew -version
   ```

5. （可选）启用 **SDKMAN! 自动环境切换**：在 `~/.sdkman/etc/config` 中设置 `sdkman_auto_env=true`，进入含 `.sdkmanrc` 的目录时自动执行 `sdk env`。

---

## 2. 根目录 `.sdkmanrc`

仓库根目录应存在 **`.sdkmanrc`**（与 `settings.gradle.kts` 同级）。若你本地尚未检出该文件，可新建并写入：

```properties
java=25.0.2-tem
```

说明：

- `25.0.2-tem` 对应 **Eclipse Temurin 25.0.2**，是 SDKMAN! 对 Temurin 的命名格式。
- 若你使用其他发行版（如 `25.0.2-libica`、`25.0.2-graalce` 等），请选取 **本机可用** 的 JDK 25 条目，将 **同一语义版本** 替换进 `.sdkmanrc`，并在 MR 中说明原因。

---

## 3. 与 Gradle / CI 的关系

| 层级 | 作用 |
|------|------|
| **SDKMAN!** | 本机 `java` / `JAVA_HOME` 与团队对齐。 |
| **Gradle Wrapper**（`./gradlew`） | 统一 Gradle 版本，**所有人应使用 wrapper 构建**。 |
| **Java Toolchains**（`build.gradle.kts`） | 构建与测试使用的语言版本 **25**；未安装 JDK 25 时，Foojay 等可自动解析（见 [`layering-and-open-source.md`](./layering-and-open-source.md)）。 |
| **GitHub Actions** | 使用 `actions/setup-java` 固定 **Temurin 25**，**不依赖** SDKMAN!。 |

结论：**生产与 CI 不以 SDKMAN! 为准**；SDKMAN! 是 **提升多人本地体验** 的可选层。

---

## 4. 可选替代

- **[asdf](https://asdf-vm.com/)**：多语言版本管理器，同样可装 JDK 25（见 [`asdf-vm.md`](./asdf-vm.md)）。
- **[mise](https://mise.jdx.dev/)**：与 asdf 类似，部分工作流可迁移。

---

## 5. 常见问题

- **Windows**：原生环境对 SDKMAN! 支持较弱，建议使用 **WSL2（Linux）** 或直接使用 **IDE 内置 JDK + Gradle Toolchains**。
- **与 Docker 开发**：容器内 JDK 以 **镜像** 为准；SDKMAN! 仅影响宿主机命令行。
- **版本升级**：升级 JDK 补丁时，同步更新 `.sdkmanrc`、本说明中的示例版本号，并跑通 `./gradlew test`。
