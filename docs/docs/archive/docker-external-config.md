# Docker 发布与外部配置

> 索引：[docs/README.md](./README.md)。IaC 与仓库边界见 [infrastructure-as-code.md](./infrastructure-as-code.md)。

## 1. 激活 `prod` 配置

构建的镜像内已包含 [`application-prod.yml`](../platform-app/src/main/resources/application-prod.yml)。运行时设置：

```bash
-e SPRING_PROFILES_ACTIVE=prod
```

作用摘要：

- 关闭 **H2 控制台**（若仍误用 H2，至少不暴露控制台）。
- 启用 **`spring.config.import`**，可选加载挂载到 `/etc/media-platform/application.yaml` 的额外配置（文件不存在则忽略）。
- 将根日志默认设为 **INFO**（可按环境再覆盖）。

**生产环境仍须**通过环境变量或挂载文件提供 **PostgreSQL（或其它）数据源**；默认 `application.yml` 中的 H2 内存库不适合生产。

## 2. 环境变量（推荐）

Spring Boot 宽松绑定示例：

| 用途 | 变量示例 |
|------|----------|
| JDBC URL | `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/platform` |
| 用户名 / 密码 | `SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` |
| 本地文件存储根路径 | `APP_STORAGE_LOCAL_ROOT=/data/storage`（对应 `app.storage.local-root`） |
| 外部工具路径 | `MEDIA_FFMPEG_PATH`、`MEDIA_FFPROBE_PATH` |

完整 `docker run` 示例见根目录 [README.md](../README.md) 与 [docker-compose.yml](../docker-compose.yml)。

## 3. 挂载配置文件（`spring.config.import`）

在 `prod` profile 下已配置：

```yaml
spring.config.import: optional:file:/etc/media-platform/application.yaml
```

部署时可将 **ConfigMap 或主机文件** 挂载为只读文件，例如：

```bash
docker run ... \
  -e SPRING_PROFILES_ACTIVE=prod \
  -v /path/on/host/media-platform.yaml:/etc/media-platform/application.yaml:ro \
  media-platform:local
```

文件内容与 `application.yml` 相同层级（`spring:`、`app:` 等），用于补充或覆盖环境变量未覆盖的项。

若不需要该文件，**不挂载即可**（`optional:` 保证缺失时不报错）。

## 4. 数据卷与本地存储

`LocalFsStorageProvider` 使用 `app.storage.local-root`（默认 `./.data/storage`）。容器内需 **挂载可写卷** 到所选路径，并设置 `APP_STORAGE_LOCAL_ROOT` 与该路径一致，否则数据只写在容器可写层，删除容器即丢失。

## 5. 敏感信息

数据库密码、API 密钥等优先使用 **环境变量** 或 **Kubernetes Secret** 注入环境变量；避免将密文提交进 Git。`secrets-config-module` 可与后续 Vault / 云厂商密钥服务对接。
