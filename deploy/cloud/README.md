# Curmerce Cloud Local Runtime

This directory contains the reproducible user-level runtime layout for Curmerce's first service-extraction stage. It keeps every development endpoint on loopback and leaves private credentials outside Git.

## Runtime topology

| Process | Address | Responsibility |
| --- | --- | --- |
| Nacos 3.0.3 | `127.0.0.1:8848` | Service registration and discovery |
| `curmerce-core-service` | `127.0.0.1:48080` | Identity, members, products, inventory, orders, payments, refunds, and media |
| `curmerce-community-service` | `127.0.0.1:48083` | Community posts and interactions; owns only `community_*` tables |
| `curmerce-agent-service` | `127.0.0.1:48084` | Failure-isolated read-only product and community retrieval |
| `curmerce-gateway` | `127.0.0.1:48082` | Stable frontend entry point, routing, CORS, and trace IDs |
| `curmerce-web` | `127.0.0.1:3003` | Next.js acceptance frontend |

MySQL and Redis remain user-level loopback services. The current extraction intentionally shares one MySQL instance, but the community process accesses only community-owned tables. Cross-boundary member, product, permission, token, and media operations use the core service's internal HTTP contract.

## Build

Use JDK 21 and package the complete reactor:

```bash
mvn -DskipTests package
```

The deployable JARs are:

```text
yudao-server/target/yudao-server.jar
curmerce-services/curmerce-community-service/target/curmerce-community-service.jar
curmerce-services/curmerce-agent-service/target/curmerce-agent-service.jar
curmerce-services/curmerce-gateway/target/curmerce-gateway.jar
```

## Private configuration

Keep `/home/wugang/.config/curmerce-services/credentials.env` mode `0600`. In addition to the existing MySQL and Redis settings, define a random internal token of at least 32 characters:

```text
CURMERCE_INTERNAL_TOKEN=<random value with at least 32 characters>
```

The temporary fallback to `CURMERCE_OAUTH_CLIENT_SECRET` exists only for migration compatibility. A dedicated token is preferred because it can be rotated independently. Never expose the internal core API through the Gateway.

## Nacos installation

Download the official Nacos 3.0.3 server release, verify its SHA-256 digest, and extract it under the user's toolchain directory. The verified release digest used by this runtime is:

```text
4c222756f7e2e4004fc137738a4877c00203fc6ae029b771b3ee52d70eb3ef4c  nacos-server-3.0.3.tar.gz
```

The local installation path is `/home/wugang/.local/share/curmerce-toolchains/nacos-3.0.3`. Nacos runs in standalone server-only mode and must advertise and listen on `127.0.0.1`; the Console process is intentionally not started. Do not bind registry or management endpoints to a public interface.

## User services

The templates in [`systemd/`](./systemd/) document process ownership, ordering, memory limits, and restart behavior. Install them into `~/.config/systemd/user/`, then run:

```bash
systemctl --user daemon-reload
systemctl --user enable --now curmerce-nacos.service curmerce-yudao-server.service curmerce-community.service curmerce-agent.service curmerce-gateway.service curmerce-web.service
```

Inspect all states without exposing credentials:

```bash
systemctl --user --no-pager --full status curmerce-nacos.service curmerce-yudao-server.service curmerce-community.service curmerce-agent.service curmerce-gateway.service curmerce-web.service
```

## Verification

Use the Gateway for public and authenticated API checks. Direct service ports are for local diagnostics only.

```bash
curl --max-time 10 --fail http://127.0.0.1:48082/
curl --max-time 10 --fail http://127.0.0.1:48082/actuator/health
curl --max-time 10 --fail 'http://127.0.0.1:48082/app-api/commerce/catalog/product-page?pageNo=1&pageSize=5'
curl --max-time 10 --fail 'http://127.0.0.1:48082/app-api/community/post/page?pageNo=1&pageSize=5'
curl --max-time 10 --fail http://127.0.0.1:48082/app-api/agent/capabilities
```

For failure isolation, stop the community service and verify that community routing fails clearly while core commerce remains available and Agent assistance returns a result with `community` in `degradedSources`. Restart community and verify that it registers again without restarting Gateway.

```bash
systemctl --user stop curmerce-community.service
curl --max-time 10 -i -H 'X-Curmerce-Trace-Id: trace_failure_check' 'http://127.0.0.1:48082/app-api/community/post/page?pageNo=1&pageSize=2'
curl --max-time 10 --fail -H 'Content-Type: application/json' -d '{"query":"test"}' http://127.0.0.1:48082/app-api/agent/assist
curl --max-time 10 --fail 'http://127.0.0.1:48082/app-api/commerce/catalog/product-page?pageNo=1&pageSize=2'
systemctl --user start curmerce-community.service
curl --max-time 10 --fail http://127.0.0.1:48083/actuator/health
curl --max-time 10 --fail -H 'X-Curmerce-Trace-Id: trace_recovery_check' 'http://127.0.0.1:48082/app-api/community/post/page?pageNo=1&pageSize=2'
```

The expected failure response is HTTP `503` with the supplied trace ID. The Agent response remains HTTP `200` with business code `0` and includes `community` in `degradedSources`; the Core catalog remains available. After Community restarts, its health and routed API both return HTTP `200` without restarting Gateway.

---

# Curmerce Cloud 本地运行

本目录记录 Curmerce 第一次服务拆分阶段的可重复用户级运行方式。所有开发端口只监听回环地址，私有凭据始终保留在 Git 外部。

运行拓扑、端口和职责以文档开头的表格为准。MySQL 与 Redis 继续使用当前用户级回环服务。社区进程暂时与核心进程共享同一个 MySQL 实例，但只能访问自己拥有的 `community_*` 表；会员、商品、权限、Token 与媒体操作必须通过核心服务的内部 HTTP 契约完成。

使用 JDK 21 执行 `mvn -DskipTests package` 生成四个后端 JAR。私有配置继续放在权限为 `0600` 的 `/home/wugang/.config/curmerce-services/credentials.env` 中，并增加至少 32 字符的随机 `CURMERCE_INTERNAL_TOKEN`。兼容性的 OAuth 密钥回退仅用于迁移，内部 Token 应独立配置和轮换，内部核心接口不得经过 Gateway 暴露。

Nacos 3.0.3 安装包必须使用上方 SHA-256 校验，解压到 `/home/wugang/.local/share/curmerce-toolchains/nacos-3.0.3`，以 standalone server-only 模式运行，不启动 Console，并确保监听和注册地址均为 `127.0.0.1`。将 [`systemd/`](./systemd/) 模板安装到 `~/.config/systemd/user/` 后，使用上方命令统一启动、检查和停止服务。

验收时始终通过 Gateway 的 `48082` 访问业务 API，并为 `curl` 设置明确超时。故障隔离验收需要停掉 Community：社区路由应返回 HTTP `503` 并保留 Trace ID，核心商城仍可访问，Agent 继续返回业务码 `0` 并在 `degradedSources` 中标记 `community`；恢复 Community 后健康检查与 Gateway 路由都应恢复为 HTTP `200`，且无需重启 Gateway。具体命令和期望结果见上方英文段落。
